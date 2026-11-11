package org.qubership.colly.projectrepo;

import java.util.List;

public record Project(String id, String name, List<InstanceRepository> instanceRepositories,
                      EnvgeneTemplateRepository envgeneTemplateRepository,
                      List<GitGroupUrl> gitGroupUrls,
                      List<InstanceRepository> dclRepositories) {
    public Project {
        // Projects cached in Redis before the field was introduced deserialize to null
        dclRepositories = dclRepositories == null ? List.of() : dclRepositories;
    }
}
