package org.qubership.colly.db.data;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.ALWAYS)
@NoArgsConstructor
public class Environment {

    private String id;
    private String name;
    private String clusterId;
    private String clusterName;
    private List<String> owners;
    private List<String> teams;
    private String description;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expirationDate;
    private EnvironmentStatus status = EnvironmentStatus.FREE;
    private EnvironmentType type = EnvironmentType.ENVIRONMENT;
    private String role;
    private String region;
    private List<String> labels;
    private List<String> accessGroups;
    private List<String> effectiveAccessGroups;
    private List<Namespace> namespaces;


    public Environment(String id, String name) {
        this.setId(id);
        this.setName(name);
        this.namespaces = new ArrayList<>();
    }

    public List<Namespace> getNamespaces() {
        return namespaces != null ? Collections.unmodifiableList(namespaces) : Collections.emptyList();
    }

    public void addNamespace(Namespace namespace) {
        if (this.namespaces == null) {
            this.namespaces = new ArrayList<>();
        }
        this.namespaces.add(namespace);
    }

    public List<String> getLabels() {
        return labels != null ? Collections.unmodifiableList(labels) : Collections.emptyList();
    }

    public void setLabels(List<String> labels) {
        this.labels = labels != null ? new ArrayList<>(labels) : null;
    }

    public List<String> getOwners() {
        return owners != null ? Collections.unmodifiableList(owners) : Collections.emptyList();
    }

    public void setOwners(List<String> owners) {
        this.owners = owners != null ? new ArrayList<>(owners) : null;
    }

    public void setTeams(List<String> teams) {
        this.teams = teams != null ? new ArrayList<>(teams) : null;
    }

    public List<String> getAccessGroups() {
        return accessGroups != null ? Collections.unmodifiableList(accessGroups) : Collections.emptyList();
    }

    public void setAccessGroups(List<String> accessGroups) {
        this.accessGroups = accessGroups != null ? new ArrayList<>(accessGroups) : null;
    }

    public List<String> getEffectiveAccessGroups() {
        return effectiveAccessGroups != null ? Collections.unmodifiableList(effectiveAccessGroups) : Collections.emptyList();
    }

    public void setEffectiveAccessGroups(List<String> effectiveAccessGroups) {
        this.effectiveAccessGroups = effectiveAccessGroups != null ? new ArrayList<>(effectiveAccessGroups) : null;
    }
}

