package org.qubership.colly.podsecrets.runtime;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * MicroProfile {@link ConfigSource} that exposes Kubernetes secret files mounted into
 * the pod as configuration properties.
 *
 * <p>BASS Authenticators Handling Guide, requirement #4 (mandatory): deployment-time
 * authenticators must be distributed through etcd (k8s) secrets and mounted as files in
 * the pods; passing them as environment variables is prohibited. This source lets the
 * pod carry only non-sensitive file paths while the secret values live in the mounted
 * files.
 *
 * <p>Every regular file under the secrets directory (default
 * {@code /etc/secrets/pod-secrets}) becomes a property whose name is the file name and
 * whose value is the file content. The directory is scanned recursively, so several
 * k8s Secrets can be mounted as sub-directories of one path (the chart renames secret
 * keys to property names via {@code items:}). Kubernetes swaps the mounted files
 * atomically on secret updates; the directory is therefore re-scanned after the TTL
 * (default {@code PT60S}) so a rotated authenticator is picked up without a pod
 * restart (guide requirement #8).
 *
 * <p>Configuration (names are compatible with the platform
 * {@code pod-secrets-config-source} conventions), resolved from the surrounding
 * configuration and then from environment/system properties:
 * <ul>
 *   <li>{@code pod.secrets.enabled} — set {@code false} to disable the source
 *       (default {@code true})</li>
 *   <li>{@code pod.secrets.dir} or env {@code POD_SECRETS_DIR} — the secrets
 *       directory (default {@code /etc/secrets/pod-secrets})</li>
 *   <li>{@code pod.secrets.ttl} — ISO-8601 duration between directory re-scans
 *       (default {@code PT60S})</li>
 * </ul>
 *
 * <p>Secret values are never logged (guide requirement #2); only directory and file
 * names are.
 */
public class SecretFilesConfigSource implements ConfigSource {

    private static final Logger log = Logger.getLogger(SecretFilesConfigSource.class.getName());

    /** Above EnvConfigSource (300) and SysPropsConfigSource (400), below Consul (500). */
    public static final int PRIORITY = 450;

    public static final Path DEFAULT_BASE_DIR = Path.of("/etc/secrets/pod-secrets");
    public static final Duration DEFAULT_TTL = Duration.ofSeconds(60);
    public static final String PROP_ENABLED = "pod.secrets.enabled";
    public static final String PROP_DIR = "pod.secrets.dir";
    public static final String PROP_TTL = "pod.secrets.ttl";
    public static final String ENV_DIR = "POD_SECRETS_DIR";

    private final Path baseDir;
    private final Duration ttl;
    private final AtomicReference<Map<String, String>> values = new AtomicReference<>(Map.of());
    private final AtomicLong expiresAtMillis = new AtomicLong(0);

    public SecretFilesConfigSource(Path baseDir, Duration ttl) {
        this.baseDir = baseDir;
        this.ttl = ttl;
    }

    @Override
    public String getName() {
        return "secret-files-config-source";
    }

    @Override
    public int getOrdinal() {
        return PRIORITY;
    }

    @Override
    public Map<String, String> getProperties() {
        return load();
    }

    @Override
    public Set<String> getPropertyNames() {
        return load().keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return load().get(propertyName);
    }

    private Map<String, String> load() {
        long now = System.currentTimeMillis();
        if (expiresAtMillis.get() > now) {
            return values.get();
        }
        synchronized (this) {
            if (expiresAtMillis.get() > now) {
                return values.get();
            }
            try {
                values.set(scanDirectory());
                log.fine(() -> "Loaded secret files: " + values.get().keySet() + " (dir=" + baseDir + ")");
            } catch (IOException | RuntimeException e) {
                // Keep the previous value on transient read errors so a rotating
                // volume never blanks a configuration property.
                log.log(Level.WARNING, "Failed to re-read secret files from " + baseDir + "; keeping previous values", e);
            }
            expiresAtMillis.set(System.currentTimeMillis() + ttl.toMillis());
            return values.get();
        }
    }

    private Map<String, String> scanDirectory() throws IOException {
        if (!Files.isDirectory(baseDir)) {
            // No mount (local development, tests): the source simply contributes nothing.
            log.fine(() -> "Secrets directory not found: " + baseDir);
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(baseDir)) {
            walk.filter(Files::isRegularFile)
                    .forEach(file -> {
                        String name = file.getFileName().toString();
                        if (result.containsKey(name)) {
                            log.warning("Duplicate secret file name '" + name + "' under " + baseDir
                                    + "; keeping the first one found");
                            return;
                        }
                        try {
                            result.put(name, Files.readString(file, StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            // A file may vanish between listing and reading while
                            // Kubernetes swaps the ..data directory.
                            log.log(Level.WARNING, "Cannot read secret file " + file, e);
                        }
                    });
        }
        return Map.copyOf(result);
    }
}
