# Effective Set API design

- [Effective Set API design](#effective-set-api-design)
  - [Introduction](#introduction)
  - [Requirements and constraints](#requirements-and-constraints)
  - [Context](#context)
    - [Storage model](#storage-model)
    - [Cache](#cache)
  - [General API rules](#general-api-rules)
    - [Context selection](#context-selection)
    - [Scope per context](#scope-per-context)
    - [Response and request bodies](#response-and-request-bodies)
    - [State and originalValue computation](#state-and-originalvalue-computation)
    - [Status codes](#status-codes)
  - [UI contract](#ui-contract)
  - [API reference](#api-reference)
    - [POST /api/v1/environments/{environmentId}/ui-parameters/effective-set](#post-apiv1environmentsenvironmentidui-parameterseffective-set)
      - [Path parameters](#path-parameters)
      - [Query parameters](#query-parameters)
      - [Request body](#request-body)
      - [Example POST requests](#example-post-requests)
      - [Response](#response)
      - [Processing logic](#processing-logic)
  - [Open questions](#open-questions)
  - [Out of scope](#out-of-scope)

## Introduction

This document describes the Effective Set API in Colly. It specifies the HTTP endpoint
Colly exposes to UI clients, the storage layout Colly reads from the EnvGene instance repository, and
the processing logic that turns Calculator-produced Effective Set files into the API response.

## Requirements and constraints

- **Performance.** Response time ≤ 300 ms.
- **API contract.** `state` and `originalValue` are computed per parameter leaf by comparing the value across three
  layers — the raw Effective Set file, the applicable paramsets layered on top, and the request body `parameters`
  layered on top of that. See [State and originalValue computation](#state-and-originalvalue-computation).
- **BG Domain (blue-green deployment) scenarios.** Effective Set retrieval for environments with
  a BG Domain object is not supported.
- **Credentials files.** `credentials.yaml` and `collision-credentials.yaml` are not read. Their
  handling is deferred (see [Open questions](#open-questions)).
- **Collision parameters.** `collision-deployment-parameters.yaml` is not read. Its handling is
  deferred (see [Open questions](#open-questions)).
- **Per-service parameters.** Service-level inputs (`deploy-descriptor.yaml` and files under
  `per-service-parameters/`) are not read.
- **External credentials.** `external-credentials.yaml` (credential references for VALS / ESO) is
  not read. Its handling is deferred (see [Open questions](#open-questions)).
- **Top-level keys only.** Inside `deployment-parameters.yaml`, the API reads only the top-level
  keys other than `global` and per-service alias keys. See [Storage model](#storage-model) for
  the file structure.
- **Uncommitted overlay semantics.** Request body `parameters` is merged over the cached Effective
  Set as a plain recursive overlay. A `null` in the request body becomes a literal `null` value
  in the response. It is not interpreted as a deletion preview (unlike the Override UI write
  contract, where `null` deletes a key).

## Context

### Storage model

The Effective Set is produced by EnvGene's Calculator and committed to the instance repository under
`/environments/<cluster>/<environment>/effective-set/`. Colly reads these files via the cache and
does not write to them.

The Calculator emits several files per context. The API reads only one parameters file per context
(see [Requirements and constraints](#requirements-and-constraints) for excluded files).

**File layout (files read by the API):**

```text
/environments/<cluster>/<environment>/effective-set/
  deployment/<deployPostfix>/<applicationName>/values/
    deployment-parameters.yaml
  runtime/<deployPostfix>/<applicationName>/
    parameters.yaml
  pipeline/
    parameters.yaml
```

**File read per context:**

| Context      | File                          |
|--------------|-------------------------------|
| `deployment` | `deployment-parameters.yaml`  |
| `runtime`    | `parameters.yaml`             |
| `pipeline`   | `parameters.yaml`             |

**File content.**

`deployment-parameters.yaml`:

```yaml
<key-1>: <value-1>
<key-N>: <value-N>
global: &id001
  <key-1>: <value-1>
  <key-N>: <value-N>
<service-name-1>: *id001
<service-name-2>: *id001
```

In `deployment-parameters.yaml` the Calculator places the same parameter map at three positions:

- At the top level, as the `<key>: <value>` entries shown above.
- Inside the `global` key, where the parsed YAML emits an anchor (`&id001`).
- Inside each per-service key, as an alias of `global` (`*id001`).

The `global` value and per-service alias values share the same parsed-map object after YAML
loading.

`parameters.yaml` (runtime and pipeline contexts):

```yaml
<key-1>: <value-1>
<key-N>: <value-N>
```

`parameters.yaml` has no `global` key and no per-service aliases.

UI override paramsets are part of the inventory that the Calculator consumes when generating the
Effective Set. Once the Calculator runs, UI override values are reflected in the Effective Set files
read by Colly. The Effective Set API does not read UI override paramsets directly.

### Cache

Colly caches Effective Set files from the Git repository. The API serves requests exclusively from
this cache and does not read the repository directly on every request.

**Refresh schedule.** A background job refreshes the cache for all environments every N minutes
(configurable), independently of API requests.

**Cache build / refresh:**

1. Read the per-context parameters file (see [Storage model](#storage-model)) from Git for each
   (context, scope) tuple in the environment.
2. Extract the cached subset from the parsed file:
   - Exclude the `global` key.
   - Exclude any top-level key whose value is the same parsed-map object as the value of
     `global`.
   - Keep all remaining top-level keys.
3. Store the extracted subset as the cache entry, keyed by `(context, scope)`.

The cache is read-only for API requests. The request-body overlay must not mutate the cached
map.

## General API rules

### Context selection

Context is explicit via the `context` query parameter:

| `context`    | Meaning                                                |
|--------------|--------------------------------------------------------|
| `deployment` | Application-level deployment parameters                |
| `runtime`    | Application-level runtime configuration parameters     |
| `pipeline`   | Environment-wide pipeline parameters                   |

Missing or unrecognized `context` value → `400 Bad Request`.

### Scope per context

Each request resolves to one of two scopes:

- **Environment scope** for `context=pipeline`. `namespaceName` and `applicationName` are absent.
- **Application scope** for `context=deployment` and `context=runtime`. Both `namespaceName` and
  `applicationName` are required.

| `context`    | `namespaceName` | `applicationName` |
|--------------|:---------------:|:-----------------:|
| `deployment` | required        | required          |
| `runtime`    | required        | required          |
| `pipeline`   | -               | -                 |

Sending `namespaceName` or `applicationName` with `context=pipeline` → `400 Bad Request`. Missing
`namespaceName` or `applicationName` for `deployment` or `runtime` → `400 Bad Request`.

### Response and request bodies

Request body:

```json
{
  "parameters": {
    "<key>": "<value>"
  }
}
```

- `parameters` (object, optional) - uncommitted parameters from the UI, merged over the Effective
  Set into the response `value`. Omit or send `{}` when there are no uncommitted edits.

Response body:

```json
{
  "context": "<deployment|runtime|pipeline>",
  "environmentId": "<uuid>",
  "namespaceName": "<string>",   // present for `deployment` and `runtime` only
  "applicationName": "<string>", // present for `deployment` and `runtime` only
  "parameters": {
    "<key>": { "_type": "...", "_data": { ... } }
  }
}
```

- `parameters` is the Effective Set with each parameter wrapped as `EffectiveSetParameter`.

**EffectiveSetParameter shape:**

```yaml
_type: enum[container, leaf]
_data:
  value: any                    # Resolved parameter value (after all three layers)
  state: string                 # One of ui_override_untouched | ui_override_committed | ui_override_uncommitted
  originalValue: any            # Value at the layer below the one that caused the current state; see below
```

`value`, `state`, and `originalValue` are required in every `_data` on a `leaf`. Container nodes have no `value`/
`state`/`originalValue` — only nested `EffectiveSetParameter` entries.

The `_type` and `_data` underscore-prefixed names avoid collisions with user-defined parameter
names such as `data` or `type`.

`_type` is `leaf` when the wrapped value is a primitive (string, number, boolean, null) or a list.
It is `container` when the wrapped value is an object, in which case `_data` recursively contains
further `EffectiveSetParameter` entries keyed by the original parameter names.

### State and originalValue computation

Every parameter leaf is resolved from three ordered layers:

1. **Raw layer** — the value in the Effective Set file (see [Storage model](#storage-model)), or absent if the key isn't
   in the file.
2. **Paramset layer** — the raw layer with the applicable paramsets merged on top (environment → namespace → application
   specificity, see [Cache](#cache) / [Processing logic](#processing-logic)), or absent if no paramset defines the key
   either.
3. **Request layer** — the paramset layer with the request body `parameters` merged on top (see
   [Response and request bodies](#response-and-request-bodies)), or absent if the key doesn't exist at any layer.

`state` and `originalValue` for a leaf are computed by comparing these layers, most specific first:

| Condition                                                                                         | `state`                   | `originalValue`                                 |
|---------------------------------------------------------------------------------------------------|---------------------------|-------------------------------------------------|
| Request layer value differs from paramset layer value, or the key is absent at the paramset layer | `ui_override_uncommitted` | paramset layer value, or `null` if absent there |
| (else) Paramset layer value differs from raw layer value, or the key is absent at the raw layer   | `ui_override_committed`   | raw layer value, or `null` if absent there      |
| (else)                                                                                            | `ui_override_untouched`   | equal to `value` (the raw layer value)          |

In words: a parameter is **untouched** if it comes straight from the Effective Set file and nothing overrides it. It is
**committed** if a paramset (previously saved via `POST /ui-parameters`)
overrides it — either with a different value or as a brand-new parameter not present in the Effective Set file. It is
**uncommitted** if the request body's `parameters` overlay (edits made in the UI but not yet saved) overrides the
committed/raw value further — either with a different value or as a brand-new parameter.

This comparison happens independently per leaf, not per top-level parameter — two keys inside the same container object
can have different states. Container nodes themselves carry no state.

### Status codes

| Status            | Meaning                                                                                  |
|-------------------|------------------------------------------------------------------------------------------|
| `200 OK`          | Success                                                                                  |
| `400 Bad Request` | Validation error (missing or invalid `context`, scope mismatch)                          |
| `404 Not Found`   | Environment, Namespace, or Application not found                                         |

## UI contract

The UI is implemented separately. This design treats it as a black box. The following client-side
behavior is part of the contract and shapes backend semantics:

1. **Uncommitted parameters in the request body.** UI clients send all parameters that have been
   edited in the UI but not yet committed via the Override UI POST endpoint in `parameters`.
   Backend merges them over the Effective Set into the response `value` (see [Processing
   logic](#processing-logic)). When the UI has no uncommitted edits, `parameters`
   MAY be omitted or sent as `{}`.

2. **`state` and `originalValue` reflect override status.** The UI MAY use `state` to distinguish values that come
   straight from the Effective Set (`ui_override_untouched`) from values overridden by a saved paramset
   (`ui_override_committed`) or by the in-progress request-body edit (`ui_override_uncommitted`), and MAY use
   `originalValue` to show what a committed or uncommitted value previously was
   (see [State and originalValue computation](#state-and-originalvalue-computation)). New `state` values may be added in
   the future; the UI MUST tolerate unrecognized `state` values gracefully.

## API reference

### POST /api/v1/environments/{environmentId}/ui-parameters/effective-set

Returns the Effective Set for the requested context, scope, and environment, with each parameter
wrapped as `EffectiveSetParameter`.

#### Path parameters

- `environmentId` (string, required) - Environment UUID.

#### Query parameters

- `context` (string, required) - one of `deployment`, `runtime`, `pipeline`.
- `namespaceName` (string, required for `deployment` and `runtime`, rejected for `pipeline`) -
  selects the namespace.
- `applicationName` (string, required for `deployment` and `runtime`, rejected for `pipeline`) -
  selects the application within the namespace.

#### Request body

```json
{
  "parameters": {}
}
```

- `parameters` (object, optional) - uncommitted parameters from the UI. Merged over the Effective
  Set into the response `value`. Omit or send `{}` when there are no uncommitted edits.

#### Example POST requests

Deployment context:

```http
POST /api/v1/environments/550e8400-e29b-41d4-a716-446655440000/ui-parameters/effective-set?context=deployment&namespaceName=env-01-core&applicationName=my-app
```

```json
{
  "parameters": {
    "backupDaemon": {
      "resources": {
        "limits": {
          "cpu": "400m"
        }
      }
    }
  }
}
```

Runtime context:

```http
POST /api/v1/environments/.../ui-parameters/effective-set?context=runtime&namespaceName=env-01-core&applicationName=backend-service
```

```json
{
  "parameters": {}
}
```

Pipeline context:

```http
POST /api/v1/environments/.../ui-parameters/effective-set?context=pipeline
```

```json
{}
```

#### Response

`200 OK` example for the deployment request above. The source Effective Set contains the parameters shown below, no
paramset overrides `backupDaemon.*`, and the request body overrides
`resources.limits.cpu` from `"300m"` to `"400m"`:

Source Effective Set:

```yaml
backupDaemon:
  data: true
  backupSchedule: "0 0 * * *"
  resources:
    limits:
      cpu: "300m"
```

Response body:

```json
{
  "context": "deployment",
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "namespaceName": "env-01-core",
  "applicationName": "my-app",
  "parameters": {
    "backupDaemon": {
      "_type": "container",
      "_data": {
        "data": {
          "_type": "leaf",
          "_data": {
            "value": true,
            "state": "ui_override_untouched",
            "originalValue": true
          }
        },
        "backupSchedule": {
          "_type": "leaf",
          "_data": {
            "value": "0 0 * * *",
            "state": "ui_override_untouched",
            "originalValue": "0 0 * * *"
          }
        },
        "resources": {
          "_type": "container",
          "_data": {
            "limits": {
              "_type": "container",
              "_data": {
                "cpu": {
                  "_type": "leaf",
                  "_data": {
                    "value": "400m",
                    "state": "ui_override_uncommitted",
                    "originalValue": "300m"
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
```

`cpu` is `ui_override_uncommitted` because the request body's `"400m"` differs from the paramset layer's value (which
equals the raw Effective Set value `"300m"`, since no paramset overrides
`cpu`); `originalValue` is that paramset-layer value.

Example of a parameter overridden by a saved paramset (not by the current request), with the same
`backupDaemon.resources.limits.cpu` key: assume a namespace-level paramset (previously saved via
`POST /ui-parameters`) sets it to `"500m"`, and the current request body does not mention `cpu`:

```json
{
  "cpu": {
    "_type": "leaf",
    "_data": {
      "value": "500m",
      "state": "ui_override_committed",
      "originalValue": "300m"
    }
  }
}
```

`cpu` is `ui_override_committed` because the paramset layer's `"500m"` differs from the raw Effective Set value
`"300m"`, and the request layer doesn't override it further (so it equals the paramset layer's value); `originalValue`
is the raw Effective Set value.

Empty response when the resolved application has no Effective Set files yet (registered in the
namespace but never built by the Calculator) and the request body is empty:

```json
{
  "context": "deployment",
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "namespaceName": "env-01-core",
  "applicationName": "my-app",
  "parameters": {}
}
```

#### Processing logic

1. Validate request fields:
   - `context` present and one of `deployment`, `runtime`, `pipeline`.
   - For `deployment` and `runtime`: `namespaceName` and `applicationName` present.
   - For `pipeline`: `namespaceName` and `applicationName` absent.
   - Otherwise → `400 Bad Request`.
2. Resolve scope:
   - Resolve Environment by `environmentId`. Missing → `404`.
   - For `deployment` and `runtime`: resolve Namespace by `namespaceName` to obtain `deployPostfix`.
     Missing → `404`. Validate `applicationName` is associated with that namespace. Missing →
     `404`.
3. Read the cached per- (context, scope) parameter map (see [Cache](#cache)) — this is the **raw layer**.
4. Build the **paramset layer**: deep-copy the raw layer and merge the applicable paramsets on top (environment →
   namespace → application specificity; recursive merge for objects, full replacement for lists and primitives; keys not
   present in any paramset are kept from the raw layer).
5. Build the **request layer**: deep-copy the paramset layer and merge the request body
   `parameters` on top with the same merge rules. Keys absent from `parameters` are kept from the paramset layer.
6. Wrap each parameter of the request layer as `EffectiveSetParameter`:
    - For primitives and lists, set `_type = "leaf"` and `_data = { value, state, originalValue }`, computed
      per [State and originalValue computation](#state-and-originalvalue-computation) by comparing the same key across
      the raw, paramset, and request layers.
   - For objects, set `_type = "container"` and `_data` recursively.
7. Return the wrapped result along with `context`, `environmentId`, and (for `deployment`
   and `runtime`) `namespaceName` and `applicationName`.

## Open questions

- **Credentials files handling.** Whether and how the API should read `credentials.yaml` and
  `collision-credentials.yaml`, including masking of SOPS-encrypted values.
- **Collision parameters handling.** Whether and how the API should read
  `collision-deployment-parameters.yaml` and merge it over `deployment-parameters.yaml`.
- **`external-credentials.yaml` handling.** Whether and how the API should read
  `external-credentials.yaml`. The file is emitted by the Calculator in
  `effective-set/deployment/<deployPostfix>/<applicationName>/values/`. It is not SOPS-encrypted
  and holds credential reference metadata (`secretStoreId`, `normalizedSecretName`, `secretKeys`).

## Out of scope

- Reading `credentials.yaml`, `collision-credentials.yaml`,
  `collision-deployment-parameters.yaml`, `external-credentials.yaml` (see
  [Open questions](#open-questions)).
- Per-service parameters: `deploy-descriptor.yaml`, files under `per-service-parameters/`.
- BG Domain (blue-green deployment) scenarios.
