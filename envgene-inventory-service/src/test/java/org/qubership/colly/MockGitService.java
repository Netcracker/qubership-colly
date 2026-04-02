package org.qubership.colly;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

@Mock
@ApplicationScoped
public class MockGitService extends GitService {

    @FunctionalInterface
    public interface CloneAction {
        void clone(String repositoryUrl, File destinationPath) throws IOException;
    }

    private CloneAction cloneAction = null;

    public void setCloneAction(CloneAction action) {
        this.cloneAction = action;
    }

    public void reset() {
        this.cloneAction = null;
    }

    @Override
    public void cloneRepository(String repositoryUrl, String branch, String token, File destinationPath) {
        try {
            if (cloneAction != null) {
                cloneAction.clone(repositoryUrl, destinationPath);
            } else {
                File source = new File("src/test/resources/" + repositoryUrl);
                if (source.exists()) {
                    FileUtils.copyDirectory(source, destinationPath);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("MockGitService: failed to clone " + repositoryUrl, e);
        }
    }

    @Override
    public void commitAndPush(File repositoryPath, String commitMessage) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void commitAndPush(File repositoryPath, String commitMessage, String token, String gitUser, String gitEmail) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
