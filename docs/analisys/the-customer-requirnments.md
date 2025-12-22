# API Enhancement according to the Customer requirements

- [API Enhancement according to the Customer requirements](#api-enhancement-according-to-the-customer-requirements)
  - [Description](#description)
  - [Environment](#environment)
  - [Namespace](#namespace)
  - [Cluster](#cluster)
  - [Colly instance](#colly-instance)
  - [To discuss](#to-discuss)
  - [To implement](#to-implement)

## Description

This document describes the requirements for Colly from The Customer and the changes they introduce to Colly.

This is not the full list of attributes for these objects, but only those that will be processed as per The Customer’s requirements.

## Environment

| Colly Attribute                         | Attribute Type                                                      | Description                                                                                                                                       |
|-----------------------------------------|---------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`                                  | string                                                              | Environment name, cannot be changed after creation                                                                                                |
| `status`                                | enum [`PLANNED`, `FREE`, `IN_USE`, `RESERVED`, `DEPRECATED`]        | Current status of the Environment                                                                                                                 |
| `role`                                  | string (the valid values are configured via a deployment parameter) | Defines the usage role of the Environment within the project. The list is configured via a deployment parameter and can be extended.              |
| `teams`                                 | list of strings                                                     | Teams assigned to the Environment. If there are multiple teams, their names are separated by commas.                                              |
| `owners`                                | list of strings                                                     | People responsible for the Environment. If there are multiple, their names are separated by commas.                                               |
| `deploymentOperations`                  | list of objects                                                     | History of deployment operations per environment; each entry has `completedAt` and `deploymentItems` with SD `name`, `type`, `mode`, `status`.    |
| `description`                           | string                                                              | Free-form Environment description                                                                                                                 |
| `namespaces`                            | list of [Namespace](#namespace) objects                             | List of associated namespaces                                                                                                                     |
| `cluster`                               | [Cluster](#cluster) object                                          | Associated cluster                                                                                                                                |
| `monitoringData.lastIdpLoginDate`       | string, date-time                                                   | Time of the last successful login to the IDP associated with the Environment                                                                      |
| `region`                                | string                                                              | Geographical region associated with the Environment. This attribute is user-defined                                                               |

## Namespace

| Colly Attribute                         | Attribute Type | Description                |
|-----------------------------------------|----------------|----------------------------|
| `name`                                  | string         | Namespace name             |

## Cluster

| Colly Attribute                         | Attribute Type   | Description                                                                                           |
|-----------------------------------------|------------------|-------------------------------------------------------------------------------------------------------|
| `name`                                  | string           | Cluster name, cannot be changed after creation                                                        |
| `clusterInfoUpdateStatus.lastSuccessAt` | string           | Time of the last successful update information from the cluster                                       |

## Colly instance

| Colly Attribute                         | Attribute Type   | Description                                                   |
|-----------------------------------------|----------------- |---------------------------------------------------------------|
| `clusterInfoUpdateInterval`             | string, duration | Period of synchronization with the cluster in ISO 8601 format |

## To discuss

- [x] `lastIdpLoginDate`
  - Implement as in the old Colly

- [x] `ticketLinks`
  - This is the last deploy ticket ID
  - Not needed

- [x] `type`

  - `type` was previously determined based on labels set by the Cloud Passport Discovery CLI, but this functionality is being removed.
  - We will keep the attribute with the ability for users to set it manually.
  - Open Questions (OQ):
    1. Should this attribute be computed by Colly (and if so, based on what criteria), or should it be user-defined?
       1. It should be user-defined, selected from a predefined list without ability to extend
    2. Does the customer need this type-based environment categorization?
       1. No, it is not required.

- [x] `lastDeployedSDsByType` ??

  - Determines the latest SD of a given type that was successfully deployed to one of the namespaces included in the environment.
  - This attribute is used for CI environments.
  - Shows the scope of the last successful deployment operation.
  - Single field with a complex object:

    ```yaml
    <sd-type>: <SD app:ver>
    ```

    for example

    ```yaml
    product: product-sd:1.2.3
    project: my-project:1.2.3
    ```

  - Mapping of SD type to SD name is specified in the Colly deployment parameters:

    ```yaml
    solutionDescriptors:
      <sd-type>:
        - <sd-name-regexp>
    ```

  - Default value:

    ```yaml
    solutionDescriptors:
      product:
        - (?i)product
      project:
        - (?i)project
    ```

- [x] `deploymentOperations`
  - This attribute contains information obtained from the configMap `sd_versions`.
  - This attribute is temporary. In the future, the information source for deployment operations will be replaced by a dedicated service, which will require a change in the parameter model.
  - DD deployment is out of scope.

- [x] `status`
  - Propose ![env-state-machine.drawio.png](/docs/images/env-state-machine.drawio.png)
    1. `PLANNED` Planned for deployment, but not yet deployed. It exists only as a record in Colly for planning purposes.
    2. `FREE` The Environment is successfully deployed in the cloud but is not used by anyone; it is ready for use and not scheduled for usage.
    3. `IN_USE` The Environment is successfully deployed in the cloud and is being used by a specific team for specific purposes.
    4. `RESERVED` The Environment is successfully deployed in the cloud and reserved for use by a specific team, but is not currently in use.
    5. `DEPRECATED` The Environment is not used by anyone, and a decision has been made to delete it.
  - OQ:
    1. What are the cases?
    2. Should it be extendable?
       1. NO
    3. What is `to be deprecated`? Why do we not have `deprecated`, `deleted`, or `not used` states?
    4. Do we need `MIGRATING` (meaning the upgrade is in progress)?

- [x] `clusterInfoUpdateInterval`, `clusterInfoUpdateStatus.lastSuccessAt`
    1. What are the main scenarios?
       1. The user wants to check that the cluster status shown in Colly is up to date and matches the real state of the cluster, so they can make decisions.
          1. Solution:
             1. Cluster attribute `clusterInfoUpdateStatus.lastSuccessAt` shows the time of the last successful sync
             2. Colly instance attribute `clusterInfoUpdateInterval` shows the sync frequency
       2. The user thinks the cluster status data is outdated and wants to trigger a sync manually.
          1. Solution: We do NOT allow users to trigger the sync manually.
       3. An external system integrated with Colly thinks the data is outdated and wants to trigger sync.
          1. Solution: Use the API `/colly/v2/inventory-service/manual-sync`
    2. Should Colly support a forced `clusterInfoUpdate`, not by schedule but by user request?
       1. The customer will call this API as part of their workflow, but users in the UI will not have access to this.
    3. What is the recommended sync frequency with the cluster?
       1. At least once every 30 minutes.

- [x] `role`

  - Should it be extendable?
    - Currently, `role` values are extended via deployment parameters
  - A separate interface to provide the list of roles is needed
  - Challenge the predefined list of roles
    - [`Dev`, `QA`, `Project CI`, ~~`SaaS`~~, `Joint CI`, ~~`Other`~~, `Pre Sale`] - set via deployment parameters

- [x] `team` or `teams`? `owner` or `owners`?
  - `owners`, `teams` are lists

- [x] Each POST in the API will result in a separate commit

- [x] `id` is `uuid`; `name` is `<environment-name>`

- [x] The mediation layer composes the API between the inventory and operational services

- [x] It should be possible to get a list of projects
  - `/colly/v2/inventory-service/projects`
    - returns a summary view:
      - projectId
      - projectName
  - `/colly/v2/inventory-service/projects/Id`
    - returns detail view
    - The potential problem of a project with two instance repositories will be addressed when it arises

- [x] It should be possible to get a list of clusters
  - `/colly/v2/inventory-service/clusters`
    - returns a summary view:
      - clusterId
      - projectName
  - `/colly/v2/inventory-service/projects/Id`
    - returns detail view

- [x] Add the `deployPostfix` attribute to the Namespace?
  - No

- [x] Is it correct to say that a single physical business solution instance, consisting of product and project applications, can be modeled with two EnvGene environments - one for product, one for project?
  - No, there is only one Environment.

- [x] Before creating a cluster, the environment queries Colly to check if there is already a cluster with the same name.

- [x] If Colly discovers the instance repo and receives a cluster that already exists in Colly (by the `name` attribute), another one is created with the same name but a unique ID.

- [ ] What should be the scope of synchronization between Colly and the cluster
  1. All clusters of the instance
  2. Individual clusters
  3. Individual environments within a cluster

- [ ] Lock
  - The lock must answer the following questions:
    - Status: locked or not locked
    - Who locked it: free-form string
    - Reason for locking: free-form string
    - When it was locked: timestamp
    - When it will be unlocked: date
  - Required interfaces:
    - Set/remove lock on the environment
    - Force sync lock status from Git (can be implemented later)
  - Only a Colly admin can lock through the UI; users cannot
  - OQ:
    1. Should locks be defined by inventory backend?
    2. Who, when and why lock/unlock
    3. What are the cases from SSP?

- [ ] What does the TheCustomer -> Colly -> EnvGene integration look like when creating an Environment?

- [ ] How to aggregate DeploymentOperations across the namespaces of the environment?

- [ ] The SaaS instance of Colly must support:
  - How do we roll out a new version of Colly (canary deployment)?
  - The mediation layer finds Colly via service mesh

- [ ] It should be possible to get a list of environments per project
  - Implement by adding a search by `projectId` parameter to `/colly/v2/inventory-service/environments`
    - It is necessary to introduce the `projectId` attribute to the environment object
  - The Customer does NOT require information about which `customerName` an environment belongs to, nor obtaining lists of environments by `customerName`

## To implement

- [x] Change environment attributes
   1. `team`(string) -> `teams`(list of strings)
   2. `owner`(string) -> `owners`(list of strings)
- [ ] `role`
  - [x] Add `role` attribute on Environment
  - [ ] Add deployment parameter to extend `role` valid values
  - [x] Remove default value for `role`
  - [ ] Implement an interface (/colly/operational-service/v2/metadata) that returns the list of `role` valid values (Low priority)
- [x] `type`
  - [x] Remove the functionality for auto-generating the `type` attribute. Users should be able to set this value themselves by selecting from a list of allowed values. The list of values should be specified as a deployment parameter.
- [ ] Include the current Colly API version in the X-API-Version HTTP response header for every API response (Low priority)
- [x] Add `lastIdpLoginDate` attribute (via configurable monitoringData)
- [x] Add deployment parameter for `monitoringData` extension
- [x] Remove `ticketLinks` attribute
- [ ] Add `deploymentOperations` ( will do via ACHKA in 26.1 )
  - [ ] Remove `cleanInstallationDate`
- [x] Add `region` attribute
- [ ] Add `clusterInfoUpdateInterval`, `clusterInfoUpdateStatus.lastSuccessAt` + remove `synced`
- [ ] Add `accessGroups` to Project
- [x] Add `clustersPlatform` to Project
- [ ] Add `envgeneArtifact` to Project
- [ ] Create default configuration for `monitoringData`
- [ ] Support cred macro
- [ ] `numberOfNodes`
- [ ] The configuration for `monitoringData` is currently shared across all environments - it needs to be made more granular
