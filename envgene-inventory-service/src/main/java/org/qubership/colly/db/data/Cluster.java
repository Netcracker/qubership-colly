package org.qubership.colly.db.data;

import org.qubership.colly.cloudpassport.GitInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class Cluster {
    private String id;
    private String name;
    private String token;
    private String cloudApiHost;
    private URI monitoringUrl;
    private List<Environment> environments;
    private List<Namespace> namespaces;
    private String description;
    private GitInfo gitInfo;

    public GitInfo getGitInfo() {
        return gitInfo;
    }

    public void setGitInfo(GitInfo gitInfo) {
        this.gitInfo = gitInfo;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCloudApiHost() {
        return cloudApiHost;
    }

    public void setCloudApiHost(String cloudApiHost) {
        this.cloudApiHost = cloudApiHost;
    }

    public URI getMonitoringUrl() {
        return monitoringUrl;
    }

    public void setMonitoringUrl(URI monitoringUrl) {
        this.monitoringUrl = monitoringUrl;
    }

    public List<Environment> getEnvironments() {
        return environments != null ? environments : new ArrayList<>();
    }

    public void setEnvironments(List<Environment> environments) {
        this.environments = environments;
    }

    public List<Namespace> getNamespaces() {
        return namespaces != null ? namespaces : new ArrayList<>();
    }

    public void setNamespaces(List<Namespace> namespaces) {
        this.namespaces = namespaces;
    }

    public void addEnvironment(Environment environment) {
        if (environments == null) {
            environments = new ArrayList<>();
        }
        environments.add(environment);
    }

    public void addNamespace(Namespace namespace) {
        if (namespaces == null) {
            namespaces = new ArrayList<>();
        }
        namespaces.add(namespace);
    }
}
