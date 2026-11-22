package org.qubership.colly.cloudpassport;

import java.util.List;
import java.util.Set;

/**
 * @param cloudPassports   passports read from the repositories that were synced successfully
 * @param failedProjectIds projects with at least one instance repository that failed to sync: their passports
 *                         are incomplete, so a cluster missing from them must not be treated as deleted
 */
public record CloudPassportLoadResult(List<CloudPassport> cloudPassports, Set<String> failedProjectIds) {
}
