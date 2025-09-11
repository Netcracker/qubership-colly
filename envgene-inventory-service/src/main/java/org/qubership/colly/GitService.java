package org.qubership.colly;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

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
}
