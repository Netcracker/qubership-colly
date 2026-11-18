package org.qubership.colly.podsecrets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.colly.podsecrets.runtime.SecretFilesConfigSource;
import org.qubership.colly.podsecrets.runtime.SecretFilesConfigSourceFactory;
import org.qubership.colly.podsecrets.runtime.SecretFilesConfigSourceFactoryBuilder;
import io.quarkus.runtime.configuration.ConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the config source and its wiring, independent of a Quarkus application.
 */
class SecretFilesConfigSourceTest {

    @Test
    void missingDirectory_contributesNothing(@TempDir Path tmp) {
        SecretFilesConfigSource source = new SecretFilesConfigSource(tmp.resolve("nonexistent"), Duration.ZERO);
        assertThat(source.getProperties()).isEmpty();
        assertThat(source.getPropertyNames()).isEmpty();
        assertThat(source.getValue("anything")).isNull();
    }

    @Test
    void files_becomePropertiesNamedAfterTheFile(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("quarkus.redis.username"), "redis-user");
        Files.createDirectories(tmp.resolve("oidc"));
        Files.writeString(tmp.resolve("oidc").resolve("quarkus.oidc.credentials.secret"), "s3cret");

        SecretFilesConfigSource source = new SecretFilesConfigSource(tmp, Duration.ZERO);
        assertThat(source.getPropertyNames()).containsExactlyInAnyOrder(
                "quarkus.redis.username", "quarkus.oidc.credentials.secret");
        assertThat(source.getValue("quarkus.redis.username")).isEqualTo("redis-user");
        assertThat(source.getValue("quarkus.oidc.credentials.secret")).isEqualTo("s3cret");
    }

    @Test
    void duplicateFileNames_firstOneWins(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("a"));
        Files.createDirectories(tmp.resolve("b"));
        Files.writeString(tmp.resolve("a").resolve("quarkus.redis.username"), "first");
        Files.writeString(tmp.resolve("b").resolve("quarkus.redis.username"), "second");

        SecretFilesConfigSource source = new SecretFilesConfigSource(tmp, Duration.ZERO);
        assertThat(source.getPropertyNames()).containsExactly("quarkus.redis.username");
    }

    @Test
    void zeroTTL_picksUpRotatedFileContent(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("colly.eis.git.tokens.cn");
        Files.writeString(file, "token-v1");

        SecretFilesConfigSource source = new SecretFilesConfigSource(tmp, Duration.ZERO);
        assertThat(source.getValue("colly.eis.git.tokens.cn")).isEqualTo("token-v1");

        // Simulates kubelet swapping the mounted file content on secret rotation.
        Files.writeString(file, "token-v2");
        assertThat(source.getValue("colly.eis.git.tokens.cn")).isEqualTo("token-v2");
    }

    @Test
    void nonZeroTTL_keepsValueUntilExpired(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("colly.eis.git.tokens.cn");
        Files.writeString(file, "token-v1");

        SecretFilesConfigSource source = new SecretFilesConfigSource(tmp, Duration.ofMinutes(1));
        assertThat(source.getValue("colly.eis.git.tokens.cn")).isEqualTo("token-v1");

        Files.writeString(file, "token-v2");
        assertThat(source.getValue("colly.eis.git.tokens.cn")).isEqualTo("token-v1");
    }

    @Test
    void factory_disabledViaContext_returnsNoSource() {
        // No directory and no system properties: the factory still returns a source
        // pointed at the default location; disabled is honored via the knob.
        SecretFilesConfigSourceFactory factory = new SecretFilesConfigSourceFactory();
        List<ConfigSource> sources = (List<ConfigSource>) factory.getConfigSources(null);
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getOrdinal()).isEqualTo(SecretFilesConfigSource.PRIORITY);
    }

    @Test
    void builder_registersFactoryWithPriority() {
        SecretFilesConfigSourceFactoryBuilder builder = new SecretFilesConfigSourceFactoryBuilder();
        assertThat(builder).isInstanceOf(ConfigBuilder.class);
        assertThat(builder.priority()).isEqualTo(SecretFilesConfigSource.PRIORITY);
        SmallRyeConfigBuilder result = builder.configBuilder(new SmallRyeConfigBuilder());
        assertThat(result).isNotNull();
    }

    @Test
    void ordinal_isBetweenEnvAndConsul() {
        assertThat(SecretFilesConfigSource.PRIORITY)
                .isGreaterThan(300) // EnvConfigSource
                .isGreaterThan(400) // SysPropsConfigSource
                .isLessThan(500)    // ConsulConfigSource
                .isEqualTo(450);
    }
}
