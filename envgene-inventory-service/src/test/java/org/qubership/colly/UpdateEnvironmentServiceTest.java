package org.qubership.colly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.colly.cloudpassport.GitInfo;
import org.qubership.colly.cloudpassport.envgen.EnvDefinition;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.EnvironmentStatus;
import org.qubership.colly.db.data.EnvironmentType;
import org.qubership.colly.projectrepo.InstanceRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@QuarkusComponentTest
class UpdateEnvironmentServiceTest {

    @Inject
    UpdateEnvironmentService updateEnvironmentService;

    @TempDir
    Path tempDir;

    @InjectMock
    GitService gitService;

    private Cluster testCluster;
    private Environment testEnvironment;

    @BeforeEach
    void setUp() throws IOException {
        // Copy test resources to the temp directory
        Path testResourcesPath = Path.of("src/test/resources/gitrepo_with_cloudpassports");
        if (Files.exists(testResourcesPath)) {
            copyDirectory(testResourcesPath, tempDir.resolve("gitrepo_with_cloudpassports"));
        }

        GitInfo gitInfo = new GitInfo(new InstanceRepository("gitrepo_with_cloudpassports", "gitrepo_with_cloudpassports", "42"), tempDir.toString());
        testCluster = Cluster.builder().build();
        testCluster.setName("test-cluster");
        testCluster.setGitInfo(gitInfo);
        testEnvironment = new Environment("1", "env-test");
        testEnvironment.setDescription("new description");
        testEnvironment.setOwners(List.of("new owner"));
        testEnvironment.setTeams(List.of("new test-team"));
        testEnvironment.setLabels(List.of("ci", "dev"));
        testEnvironment.setType(EnvironmentType.DESIGN_TIME);
        testEnvironment.setRole("Dev");
        testEnvironment.setStatus(EnvironmentStatus.IN_USE);
        testEnvironment.setExpirationDate(LocalDate.of(2025, 12, 31));
    }

    @Test
    void updateEnvironment_shouldProcessExistingEnvironmentFile() throws IOException {
        updateEnvironmentService.updateEnvironment(testCluster, testEnvironment);

        Path path = Paths.get(testCluster.getGitInfo().folderName() + "/gitrepo_with_cloudpassports/test-cluster/env-test/Inventory/env_definition.yml");
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        EnvDefinition envDefinition = objectMapper.readValue(path.toFile(), EnvDefinition.class);
        assertEquals("new description", envDefinition.getInventory().getMetadata().description());
        assertNull(envDefinition.getInventory().getDescription());
        assertThat(envDefinition.getInventory().getMetadata().owners(), contains("new owner"));
        assertThat(envDefinition.getInventory().getMetadata().teams(), contains("new test-team"));
        assertNull(envDefinition.getInventory().getOwners());
        assertThat(envDefinition.getInventory().getMetadata().labels(), contains("ci", "dev"));
        assertEquals("DESIGN_TIME", envDefinition.getInventory().getMetadata().type());
        assertEquals("Dev", envDefinition.getInventory().getMetadata().role());
        assertThat(envDefinition.getInventory().getMetadata().status(), is("IN_USE"));
        assertThat(envDefinition.getInventory().getMetadata().expirationDate() , is("2025-12-31"));
        verify(gitService).commitAndPush(Paths.get(testCluster.getGitInfo().folderName()).toFile(), "Update environment " + testEnvironment.getName());
    }

    @Test
    void updateEnvironment_shouldHandleNonExistentEnvironment() {
        // Given
        Environment nonExistentEnv = new Environment("2", "non-existent-env");
        nonExistentEnv.setDescription("Non-existent environment");

        assertThrows(IllegalArgumentException.class, () -> updateEnvironmentService.updateEnvironment(testCluster, nonExistentEnv));
    }

    @Test
    void updateEnvironment_shouldHandleInvalidGitInfoPath() {
        GitInfo invalidGitInfo = new GitInfo(new InstanceRepository("test-repo", "test-repo", "42"), "/invalid/path");
        Cluster invalidCluster = Cluster.builder().build();
        invalidCluster.setName("invalid-cluster");
        invalidCluster.setGitInfo(invalidGitInfo);

        assertThrows(IllegalArgumentException.class, () -> updateEnvironmentService.updateEnvironment(invalidCluster, testEnvironment));
    }


    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy " + sourcePath, e);
            }
        });
    }
}
