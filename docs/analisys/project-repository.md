# Project Repository

- [Project Repository](#project-repository)
  - [Description](#description)
  - [Repository Structure](#repository-structure)
    - [Defaults](#defaults)
      - [\[Defaults\] `parameters.yaml`](#defaults-parametersyaml)
      - [\[Defaults\] `credentials.yaml`](#defaults-credentialsyaml)
    - [Projects](#projects)
      - [\[Projects\] `parameters.yaml`](#projects-parametersyaml)
      - [\[Projects\] `credentials.yaml`](#projects-credentialsyaml)
      - [Example](#example)
  - [To discuss](#to-discuss)

## Description

This document describes the structure and contents of the Project repository.

## Repository Structure

```text
├── defaults
|   ├── parameters.yaml|yml
|   └── credentials.yaml|yml
└── clusters
|   ├── parameters.yaml|yml
|   └── credentials.yaml|yml
└── projects
    └── <projectId>
        ├── parameters.yaml|yml
        └── credentials.yaml|yml
```

### Defaults

#### [Defaults] `parameters.yaml`

<!-- Currently, this file has no contents -->

```yaml
# Optional
# Global list of users with access permissions for all projects
accessGroups:
  # Mandatory
  # User name
  name: string
  # Optional
  # User permissions
  permissions: enum[RO, RW]
```

#### [Defaults] `credentials.yaml`

Currently, this file has no contents

### Projects

#### [Projects] `parameters.yaml`

```yaml
# Mandatory
# Name of the customer
customerName: string
# Mandatory
# Name of the project
name: string
# Mandatory
# Type of the project
type: enum[ project, product ]
# Optional
# List of groups with RW access rights to objects of this project
accessGroups:
  - string
# Optional
# Platform type for clusters in this project
# "ocp" stands for OpenShift, "k8s" for generic Kubernetes
clustersPlatform: enum[ ocp, k8s ]
mavenRepoName: string
# Mandatory
repositories:
  - # Mandatory
    # Assumption: repository with type envgeneTemplate is only one per project
    type: enum[ envgeneInstance, envgeneTemplate, clusterProvision, envProvision, solutionDeploy ]
    # Mandatory
    url: string
    # Mandatory
    # Token for repository access
    # Pointer to Credential in credentials.yaml
    # In MS1, Colly will get access to the repository using a technical user, parameters for the user will be passed as a deployment parameter
    token: creds.get('<credential-id>').secret
    # Optional
    # If not set, the "default" branch is used (as in GitLab/GitHub)
    branch: string
    # Optional
    # Geographical region associated with the Environment. This attribute is user-defined
    # Used in cases where specific `pipeline` repositories need to be used for certain environments
    region: string
    # Optional
    # Only for type: envgeneTemplate
    # TODO: discover from repository 
    envgeneArtifact:
      # Mandatory
      # EnvGene environment template artifact name (application from the application:version notation)
      # For example platform:20251215.113905-22
      name: string
      # Optional
      # List of template names inside the artifact
      templateDescriptorNames: list of strings
      # Optional
      # Template name that is used by default when creating project environments.
      # Should be included in templateDescriptorNames
      defaultTemplateDescriptorName: string
```

#### [Projects] `credentials.yaml`

Contains [Credential](https://github.com/Netcracker/qubership-envgene/blob/main/docs/envgene-objects.md#credential) objects

```yaml
<credential-usernamePassword>:
  type: usernamePassword
  data:
    username: string
    password: string
<credential-secret>:
  type: secret
  data:
    secret: string
```

Example:

```yaml
customerName: ACME
projectName: ACME-bss
repositories:
  - type: envgeneInstance
    url: https://git.acme.com/instance
    token: instance-cred
  - region: offsite-cn
    type: pipeline
    url: https://git.acme.com/pipeline
    token: offsite-cn-pipeline-cred
  - region: offsite-mb
    type: pipeline
    url: https://git.acmemb.com/pipelines
    token: offsite-mb-pipeline-cred
  - type: envgeneTemplate
    url: https://git.acme.com/template
    token: template-cred
    branches:
      - r25.3
      - r25.4
envgeneTemplates:
  envgene-acme:
    - main
    - dt
    - dm
```

```yaml
instance-cred:
  type: secret
  data:
    secret: "MGE3MjYwNTQtZGE4My00MTlkLWIzN2MtZjU5YTg3NDA2Yzk0MzlmZmViZGUtYWY4_PF84_ba"
template-cred:
  type: secret
  data:
    secret: "MGE3MjYwNTQtZGE4My00MTlkLWIzN2MtZjU5YTg3NDA2Yzk0MzlmZmViZGUtYWY4_PF84_bb"
offsite-cn-pipeline-cred:
  type: secret
  data:
    secret: "MGE3MjYwNTQtZGE4My00MTlkLWIzN2MtZjU5YTg3NDA2Yzk0MzlmZmViZGUtYWY4_PF84_bb"
offsite-mb-pipeline-cred:
  type: secret
  data:
    secret: "MGE3MjYwNTQtZGE4My00MTlkLWIzN2MtZjU5YTg3NDA2Yzk0MzlmZmViZGUtYWY4_PF84_bb"
```

#### Example

Minimal project

```yaml
# parameters.yaml
customerName: ACME Corp
name: ACME BSS
type: project
repositories:
  - type: envgeneInstance
    url: https://gitlab.example.com/acme/instance.git
    token: creds.get('gitlab-token').secret

# credentials.yaml
gitlab-token:
  type: secret
  data:
    secret: dummy-token-value
```

Full project

```yaml
# parameters.yaml
customerName: ACME Corp
name: ACME OSS
type: project
accessGroups:
  - devops-team
  - developers
  - qa
clustersPlatform: k8s
repositories:
  - type: envgeneInstance
    url: https://gitlab.example.com/acme/instance.git
    token: creds.get('gitlab-token').secret
    defaultBranch: release/26.1
    region: cn

# credentials.yaml
gitlab-token:
  type: secret
  data:
    secret: dummy-token-value
```

## To discuss

- [x] Use case for Colly using its own project repository:
  1. Read all projects and extract the URL, token, and branches from the `envgeneInstance` repositories in order to display the environments from these projects.

- [x] Does each project currently have a different `accessGroups`?
  - Yes, it is different

- [x] Is creating Project post/patch via the Colly API planned?
  - Not right now, maybe in the future

- [x] `projectId` = customerAbbr + projectAbbr

- [x] Should the Project repository be used as the Maintenance inventory?
  - yes

- [x] Need a mapping from environment to project. For example, an environment attribute `project`.
  - Use case: find the DCL pipeline repository by environment
  - Requestor - The Customer

- [ ] global configuration

- [ ] `pipeline` is too generic, we need to specify the exact type of pipeline

- [ ] `accessGroups` is a list of user groups for "clusters" or for Colly?
  - list of groups that can work with the project => used for access control in SSP:
    - who can see/edit which project/cluster/env

- [ ] `jiraCustomerName` - is it needed?

- [x] `envgeneArtifact`
  - Short term:
    - In the Repository, `envgeneArtifact.name` is set manually in Git
  - Long term:
    - need to decide whether Colly can discover:
      - `envgeneArtifact.name`
      - `envgeneArtifact.templateDescriptorNames`
