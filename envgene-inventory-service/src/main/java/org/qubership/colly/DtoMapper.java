package org.qubership.colly;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.dto.*;
import org.qubership.colly.projectrepo.EnvgeneTemplateRepository;
import org.qubership.colly.projectrepo.GitGroupUrl;
import org.qubership.colly.projectrepo.InstanceRepository;
import org.qubership.colly.projectrepo.Project;

import java.util.List;

@ApplicationScoped
public class DtoMapper {

    @Inject
    EnvironmentRepository environmentRepository;

    public EnvironmentDto toDto(Environment environment) {
        return new EnvironmentDto(environment.getId(),
                environment.getName(),
                environment.getDescription(),
                environment.getNamespaces().stream().map(this::toDto).toList(),
                new LightClusterDto(environment.getClusterId(), environment.getClusterName()),
                environment.getOwners(),
                environment.getLabels(),
                environment.getTeams(),
                environment.getStatus(),
                environment.getExpirationDate(),
                environment.getType(),
                environment.getRole(),
                environment.getAccessGroups(),
                environment.getEffectiveAccessGroups(),
                environment.isSspStandalone(),
                environment.getCmApproach()
        );
    }

    public NamespaceDto toDto(Namespace namespace) {
        return new NamespaceDto(namespace.getUid(), namespace.getName(), namespace.getDeployPostfix());
    }

    public InternalClusterInfoDto toDto(Cluster cluster) {
        return new InternalClusterInfoDto(cluster.getId(),
                cluster.getName(),
                cluster.getToken(),
                cluster.getCloudApiHost(),
                cluster.getCloudPublicHost(),
                toLightDtos(environmentRepository.findByClusterId(cluster.getId())),
                cluster.getMonitoringUrl(),
                cluster.getAchkaUrl()
        );
    }

    public ClusterDto toClusterDto(Cluster cluster) {
        return new ClusterDto(cluster.getId(),
                cluster.getName(),
                toLightDtos(environmentRepository.findByClusterId(cluster.getId())),
                cluster.getDashboardUrl(),
                cluster.getDbaasUrl(),
                cluster.getDeployerUrl(),
                cluster.getArgoUrl(),
                cluster.getRegion());
    }

    private List<LightEnvironmentDto> toLightDtos(List<Environment> environments) {
        return environments.stream().map(this::toLightDto).toList();
    }

    public LightEnvironmentDto toLightDto(Environment environment) {
        return new LightEnvironmentDto(environment.getId(),
                environment.getName(),
                environment.getNamespaces().stream().map(this::toDto).toList());
    }

    public List<InternalClusterInfoDto> toClusterInfoDtos(List<? extends Cluster> clusters) {
        return clusters.stream().map(this::toDto).toList();
    }

    public List<EnvironmentDto> toDtos(List<? extends Environment> environments) {
        return environments.stream().map(this::toDto).toList();
    }

    public List<ClusterDto> toClusterDtos(List<Cluster> clusters) {
        return clusters.stream().map(this::toClusterDto).toList();
    }

    public ProjectDto toProjectDto(Project project) {
        return new ProjectDto(
                project.id(),
                project.name(),
                project.instanceRepositories().stream().map(this::toDto).toList(),
                toDto(project.envgeneTemplateRepository()),
                project.gitGroupUrls().stream().map(this::toDto).toList());
    }

    public InstanceRepositoryDto toDto(InstanceRepository instanceRepository) {
        return new InstanceRepositoryDto(
                instanceRepository.url(),
                instanceRepository.branch(),
                instanceRepository.region()
        );
    }

    public TemplateRepositoryDto toDto(EnvgeneTemplateRepository envgeneTemplateRepository) {
        if (envgeneTemplateRepository == null) {
            return null;
        }
        return new TemplateRepositoryDto(
                envgeneTemplateRepository.url(),
                envgeneTemplateRepository.branch(),
                envgeneTemplateRepository.envgeneArtifact()
        );
    }

    public GitGroupUrlDto toDto(GitGroupUrl gitGroupUrl) {
        return new GitGroupUrlDto(gitGroupUrl.region(), gitGroupUrl.url());
    }

    public List<ProjectDto> toProjectDtos(List<Project> projects) {
        return projects.stream().map(this::toProjectDto).toList();
    }
}

