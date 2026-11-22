# Synchronization (envgene-inventory-service)

How `envgene-inventory-service` turns the content of Git into the data it serves from Redis: what starts a sync,
what it does, what it deletes, and what happens on failures.

How a single repository is cloned, updated and locked is described in
[git-repository-handling.md](git-repository-handling.md).

## Overview

```
project repository ──→ projects ──┬─→ instance repositories ──→ clusters, environments ──→ Redis
 (parameters.yaml)                └─→ dcl repositories ───────→ local copy only
                                                                 (effective set is read from it on demand)
```

- Git is the source of truth. Redis holds the result of the last sync plus the writes made through the API since then.
- Sync never writes to Git.
- `environment-operational-service` has its own schedule and reads the result over REST. It is not notified about a sync.

## Triggers

| Trigger                                | Method                    | Scope        | Project list comes from |
|----------------------------------------|---------------------------|--------------|-------------------------|
| Service startup                        | `onStart` → `syncAll`     | all projects | project repository      |
| Cron `colly.eis.cron.schedule`         | `syncAll`                 | all projects | project repository      |
| `POST /manual-sync`                    | `syncAll`                 | all projects | project repository      |
| `POST /manual-sync?projectId=<id>`     | `syncProject`             | one project  | Redis                   |

All methods are in `CollyStorage`. The first three run exactly the same logic.

- **Schedule**: every minute by default, every 2 minutes in the Helm chart (`cronSchedule`).
- **Overlap**: if `syncAll` is already running, another call returns at once without syncing
  (`syncRunning` flag). A manual sync answers 204 in this case too. `syncProject` does not use the flag.
- **Startup is blocking**: HTTP, health endpoints included, starts listening only after the first sync. The chart
  mounts no volume for the clone folders, so every new pod clones all repositories from scratch, and this has to
  fit into the liveness probe limits.

## `syncAll` step by step

```
syncAll
 1. effectiveSetCalculator.clearCache
 2. ProjectRepoLoader.loadProjects             update the project repository, parse parameters files
 3. removeDeletedProjects                      projects missing from step 2, with their clusters and environments
 4. projectRepository.persist                  every loaded project
 5. CloudPassportLoader.loadCloudPassports
      a. for each project: dcl repositories, then instance repositories
      b. for each synced instance repository: read clusters and environments from the files
      → CloudPassportLoadResult(cloudPassports, failedProjectIds)
 6. removeDeletedClusters                      skips the projects in failedProjectIds
 7. saveDataToCache                            upsert clusters and environments, delete missing environments
 8. effectiveSetCalculator.clearCache          drop what was cached while the folders were changing
 9. logFailedProjects
```

Redis is updated step by step, without a transaction: readers can see a partly updated state during a sync,
and an exception leaves the steps already done in place.

### Projects

- Every file whose path ends with `parameters.yaml`, or whose name is `parameters.yml`, is a project, anywhere in
  the project repository. The project id is the name of the folder that contains the file.
- Repository types used by sync: `envgeneInstance` and `dcl`. `envgeneTemplate` is stored with the project
  and is not cloned.

### Which repositories are downloaded

Every instance repository of every project, and a dcl repository if all of these hold:

1. it has a url;
2. the project has an instance repository in the same region (blank region = `colly.eis.git.default-region`, `cn`);
3. it is the first such dcl repository for that region. The next ones are skipped even if the first one fails;
4. a token is found: the explicit one, or the one configured for the region.

Whether the project has `NO_CMDB_V2` environments does not matter: dcl repositories are downloaded before
`env_definition.yml` files are read.

"Downloaded" means a full shallow clone only when there is no reusable copy. Otherwise it is an `ls-remote`,
plus a fetch if the remote moved or the copy has local changes.

### What is read from an instance repository

Paths are relative to the repository root.

| Data                  | Source                                                                                          |
|-----------------------|-------------------------------------------------------------------------------------------------|
| Cluster               | a folder under `environments/` that contains `cloud-passport/`; its name is the cluster name     |
| Cluster properties    | `cloud-passport/<cluster>.y(a)ml` or `passport.y(a)ml`, token from the matching `-creds` file    |
| Environment           | `env_definition.yml` in the cluster folder (`<env>/Inventory/`)                                 |
| Environment name      | `inventory.environmentName`, else the environment folder name                                   |
| Namespaces            | `namespace.yml` files in the environment folder; `deployPostfix` = the parent folder name        |
| Paramsets             | files referenced from `envTemplate`, see [paramset-architecture.md](paramset-architecture.md)    |
| Applications          | deploy plan, else SD, see [applications-api.md](applications-api.md)                             |
| `cmApproach`          | `inventory.noCmdbVersion` (`v2` / `v1`), else `CMDB` if `inventory.deployer` is set, else `NO_CMDB` |
| `effectiveSetPath`    | `<env>/effective-set` in the instance copy                                                      |
| `dclEffectiveSetPath` | the same relative path in the dcl copy of the region; `NO_CMDB_V2` only, else `null`            |

Sync stores only the paths of effective sets. Their files are read on request, see
[effective-set.md](effective-set.md#cache).

## How data is matched and deleted

Ids are generated by Colly and survive a sync as long as the object is matched:

| Object      | Matched by           | Updated                         | Deleted from Redis                                                          |
|-------------|----------------------|---------------------------------|-----------------------------------------------------------------------------|
| Project     | id (folder name)     | replaced                        | by `syncAll`, when it is not among the loaded projects                      |
| Cluster     | project id + name    | all properties read from Git    | by `syncAll`, when it was not loaded and its project has no failed repository; or with its project |
| Environment | cluster id + name    | all properties read from Git    | by both syncs, when its cluster was loaded without it; or with its cluster  |
| Namespace   | environment + name   | never, only added               | never, only with its environment                                            |

"Not loaded" covers both "removed from Git" and "could not be parsed", see [Known limitations](#known-limitations).
A deleted object that appears again gets a new id.

## `syncProject`

Same loading and saving for one project, with these differences:

- the project definition is taken from Redis, so a change in `parameters.yaml` (a new dcl repository, for example)
  is visible only after the next `syncAll`;
- projects and clusters are never deleted, environments of the loaded clusters are;
- it can run in parallel with `syncAll`.

## Failures

A failed repository never fails the sync. Startup, cron and manual sync behave the same.

| Failure                                             | Effect                                                                                     | In the final log line | Manual sync |
|-----------------------------------------------------|--------------------------------------------------------------------------------------------|-----------------------|-------------|
| Instance repository: no url, no token, git or folder error | repository skipped, others continue; no cluster of its project is deleted            | yes                   | 204         |
| Dcl repository, a previous copy exists              | the previous copy keeps being read                                                         | no                    | 204         |
| Dcl repository, no copy                             | `dclEffectiveSetPath = null`, deployment and runtime effective sets have an empty raw layer | no                   | 204         |
| Project repository unavailable                      | `syncAll` aborts before changing Redis; at startup the service fails to start              | no, exception         | 500         |
| Redis or another unexpected error                   | sync aborts where it was; at startup the service fails to start                            | no, exception         | 500         |
| Unknown `projectId`                                 | nothing is synced                                                                          | no                    | 404         |

Each repository failure is logged when it happens, with the reason. The sync ends with the list of project ids
that have a failed instance repository:

```
ERROR [org.qub.col.CollyStorage] Failed to sync 1 project(s): [solar_earth]
```

Clusters are kept by project and not by repository url, because the url of the failed repository may have just
been changed. So while one repository of a project fails, clusters removed from the other repositories of that
project stay in Colly too.

## Known limitations

Current behavior, not intended design. Update this list when one of them is fixed.

### Data is deleted although it is still in Git

These cases are not reported as failed projects. The data comes back with new ids once the cause is fixed.

| Cause                                                                            | What is deleted                         |
|----------------------------------------------------------------------------------|-----------------------------------------|
| Project parameters file cannot be parsed, or has no `repositories`               | the project, its clusters, environments |
| Cloud passport or its creds cannot be parsed                                     | the cluster and its environments        |
| No `environments/` folder in an instance repository (wrong branch, for example)  | all clusters of that repository         |
| `env_definition.yml` is not valid YAML or has no `inventory`                     | that environment                        |
| Any other error in one environment: unknown `status` / `type`, bad date, empty entry in paramset `applications` | **all environments of the cluster** |
| Two projects have the same `name` and instance repository url, thus one folder   | the clusters of the project that is processed second |
| Two repositories of one project have a cluster with the same name                | each save deletes the environments of the other: the last one wins, ids change on every sync |

### Stale or wrong data

- A namespace removed from Git stays in Redis, and a changed `deployPostfix` is not picked up.
- A dcl and an instance repository with the same url share one folder in a project, whatever their branches are.
  The instance repository is synced last, so its branch is what gets read.
- Files are parsed outside the repository lock, and an API write can race with saving to Redis, see
  [ui-override-versioning.md](ui-override-versioning.md#known-limitations).
- Folders of removed or renamed projects and repositories are not cleaned up.

### Concurrency

- `syncProject` in parallel with another sync can delete its `*-tmp` folder in the middle of a clone
  (`cleanupTmpDirectories` removes all of them).
- The `syncRunning` flag and the repository locks are local to the JVM. Replicas would sync independently
  (the chart runs one).

## Code map

| What                          | Where                                                                    |
|-------------------------------|--------------------------------------------------------------------------|
| Triggers, Redis update        | `CollyStorage`                                                           |
| REST endpoint                 | `InventoryServiceRest.syncEnvironmentsWithGit`                           |
| Projects                      | `ProjectRepoLoader`                                                      |
| Repositories, file parsing    | `CloudPassportLoader`, `ParamsetService`                                 |
| Sync result                   | `CloudPassportLoadResult`                                                |
| Redis                         | `ProjectRepository`, `ClusterRepository`, `EnvironmentRepository`        |
| Tests                         | `CloudPassportLoaderTest`, `CollyStorageStartupTest`, `InventoryServiceRestTest` (`sync*`) |
