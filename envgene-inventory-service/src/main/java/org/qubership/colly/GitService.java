package org.qubership.colly;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

@ApplicationScoped
public class GitService {

    @ConfigProperty(name = "colly.eis.git.token")
    String gitToken;

    public void cloneRepository(String repositoryUrl, File destinationPath) {
        CredentialsProvider credentialsProvider =
                new UsernamePasswordCredentialsProvider("", gitToken);

        Log.info("Cloning repository from " + repositoryUrl + " to " + destinationPath);
        Git git = null;
        try {
            if (gitToken != null && !gitToken.isBlank()) {
                git = Git.cloneRepository()
                        .setURI(repositoryUrl)
                        .setDirectory(destinationPath)
                        .setCredentialsProvider(credentialsProvider)
                        .call();
            } else {
                git = Git.cloneRepository()
                        .setURI(repositoryUrl)
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

    public void commitAndPush(File repositoryPath, String commitMessage) {
        Log.info("Committing and pushing changes in repository: " + repositoryPath);
        try (Git git = Git.open(repositoryPath)) {
            git.add().addFilepattern(".").call();

            git.commit()
                    .setMessage(commitMessage)
                    .call();

            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider("", gitToken);
            git.push()
                    .setCredentialsProvider(credentialsProvider)
                    .call();

            Log.info("Changes committed and pushed successfully.");
        } catch (Exception e) {
            throw new IllegalStateException("Error during commit and push: " + e.getMessage(), e);
        }
    }
}
