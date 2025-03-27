package org.qubership.colly;

import io.kubernetes.client.util.KubeConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class KubeConfigLoader {

    @ConfigProperty(name = "kubeconfigs.path")
    Optional<String> kubeconfigFolder;

    public List<KubeConfig> loadKubeConfigs() {
        if (kubeconfigFolder.isEmpty()) {
            Log.error("kubeconfigs.path parameter is not set. Skipping kubeconfigs loading.");
            return Collections.emptyList();
        }
        List<KubeConfig> kubeConfigs = new ArrayList<>();
        Path dir = Paths.get(kubeconfigFolder.get());
        Log.debug("Loading kubeconfigs from " + dir);
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(Files::isRegularFile)
                    .forEach(fileName -> {
                        try {
                            kubeConfigs.add(KubeConfig.loadKubeConfig(Files.newBufferedReader(fileName)));
                        } catch (IOException e) {
                            throw new RuntimeException("[ERROR] unable to read file - " + fileName, e);
                        }
                    });
        } catch (IOException e) {
            Log.error("[ERROR] Failed to read files: " + e.getMessage());
        }
        Log.info("[INFO] Loaded " + kubeConfigs.size() + " kubeconfigs");
        return kubeConfigs;
    }
}
