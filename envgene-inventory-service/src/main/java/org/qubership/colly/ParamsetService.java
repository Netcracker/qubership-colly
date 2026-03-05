package org.qubership.colly;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.qubership.colly.cloudpassport.Paramset;
import org.qubership.colly.cloudpassport.envgen.EnvTemplate;
import org.qubership.colly.cloudpassport.envgen.ParamsetFileData;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.db.data.ParamsetContext;
import org.qubership.colly.db.data.ParamsetLevel;
import org.qubership.colly.dto.ParameterDto;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ParamsetService {

    @Inject
    YqService yqService;


    public List<Paramset> parseParamsets(EnvTemplate envTemplate, Path inventoryDir) {
        if (envTemplate == null) {
            return List.of();
        }
        List<Paramset> paramsets = new ArrayList<>();
        parseParamsetsForContext(envTemplate.envSpecificParamsets(), ParamsetContext.DEPLOYMENT, "deploy-ui-override", inventoryDir, paramsets);
        parseParamsetsForContext(envTemplate.envSpecificTechnicalParamsets(), ParamsetContext.RUNTIME, "runtime-ui-override", inventoryDir, paramsets);
        parseParamsetsForContext(envTemplate.envSpecificE2EParamsets(), ParamsetContext.PIPELINE, "pipeline-ui-override", inventoryDir, paramsets);
        return paramsets;
    }

    private void parseParamsetsForContext(Map<String, List<String>> paramsetMap, ParamsetContext paramsetContext,
                                          String suffix, Path inventoryDir, List<Paramset> result) {
        if (paramsetMap == null) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : paramsetMap.entrySet()) {
            String deployPostfix = entry.getKey();
            List<String> paramsetNames = entry.getValue();
            if (paramsetNames == null) {
                continue;
            }
            for (String paramsetName : paramsetNames) {
                if (!paramsetName.contains(suffix)) {
                    continue;
                }
                Paramset paramset = parseParamsetFile(paramsetName, deployPostfix, paramsetContext, suffix, inventoryDir);
                if (paramset != null) {
                    result.add(paramset);
                }
            }
        }
    }

    private Paramset parseParamsetFile(String paramsetName, String deployPostfix, ParamsetContext paramsetContext,
                                       String suffix, Path inventoryDir) {
        Path paramsetFilePath = inventoryDir.resolve("parameters").resolve(paramsetName + ".yaml");
        if (!Files.isRegularFile(paramsetFilePath)) {
            Log.warn("Paramset file not found: " + paramsetFilePath);
            return null;
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (FileInputStream inputStream = new FileInputStream(paramsetFilePath.toFile())) {
            ParamsetFileData fileData = mapper.readValue(inputStream, ParamsetFileData.class);

            ParamsetLevel level;
            String applicationName = null;
            Map<String, String> parameters;

            if (paramsetName.equals(suffix) && deployPostfix.equals("cloud")) { //cloud is the reserved word for environment level paramsets
                level = ParamsetLevel.ENVIRONMENT;
                parameters = fileData.parameters() != null ? fileData.parameters() : Map.of();
            } else if (paramsetName.equals(deployPostfix + "-" + suffix)) {
                level = ParamsetLevel.NAMESPACE;
                parameters = fileData.parameters() != null ? fileData.parameters() : Map.of();
            } else if (paramsetName.startsWith(deployPostfix + "-") && paramsetName.endsWith("-" + suffix)) {
                level = ParamsetLevel.APPLICATION;
                String prefix = deployPostfix + "-";
                String suffixWithDash = "-" + suffix;
                applicationName = paramsetName.substring(prefix.length(), paramsetName.length() - suffixWithDash.length());
                parameters = extractApplicationParameters(fileData, applicationName);
            } else {
                Log.warn("Cannot determine level for paramset: " + paramsetName + " with deployPostfix: " + deployPostfix);
                return null;
            }

            return new Paramset(paramsetContext, level, deployPostfix, applicationName, parameters);
        } catch (IOException e) {
            Log.error("Error reading paramset file: " + paramsetFilePath, e);
            return null;
        }
    }

    private Map<String, String> extractApplicationParameters(ParamsetFileData fileData, String applicationName) {
        if (fileData.applications() == null) {
            return Map.of();
        }
        return fileData.applications().stream()
                .filter(app -> applicationName.equals(app.appName()))
                .findFirst()
                .map(app -> app.parameters() != null ? app.parameters() : Map.<String, String>of())
                .orElse(Map.of());
    }

    public void writeParamsetFile(Path inventoryDir, ParamsetTarget target,
                                  String applicationName, ParamsetContext context,
                                  List<ParameterDto> parameters) throws IOException {
        String fileName = calculateParamsetFileName(target.level(), target.deployPostfix(), applicationName, context);
        Path filePath = calculateParamsetFilePath(inventoryDir, target.level(), target.deployPostfix(), applicationName, context);
        Files.createDirectories(filePath.getParent());
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        Map<String, String> newParams = new LinkedHashMap<>();
        parameters.forEach(p -> newParams.put(p.name(), p.value()));

        ParamsetFileData fileData;
        if (target.level() == ParamsetLevel.APPLICATION) {
            List<ParamsetFileData.ParamsetApplicationData> apps = new ArrayList<>();
            if (Files.isRegularFile(filePath)) {
                ParamsetFileData existing = mapper.readValue(filePath.toFile(), ParamsetFileData.class);
                if (existing.applications() != null) {
                    existing.applications().stream()
                            .filter(a -> !applicationName.equals(a.appName()))
                            .forEach(apps::add);
                }
            }
            apps.add(new ParamsetFileData.ParamsetApplicationData(applicationName, newParams));
            fileData = new ParamsetFileData(fileName, Map.of(), apps);
        } else {
            fileData = new ParamsetFileData(fileName, newParams, List.of());
        }

        mapper.writeValue(filePath.toFile(), fileData);
        Log.info("Written paramset file: " + filePath);
    }

    public void deleteParamsetFile(Path inventoryDir, ParamsetTarget target,
                                   String applicationName, ParamsetContext context) throws IOException {
        Path filePath = calculateParamsetFilePath(inventoryDir, target.level(), target.deployPostfix(), applicationName, context);
        if (!Files.isRegularFile(filePath)) {
            return;
        }
        Files.delete(filePath);
        Log.info("Deleted paramset file: " + filePath);
    }

    // -------------------------------------------------------------------------
    // Reference management in env_definition.yml
    // -------------------------------------------------------------------------

    public void addParamsetReferenceToEnvDefinition(Path inventoryDir, ParamsetContext context,
                                                    ParamsetTarget target, String applicationName) throws IOException {
        if (!yqService.isYqAvailable()) {
            throw new IllegalStateException("yq is not available. Please install yq to use this feature.");
        }
        Path envDefPath = inventoryDir.resolve("env_definition.yml");
        if (!Files.isRegularFile(envDefPath)) {
            Log.warn("env_definition.yml not found at " + envDefPath + ", skipping reference update");
            return;
        }
        String paramsetName = calculateParamsetFileName(target.level(), target.deployPostfix(), applicationName, context);
        String sectionName = getEnvTemplateSectionName(context);
        String yqArrayPath = ".envTemplate." + sectionName + "[\"" + target.deployPostfix() + "\"]";
        yqService.addToYamlArrayUnique(envDefPath, yqArrayPath, paramsetName);
        Log.info("Added paramset reference '" + paramsetName + "' to " + sectionName + "[" + target.deployPostfix() + "]");
    }

    public void removeParamsetReferenceFromEnvDefinition(Path inventoryDir, ParamsetContext context,
                                                         ParamsetTarget target, String applicationName) throws IOException {
        Path envDefPath = inventoryDir.resolve("env_definition.yml");
        if (!Files.isRegularFile(envDefPath)) {
            Log.warn("env_definition.yml not found at " + envDefPath + ", skipping reference removal");
            return;
        }
        String paramsetName = calculateParamsetFileName(target.level(), target.deployPostfix(), applicationName, context);
        String sectionName = getEnvTemplateSectionName(context);
        String yqPath = ".envTemplate." + sectionName + "[\"" + target.deployPostfix() + "\"][] | select(. == \""
                + yqService.escapeForYq(paramsetName) + "\")";
        yqService.deleteYamlField(envDefPath, yqPath);
        Log.info("Removed paramset reference '" + paramsetName + "' from " + sectionName + "[" + target.deployPostfix() + "]");
    }

    // -------------------------------------------------------------------------
    // Naming utilities
    // -------------------------------------------------------------------------

    private String calculateParamsetFileName(ParamsetLevel level, String deployPostfix, String applicationName, ParamsetContext context) {
        String suffix = calculateFileSuffix(context);
        return switch (level) {
            case ENVIRONMENT -> suffix;
            case NAMESPACE -> deployPostfix + "-" + suffix;
            case APPLICATION -> deployPostfix + "-" + applicationName + "-" + suffix;
        };
    }

    private Path calculateParamsetFilePath(Path inventoryDir, ParamsetLevel level, String deployPostfix,
                                           String applicationName, ParamsetContext context) {
        String fileName = calculateParamsetFileName(level, deployPostfix, applicationName, context);
        return inventoryDir.resolve("parameters").resolve(fileName + ".yaml");
    }

    private String calculateFileSuffix(ParamsetContext context) {
        return switch (context) {
            case DEPLOYMENT -> "deploy-ui-override";
            case RUNTIME -> "runtime-ui-override";
            case PIPELINE -> "pipeline-ui-override";
        };
    }

    private String getEnvTemplateSectionName(ParamsetContext context) {
        return switch (context) {
            case DEPLOYMENT -> "envSpecificParamsets";
            case RUNTIME -> "envSpecificTechnicalParamsets";
            case PIPELINE -> "envSpecificE2EParamsets";
        };
    }

    public ParamsetTarget resolveParamsetTarget(Environment environment, String namespaceName, String applicationName) {
        ParamsetLevel requestedLevel;
        String deployPostfix = "cloud";

        if (applicationName != null && !applicationName.isEmpty()) {
            requestedLevel = ParamsetLevel.APPLICATION;
        } else if (namespaceName != null && !namespaceName.isEmpty()) {
            requestedLevel = ParamsetLevel.NAMESPACE;
        } else {
            requestedLevel = ParamsetLevel.ENVIRONMENT;
        }

        if (requestedLevel != ParamsetLevel.ENVIRONMENT) {
            Namespace namespace = environment.getNamespaces().stream()
                    .filter(ns -> ns.getName().equals(namespaceName))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Namespace with name=" + namespaceName + " not found in environment " + environment.getId()));
            deployPostfix = namespace.getDeployPostfix();
        }

        return new ParamsetTarget(requestedLevel, deployPostfix);
    }

    public record ParamsetTarget(ParamsetLevel level, String deployPostfix) {
    }
}
