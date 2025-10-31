package org.qubership.colly.db.data;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class Environment {

    private String name;
    private List<String> owners;
    private List<String> teams;
    private String description;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expirationDate;
    private EnvironmentStatus status = EnvironmentStatus.FREE;

    private EnvironmentType type = EnvironmentType.ENVIRONMENT;
    private String role;
    private List<String> labels;
    private List<Namespace> namespaces;


    public Environment(String name) {
        this.setName(name);
        this.namespaces = new ArrayList<>();
    }

    public Environment() {
    }

    public List<Namespace> getNamespaces() {
        return namespaces != null ? Collections.unmodifiableList(namespaces) : Collections.emptyList();
    }

    public void setNamespaces(List<Namespace> namespaces) {
        this.namespaces = namespaces;
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

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getOwners() {
        return owners != null ? Collections.unmodifiableList(owners) : Collections.emptyList();
    }

    public void setOwners(List<String> owners) {
        this.owners = owners != null ? new ArrayList<>(owners) : null;
    }

    public List<String> getTeams() {
        return teams;
    }

    public void setTeams(List<String> teams) {
        this.teams = teams != null ? new ArrayList<>(teams) : null;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public EnvironmentStatus getStatus() {
        return status;
    }

    public void setStatus(EnvironmentStatus status) {
        this.status = status;
    }

    public EnvironmentType getType() {
        return type;
    }

    public void setType(EnvironmentType type) {
        this.type = type;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}

