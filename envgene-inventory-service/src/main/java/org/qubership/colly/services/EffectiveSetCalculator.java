package org.qubership.colly.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.db.data.ParamsetContext;
import org.qubership.colly.dto.EffectiveSetResponseDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class EffectiveSetCalculator {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final ConcurrentHashMap<String, Map<String, Object>> effectiveSetCache = new ConcurrentHashMap<>();

    private final EnvironmentRepository environmentRepository;

    @Inject
    public EffectiveSetCalculator(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    public void clearCache() {
        effectiveSetCache.clear();
    }

    public EffectiveSetResponseDto getEffectiveSet(String environmentId, String context,
                                                   String namespaceName, String applicationName, Map<String, Object> requestParameters) {

        ParamsetContext ctx = ParamsetContext.fromKey(context);
        if (ctx == null) {
            throw new BadRequestException("context must be one of: " +
                    Arrays.stream(ParamsetContext.values()).map(ParamsetContext::key).collect(Collectors.joining(", ")));
        }

        if (ctx == ParamsetContext.PIPELINE) {
            if (namespaceName != null || applicationName != null) {
                throw new BadRequestException("namespaceName and applicationName must not be present for context=" + ctx.key());
            }
        } else {
            if (namespaceName == null || namespaceName.isBlank()) {
                throw new BadRequestException("namespaceName is required for context=" + ctx.key());
            }
            if (applicationName == null || applicationName.isBlank()) {
                throw new BadRequestException("applicationName is required for context=" + ctx.key());
            }
        }

        Environment environment = environmentRepository.findById(environmentId);
        if (environment == null) {
            throw new NotFoundException("Environment with id=" + environmentId + " not found");
        }

        String deployPostfix = null;
        if (ctx != ParamsetContext.PIPELINE) {
            deployPostfix = environment.getNamespaces().stream()
                    .filter(ns -> namespaceName.equals(ns.getName()))
                    .map(Namespace::getDeployPostfix)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(
                            "Namespace '" + namespaceName + "' not found in environment " + environmentId));

            String dp = deployPostfix;
            boolean appExists = environment.getSdApplications().stream()
                    .anyMatch(app -> dp.equals(app.deployPostfix())
                            && applicationName.equals(app.version().contains(":") ? app.version().split(":")[0] : app.version()));
            if (!appExists) {
                throw new NotFoundException(
                        "Application '" + applicationName + "' not found for namespace '" + namespaceName + "'");
            }
        }

        Path filePath = resolveEffectiveSetFilePath(environment.getEffectiveSetPath(), ctx, deployPostfix, applicationName);
        String cacheKey = environmentId + ":" + ctx.key() + ":" + deployPostfix + ":" + applicationName;

        Map<String, Object> cached = effectiveSetCache.computeIfAbsent(cacheKey,
                k -> readEffectiveSetFile(filePath, ctx));

        Map<String, Object> merged = deepCopy(cached);
        if (requestParameters != null) {
            mergeInto(merged, requestParameters);
        }

        return new EffectiveSetResponseDto(ctx.key(), environmentId, namespaceName, applicationName, wrapMap(merged));
    }

    private static Path resolveEffectiveSetFilePath(String root, ParamsetContext ctx, String deployPostfix, String applicationName) {
        Path r = Path.of(root);
        return switch (ctx) {
            case DEPLOYMENT -> r.resolve(ctx.key()).resolve(deployPostfix).resolve(applicationName)
                    .resolve("values").resolve("deployment-parameters.yaml");
            case RUNTIME -> r.resolve(ctx.key()).resolve(deployPostfix).resolve(applicationName)
                    .resolve("parameters.yaml");
            case PIPELINE -> r.resolve(ctx.key()).resolve("parameters.yaml");
        };
    }

    private static Map<String, Object> readEffectiveSetFile(Path filePath, ParamsetContext ctx) {
        if (!Files.isRegularFile(filePath)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = YAML_MAPPER.readValue(filePath.toFile(), Map.class);
            return ctx == ParamsetContext.DEPLOYMENT ? filterGlobalAliases(raw) : raw;
        } catch (IOException e) {
            Log.errorf("Failed to read effective-set file %s: %s", filePath, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static Map<String, Object> filterGlobalAliases(Map<String, Object> raw) {
        Object globalVal = raw.get("global");
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if ("global".equals(entry.getKey())) continue;
            if (globalVal != null && entry.getValue() == globalVal) continue;
            filtered.put(entry.getKey(), entry.getValue());
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object val = entry.getValue();
            copy.put(entry.getKey(), val instanceof Map ? deepCopy((Map<String, Object>) val) : val);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static void mergeInto(Map<String, Object> target, Map<String, Object> overlay) {
        for (Map.Entry<String, Object> entry : overlay.entrySet()) {
            String key = entry.getKey();
            Object overlayVal = entry.getValue();
            if (overlayVal instanceof Map && target.get(key) instanceof Map) {
                mergeInto((Map<String, Object>) target.get(key), (Map<String, Object>) overlayVal);
            } else {
                target.put(key, overlayVal);
            }
        }
    }

    private static Map<String, Object> wrapMap(Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            result.put(entry.getKey(), wrapValue(entry.getValue()));
        }
        return result;
    }

    private static Map<String, Object> wrapValue(Object value) {
        if (value instanceof Map<?, ?>) {
            return Map.of("_type", "container", "_data", wrapMap((Map<String, Object>) value));
        }
        Map<String, Object> leafData = new LinkedHashMap<>();
        leafData.put("value", value);
        leafData.put("state", "ui_override_untouched");
        leafData.put("originalValue", value);
        return Map.of("_type", "leaf", "_data", leafData);
    }
}
