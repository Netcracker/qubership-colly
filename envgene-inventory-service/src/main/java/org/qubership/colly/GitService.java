package org.qubership.colly;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class GitService {

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
        String tokenToUse = token != null && !token.isBlank() ? token : projectRepoGitToken;
        try (Git git = Git.open(repositoryPath)) {
            String branch = git.getRepository().getBranch();
            String remoteRef = "refs/remotes/origin/" + branch;
            git.fetch()
                    .setRemote("origin")
                    .setRefSpecs(new RefSpec("+refs/heads/" + branch + ":" + remoteRef))
                    .setDepth(1)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("oauth2", tokenToUse))
                    .call();
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(remoteRef).call();
            git.clean().setCleanDirectories(true).setForce(true).call();
        } catch (Exception e) {
            throw new IllegalStateException("Error during sync with remote: " + e.getMessage(), e);
        }
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
