package org.qubership.colly.cloudpassport;

import org.qubership.colly.projectrepo.InstanceRepository;

public record GitInfo(InstanceRepository instanceRepository, String folderName, String projectId) {
}
