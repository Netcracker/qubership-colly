package org.qubership.colly.services;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.qubership.colly.cloudpassport.Paramset;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.db.data.ParamsetContext;
import org.qubership.colly.db.data.ParamsetLevel;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class EffectiveSetCalculator {

    private static final String GLOBAL_KEY = "global";
    private static final String STATE_UNTOUCHED = "ui_override_untouched";
    private static final String STATE_COMMITTED = "ui_override_committed";
    private static final String STATE_UNCOMMITTED = "ui_override_uncommitted";
    private static final Object ABSENT = new Object();
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

        Map<String, Object> raw = effectiveSetCache.computeIfAbsent(cacheKey,
                k -> readEffectiveSetFile(filePath, ctx));

        Map<String, Object> paramsetLayer = deepCopy(raw);
        mergeApplicableParamsets(paramsetLayer, environment, ctx, deployPostfix, applicationName);
        Map<String, Object> requestLayer = deepCopy(paramsetLayer);
        if (requestParameters != null) {
            mergeInto(requestLayer, requestParameters);
        }

        return new EffectiveSetResponseDto(ctx.key(), environmentId, namespaceName, applicationName,
                wrapMap(requestLayer, paramsetLayer, raw));
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

    /**
     * Layers the environment's persisted paramsets (env/namespace/application-level UI overrides
     * already saved via POST /ui-parameters) on top of the raw effective-set file content, in
     * increasing order of specificity so an application-level override wins over a namespace one,
     * which in turn wins over an environment-level one.
     */
    private static void mergeApplicableParamsets(Map<String, Object> target, Environment environment,
                                                 ParamsetContext ctx, String deployPostfix, String applicationName) {
        for (ParamsetLevel level : new ParamsetLevel[]{ParamsetLevel.ENVIRONMENT, ParamsetLevel.NAMESPACE, ParamsetLevel.APPLICATION}) {
            for (Paramset paramset : environment.getParamsets()) {
                if (paramset.paramsetContext() == ctx && paramset.level() == level
                        && paramsetApplies(paramset, deployPostfix, applicationName)) {
                    mergeInto(target, paramset.parameters());
                }
            }
        }
    }

    private static boolean paramsetApplies(Paramset paramset, String deployPostfix, String applicationName) {
        return switch (paramset.level()) {
            case ENVIRONMENT -> true;
            case NAMESPACE -> deployPostfix != null && deployPostfix.equals(paramset.deployPostfix());
            case APPLICATION -> deployPostfix != null && deployPostfix.equals(paramset.deployPostfix())
                    && applicationName != null && applicationName.equals(paramset.applicationName());
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
        Map<String, Object> copy = LinkedHashMap.newLinkedHashMap(source.size());
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

    private static Object getOrAbsent(Map<String, Object> map, String key) {
        if (map == null) {
            return ABSENT;
        }
        return map.containsKey(key) ? map.get(key) : ABSENT;
    }

    private static Map<String, Object> wrapMap(Map<String, Object> finalMap, Map<String, Object> paramsetLayer,
                                               Map<String, Object> rawLayer) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : finalMap.entrySet()) {
            String key = entry.getKey();
            result.put(key, wrapValue(entry.getValue(), getOrAbsent(paramsetLayer, key), getOrAbsent(rawLayer, key)));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> wrapValue(Object finalVal, Object paramsetVal, Object rawVal) {
        if (finalVal instanceof Map<?, ?>) {
            Map<String, Object> paramsetSub = paramsetVal instanceof Map ? (Map<String, Object>) paramsetVal : null;
            Map<String, Object> rawSub = rawVal instanceof Map ? (Map<String, Object>) rawVal : null;
            return Map.of("_type", "container", "_data",
                    wrapMap((Map<String, Object>) finalVal, paramsetSub, rawSub));
        }
        return Map.of("_type", "leaf", "_data", computeLeafData(finalVal, paramsetVal, rawVal));
    }

    /**
     * Determines a leaf's UI-override state by comparing it across the three layers: the raw
     * Effective Set file, the applicable paramsets layered on top, and the request-body overlay
     * layered on top of that. A leaf is "uncommitted" if the request layer differs from (or adds to)
     * the paramset layer, "committed" if the paramset layer differs from (or adds to) the raw layer,
     * and "untouched" otherwise. originalValue is the value at the layer immediately below the one
     * that caused the current state.
     */
    private static Map<String, Object> computeLeafData(Object finalVal, Object paramsetVal, Object rawVal) {
        boolean paramsetAbsent = paramsetVal == ABSENT;
        boolean rawAbsent = rawVal == ABSENT;
        String state;
        Object originalValue;

        if (paramsetAbsent || !Objects.equals(finalVal, paramsetVal)) {
            state = STATE_UNCOMMITTED;
            originalValue = paramsetAbsent ? null : paramsetVal;
        } else if (rawAbsent || !Objects.equals(paramsetVal, rawVal)) {
            state = STATE_COMMITTED;
            originalValue = rawAbsent ? null : rawVal;
        } else {
            state = STATE_UNTOUCHED;
            originalValue = finalVal;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("value", finalVal);
        data.put("state", state);
        data.put("originalValue", originalValue);
        return data;
    }
}
