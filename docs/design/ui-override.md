# Override UI API design

- [Override UI API design](#override-ui-api-design)
  - [Introduction](#introduction)
  - [Requirements and constraints](#requirements-and-constraints)
  - [Context](#context)
    - [Storage model](#storage-model)
    - [Cache](#cache)
  - [General API rules](#general-api-rules)
    - [Scope selection](#scope-selection)
    - [Context support per scope](#context-support-per-scope)
    - [Response and request bodies](#response-and-request-bodies)
    - [Status codes](#status-codes)
  - [UI contract](#ui-contract)
  - [API reference](#api-reference)
    - [GET /api/v1/environments/{environmentId}/ui-parameters](#get-apiv1environmentsenvironmentidui-parameters)
    - [POST /api/v1/environments/{environmentId}/ui-parameters](#post-apiv1environmentsenvironmentidui-parameters)
    - [DELETE /api/v1/environments/{environmentId}/ui-parameters](#delete-apiv1environmentsenvironmentidui-parameters)
  - [Open questions](#open-questions)
  - [Out of scope](#out-of-scope)

## Introduction

This document describes the Override UI API as implemented in Colly. It specifies the HTTP endpoints Colly
exposes to UI clients, the storage layout Colly maintains in the EnvGene instance repository, and the
per-endpoint processing logic that ties the two together. The design follows the storage approach selected
in [Analysis](/docs/analysis/ui-override.md).

## Requirements and constraints

- **Performance.** GET response time ≤ 300 ms (cache-served).
- **Atomicity.** Each POST commits all file and inventory changes in one Git commit.
- **Self-healing.** On every POST, Colly ensures the modified contract paramset entries are at the end of
  their lists.
- **commitMessage validation.** Colly does not validate the *content* of `commitMessage` (formatting, length,
  ticket-id pattern). Only field presence is required. Content validation is the UI's responsibility.
- **No EnvGene-side validation.** EnvGene consumes the resulting paramsets through its standard merge logic.
  No UI-override-specific validation runs on the EnvGene side.
- **BG Domain (blue-green deployment) scenarios.** Out of scope this iteration. Namespace and application
  overrides on environments with a BG Domain object are not supported.
- **Credential macros.** Colly does not process or validate `${creds.<credId>}` macros. UI override values
  are stored as plain strings.
- **No literal `null` parameter values.** UI override paramsets are not expected to store the literal
  `null` as a parameter value. The `null` token in the POST `parameters.<context>` map is reserved as the
  deletion marker (see UI client contract).

## Context

### Storage model

Each UI override is stored as one Env-Specific ParamSet per scope × context under
`/environments/<cluster>/<environment>/Inventory/parameters/`. ParamSet name and body shape depend on scope.
Context `deployment` maps to filename token `deploy`. `runtime` and `pipeline` are identical in both
layers.

**Filenames:**

| Scope         | Context      | Filename                                                       |
|---------------|--------------|----------------------------------------------------------------|
| `environment` | `deployment` | `deploy-ui-override.yaml`                                      |
| `environment` | `runtime`    | `runtime-ui-override.yaml`                                     |
| `environment` | `pipeline`   | `pipeline-ui-override.yaml`                                    |
| `namespace`   | `deployment` | `<deploy-postfix>-deploy-ui-override.yaml`                     |
| `namespace`   | `runtime`    | `<deploy-postfix>-runtime-ui-override.yaml`                    |
| `application` | `deployment` | `<deploy-postfix>-<application-name>-deploy-ui-override.yaml`  |
| `application` | `runtime`    | `<deploy-postfix>-<application-name>-runtime-ui-override.yaml` |

**Body schemas.**

Env / ns scope:

```yaml
name: <name-from-filename>
parameters:
  <param-key>: <value>
applications: []
```

App scope:

```yaml
name: <name-from-filename>
parameters: {}
applications:
  - appName: <application-name>
    parameters:
      <param-key>: <value>
```

Values may reference credentials via `${creds.<credId>}`, which Colly stores as plain strings (see
Requirements and constraints).

**Inventory association** in `env_definition.yml`. The UI-override paramset name is appended to a list at
the path below, chosen by context and scope:

| Context      | Path for env scope                      | Path for ns / app scope                          |
|--------------|-----------------------------------------|--------------------------------------------------|
| `deployment` | `envSpecificParamsets.cloud`            | `envSpecificParamsets.<deploy-postfix>`          |
| `runtime`    | `envSpecificTechnicalParamsets.cloud`   | `envSpecificTechnicalParamsets.<deploy-postfix>` |
| `pipeline`   | `envSpecificE2EParamsets.cloud`         | -                                                |

The `cloud` key is the EnvGene convention for environment-wide entries. Namespace- and application-scope
entries share the `<deploy-postfix>` key.

Because UI-override entries occupy the end of their lists, EnvGene's in-order merge gives them precedence
over earlier paramsets in the same list.

### Cache

Colly caches paramset files from the Git repository. The API serves GET requests exclusively
from this cache and does not read the repository directly on every request.

**Refresh schedule.** A background job refreshes the cache for all environments every N minutes
(configurable), independently of API requests.

**Cache build / refresh:**

1. Read the paramset files associated with the environment from Git.
2. Validate file structure against the paramset schema.
3. On successful validation, create or update the cache entry.

## General API rules

### Scope selection

Scope is implicit in query parameters:

| Query parameters                     | Scope         |
|--------------------------------------|---------------|
| (none)                               | `environment` |
| `namespaceName`                      | `namespace`   |
| `namespaceName` + `applicationName`  | `application` |

### Context support per scope

| Scope         | `deployment` | `runtime` | `pipeline` |
|---------------|:------------:|:---------:|:----------:|
| `environment` | ✓            | ✓         | ✓          |
| `namespace`   | ✓            | ✓         | rejected   |
| `application` | ✓            | ✓         | rejected   |

Sending `pipeline` context at namespace or application scope → `400 Bad Request`.

### Response and request bodies

GET response body:

```json
{
  "parameters": {
    "deployment": { "<key>": "<value>" },
    "runtime":    { "<key>": "<value>" },
    "pipeline":   { "<key>": "<value>" }
  }
}
```

`pipeline` field appears only at environment scope. Each context defaults to `{}` when no contract paramset
contributes.

POST request body:

```json
{
  "commitMessage": "<string>",
  "commitUser": "<string>",
  "commitUserEmail": "<string>",
  "parameters": {
    "deployment": { "<key>": "<value>" },
    "runtime":    { "<key>": "<value>" },
    "pipeline":   { "<key>": "<value>" }
  }
}
```

A `null` value on a key means deletion of that key from the contract paramset. POST response echoes the
request body.

### Status codes

| Status            | Meaning                                                                                     |
|-------------------|---------------------------------------------------------------------------------------------|
| `200 OK`          | Success. At least one contract paramset for the touched scope existed before this request   |
| `201 Created`     | Success. No contract paramset for the touched scope existed before                          |
| `400 Bad Request` | Validation error (missing required field, `pipeline` at ns / app scope)                     |
| `404 Not Found`   | Environment, Namespace, or Application not found                                            |

## UI contract

The UI is implemented separately. This design treats it as a black box. The following client-side
behaviors are part of the contract and shape backend semantics:

1. **Full-parameter POST bodies (not deltas).** UI clients send all parameters they know about in
   `parameters.<context>` - both unchanged and modified.

2. **`null` value = explicit deletion.** Sending `null` as a value removes the key from the UI override
   paramset and every non-UI-override paramset where the key exists.

3. **commitMessage content validation.** UI clients validate `commitMessage` format (length, ticket-id
   pattern, language conventions) before POST. Colly accepts any non-empty string (see [Requirements and
   constraints](/docs/design/ui-override.md#requirements-and-constraints)).

## API reference

### GET /api/v1/environments/{environmentId}/ui-parameters

Returns merged effective parameter values per supported context.

#### Path parameters

- `environmentId` (string, required) - Environment UUID.

#### Query parameters

- `namespaceName` (string, optional) - selects namespace scope.
- `applicationName` (string, optional, requires `namespaceName`) - selects application scope.

#### Example GET requests

```text
GET /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/ui-parameters
GET /api/v1/environments/.../ui-parameters?namespaceName=env-01-core
GET /api/v1/environments/.../ui-parameters?namespaceName=env-01-core&applicationName=billing
```

**Response (200 OK)** - environment scope example:

```json
{
  "parameters": {
    "deployment": { "CLUSTER_REGION": "us-east-1" },
    "runtime":    { "LOG_LEVEL": "INFO" },
    "pipeline":   { "ARTIFACT_REPO": "nexus" }
  }
}
```

Namespace / application scope: same response body without `pipeline`.

#### Processing logic

1. Resolve the Environment by `environmentId`. If missing → `404`.
2. Determine scope from query parameters. For namespace / application scope:
   - Resolve Namespace by `namespaceName` to obtain `deployPostfix`. Missing → `404`.
   - For application scope, validate `applicationName` is associated with that namespace. Missing → `404`.
3. For each context valid at this scope:
   1. Look up the paramset list at the matching path in `env_definition.yml`.
   2. Filter the paramsets by scope:
      - `environment`, `namespace`: paramsets whose body has `applications == []`.
      - `application`: paramsets with `applications` containing one entry where `appName == applicationName`.
   3. Merge in list order. Later entries override earlier ones on the same key. For application scope, merge
      `applications[0].parameters`. Otherwise merge `parameters`.
4. Wrap the per-context maps in the response body and return.

Reads are served from the cache. See [Cache](/docs/design/ui-override.md#cache) for staleness semantics.

### POST /api/v1/environments/{environmentId}/ui-parameters

Creates or updates UI override parameters at the scope determined by query parameters. Atomic: one Git commit
covers all file edits, file deletions, and inventory updates.

**Path / query parameters** - same as GET.

#### Request body

```json
{
  "commitMessage": "FAKE-0000",
  "commitUser": "Vasya A. Pupkin",
  "commitUserEmail": "vasya@example.com",
  "parameters": {
    "deployment": { "CORE_LOG_LEVEL": "DEBUG" },
    "runtime":    {},
    "pipeline":   {}
  }
}
```

- `commitMessage`, `commitUser`, `commitUserEmail` (strings, required) - Git commit metadata used as commit
  message and author.
- `parameters` (object, required) - per-context map of values. A `null` value on a key removes that key from
  the contract paramset.

Namespace / application scope omits `pipeline` from `parameters`. Presence triggers `400`.

**Response** - request body echoed back.

- `201 Created` if no contract paramset for the touched scope existed before this POST.
- `200 OK` if at least one did.

**Write semantics per key** in the POST body:

| Operation                                          | UI sends    | UI override   | Non-UI-override |
|----------------------------------------------------|-------------|---------------|-----------------|
| ADD (KEY not in any paramset)                      | `KEY: V`    | Add `KEY: V`  | Untouched       |
| MODIFY (V ≠ manual)                                | `KEY: V`    | Add or update | Untouched       |
| RESET (V == manual, KEY was in UI override)        | `KEY: V`    | Remove `KEY`  | Untouched       |
| NO-OP MODIFY (V == manual, KEY not in UI override) | `KEY: V`    | No-op         | Untouched       |
| DELETE                                             | `KEY: null` | Remove `KEY`  | Remove `KEY`    |
| OMIT (KEY absent from body)                        | (absent)    | Untouched     | Untouched       |

#### Algorithm

1. Validate request fields (`commitMessage`, `commitUser`, `commitUserEmail`, `parameters` present).
2. Resolve Environment / Namespace / Application as for GET. Reject `pipeline` context at namespace or
   application scope with `400`.
3. Compute the UI override paramset filename and inventory list key for this scope and context from
   scope × context × `deployPostfix` × `applicationName` per [Storage
   model](/docs/design/ui-override.md#storage-model).
4. For each key in the request body, apply the semantics in the table above..
5. If the UI override file's becomes empty as a result, mark the file for deletion.
6. Reconcile the UI override inventory entry:
   - file marked for deletion → remove inventory entry and delete file.
   - entry missing → append at the end of the list.
   - entry present but not last → move to the end (self-heal).
   - entry already last → leave unchanged.
7. Commit all changes - UI override edits, non-UI-override edits (DELETE case only), and inventory updates
   - in a single Git commit authored with the supplied metadata.
8. Invalidate the local cache for this environment.
9. Return the request body. Status `201` if no UI override paramset for the touched scope existed before
   this request, else `200`.

#### Example POST requests

Environment-scope deploy edit:

```http
POST /api/v1/environments/.../ui-parameters
```

```json
{
  "commitMessage": "raise log level",
  "commitUser": "Vasya A. Pupkin",
  "commitUserEmail": "vasya@example.com",
  "parameters": {
    "deployment": { "CLUSTER_REGION": "us-east-1" },
    "runtime":    {},
    "pipeline":   {}
  }
}
```

Application-scope deploy edit (namespace `env-01-core`, application `billing`):

```http
POST /api/v1/environments/.../ui-parameters?namespaceName=env-01-core&applicationName=billing
```

```json
{
  "commitMessage": "wire billing DB url",
  "commitUser": "Vasya A. Pupkin",
  "commitUserEmail": "vasya@example.com",
  "parameters": {
    "deployment": { "BILLING_DB_URL": "${creds.billing-db}" },
    "runtime":    {}
  }
}
```

### DELETE /api/v1/environments/{environmentId}/ui-parameters

Out of scope this iteration. Specification deferred to a follow-up document covering env-wide reset semantics
and atomic removal of all UI-override paramsets and their inventory entries.

## Open questions

- **EnvGene reaction to `null`-valued parameters.** Verify how `env_build` treats a paramset key whose value
  is `null`: as the reserved `envgeneNullValue`, as a literal null, or as a removal directive during merge.
  Outcome may change how Colly translates the POST `null` semantics on the storage side.
- **Encrypted-repo guard.** Encrypted repositories are an unsupported assumption with no explicit POST-time
  guard. Whether Colly should refuse POST on detection - and how to detect cheaply - is undecided.
- **Concurrent writes to non-UI-override paramsets.** On DELETE, Colly mutates non-UI-override paramsets.
  External writers (CI, manual Git pushes, other tools) may modify the same files. A POST that races with
  an external push will produce a Git push conflict. The current plan is to abort the POST and return
  `409 Conflict`, leaving retry to the UI client. Detailed conflict-resolution flow needs further design.
- **Alternative: never mutate non-UI-override paramsets.** A simpler variant of this design keeps Colly as
  the sole writer of UI override only, never touching non-UI-override paramsets. In that variant, `null`
  on a key removes it from UI override only. If the key also lives in a non-UI-override paramset, it
  remains there and the effective value falls back to it. Users cannot fully remove a key from an
  environment via UI when the key is defined in a project paramset. This variant removes the concurrency
  concern above but limits the UI's expressiveness on deletion.
- **DELETE endpoint.** Deferred to a follow-up iteration. Atomic removal of all UI-override paramsets and
  their inventory entries for an environment needs design.

## Out of scope

- DELETE endpoint specification.
- Concurrent-write conflict resolution.
