package org.qubership.colly;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
public class YqService {

    public boolean isYqAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("yq", "--version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException e) {
            Log.error("Error checking yq availability:", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.error("Error checking yq availability:", e);
            return false;
        }
    }

    public void deleteYamlField(Path yamlPath, String yamlFieldPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "yq",
                "eval",
                "del(" + yamlFieldPath + ")",
                yamlPath.toString(),
                "--inplace"
        );
        executeYqCommand(pb);
    }

    public void updateYamlField(Path yamlPath, String yamlFieldPath, String value) throws IOException {
        if (value == null || value.isEmpty()) {
            deleteYamlField(yamlPath, yamlFieldPath);
            return;
        }
        ProcessBuilder pb = new ProcessBuilder(
                "yq",
                "eval",
                yamlFieldPath + " = " + "\"" + escapeForYq(value) + "\"",
                yamlPath.toString(),
                "--inplace"
        );
        executeYqCommand(pb);
    }

    public void updateYamlArrayField(Path yamlPath, String yamlFieldPath, List<String> values) throws IOException {
        if (values == null || values.isEmpty()) {
            deleteYamlField(yamlPath, yamlFieldPath);
            return;
        }
        String arrayValue = values.stream()
                .map(value -> "\"" + escapeForYq(value) + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
        ProcessBuilder pb = new ProcessBuilder(
                "yq",
                "eval",
                yamlFieldPath + " = " + arrayValue,
                yamlPath.toString(),
                "--inplace"
        );
        executeYqCommand(pb);
    }

    public void addToYamlArrayUnique(Path yamlPath, String yamlArrayPath, String value) throws IOException {
        String expression = yamlArrayPath + " |= ((. // []) + [\"" + escapeForYq(value) + "\"] | unique)";
        ProcessBuilder pb = new ProcessBuilder("yq", "eval", expression, yamlPath.toString(), "--inplace");
        executeYqCommand(pb);
    }

    public String escapeForYq(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private void executeYqCommand(ProcessBuilder processBuilder) throws IOException {
        Process process = processBuilder.start();
        boolean finished;
        try {
            finished = process.waitFor(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("yq command interrupted", e);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new IOException("yq command timed out");
        }

        if (process.exitValue() != 0) {
            StringBuilder output = new StringBuilder();
            try (BufferedReader bufferedReader = process.errorReader()) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                throw new IOException("yq command failed with exit code " + process.exitValue() + ": " + output.toString().trim());
            }
        }
    }
}
