# Plan: skip only the environment that cannot be loaded

**Status:** implemented. Branch: `fix/keep-environments-on-parse-error`.

Related: [sync.md](sync.md) describes the resulting behavior.

## Problem

One bad value in one file removes **all environments of the cluster** from Colly:

1. An error that is not an `IOException` (`valueOf`, `LocalDate.parse`, NPE) escapes
   `CloudPassportLoader.processEnvDefinition`.
2. It aborts the stream over all environments of the cluster in `processEnvironmentsInClusterFolder`, whose
   `catch` returns an empty set.
3. `CollyStorage.saveDataToCache` deletes every environment that is not in that set.

Reproduced on `main` `f5b8a93` with two clusters of 6 environments, one file broken at a time:

| # | Broken input                                                           | Result today                                |
|---|------------------------------------------------------------------------|---------------------------------------------|
| A | unknown or wrong-case `status` / `type`, bad or empty `expirationDate` | cluster loses 6 of 6                        |
| B | `env_definition.yml` is only `---`                                     | cluster loses 6 of 6                        |
| C | `env_definition.yml` is empty, only a comment, or invalid YAML         | that environment is deleted                 |
| D | `env_definition.yml` without `inventory`                               | that environment is deleted, nothing logged |
| E | `namespace.yml` is only `---`                                          | cluster loses 6 of 6                        |
| F | `namespace.yml` without `name`                                         | **next syncs of all projects fail**         |
| G | paramset is only `---`, or has an empty item in `applications`         | 6 of 6; 12 of 12 for a global paramset      |
| H | a folder inside an environment cannot be read                          | all clusters of the repository are deleted  |

F in detail: the first sync stores a namespace with `name = null` in Redis, the next one throws an NPE in
`saveNamespaceToCache`: manual sync answers 500, startup fails. Fixing the file does not always help: if that
namespace is the first one stored for the environment, as in a new environment, the NPE stays.

## Decisions

### Agreed

1. An environment that cannot be loaded is **not added to the list of environments**, an error is logged.
2. If it was loaded before and is broken now, **the sync deletes it from Colly**.
3. Other environments are not affected: they are loaded, updated and deleted as usual.
4. An environment without `inventory` is skipped the same way: log an error with the file path.
5. Startup, cron and manual sync behave the same. None of the above fails the sync, manual sync answers 204.
6. A bad namespace or paramset file (E, F, G) does not remove the environment: the file or item is skipped with
   an error. Otherwise one global paramset deletes every environment that uses it, 12 of 12 in the test.
7. Namespaces with `name = null` that are already in Redis are removed when their environment is saved.
8. Folders that cannot be walked (H) make a failed repository, as in #350: the clusters of the project are kept.

## Target behavior

| Case                          | That environment                      | Other environments             | Log                              |
|-------------------------------|---------------------------------------|--------------------------------|----------------------------------|
| A, C, any unexpected error    | not added, deleted if it was in Colly | updated                        | ERROR with path and exception    |
| B                             | not added, deleted if it was in Colly | updated                        | ERROR with path                  |
| D                             | not added, deleted if it was in Colly | updated                        | ERROR with path                  |
| E, F                          | updated, without that namespace entry | updated                        | ERROR with path                  |
| G                             | updated, without that file or item    | updated                        | ERROR with path                  |
| H                             | kept as of the last good sync         | in that repository: kept as is | ERROR, project in the final line |

If all environments of a cluster are broken, the cluster stays with an empty list.
A fixed environment is added by the next sync **with a new id**.

## Changes

All in `envgene-inventory-service`. `CloudPassport`, the REST contract and the Redis format do not change.

### Step 1. Isolate the failure of one environment

`CloudPassportLoader`:

- `processEnvironmentsInClusterFolder`: collect the `env_definition.yml` paths first, then load each in its own
  `try / catch (Exception)`. On error: log the path with the exception and continue.
- `processEnvDefinition`: do not swallow `IOException`, neither of the file nor of the namespace walk.
- A document without content (B): error with the path, return `null`, the same as without `inventory`.
- `inventory == null`: error with the path, return `null`.

### Step 2. Deletion

The logic of `CollyStorage.saveDataToCache` stays: what was not loaded is deleted. `syncProject` uses it too.

Only fix its log message, "no longer exists" is false for a broken environment:

```
INFO Environment env-test was not loaded from Git for cluster test-cluster - removing from cache
```

### Step 3. Bad namespace and paramset files

- `parseNamespaceFile`: no content or blank `name` → error with the path, skip the file.
- `ParamsetService.parseParamsetFile`: no content → error with the path, no parameters; empty item in
  `applications` → error, skip the item.
- `saveEnvironmentToCache`: drop stored namespaces with `name = null` before merging (a new method on
  `db/data/Environment`).

### Step 4. Failed walk over folders

- `processEnvironmentsInClusterFolder`: a failed walk is rethrown, it must not become an empty list.
- `loadCloudPassports`: on a failed walk add the project to `failedProjectIds`, so that `removeDeletedClusters`
  keeps its clusters and `logFailedProjects` reports it.

### Step 5. Write path

Found while writing the tests: steps 1 and 3 make environments next to broken files available, and then writes to
them failed with 500.

- `UpdateEnvironmentService.resolveEnvironmentName`: a neighbour `env_definition.yml` without content is ignored,
  otherwise `PATCH` and `POST ui-parameters` fail for the environments of that cluster.
- `ParamsetService.readParamsetFile`: one method for sync and write, so `---` and empty application items do not
  break a POST. A file with 0 bytes, only a comment or invalid YAML still fails it, see "Out of scope".

## Tests

Write them first. The ones for changed behavior must fail on the current code.

| Test class                 | Cases                                                                                   |
|----------------------------|-----------------------------------------------------------------------------------------|
| `CloudPassportLoaderTest`  | A, B, C: only that environment is missing, the others are loaded                        |
|                            | D: only that environment is missing                                                     |
|                            | E, F, G: environment is loaded without the bad entry                                    |
|                            | H: project is in `failedProjectIds`                                                     |
| `InventoryServiceRestTest` | A: 204, the environment that was good is deleted, the others keep ids and get updates   |
|                            | A with `projectId`: same                                                                |
|                            | A, then fixed: the environment is back                                                  |
|                            | all environments of a cluster are broken: the cluster stays, without environments       |
|                            | F: a `null` namespace already in Redis → sync answers 204 and removes it                |
|                            | writes work next to a broken environment, and with skipped paramset files and items     |
| `CollyStorageStartupTest`  | startup succeeds with a `null` namespace already in Redis                               |

Existing tests need no changes.

## Documentation

- `sync.md`: steps, failures, remove the fixed limitations, describe the deletion of a broken environment and
  the operational id mismatch.
- `ui-override.md`, section Cache: which paramset files are skipped.
- `ui-override-versioning.md`: a sync that reads a half-written file can delete the environment.
- `paramset-architecture.md`, `environment_properties_mapping.md`, `CONFIGURATION.md` (troubleshooting),
  `AGENTS.md`: check against the code, update where needed.
- OpenAPI: check the description of 204 of `/manual-sync`. Status codes do not change.

## Consequences of deleting a broken environment

- Until the file is fixed it is absent from the UI and API, requests by its id answer 404. It cannot be fixed
  through Colly, only in Git.
- After the fix it has a new id. `environment-operational-service` finds it by name and keeps the old one, the UI
  joins by id, so its monitoring, deployment version, clean installation date and namespace state are **not shown**,
  also after next syncs.
- A short read error of `env_definition.yml` has the same effect.
- It is noticed only by the log entry: the final line of the sync lists projects, not environments.

## Out of scope

- The id mismatch in `environment-operational-service`: a separate PR, needed for the fixed environment to show
  its runtime data again.
- Broken `parameters.yaml`, broken cloud passport, missing `environments/` folder: the next fixes.
- Namespaces removed from Git stay in Redis, a changed `deployPostfix` is not picked up.
- Accepting `status` and `type` in any case.
- A POST that has to read a paramset file with 0 bytes, only a comment or invalid YAML answers 500, as before this fix.

## Verification

1. The new tests for changed behavior fail before the fix, all pass after it.
2. `mvn test` is green in the working tree and on a clean export of the change (`./mvnw` has no wrapper files).
3. Cases A–H are run again on the fix and compared with [Target behavior](#target-behavior).
4. Documentation is checked as required by the Definition of done in `AGENTS.md`.
