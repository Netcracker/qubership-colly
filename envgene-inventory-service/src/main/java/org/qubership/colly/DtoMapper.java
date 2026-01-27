package org.qubership.colly;

import jakarta.enterprise.context.ApplicationScoped;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.dto.*;
import org.qubership.colly.projectrepo.EnvgeneTemplateRepository;
import org.qubership.colly.projectrepo.InstanceRepository;
import org.qubership.colly.projectrepo.Pipeline;
import org.qubership.colly.projectrepo.Project;

import java.util.List;

@ApplicationScoped
public class DtoMapper {

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
                environment.getRegion(),
                environment.getAccessGroups(),
                environment.getEffectiveAccessGroups()
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
                toLightDtos(cluster.getEnvironments()),
                cluster.getMonitoringUrl()
        );
    }

    public ClusterDto toClusterDto(Cluster cluster) {
        return new ClusterDto(cluster.getId(),
                cluster.getName(),
                toLightDtos(cluster.getEnvironments()),
                cluster.getDashboardUrl(),
                cluster.getDbaasUrl(),
                cluster.getDeployerUrl(),
                cluster.getArgoUrl());
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
                project.type(),
                project.customerName(),
                project.instanceRepositories().stream().map(this::toDto).toList(),
                project.pipelines().stream().map(this::toDto).toList(),
                project.clusterPlatform(),
                toDto(project.envgeneTemplateRepository()),
                project.accessGroups()
        );
    }

    public InstanceRepositoryDto toDto(InstanceRepository instanceRepository) {
        return new InstanceRepositoryDto(
                instanceRepository.id(),
                instanceRepository.url(),
                instanceRepository.branch(),
                instanceRepository.token(),
                instanceRepository.region()
        );
    }

    public TemplateRepositoryDto toDto(EnvgeneTemplateRepository envgeneTemplateRepository) {
        if (envgeneTemplateRepository == null) {
            return null;
        }
        return new TemplateRepositoryDto(
                envgeneTemplateRepository.id(),
                envgeneTemplateRepository.url(),
                envgeneTemplateRepository.token(),
                envgeneTemplateRepository.branch(),
                envgeneTemplateRepository.envgeneArtifact()
        );
    }

    public PipelineDto toDto(Pipeline pipeline) {
        return new PipelineDto(
                pipeline.type(),
                pipeline.url(),
                pipeline.token(),
                pipeline.region()
        );
    }

    public List<ProjectDto> toProjectDtos(List<Project> projects) {
        return projects.stream().map(this::toProjectDto).toList();
    }
}

