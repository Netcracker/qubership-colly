package org.qubership.colly;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.util.*;

@ApplicationScoped
public class GitService {

    private static final long GC_LOOSE_OBJECTS_THRESHOLD = 2000;
    private static final long GC_PACK_FILES_THRESHOLD = 20;
    private static final String REMOTE_ORIGIN = "origin";

    @ConfigProperty(name = "colly.eis.project.repo.git.token")
    String projectRepoGitToken;

    @ConfigProperty(name = "colly.eis.git.tokens")
    Optional<Map<String, String>> gitTokens;

    public String resolveToken(String explicitToken, String region) {
        if (explicitToken != null && !explicitToken.isBlank()) {
            return explicitToken;
        }
        if (region == null || region.isBlank()) {
            throw new IllegalStateException(
                    "Cannot resolve git token: no explicit token and no region provided");
        }
        String token = gitTokens.map(m -> m.get(region)).orElse(null);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Cannot resolve git token: no token configured for region: " + region);
        }
        return token;
    }

    public void cloneRepository(String repositoryUrl, String branch, String token, File destinationPath) {
        String tokenToUse = token != null && !token.isBlank() ? token : projectRepoGitToken;
        CredentialsProvider credentialsProvider =
                new UsernamePasswordCredentialsProvider("", tokenToUse);

        Log.info("Cloning repository from " + repositoryUrl + " to " + destinationPath);
        Git git = null;
        try {
            if (!tokenToUse.isBlank()) {
                git = Git.cloneRepository()
                        .setURI(repositoryUrl)
                        .setBranch(branch)
                        .setDirectory(destinationPath)
                        .setDepth(1)
                        .setCredentialsProvider(credentialsProvider)
                        .call();
            } else {
                git = Git.cloneRepository()
                        .setURI(repositoryUrl)
                        .setBranch(branch)
                        .setDirectory(destinationPath)
                        .setDepth(1)
                        .call();
            }
        } catch (GitAPIException e) {
            throw new IllegalStateException("Error during clone repository: " + repositoryUrl, e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
        Log.info("Repository cloned.");
    }

    /**
     * Brings the local working copy to the latest state of its remote branch: fetches the current branch,
     * hard-resets to it and removes untracked files. Any local commits or changes are discarded.
     */
    public void syncWithRemote(File repositoryPath, String token) {
        Log.info("Syncing repository with remote: " + repositoryPath);
        try (Git git = Git.open(repositoryPath)) {
            fetchAndReset(git, git.getRepository().getBranch(), credentials(token));
        } catch (Exception e) {
            throw new IllegalStateException("Error during sync with remote: " + e.getMessage(), e);
        }
    }

    /**
     * Brings an existing local working copy up to date without re-cloning it.
     * <p>
     * First asks the remote for the head of the branch ({@code ls-remote}, a single lightweight request).
     * If it matches the local {@code HEAD} and the working tree is clean, nothing else is done.
     * Otherwise the branch is fetched (depth 1) and the working copy is hard-reset to it, like {@link #syncWithRemote}.
     *
     * @param branch expected branch; {@code null}/blank means "whatever branch the copy is on" (remote default branch)
     * @return {@code true} if the copy is now up to date; {@code false} if it cannot be reused
     * (missing, not a git repository, other remote url or branch, any git error) and the caller must clone from scratch
     */
    public boolean tryUpdateExistingRepository(File repositoryPath, String repositoryUrl, String branch, String token) {
        if (!new File(repositoryPath, ".git").isDirectory()) {
            return false;
        }
        try (Git git = Git.open(repositoryPath)) {
            Repository repository = git.getRepository();
            String remoteUrl = repository.getConfig().getString("remote", REMOTE_ORIGIN, "url");
            if (!repositoryUrl.equals(remoteUrl)) {
                Log.infof("Local copy %s points to another remote (%s), it will be re-cloned", repositoryPath, remoteUrl);
                return false;
            }
            String localBranch = repository.getBranch();
            if (branch != null && !branch.isBlank() && !branch.equals(localBranch)) {
                Log.infof("Local copy %s is on branch %s instead of %s, it will be re-cloned", repositoryPath, localBranch, branch);
                return false;
            }
            CredentialsProvider credentialsProvider = credentials(token);
            ObjectId remoteHead = git.lsRemote()
                    .setRemote(REMOTE_ORIGIN)
                    .setHeads(true)
                    .setCredentialsProvider(credentialsProvider)
                    .call().stream()
                    .filter(ref -> ref.getName().equals(Constants.R_HEADS + localBranch))
                    .map(Ref::getObjectId)
                    .findFirst()
                    .orElse(null);
            if (remoteHead == null) {
                Log.warnf("Branch %s is not found in remote %s, local copy %s will be re-cloned", localBranch, repositoryUrl, repositoryPath);
                return false;
            }
            ObjectId localHead = repository.resolve(Constants.HEAD);
            if (remoteHead.equals(localHead) && git.status().call().isClean()) {
                Log.infof("Repository %s is up to date (%s), skipping fetch", repositoryUrl, remoteHead.abbreviate(8).name());
                return true;
            }
            fetchAndReset(git, localBranch, credentialsProvider);
            Log.infof("Repository %s updated: %s -> %s", repositoryUrl,
                    localHead == null ? "none" : localHead.abbreviate(8).name(), remoteHead.abbreviate(8).name());
            gcIfNeeded(git);
            return true;
        } catch (Exception e) {
            Log.warnf("Failed to update local copy %s of %s, it will be re-cloned: %s", repositoryPath, repositoryUrl, e.getMessage());
            return false;
        }
    }

    private static void fetchAndReset(Git git, String branch, CredentialsProvider credentialsProvider) throws GitAPIException {
        String remoteRef = Constants.R_REMOTES + "origin/" + branch;
        git.fetch()
                .setRemote(REMOTE_ORIGIN)
                .setRefSpecs(new RefSpec("+" + Constants.R_HEADS + branch + ":" + remoteRef))
                .setDepth(1)
                .setCredentialsProvider(credentialsProvider)
                .call();
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(remoteRef).call();
        git.clean().setCleanDirectories(true).setForce(true).call();
    }

    // Every incremental fetch leaves a new pack / loose objects behind; compact them once they pile up.
    private static void gcIfNeeded(Git git) {
        try {
            Properties stats = git.gc().getStatistics();
            long looseObjects = Long.parseLong(String.valueOf(stats.getOrDefault("numberOfLooseObjects", "0")));
            long packFiles = Long.parseLong(String.valueOf(stats.getOrDefault("numberOfPackFiles", "0")));
            if (looseObjects > GC_LOOSE_OBJECTS_THRESHOLD || packFiles > GC_PACK_FILES_THRESHOLD) {
                Log.infof("Running gc for %s (loose objects=%d, pack files=%d)",
                        git.getRepository().getDirectory(), looseObjects, packFiles);
                git.gc().call();
            }
        } catch (Exception e) {
            Log.warnf("gc failed for %s: %s", git.getRepository().getDirectory(), e.getMessage());
        }
    }

    private CredentialsProvider credentials(String token) {
        String tokenToUse = token != null && !token.isBlank() ? token : projectRepoGitToken;
        return new UsernamePasswordCredentialsProvider("oauth2", tokenToUse);
    }

    public void commitAndPush(File repositoryPath, String commitMessage, String token, String gitUser, String gitEmail) {
        Log.info("Committing and pushing changes in repository: " + repositoryPath);
        String tokenToUse = token != null && !token.isBlank() ? token : projectRepoGitToken;
        Iterable<PushResult> pushResults;
        try (Git git = Git.open(repositoryPath)) {
            git.add().addFilepattern(".").call();

            CommitCommand commitCommand = git.commit().setMessage(commitMessage);
            if (gitUser != null && !gitUser.isBlank() && gitEmail != null && !gitEmail.isBlank()) {
                commitCommand.setAuthor(gitUser, gitEmail);
            }
            commitCommand.call();

            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider("oauth2", tokenToUse);
            pushResults = git.push()
                    .setCredentialsProvider(credentialsProvider)
                    .call();
        } catch (Exception e) {
            throw new IllegalStateException("Error during commit and push: " + e.getMessage(), e);
        }
        verifyPushResults(pushResults);
        Log.info("Changes committed and pushed successfully.");
    }

    // JGit does not throw when the remote rejects a ref update (e.g. non-fast-forward) — it reports it in the result.
    private static void verifyPushResults(Iterable<PushResult> pushResults) {
        List<String> failures = new ArrayList<>();
        for (PushResult pushResult : pushResults) {
            for (RemoteRefUpdate update : pushResult.getRemoteUpdates()) {
                RemoteRefUpdate.Status status = update.getStatus();
                if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                    failures.add(update.getRemoteName() + ": " + status
                            + (update.getMessage() != null ? " (" + update.getMessage() + ")" : ""));
                }
            }
        }
        if (!failures.isEmpty()) {
            throw new GitPushRejectedException("Push rejected by remote: " + String.join(", ", failures));
        }
    }

    public void commitAndPush(File repositoryPath, String commitMessage) {
        commitAndPush(repositoryPath, commitMessage, projectRepoGitToken, null, null);
    }
}
