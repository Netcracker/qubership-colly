package org.qubership.colly;

public class GitPushRejectedException extends RuntimeException {
    public GitPushRejectedException(String message) {
        super(message);
    }
}
