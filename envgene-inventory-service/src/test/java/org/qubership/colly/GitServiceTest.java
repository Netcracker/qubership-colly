package org.qubership.colly;

import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusComponentTest
@TestConfigProperty(key = "colly.eis.git.token", value = "test-token")
class GitServiceTest {

    @Inject
    GitService gitService;

    @TempDir
    Path tempDir;

    private String bareRepoUri;

    @BeforeEach
    void setUp() throws Exception {
        Path bareRepo = tempDir.resolve("remote.git");
        Path initialWork = tempDir.resolve("init-work");

        Git.init().setBare(true).setDirectory(bareRepo.toFile()).call().close();

        bareRepoUri = bareRepo.toUri().toString();

        try (Git git = Git.init()
                .setDirectory(initialWork.toFile())
                .setInitialBranch("main")
                .call()) {
            Files.writeString(initialWork.resolve("README.md"), "initial content");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit").setAuthor("setup", "setup@test.com").call();
            git.remoteAdd().setName("origin").setUri(new URIish(bareRepoUri)).call();
            git.push().setRemote("origin").setPushAll().call();
        }
    }

    @Test
    void cloneRepository_shouldCloneSuccessfully() {
        Path dest = tempDir.resolve("cloned");
        gitService.cloneRepository(bareRepoUri, "main", "", dest.toFile());

        assertTrue(Files.exists(dest.resolve("README.md")));
    }

    @Test
    void cloneRepository_shouldCloneWithNullToken() {
        Path dest = tempDir.resolve("cloned-null-token");

        gitService.cloneRepository(bareRepoUri, "main", null, dest.toFile());

        assertTrue(Files.exists(dest.resolve("README.md")));
    }

    @Test
    void cloneRepository_shouldCloneWithBlankToken() {
        Path dest = tempDir.resolve("cloned-blank-token");

        gitService.cloneRepository(bareRepoUri, "main", "", dest.toFile());

        assertTrue(Files.exists(dest.resolve("README.md")));
    }

    @Test
    void cloneRepository_shouldThrowOnInvalidUrl() {
        Path dest = tempDir.resolve("cloned-invalid");
        assertThrows(IllegalStateException.class, () ->
                gitService.cloneRepository("https://invalid.invalid/repo.git", "main", "", dest.toFile()));
    }

    @Test
    void commitAndPush_shouldPushNewFile() throws Exception {
        Path localRepo = tempDir.resolve("local");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());

        Files.writeString(localRepo.resolve("new-file.txt"), "some content");
        gitService.commitAndPush(localRepo.toFile(), "add new-file.txt", "", "Test User", "test@test.com");

        Path verify = tempDir.resolve("verify");
        gitService.cloneRepository(bareRepoUri, "main", "", verify.toFile());
        assertTrue(Files.exists(verify.resolve("new-file.txt")));
        assertEquals("some content", Files.readString(verify.resolve("new-file.txt")));
    }

    @Test
    void commitAndPush_shouldSetAuthorWhenProvided() throws Exception {
        Path localRepo = tempDir.resolve("local-author");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());

        Files.writeString(localRepo.resolve("file.txt"), "content");
        gitService.commitAndPush(localRepo.toFile(), "authored commit", "", "John Doe", "john@example.com");

        try (Git git = Git.open(localRepo.toFile())) {
            RevCommit commit = git.log().setMaxCount(1).call().iterator().next();
            assertEquals("John Doe", commit.getAuthorIdent().getName());
            assertEquals("john@example.com", commit.getAuthorIdent().getEmailAddress());
        }
    }

    @Test
    void commitAndPush_shouldNotSetAuthorWhenNullProvided() throws Exception {
        Path localRepo = tempDir.resolve("local-no-author");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());

        Files.writeString(localRepo.resolve("file.txt"), "content");
        assertDoesNotThrow(() ->
                gitService.commitAndPush(localRepo.toFile(), "commit without author", "", null, null));
    }

    @Test
    void commitAndPush_shouldPushMultipleFiles() throws Exception {
        Path localRepo = tempDir.resolve("local-multi");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());

        Files.writeString(localRepo.resolve("file1.txt"), "content1");
        Files.writeString(localRepo.resolve("file2.txt"), "content2");
        gitService.commitAndPush(localRepo.toFile(), "add two files", "", "User", "user@test.com");

        Path verify = tempDir.resolve("verify-multi");
        gitService.cloneRepository(bareRepoUri, "main", "", verify.toFile());
        assertTrue(Files.exists(verify.resolve("file1.txt")));
        assertTrue(Files.exists(verify.resolve("file2.txt")));
    }

    @Test
    void commitAndPush_withNoArgs_shouldUseGitTokenFromConfig() throws Exception {
        Path localRepo = tempDir.resolve("local-default");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());

        Files.writeString(localRepo.resolve("default-token-file.txt"), "content");

        assertDoesNotThrow(() ->
                gitService.commitAndPush(localRepo.toFile(), "commit via default token"));
    }

    @Test
    void commitAndPush_shouldThrowOnInvalidRepository() {
        Path notARepo = tempDir.resolve("not-a-repo");
        assertThrows(IllegalStateException.class, () ->
                gitService.commitAndPush(notARepo.toFile(), "commit", "", "User", "user@test.com"));
    }

    @Test
    void commitAndPush_shouldPushModifiedFile() throws Exception {
        Path localRepo = tempDir.resolve("local-modified");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());

        Files.writeString(localRepo.resolve("README.md"), "modified content");
        gitService.commitAndPush(localRepo.toFile(), "update README", "", "User", "user@test.com");

        Path verify = tempDir.resolve("verify-modified");
        gitService.cloneRepository(bareRepoUri, "main", "", verify.toFile());
        assertEquals("modified content", Files.readString(verify.resolve("README.md")));
    }

    @Test
    void commitAndPush_shouldCreateCommitWithCorrectMessage() throws Exception {
        Path localRepo = tempDir.resolve("local-msg");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());

        Files.writeString(localRepo.resolve("file.txt"), "content");
        gitService.commitAndPush(localRepo.toFile(), "my specific message", "", "User", "user@test.com");

        try (Git git = Git.open(localRepo.toFile())) {
            List<RevCommit> commits = new java.util.ArrayList<>();
            git.log().setMaxCount(1).call().forEach(commits::add);
            assertEquals("my specific message", commits.getFirst().getFullMessage());
        }
    }
}
