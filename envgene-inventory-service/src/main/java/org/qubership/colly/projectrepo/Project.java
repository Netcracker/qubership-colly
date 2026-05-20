package org.qubership.colly.projectrepo;

import java.util.List;

public record Project(String id, String name, List<InstanceRepository> instanceRepositories,
                      EnvgeneTemplateRepository envgeneTemplateRepository,
                      List<GitGroupUrl> gitGroupUrls) {
}
