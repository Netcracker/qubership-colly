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
@TestConfigProperty(key = "colly.eis.project.repo.git.token", value = "test-token")
@TestConfigProperty(key = "colly.eis.git.tokens.cn", value = "token-for-cn")
@TestConfigProperty(key = "colly.eis.git.tokens.mb", value = "token-for-mb")
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
    void resolveToken_shouldReturnExplicitTokenWhenProvided() {
        String result = gitService.resolveToken("my-explicit-token", null);
        assertEquals("my-explicit-token", result);
    }

    @Test
    void resolveToken_shouldReturnExplicitTokenWhenBothProvided() {
        String result = gitService.resolveToken("my-explicit-token", "cn");
        assertEquals("my-explicit-token", result);
    }

    @Test
    void resolveToken_shouldReturnRegionalTokenWhenNoExplicitToken() {
        String result = gitService.resolveToken(null, "cn");
        assertEquals("token-for-cn", result);
    }

    @Test
    void resolveToken_shouldReturnRegionalTokenForDifferentRegion() {
        String result = gitService.resolveToken("", "mb");
        assertEquals("token-for-mb", result);
    }

    @Test
    void resolveToken_shouldUseDefaultRegionWhenNoRegion() {
        assertEquals("token-for-cn", gitService.resolveToken(null, null));
    }

    @Test
    void resolveToken_shouldUseDefaultRegionWhenBlankRegion() {
        assertEquals("token-for-cn", gitService.resolveToken("", " "));
    }

    @Test
    @TestConfigProperty(key = "colly.eis.git.default-region", value = "mb")
    void resolveToken_shouldUseConfiguredDefaultRegion() {
        assertEquals("token-for-mb", gitService.resolveToken(null, null));
    }

    @Test
    @TestConfigProperty(key = "colly.eis.git.tokens.cn", value = "token-for-cn")
    void resolveToken_shouldThrowWhenRegionNotConfigured() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> gitService.resolveToken(null, "unknown-region"));
        assertTrue(ex.getMessage().contains("no token configured for region: unknown-region"));
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

    @Test
    void commitAndPush_shouldThrowPushRejectedWhenRemoteHasNewerCommit() throws Exception {
        Path staleRepo = tempDir.resolve("stale");
        Path otherRepo = tempDir.resolve("other");
        gitService.cloneRepository(bareRepoUri, "main", "", staleRepo.toFile());
        gitService.cloneRepository(bareRepoUri, "main", "", otherRepo.toFile());

        Files.writeString(otherRepo.resolve("README.md"), "changed by someone else");
        gitService.commitAndPush(otherRepo.toFile(), "concurrent change", "", "Other", "other@test.com");

        Files.writeString(staleRepo.resolve("README.md"), "changed on stale copy");
        GitPushRejectedException ex = assertThrows(GitPushRejectedException.class, () ->
                gitService.commitAndPush(staleRepo.toFile(), "stale change", "", "User", "user@test.com"));
        assertTrue(ex.getMessage().contains("REJECTED_NONFASTFORWARD"), ex.getMessage());

        Path verify = tempDir.resolve("verify-rejected");
        gitService.cloneRepository(bareRepoUri, "main", "", verify.toFile());
        assertEquals("changed by someone else", Files.readString(verify.resolve("README.md")));
    }

    @Test
    void syncWithRemote_shouldPickUpRemoteChangesAndDiscardLocalState() throws Exception {
        Path localRepo = tempDir.resolve("local-sync");
        Path otherRepo = tempDir.resolve("other-sync");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());
        gitService.cloneRepository(bareRepoUri, "main", "", otherRepo.toFile());

        Files.writeString(otherRepo.resolve("remote-file.txt"), "from remote");
        gitService.commitAndPush(otherRepo.toFile(), "remote change", "", "Other", "other@test.com");

        // local unpushed commit + uncommitted modification + untracked file
        try (Git git = Git.open(localRepo.toFile())) {
            Files.writeString(localRepo.resolve("committed-locally.txt"), "local commit");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("local only").setAuthor("u", "u@test.com").call();
        }
        Files.writeString(localRepo.resolve("README.md"), "dirty");
        Files.createDirectories(localRepo.resolve("untracked-dir"));
        Files.writeString(localRepo.resolve("untracked-dir/untracked.txt"), "untracked");

        gitService.syncWithRemote(localRepo.toFile(), "");

        assertEquals("from remote", Files.readString(localRepo.resolve("remote-file.txt")));
        assertEquals("initial content", Files.readString(localRepo.resolve("README.md")));
        assertFalse(Files.exists(localRepo.resolve("committed-locally.txt")));
        assertFalse(Files.exists(localRepo.resolve("untracked-dir")));

        Files.writeString(localRepo.resolve("README.md"), "after sync");
        gitService.commitAndPush(localRepo.toFile(), "change after sync", "", "User", "user@test.com");

        Path verify = tempDir.resolve("verify-sync");
        gitService.cloneRepository(bareRepoUri, "main", "", verify.toFile());
        assertEquals("after sync", Files.readString(verify.resolve("README.md")));
        assertEquals("from remote", Files.readString(verify.resolve("remote-file.txt")));
    }

    @Test
    void tryUpdateExistingRepository_returnsFalseWhenFolderIsMissing() {
        assertFalse(gitService.tryUpdateExistingRepository(tempDir.resolve("missing").toFile(), bareRepoUri, "main", ""));
    }

    @Test
    void tryUpdateExistingRepository_returnsFalseWhenFolderIsNotAGitRepository() throws Exception {
        Path plain = tempDir.resolve("plain");
        Files.createDirectories(plain);
        Files.writeString(plain.resolve("file.txt"), "content");

        assertFalse(gitService.tryUpdateExistingRepository(plain.toFile(), bareRepoUri, "main", ""));
    }

    @Test
    void tryUpdateExistingRepository_returnsFalseWhenRemoteUrlDiffers() {
        Path localRepo = tempDir.resolve("local-other-url");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());

        assertFalse(gitService.tryUpdateExistingRepository(localRepo.toFile(), "file:///some/other/repo.git", "main", ""));
    }

    @Test
    void tryUpdateExistingRepository_returnsFalseWhenBranchDiffers() {
        Path localRepo = tempDir.resolve("local-other-branch");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());

        assertFalse(gitService.tryUpdateExistingRepository(localRepo.toFile(), bareRepoUri, "develop", ""));
    }

    @Test
    void tryUpdateExistingRepository_returnsFalseWhenRemoteIsUnreachable() throws Exception {
        Path localRepo = tempDir.resolve("local-unreachable");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());
        org.apache.commons.io.FileUtils.deleteDirectory(tempDir.resolve("remote.git").toFile());

        assertFalse(gitService.tryUpdateExistingRepository(localRepo.toFile(), bareRepoUri, "main", ""));
    }

    @Test
    void tryUpdateExistingRepository_upToDate_keepsCopyAndReturnsTrue() throws Exception {
        Path localRepo = tempDir.resolve("local-up-to-date");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());
        String headBefore = head(localRepo);

        assertTrue(gitService.tryUpdateExistingRepository(localRepo.toFile(), bareRepoUri, "main", ""));
        assertEquals(headBefore, head(localRepo));
        assertEquals("initial content", Files.readString(localRepo.resolve("README.md")));
    }

    @Test
    void tryUpdateExistingRepository_nullBranch_usesCurrentBranch() throws Exception {
        Path localRepo = tempDir.resolve("local-null-branch");
        try (Git bare = Git.open(tempDir.resolve("remote.git").toFile())) {
            bare.getRepository().updateRef("HEAD").link("refs/heads/main");
        }
        gitService.cloneRepository(bareRepoUri, null, "", localRepo.toFile());
        pushRemoteChange("remote-file.txt", "from remote");

        assertTrue(gitService.tryUpdateExistingRepository(localRepo.toFile(), bareRepoUri, null, ""));
        assertEquals("from remote", Files.readString(localRepo.resolve("remote-file.txt")));
    }

    @Test
    void tryUpdateExistingRepository_remoteMoved_fetchesAndResets() throws Exception {
        Path localRepo = tempDir.resolve("local-behind");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());
        pushRemoteChange("README.md", "updated remotely");
        Files.writeString(localRepo.resolve("untracked.txt"), "untracked");

        assertTrue(gitService.tryUpdateExistingRepository(localRepo.toFile(), bareRepoUri, "main", ""));

        assertEquals("updated remotely", Files.readString(localRepo.resolve("README.md")));
        assertFalse(Files.exists(localRepo.resolve("untracked.txt")));
        assertEquals(remoteHead(), head(localRepo));
    }

    @Test
    void tryUpdateExistingRepository_sameHeadButDirtyTree_discardsLocalChanges() throws Exception {
        Path localRepo = tempDir.resolve("local-dirty");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());
        Files.writeString(localRepo.resolve("README.md"), "dirty");
        Files.writeString(localRepo.resolve("untracked.txt"), "untracked");

        assertTrue(gitService.tryUpdateExistingRepository(localRepo.toFile(), bareRepoUri, "main", ""));

        assertEquals("initial content", Files.readString(localRepo.resolve("README.md")));
        assertFalse(Files.exists(localRepo.resolve("untracked.txt")));
    }

    @Test
    void tryUpdateExistingRepository_localUnpushedCommit_isReplacedByRemoteState() throws Exception {
        Path localRepo = tempDir.resolve("local-ahead");
        gitService.cloneRepository(bareRepoUri, "main", "", localRepo.toFile());
        try (Git git = Git.open(localRepo.toFile())) {
            Files.writeString(localRepo.resolve("local.txt"), "local");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("local only").setAuthor("u", "u@test.com").call();
        }

        assertTrue(gitService.tryUpdateExistingRepository(localRepo.toFile(), bareRepoUri, "main", ""));

        assertFalse(Files.exists(localRepo.resolve("local.txt")));
        assertEquals(remoteHead(), head(localRepo));
    }

    private void pushRemoteChange(String fileName, String content) throws Exception {
        Path otherRepo = Files.createTempDirectory(tempDir, "other");
        Files.delete(otherRepo);
        gitService.cloneRepository(bareRepoUri, "main", "", otherRepo.toFile());
        Files.writeString(otherRepo.resolve(fileName), content);
        gitService.commitAndPush(otherRepo.toFile(), "remote change", "", "Other", "other@test.com");
    }

    private static String head(Path repo) throws Exception {
        try (Git git = Git.open(repo.toFile())) {
            return git.getRepository().resolve("HEAD").name();
        }
    }

    private String remoteHead() throws Exception {
        try (Git git = Git.open(tempDir.resolve("remote.git").toFile())) {
            return git.getRepository().resolve("refs/heads/main").name();
        }
    }

    @Test
    void syncWithRemote_shouldThrowOnInvalidRepository() {
        Path notARepo = tempDir.resolve("not-a-repo-sync");
        assertThrows(IllegalStateException.class, () -> gitService.syncWithRemote(notARepo.toFile(), ""));
    }
}
