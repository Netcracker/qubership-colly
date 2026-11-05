package org.qubership.colly.podsecrets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the config source, independent of a Quarkus application.
 */
class SecretFilesConfigSourceTest {

    private static final String[] KNOB_PROPS = {"pod.secrets.dir", "pod.secrets.ttl", "pod.secrets.enabled"};

    @AfterEach
    void clearKnobs() {
        for (String prop : KNOB_PROPS) {
            System.clearProperty(prop);
        }
    }

    @Test
    void missingDirectory_contributesNothing(@TempDir Path tmp) {
        SecretFilesConfigSource source = new SecretFilesConfigSource(tmp.resolve("nonexistent"), Duration.ZERO);
        assertTrue(source.getProperties().isEmpty());
        assertTrue(source.getPropertyNames().isEmpty());
        assertNull(source.getValue("anything"));
    }

    @Test
    void files_becomePropertiesNamedAfterTheFile(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("quarkus.redis.username"), "redis-user");
        Files.createDirectories(tmp.resolve("oidc"));
        Files.writeString(tmp.resolve("oidc").resolve("quarkus.oidc.credentials.secret"), "s3cret");

        SecretFilesConfigSource source = new SecretFilesConfigSource(tmp, Duration.ZERO);
        assertEquals(Set.of("quarkus.redis.username", "quarkus.oidc.credentials.secret"),
                source.getPropertyNames());
        assertEquals("redis-user", source.getValue("quarkus.redis.username"));
        assertEquals("s3cret", source.getValue("quarkus.oidc.credentials.secret"));
    }

    @Test
    void duplicateFileNames_firstOneWins(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("a"));
        Files.createDirectories(tmp.resolve("b"));
        Files.writeString(tmp.resolve("a").resolve("quarkus.redis.username"), "first");
        Files.writeString(tmp.resolve("b").resolve("quarkus.redis.username"), "second");

        SecretFilesConfigSource source = new SecretFilesConfigSource(tmp, Duration.ZERO);
        assertEquals(Set.of("quarkus.redis.username"), source.getPropertyNames());
    }

    @Test
    void zeroTTL_picksUpRotatedFileContent(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("colly.eis.git.tokens.cn");
        Files.writeString(file, "token-v1");

        SecretFilesConfigSource source = new SecretFilesConfigSource(tmp, Duration.ZERO);
        assertEquals("token-v1", source.getValue("colly.eis.git.tokens.cn"));

        // Simulates kubelet swapping the mounted file content on secret rotation.
        Files.writeString(file, "token-v2");
        assertEquals("token-v2", source.getValue("colly.eis.git.tokens.cn"));
    }

    @Test
    void nonZeroTTL_keepsValueUntilExpired(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("colly.eis.git.tokens.cn");
        Files.writeString(file, "token-v1");

        SecretFilesConfigSource source = new SecretFilesConfigSource(tmp, Duration.ofMinutes(1));
        assertEquals("token-v1", source.getValue("colly.eis.git.tokens.cn"));

        Files.writeString(file, "token-v2");
        assertEquals("token-v1", source.getValue("colly.eis.git.tokens.cn"));
    }

    @Test
    void serviceLoaderConstructor_readsDirectoryFromSystemProperty(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("quarkus.redis.username"), "redis-user");
        System.setProperty("pod.secrets.dir", tmp.toString());
        System.setProperty("pod.secrets.ttl", "PT0S");

        try {
            SecretFilesConfigSource source = new SecretFilesConfigSource();
            assertEquals("redis-user", source.getValue("quarkus.redis.username"));
        } finally {
            clearKnobs();
        }
    }

    @Test
    void serviceLoaderConstructor_disabledContributesNothing(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("quarkus.redis.username"), "redis-user");
        System.setProperty("pod.secrets.dir", tmp.toString());
        System.setProperty("pod.secrets.enabled", "false");

        try {
            SecretFilesConfigSource source = new SecretFilesConfigSource();
            assertTrue(source.getPropertyNames().isEmpty());
            assertNull(source.getValue("quarkus.redis.username"));
        } finally {
            clearKnobs();
        }
    }

    @Test
    void ordinal_isBetweenEnvAndConsul() {
        assertTrue(SecretFilesConfigSource.PRIORITY > 300, "above EnvConfigSource");
        assertTrue(SecretFilesConfigSource.PRIORITY > 400, "above SysPropsConfigSource");
        assertTrue(SecretFilesConfigSource.PRIORITY < 500, "below ConsulConfigSource");
        assertEquals(450, SecretFilesConfigSource.PRIORITY);
        assertFalse(SecretFilesConfigSource.DEFAULT_BASE_DIR.toString().isEmpty());
    }
}
