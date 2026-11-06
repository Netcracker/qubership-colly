package org.qubership.colly;

import io.smallrye.config.SmallRyeConfigBuilder;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.colly.podsecrets.SecretFilesConfigSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BASS Authenticators Handling Guide, requirements #4 and #8: the git tokens reach
 * the pod as files mounted from k8s Secrets (no environment variables) and a rotated
 * value must be picked up without a restart.
 *
 * <p>In production the {@link SecretFilesConfigSource} (registered in
 * {@code META-INF/services/org.eclipse.microprofile.config.spi.ConfigSource} and marked
 * {@code @StaticInitSafe}) exposes each mounted file as the configuration property that
 * carries the file name, so {@code ConfigProvider.getConfig()} resolves e.g.
 * {@code colly.eis.git.tokens.cn} to the content of the mounted file
 * {@code colly.eis.git.tokens.cn}. These tests wire the same source into
 * {@link GitService} the same way (a SmallRye config with the source at a zero TTL) and
 * verify the read and the rotation behaviour without booting the full application.
 */
class GitServiceSecretFilesTest {

    @TempDir
    Path secretsDir;

    private GitService gitServiceWithMountedSecrets() {
        GitService service = new GitService();
        // Zero TTL: re-read the "mounted" files on every access, mirroring the
        // mounted-k8s-secret behaviour (production TTL defaults to PT60S).
        Config config = new SmallRyeConfigBuilder()
                .withSources(new SecretFilesConfigSource(secretsDir, Duration.ZERO))
                .build();
        service.config = config;
        return service;
    }

    @Test
    void resolveToken_readsTokenValueFromMountedSecretFile() throws Exception {
        // A k8s Secret key mounted as a file named after the property it provides
        // (the chart renames secret keys to property names via `items:`).
        Files.writeString(secretsDir.resolve("colly.eis.git.tokens.file"), "token-from-file");

        assertEquals("token-from-file", gitServiceWithMountedSecrets().resolveToken(null, "file"));
    }

    @Test
    void resolveToken_picksUpRotatedTokenWithoutRestart() throws Exception {
        Path tokenFile = secretsDir.resolve("colly.eis.git.tokens.file");
        Files.writeString(tokenFile, "token-v1");
        GitService service = gitServiceWithMountedSecrets();
        assertEquals("token-v1", service.resolveToken(null, "file"));

        // Simulates kubelet swapping the mounted file content on secret rotation.
        Files.writeString(tokenFile, "token-v2");
        assertEquals("token-v2", service.resolveToken(null, "file"));
    }

    @Test
    void resolveToken_trailingNewlineFromFileDoesNotLeakIntoToken() throws Exception {
        // Secrets created via --from-file of an echo'd file carry a trailing newline;
        // it must not reach the GitLab API.
        Files.writeString(secretsDir.resolve("colly.eis.git.tokens.file"), "token-from-file\n");
        assertEquals("token-from-file", gitServiceWithMountedSecrets().resolveToken(null, "file"));
    }

    @Test
    void mountedFileSourceWinsOverLowerOrdinalEnvLikeSource() throws Exception {
        // Ordinal contract: the secret-files source (450) must override env-like sources
        // (ordinal 300) for the same property, whatever the source registration order.
        Config config = new SmallRyeConfigBuilder()
                .withSources(
                        new SecretFilesConfigSource(secretsDir, Duration.ZERO),
                        lowOrdinalSource(Map.of("colly.eis.git.tokens.file", "env-value")))
                .build();
        Files.writeString(secretsDir.resolve("colly.eis.git.tokens.file"), "file-value");
        assertEquals("file-value", config.getOptionalValue("colly.eis.git.tokens.file", String.class).orElse(null));

        Config reversedOrder = new SmallRyeConfigBuilder()
                .withSources(
                        lowOrdinalSource(Map.of("colly.eis.git.tokens.file", "env-value")),
                        new SecretFilesConfigSource(secretsDir, Duration.ZERO))
                .build();
        assertEquals("file-value",
                reversedOrder.getOptionalValue("colly.eis.git.tokens.file", String.class).orElse(null));
    }

    private static org.eclipse.microprofile.config.spi.ConfigSource lowOrdinalSource(Map<String, String> values) {
        return new org.eclipse.microprofile.config.spi.ConfigSource() {
            @Override
            public Map<String, String> getProperties() {
                return values;
            }

            @Override
            public int getOrdinal() {
                return 300; // same ordinal as EnvConfigSource
            }

            @Override
            public String getValue(String name) {
                return values.get(name);
            }

            @Override
            public Set<String> getPropertyNames() {
                return values.keySet();
            }

            @Override
            public String getName() {
                return "test-env-like-source";
            }
        };
    }
}
