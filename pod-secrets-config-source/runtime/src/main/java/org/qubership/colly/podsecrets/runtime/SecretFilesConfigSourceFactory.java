package org.qubership.colly.podsecrets.runtime;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SmallRye {@link ConfigSourceFactory} that creates the {@link SecretFilesConfigSource}
 * for the pod secrets directory.
 *
 * <p>The knobs are read from the surrounding configuration first (so they can be set in
 * {@code application.properties} or test configuration) and then from environment /
 * system properties (available at config assembly time, when no application properties
 * are loaded yet in non-Quarkus contexts).
 */
public class SecretFilesConfigSourceFactory implements ConfigSourceFactory {

    private static final Logger log = Logger.getLogger(SecretFilesConfigSourceFactory.class.getName());

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        String enabled = readValue(context, SecretFilesConfigSource.PROP_ENABLED);
        if (enabled != null && !Boolean.parseBoolean(enabled.trim())) {
            log.fine("Secret files config source is disabled via " + SecretFilesConfigSource.PROP_ENABLED);
            return Collections.emptyList();
        }
        String dir = readValue(context, SecretFilesConfigSource.PROP_DIR);
        if (dir == null) {
            dir = System.getProperty(SecretFilesConfigSource.PROP_DIR);
        }
        if (dir == null || dir.isBlank()) {
            dir = System.getenv(SecretFilesConfigSource.ENV_DIR);
        }
        Path baseDir = (dir == null || dir.isBlank())
                ? SecretFilesConfigSource.DEFAULT_BASE_DIR
                : Path.of(dir);

        Duration ttl = SecretFilesConfigSource.DEFAULT_TTL;
        String ttlValue = readValue(context, SecretFilesConfigSource.PROP_TTL);
        if (ttlValue == null) {
            ttlValue = System.getProperty(SecretFilesConfigSource.PROP_TTL);
        }
        if (ttlValue != null && !ttlValue.isBlank()) {
            try {
                ttl = Duration.parse(ttlValue.trim());
            } catch (RuntimeException e) {
                log.warning("Invalid " + SecretFilesConfigSource.PROP_TTL + " value '" + ttlValue
                        + "', using " + SecretFilesConfigSource.DEFAULT_TTL);
            }
        }
        return Collections.singletonList(new SecretFilesConfigSource(baseDir, ttl));
    }

    private static String readValue(ConfigSourceContext context, String key) {
        if (context == null) {
            return null;
        }
        ConfigValue value = context.getValue(key);
        if (value == null) {
            return null;
        }
        String s = value.getValue();
        return (s == null || s.isBlank()) ? null : s;
    }
}
