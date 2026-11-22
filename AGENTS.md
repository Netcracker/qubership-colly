# AGENTS.md — qubership-colly-v2

Qubership Colly: Kubernetes environment tracking across many clusters. Most work happens in
`envgene-inventory-service` (Quarkus 3, Java 21). Unless the request names another module, assume this one.

## Modules

| Module                            | Stack                                        | Purpose                                           |
|-----------------------------------|----------------------------------------------|---------------------------------------------------|
| `envgene-inventory-service`       | Quarkus, Java 21, Redis                      | Reads environments from Git (envgene), writes UI overrides back to Git |
| `environment-operational-service` | Quarkus, Java 21, Redis                      | Live cluster state, monitoring, sync from inventory service |
| `ui-service`                      | Quarkus + React 19 / MUI (`src/main/webui`)  | Frontend                                          |
| `charts/qubership-colly`          | Helm                                         | Deployment                                        |

Design docs: `docs/design/` — read the relevant one before changing a feature it describes
(e.g. `paramset-architecture.md`, `ui-override.md`, `applications-api.md`, `effective-set.md`,
`sync.md`, `git-repository-handling.md`, `envgene-inventory-service-openapi.yaml`).

## Commands

```bash
./mvnw test -pl envgene-inventory-service                 # fast loop for the main service
./mvnw test -pl envgene-inventory-service -Dtest=InventoryServiceRestTest#methodName
./mvnw test                                               # full check — required before reporting done
```

- Tests use Quarkus Dev Services for Redis → **Docker must be running**. Write-path tests also need `yq` on `PATH`.

## Definition of done

1. `./mvnw test` passes (compilation + all tests). Fix failures before finishing; if something can't pass, say so with the output.
2. If test data under `gitrepo_with_cloudpassports/` changed → update `CloudPassportLoaderTest` expectations (it hardcodes
   paramsets per environment, including `sourceName`, and `sdApplications`).
3. If a REST contract changed → update `docs/design/envgene-inventory-service-openapi.yaml` and the related design doc.
4. After every change, verify that the documentation is still correct:
   - search `docs/` (not only `docs/design/`), `README.md` and this file for the changed behavior, classes,
     properties, endpoints and status codes. Search by meaning too: "fails with an error" may have become false;
   - compare what you find with the code, not with memory or the plan; fix what is wrong, describe what is new;
   - in the final report, say which docs were checked and which were updated.

## Working conventions

- The user often writes in Russian — reply in the language of the request; code, commits, and docs stay in English.
- **Start every change on a new branch**: before the first edit, create it from an up-to-date `main`
  (`feature/…`, `fix/…`, `docs/…`, `refactor/…`). Never edit on `main`. Follow-ups to the same task stay on its branch.
  Uncommitted files of the user are carried over untouched — don't stash, reset or commit them.
- Commit messages: Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`). Commit only when asked.
- The repo root and `tmp/` contain many untracked personal scripts/notes (`*.sh`, `*.md`, `logs.log`) — leave them alone
  and never stage them.

---

## envgene-inventory-service

All paths below are relative to `envgene-inventory-service/src/main/java/org/qubership/colly/`.

| Class                      | Role                                                           |
|----------------------------|----------------------------------------------------------------|
| `InventoryServiceRest`     | REST API — all public endpoints (`/colly/v2/inventory-service`) |
| `CollyStorage`             | Business logic; reading/writing data in Redis                  |
| `CloudPassportLoader`      | Loads cloud passports, `env_definition.yml`, SD from Git       |
| `ParamsetService`          | Parse/write paramset files                                     |
| `UpdateEnvironmentService` | Writes environment changes back to Git                         |
| `GitService`, `GitRepositoryLocks` | Clone/update/push repositories; per-repo locking       |
| `YqService`                | In-place YAML edits by shelling out to the `yq` binary         |

### Tests

| Test                                   | Kind                                       |
|----------------------------------------|--------------------------------------------|
| `org.qubership.InventoryServiceRestTest` | REST integration (`@QuarkusTest`, `@TestTransaction`) |
| `org.qubership.EffectiveSetRestTest`   | Effective set REST integration             |
| `UpdateEnvironmentServiceTest`         | Git file writes (`@QuarkusComponentTest`)  |
| `CloudPassportLoaderTest`, `GitServiceTest` | Loader unit tests                     |

- Test data: `src/test/resources/gitrepo_with_cloudpassports/`.
- `GitService` is replaced by `MockGitService` / `@InjectMock` — it copies a folder from `src/test/resources/` instead of cloning.

### Paramsets — rules that are easy to get wrong

**Level comes from file content, never from the file name:**

```
deployPostfix == "cloud"                           → ENVIRONMENT  (only `parameters` is read; `applications` ignored)
deployPostfix != "cloud" + non-empty parameters    → NAMESPACE
deployPostfix != "cloud" + non-empty applications  → APPLICATION  (one Paramset per app entry)
```

One file with both `parameters` and `applications` yields NAMESPACE + APPLICATION records — intentional, covered by tests.
Use the constant `ENV_SPECIFIC_DEPLOY_POSTFIX`, not the literal `"cloud"`.

**`Paramset` record has 6 args — don't drop `sourceName`:**

```java
record Paramset(ParamsetContext paramsetContext, ParamsetLevel level, String deployPostfix,
                String applicationName,          // null for NAMESPACE / ENVIRONMENT
                Map<String, Object> parameters,
                String sourceName)               // file name without .yaml, e.g. "core-deploy-ui-override"
```

`sourceName` is the key into `env_definition.yml` and tells `UpdateEnvironmentService` which file owns a paramset.
`null` only in legacy tests. `parseParamsetFile` returns `List<Paramset>`.

**READ path — 3-level file lookup** (`ParamsetService.resolveParamsetFilePath`, first hit wins):

1. `<env>/Inventory/parameters/<name>.yaml` — environment
2. `<cluster>/parameters/<name>.yaml` — shared by the cluster's environments
3. `environments/parameters/<name>.yaml` — global

**WRITE path ignores that hierarchy** — always targets `<env>/Inventory/parameters/`.

**`/environments/{id}/ui-parameters`:**

| Request                       | Behavior                                                                                       | Code |
|-------------------------------|------------------------------------------------------------------------------------------------|------|
| GET                           | Reads ALL paramset files from `envTemplate` (any name). Filtering is correct as-is — don't touch. | `CollyStorage.getUiParameters` |
| POST, non-null value          | Writes ONLY to `-ui-override` files; adds reference to `env_definition.yml` via yq. Don't change this. | `writeParamsetFile`, `calculateParamsetFileName`, `addParamsetReferenceToEnvDefinition` |
| POST, `null` value            | Deletes the key from every file defining it; empty file → file deleted + reference removed     | `removeKeysFromParamsetFile`, `UpdateEnvironmentService.updateParamset` (Parts B/C) |

**`namespaceName` → `deployPostfix`:** find in `environment.getNamespaces()` by `name`, take `getDeployPostfix()`;
not found → 404. Implemented in `ParamsetService.resolveParamsetTarget`; reused by the Applications API.

**`env_definition.yml`:**

```yaml
envTemplate:
  envSpecificParamsets:            # → ParamsetContext.DEPLOYMENT
    core: [paramset-name]          # key = deployPostfix; value = file names without .yaml
    cloud: [env-level-paramset]    # reserved: environment scope
  envSpecificTechnicalParamsets:   # → RUNTIME
  envSpecificE2EParamsets:         # → PIPELINE
```

**Adding a paramset to test data:**

1. Put the YAML at env (`test-cluster/env-X/Inventory/parameters/`), cluster (`test-cluster/parameters/`) or
   global (`environments/parameters/`) level under `gitrepo_with_cloudpassports/environments/`.
2. Reference it in `env-X/Inventory/env_definition.yml` under the right section and deployPostfix.
3. Update `CloudPassportLoaderTest`.
4. Test via `/colly/v2/inventory-service/environments/{id}/ui-parameters`.

### Applications API — `GET /environments/{id}/applications?namespaceName=`

Spec: `docs/design/applications-api.md`. Key points:

- Returns app names from the env's SD, filtered by the namespace's `deployPostfix` (namespace not found → 404,
  no SD / no match → `[]`).
- Source priority (`CloudPassportLoader.loadSolutionDescriptor`): `Inventory/deploy-plan.y(a)ml` → fallback
  `Inventory/solution-descriptor/sd.y(a)ml`. Any entry with null `version`/`deployPostfix` invalidates the whole SD
  (empty list + warning).
- Stored as `List<SdApplication>` on `db/data/Environment.sdApplications` (Redis), set in
  `CollyStorage.saveEnvironmentToCache()` during sync (`docs/design/sync.md`). `SolutionDescriptor` is a read-only
  Jackson model, not stored.
- Name = part of `version` before `:` (`"postgres:1.32.6"` → `postgres`), whole string if no `:`, then `.distinct()`.
- Fixture: `gitrepo_with_cloudpassports/environments/test-cluster/env-1/Inventory/solution-descriptor/sd.yaml`.
