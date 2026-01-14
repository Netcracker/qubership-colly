package org.qubership.colly.projectrepo;

import java.util.List;

public record EnvgeneArtifact(String name, List<String> templateDescriptorNames, String defaultTemplateDescriptorName) {
}
