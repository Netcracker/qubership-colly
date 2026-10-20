package org.qubership.colly;

import io.quarkus.logging.Log;
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
    private boolean rejectPush = false;

    public void setCloneAction(CloneAction action) {
        this.cloneAction = action;
    }

    public void setRejectPush(boolean rejectPush) {
        this.rejectPush = rejectPush;
    }

    public void reset() {
        this.cloneAction = null;
        this.rejectPush = false;
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
        Log.info("MockGitService: commitAndPush " + repositoryPath + " " + commitMessage);
    }

    @Override
    public void syncWithRemote(File repositoryPath, String token) {
        Log.info("MockGitService: syncWithRemote " + repositoryPath);
    }

    @Override
    public void commitAndPush(File repositoryPath, String commitMessage, String token, String gitUser, String gitEmail) {
        if (rejectPush) {
            throw new GitPushRejectedException("MockGitService: push rejected");
        }
        Log.info("MockGitService: commitAndPush " + repositoryPath + " " + commitMessage);
    }
}
