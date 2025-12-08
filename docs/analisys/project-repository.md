# Project Repository

- [Project Repository](#project-repository)
  - [Description](#description)
  - [Repository Structure](#repository-structure)
    - [Global](#global)
      - [\[Global\] `parameters.yaml`](#global-parametersyaml)
      - [\[Global\] `credentials.yaml`](#global-credentialsyaml)
    - [Projects](#projects)
      - [\[Projects\] `parameters.yaml`](#projects-parametersyaml)
      - [\[Projects\] `credentials.yaml`](#projects-credentialsyaml)
  - [To discuss](#to-discuss)

## Description

This document describes the structure and contents of the Project repository.

## Repository Structure

```text
├── global
|   ├── parameters.yaml
|   └── credentials.yaml
└── clusters
|   ├── parameters.yaml
|   └── credentials.yaml
└── projects
    └── <projectId>
        ├── parameters.yaml|yml (если есть оба, берется случайный)
        └── credentials.yaml
```

### Global

#### [Global] `parameters.yaml`

Currently, this file has no contents

<!-- ```yaml
# Optional
# Global list of users with access permissions for all projects
accessGroups:
      # Mandatory
      # User name
      name: string
      # Optional
      # User permissions
      permissions: enum[RO, RW]
``` -->

#### [Global] `credentials.yaml`

Currently, this file has no contents

### Projects

#### [Projects] `parameters.yaml`

```yaml
# Mandatory
# Name of the customer
customerName: string
# Mandatory
# Name of the project
projectName: string

accessGroups:
  - string
# To discuss. for different phases, there are different template versions from different branches
repositories:
  - # Mandatory
    # In MS1 only envgeneInstance is supported
    type: enum[ envgeneInstance, envgeneTemplate, pipeline ]
    # Mandatory
    url: string
    # Mandatory
    # Token for repository access
    # Pointer to Credential in credentials.yaml
    # In MS1, Colly will get access to the repository using a technical user, parameters for the user will be passed as a deployment parameter
    token: creds.get('<credential-id>').secret
    # Optional
    # If not set, the "default" branch is used (as in GitLab/GitHub)
    defaultBranch: string
    # Optional
    # Geographical region associated with the Environment. This attribute is user-defined
    # Used in cases where specific `pipeline` repositories need to be used for certain environments
    region: string
    # Optional
    # This is for MS1, we will do discovery later somehow
    # Needs further thought because the same <artifact-template-name> can contain different templates in different versions
    envgeneArtifact:
      name: 
      # Mandatory
      # The key is EnvGene environment template artifact name (application from the application:version notation)
      # The value is a list of template names inside the artifact
      templateDescriptorNames: list of strings
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

## To discuss

- [ ] Should the Project repository be used as the Maintenance inventory?

- [x] Use case for Colly using its own project repository:
  1. Read all projects and extract the URL, token, and branches from the `envgeneInstance` repositories in order to display the environments from these projects.

- [ ] Need a mapping from environment to project. For example, an environment attribute `project`.
  - Use case: find the DCL pipeline repository by environment
  - Requestor - The Customer

- [ ] global configuration

- [ ] `pipeline` is too generic, we need to specify the exact type of pipeline

- [ ] accessGroups это список групп пользователей для "кластеров" или Колли?

- [x] Есть ли сейчас для каждого проекта различный `accessGroups`
  - Да, он различный

- [x] Планируется ли создание Project post/patch через Colly API
  - сейчас нет, возможно потом

- [ ] jiraCustomerName - нужен ли? Егор расскажет

- [ ] envgeneArtifact
  - Short term:
    - в Repository задается руками envgeneArtifact.name в гите
  - Long term:
    - решить может ли Colly задискаверить:
      - envgeneArtifact.name
      - envgeneArtifact.templateDescriptorNames

projectId = customerAbriv + projectAbriv

каталог Abriv может появится когда появятся кейсы автоматизации создания проекта
