# CLAUDE.md — qubership-colly-v2

Qubership Colly is a Kubernetes environment tracking tool for multi-cluster visibility. Primary development service:
`envgene-inventory-service` (Quarkus, Java).

## Project structure

```
envgene-inventory-service/        ← main backend service (Quarkus)
environment-operational-service/  ← operational service
ui-service/                       ← frontend
charts/                           ← Helm charts
```

## Key classes (envgene-inventory-service)

| Class                      | Role                                                  |
|----------------------------|-------------------------------------------------------|
| `InventoryServiceRest`     | REST API — all public endpoints                       |
| `CollyStorage`             | Business logic for reading/writing data               |
| `ParamsetService`          | Parsing and writing paramset files                    |
| `CloudPassportLoader`      | Loads data from Git (cloud passport + env_definition) |
| `UpdateEnvironmentService` | Writes environment changes back to Git                |
| `GitService`               | Clones Git repositories                               |

## Tests

- **`InventoryServiceRestTest`** — REST API integration tests (`@QuarkusTest`, `@TestTransaction`)
- **`UpdateEnvironmentServiceTest`** — Git file write tests (`@QuarkusComponentTest`)
- **`CloudPassportLoaderTest`**, **`GitServiceTest`** — unit tests for loaders
- Test data: `src/test/resources/gitrepo_with_cloudpassports/`
- `GitService` is mocked via `@InjectMock` — copies a folder from `src/test/resources/` instead of real clone

Run tests: `mvn test -pl envgene-inventory-service`

## After every change

Always verify before reporting work as done:

```
mvn test
```

All tests must pass and compilation must succeed. Fix failures before finishing.

`CloudPassportLoaderTest` hardcodes the exact paramset list for each environment — if test data changes (new paramset
files or updated `env_definition.yml`), this test must be updated to match.

---

## Paramset system — key rules

### Level is determined by file content, NOT by file name

```
deployPostfix == "cloud"                          → ENVIRONMENT level  (uses `parameters` section)
deployPostfix != "cloud" + non-empty parameters   → NAMESPACE level
deployPostfix != "cloud" + non-empty applications → APPLICATION level (one Paramset per app entry)
```

A single file with both `parameters` and `applications` produces both NAMESPACE and APPLICATION records — this is
intentional and covered by tests.

### READ vs WRITE path — different logic

- **GET `/ui-parameters`** → reads ALL paramset files from `envTemplate` (any file name)
- **POST `/ui-parameters`** → writes ONLY to `-ui-override` files (`writeParamsetFile`, `calculateParamsetFileName`) —
  do not change this

### "cloud" is a reserved deployPostfix

`cloud` always means environment-level. Only the `parameters` section is read from files under the `cloud` key;
`applications` is ignored.

### namespaceName → deployPostfix mapping

`namespaceName` query param → look up in `environment.getNamespaces()` by `name` → take `namespace.getDeployPostfix()`.
Done in `ParamsetService.resolveParamsetTarget`. If namespace not found → `NotFoundException` (404).

### Adding a new paramset to a test

1. Create a YAML file in `src/test/resources/gitrepo_with_cloudpassports/test-cluster/env-X/Inventory/parameters/`
2. Reference it in `env-X/Inventory/env_definition.yml` under the correct section and deployPostfix
3. Update `CloudPassportLoaderTest` expected data to include the new paramsets
4. Write the test against `/colly/v2/inventory-service/environments/{id}/ui-parameters`

### `env_definition.yml` structure

```yaml
envTemplate:
  envSpecificParamsets: # → ParamsetContext.DEPLOYMENT
    core: # deployPostfix (namespace scope)
      - paramset-name            # file name without .yaml
    cloud: # reserved: environment scope
      - env-level-paramset
  envSpecificTechnicalParamsets: # → RUNTIME
    ...
  envSpecificE2EParamsets: # → PIPELINE
    ...
```

### Where things live

| What                               | Where                                                                           |
|------------------------------------|---------------------------------------------------------------------------------|
| REST endpoint                      | `InventoryServiceRest.java`                                                     |
| Paramset file parsing              | `ParamsetService.parseParamsets` / `parseParamsetFile`                          |
| Filtering on GET                   | `CollyStorage.getUiParameters`                                                  |
| Paramset file writing              | `ParamsetService.writeParamsetFile` + `UpdateEnvironmentService.updateParamset` |
| Adding reference to env_definition | `ParamsetService.addParamsetReferenceToEnvDefinition` (via yq)                  |
| Test data                          | `src/test/resources/gitrepo_with_cloudpassports/`                               |
| Architecture docs                  | `docs/design/paramset-architecture.md`                                          |

### Common mistakes

- Do not filter paramsets by file name in the READ path — level is determined by content only
- `parseParamsetFile` returns `List<Paramset>`, not a single object
- Use the constant `ENV_SPECIFIC_DEPLOY_POSTFIX` instead of the string `"cloud"`
- `CollyStorage.getUiParameters` filtering logic is correct as-is — usually no need to touch it
