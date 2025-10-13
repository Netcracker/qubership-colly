package org.qubership.colly;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;

@ApplicationScoped
public class GitService {
    public void cloneRepository(String repositoryUrl, File destinationPath) {
        Log.info("Cloning repository from " + repositoryUrl + " to " + destinationPath);
        try {
            Git.cloneRepository()
                    .setURI(repositoryUrl)
                    .setDirectory(destinationPath)
                    .call();
        } catch (GitAPIException e) {
            throw new IllegalStateException("Error during clone repository: " + repositoryUrl, e);
        }
        Log.info("Repository cloned.");
    }

    public void commitAndPush(File repositoryPath, String commitMessage, String token) {
        Log.info("Committing and pushing changes in repository: " + repositoryPath);
        try (Git git = Git.open(repositoryPath)) {
            git.add().addFilepattern(".").call();

            git.commit()
                    .setMessage(commitMessage)
                    .call();

            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(token, "");
            git.push()
                    .setCredentialsProvider(credentialsProvider)
                    .call();

            Log.info("Changes committed and pushed successfully.");
        } catch (Exception e) {
            throw new IllegalStateException("Error during commit and push: " + e.getMessage(), e);
        }
    }
}
