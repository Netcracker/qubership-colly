package org.qubership.colly;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;

@ApplicationScoped
public class GitService {

    @ConfigProperty(name = "colly.eis.git.token")
    String gitToken;

    public void cloneRepository(String repositoryUrl, String branch, String token, File destinationPath) {
        String tokenToUse = token != null && !token.isBlank() ? token : gitToken;
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
                        .setCredentialsProvider(credentialsProvider)
                        .call();
            } else {
                git = Git.cloneRepository()
                        .setURI(repositoryUrl)
                        .setBranch(branch)
                        .setDirectory(destinationPath)
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

    public void commitAndPush(File repositoryPath, String commitMessage, String token, String gitUser, String gitEmail) {
        Log.info("Committing and pushing changes in repository: " + repositoryPath);
        String tokenToUse = token != null && !token.isBlank() ? token : gitToken;
        try (Git git = Git.open(repositoryPath)) {
            git.add().addFilepattern(".").call();

            CommitCommand commitCommand = git.commit().setMessage(commitMessage);
            if (gitUser != null && !gitUser.isBlank() && gitEmail != null && !gitEmail.isBlank()) {
                commitCommand.setAuthor(gitUser, gitEmail);
            }
            commitCommand.call();

            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider("", tokenToUse);
            git.push()
                    .setCredentialsProvider(credentialsProvider)
                    .call();

            Log.info("Changes committed and pushed successfully.");
        } catch (Exception e) {
            throw new IllegalStateException("Error during commit and push: " + e.getMessage(), e);
        }
    }

    public void commitAndPush(File repositoryPath, String commitMessage) {
        commitAndPush(repositoryPath, commitMessage, gitToken, null, null);
    }
}
