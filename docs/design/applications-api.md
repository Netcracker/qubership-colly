# Applications API for Colly

- [Applications API for Colly](#applications-api-for-colly)
    - [Introduction](#introduction)
    - [API Endpoints](#api-endpoints)
        - [GET /api/v1/environments/{environmentId}/applications](#get-apiv1environmentsenvironmentidapplications)
            - [Parameters](#parameters)
            - [Request Examples](#request-examples)
            - [Responses](#responses)
            - [Processing Logic](#processing-logic)

## Introduction

This document describes the API for retrieving the list of applications in an environment. The API is used by the UI to
populate the application dropdown when creating Application-level UI override parameters.

## API Endpoints

### GET /api/v1/environments/{environmentId}/applications

Returns a list of application names for the given `environmentId` and `namespaceName`, sourced from the Solution
Descriptor (SD) located at:

```text
/environments/<cluster-name>/<env-name>/Inventory/solution-descriptor/sd.yaml|yml
```

Example SD:

```yaml
version: 2.1
type: "solutionDeploy"
deployMode: "composite"
applications:
  - version: "MONITORING:0.64.1"
    deployPostfix: "postgresql"
  - version: "postgres:1.32.6"
    deployPostfix: "postgresql"
  - version: "postgres-services:1.32.6"
    deployPostfix: "postgresql"
  - version: "postgres:1.32.6"
    deployPostfix: "postgresql-dbaas"
```

#### Parameters

- `environmentId` (path, mandatory) — Environment UUID
- `namespaceName` (query, mandatory) — Namespace name

#### Request Examples

```text
GET /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/applications?namespaceName=env-01-core
```

#### Responses

- `200 OK` — Applications found
    - Body: array of application name strings
- `200 OK` — No applications found
    - Body: `[]`
- `404 Not Found` — Namespace not found or `deployPostfix` is not defined

**Example successful response (for the SD above, assuming `deployPostfix` for `env-01-core` = `"postgresql"`):**

```json
[
  "MONITORING",
  "postgres",
  "postgres-services"
]
```

**Example response when no applications are found:**

```json
[]
```

#### Processing Logic

The API operates on cached Solution Descriptor data. The cache is created and updated on a schedule.

On each request:

1. Check whether a valid SD cache exists for the given `environmentId`:
    - If the cache exists and is valid → use cached data
    - If the cache is missing or invalid → return an empty list with a warning in the logs

2. Resolve the `deployPostfix` for the namespace:
    - Look up the `Namespace` object by `namespaceName` and `environmentId`
    - Read the `deployPostfix` field from the `Namespace` object
    - If `deployPostfix` is not found for `namespaceName` → `404 Not Found`

3. Retrieve the application list from the cache:
    - Filter applications from the cached SD by matching `deployPostfix`
    - If no applications match the given `deployPostfix` → return an empty list
    - Return the list of application names
