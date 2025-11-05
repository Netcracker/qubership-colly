# API Enhancement of according to the Customer requirements

- [API Enhancement of according to the Customer requirements](#api-enhancement-of-according-to-the-customer-requirements)
  - [Description](#description)
  - [Environment](#environment)
  - [Namespace](#namespace)
  - [Cluster](#cluster)
  - [Colly instance](#colly-instance)
  - [To discuss](#to-discuss)
  - [To implement](#to-implement)

## Description

This document describes the requirements for Colly from The Customer and the changes they introduce to Colly.

This is not the full list of attributes of these objects, but only those that will be handled by The Customer

## Environment

| Colly Attribute                         | Attribute Type                                                     | Description                                                                                                                                      |
|-----------------------------------------|--------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`                                  | string                                                             | Environment name, cannot be changed after creation                                                                                               |
| `status`                                | enum [`PLANNED`, `FREE`, `IN_USE`, `RESERVED`, `DEPRECATED`]       | Current status of the Environment                                                                                                                |
| `role`                                  | string (the valid values is configured via a deployment parameter) | Defines the usage role of the Environment within the project. The list is configured via deployment parameter and can be extended.               |
| `teams`                                 | list of strings                                                    | Teams assigned to the Environment. If there are several teams, their names are separated by commas.                                              |
| `owners`                                | list of strings                                                    | People responsible for the Environment. If there are several, their names are separated by commas.                                               |
| `lastDeployedSDsByType`                 | object                                                             | List of Solution Descriptors with type `product` in `<name>:<version>` notation, which are currently successfully deployed in this Environment   |
| `lastSDDeploymentOperation.status`      | string, date-time                                                  | Status of the most recent SD deployment operation on any namespace that is part of the environment.                                              |
| `lastSDDeploymentOperation.completedAt` | enum [`SUCCESS`, `FAILURE`]                                        | Time when the most recent SD deployment operation finished on any namespace that is part of the environment, regardless of the deployment result |
| `description`                           | string                                                             | Free-form Environment description                                                                                                                |
| `namespaces`                            | list of [Namespace](#namespace) objects                            | List of associated namespaces                                                                                                                    |
| `cluster`                               | [Cluster](#cluster) object                                         | Associated cluster                                                                                                                               |
| `monitoringData.lastIdpLoginDate`       | string, date-time                                                  | Time of the last successful login to the IDP associated with the Environment                                                                     |

## Namespace

| Colly Attribute                           | Attribute Type | Description                                                                                                                      |
|-------------------------------------------|----------------|----------------------------------------------------------------------------------------------------------------------------------|
| `name`                                    | string         | Namespace name                                                                                                                   |

## Cluster

| Colly Attribute                           | Attribute Type   | Description                                                                                                                                                                                                    |
|-------------------------------------------|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`                                    | string           | Cluster name, cannot be changed after creation                                                                                                                          |
| `clusterInfoUpdateStatus.lastResult`      | string           | Determines the information update status for cluster: if at least one object from the cluster was successfully read, the information update is considered successful                                            |
| `clusterInfoUpdateStatus.lastCompletedAt` | string           | Time of the last successful update information from the cluster                                                                                                          |

## Colly instance

| Colly Attribute                           | Attribute Type   | Description                                                   |
|-------------------------------------------|----------------- |---------------------------------------------------------------|
| `clusterInfoUpdateStatusInterval`         | string, duration | Period of synchronization with the cluster in ISO 8601 format |

## To discuss

- [+] `lastIdpLoginDate`
  - Implement as in the old Colly

- [+] `ticketLinks`
  - This is the last deploy ticket ID
  - Not needed

- [+] `type`

  - `type` was previously determined based on labels set by the Cloud Passport Discovery CLI, but this functionality is being removed.
  - We will keep the attribute with the ability for users to set it manually.
  - Open Questions (OQ):
    1. Should this attribute be computed by Colly (and if so, based on what criteria), or should it be user-defined?
       1. It should be user-defined, selected from a predefined list without ability to extend
    2. Does the customer need this type-based environment categorization?
       1. No, it is not required.

- [+] `lastDeployedSDsByType`

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

- [ ] `lastSDDeploymentOperation`
  - `lastSDDeploymentOperation.status` - Status of the most recent SD deployment operation on any namespace that is part of the environment.
  - `lastSDDeploymentOperation.completedAt` - Time when the most recent SD deployment operation finished on any namespace that is part of the environment, regardless of the deployment result |
  - DD deployment is out of scope.

- [ ] `status`

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

- [ ] `clusterInfoUpdateInterval`, `clusterInfoUpdateStatus.result`, `clusterInfoUpdateStatus.lastSuccessDate`

  - OQ:
    1. What are the cases?
    2. Should Colly support a forced clusterInfoUpdate — not on a schedule, but triggered by a user request?

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

- [+] `role`

  - Should it be extendable?
    - Currently, `role` are extended via deployment parameters
  - A separate interface to provide the list of roles is needed
  - Challenge the predefined list of roles
    - [`Dev`, `QA`, `Project CI`, ~~`SaaS`~~, `Joint CI`, ~~`Other`~~, `Pre Sale`] - set via deployment parameters

- [+] `team` or `teams`? `owner` or `owners`?
  - `owners`, `teams` are lists

- [+] Each POST in the API will result in a separate commit

- [+] `id` is `uuid`; `name` is `<environment-name>`

- [+] The mediation layer composes the API between the inventory and operational services

- [ ] The SaaS instance of Colly must support
  - How do we roll out a new version Colly (canary deployment)?
  - The mediation layer finds Colly via service mesh

- [ ] It should be possible to get a list of environments per project

## To implement

- [+] Change environment attributes
   1. `team`(string) -> `teams`(list of strings)
   2. `owner`(string) -> `owners`(list of strings)
- [ ]`role`
  - [+] Add `role` attribute on Environment
  - [ ] Add deployment parameter to extend `role` valid values
  - [+] Remove default value for `role`
  - [ ] Implement an interface (/colly/operational-service/v2/environment-roles) that returns the list of `role` valid values (Low priority)
- [+] `type`
  - [+] Remove the functionality for auto-generating the `type` attribute. Users should be able to set this value themselves by selecting from a list of allowed values. The list of values should be specified as a deployment parameter.
- [ ] Include the current Colly API version in the X-API-Version HTTP response header for every API response (Low priority)
- [ ] Add `lastIdpLoginDate` attribute
- [ ] Add deployment parameter to `monitoringData` extention
- [+] Remove `ticketLinks` attribute
- [ ] Add `lastDeployedSDsByType`
- [ ] Add `lastSDDeploymentOperation`
