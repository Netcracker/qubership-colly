package org.qubership.colly.services;

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

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    private static final String GLOBAL_KEY = "global";
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readEffectiveSetFile(Path filePath, ParamsetContext ctx) {
        if (!Files.isRegularFile(filePath)) {
            return Collections.emptyMap();
        }
        try (InputStream in = Files.newInputStream(filePath)) {
            if (ctx != ParamsetContext.DEPLOYMENT) {
                Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
                return loaded instanceof Map ? (Map<String, Object>) loaded : Collections.emptyMap();
            }
            return readDeploymentFileStrippingAliases(in);
        } catch (IOException e) {
            Log.errorf("Failed to read effective-set file %s: %s", filePath, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Parses a deployment YAML file and strips the GLOBAL_KEY key plus every top-level key
     * that is a YAML alias of GLOBAL_KEY (i.e. written as {@code service-X: *globalAnchor}).
     * <p>
     * SnakeYAML's compose() resolves alias references: after composing, an alias node and
     * its anchor become the same Node object. We use that reference identity to identify
     * alias keys without comparing values.
     */
    private static Map<String, Object> readDeploymentFileStrippingAliases(InputStream in) {
        NodeConstructor ctor = new NodeConstructor();
        Node root = new Yaml(ctor).compose(new InputStreamReader(in));
        if (!(root instanceof MappingNode mapping)) {
            return Collections.emptyMap();
        }

        // Find the Node that GLOBAL_KEY maps to; aliases of it share the same Node reference.
        Node globalNode = null;
        for (NodeTuple tuple : mapping.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode key && GLOBAL_KEY.equals(key.getValue())) {
                globalNode = tuple.getValueNode();
                break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (NodeTuple tuple : mapping.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode key)) continue;
            if (GLOBAL_KEY.equals(key.getValue())) continue;
            if (globalNode != null && tuple.getValueNode() == globalNode) continue; // alias of global
            result.put(key.getValue(), ctor.build(tuple.getValueNode()));
        }
        return result;
    }

    /**
     * Exposes SafeConstructor.constructObject so we can build individual value nodes.
     */
    private static final class NodeConstructor extends SafeConstructor {
        NodeConstructor() {
            super(new LoaderOptions());
        }

        Object build(Node node) {
            return constructObject(node);
        }
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
