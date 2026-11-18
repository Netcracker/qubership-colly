# Git repository handling (envgene-inventory-service)

How `envgene-inventory-service` keeps local copies of Git repositories, how it brings them up to date,
and how reads and writes are kept apart.

## Repositories and where they live

| Repository           | Source                                     | Local folder                                    | Token                                        |
|----------------------|--------------------------------------------|-------------------------------------------------|----------------------------------------------|
| Project repository   | `colly.eis.project.repo.url`               | `colly.eis.project.repo.folder` (`./project-git`) | `colly.eis.project.repo.git.token`         |
| Instance repositories| `Project.instanceRepositories()`           | `<cloud.passport.folder>/<folder name>`         | explicit repo token, else `colly.eis.git.tokens.<region>` |
| DCL repositories     | `Project.dclRepositories()` (NO_CMDB_V2)   | `<cloud.passport.folder>/<folder name>`         | same as instance repositories                |

`<cloud.passport.folder>` is `colly.eis.cloud.passport.folder` (`./git-repo` by default).

Tokens are resolved by `GitService.resolveToken(explicitToken, region)`: the repository's explicit token wins,
otherwise the token for its region comes from `colly.eis.git.tokens`. If neither is set, an error is raised.
Git authentication always uses `oauth2` / `<token>`.

### Stable folder name

`CloudPassportLoader.repositoryFolderName(project, url)` → `<project name>-<sha256(url)[0..12]>`
(project id if the name is blank; unsafe characters are replaced with `_`).

The name depends only on the project and the URL, not on the order of projects or repositories. So:

- `GitInfo.folderName` of a cluster points to the same repository on every sync;
- the local copy from the previous sync can be reused (see below);
- a repository listed twice in one project is cloned once (the duplicate is skipped with an error log).

## Read path — scheduled sync

`CollyStorage.syncAll()` runs on `colly.eis.cron.schedule`. Overlapping runs are skipped
(`ConcurrentExecution.SKIP` plus the `syncRunning` flag).

```
CollyStorage.syncAll
  ├─ ProjectRepoLoader.loadProjects
  │    └─ GitService.tryUpdateExistingRepository(project-git)   ── false ─→ delete folder + cloneRepository
  └─ CloudPassportLoader.loadCloudPassports
       └─ cloneGitRepositories
            ├─ cleanupTmpDirectories                        remove leftover "*-tmp" folders
            └─ for each project:
                 ├─ cloneDclRepositories                    one DCL repo per region that has an instance repo
                 │    └─ cloneToStableFolder
                 └─ for each instance repository:
                      └─ cloneToStableFolder
```

### `cloneToStableFolder` — update in place, clone as fallback

```
cloneToStableFolder(repository, token, folderName)
  ├─ withLock(stableFolder):
  │    GitService.tryUpdateExistingRepository(stableFolder, url, branch, token)
  │       └─ true  → done
  └─ false →
       cloneRepository(url, branch, token, "<folder>-tmp")      depth 1
       replaceStableFolder(tmp, stable)                          under the same lock: delete stable + move tmp
       on error: delete tmp, rethrow
```

The full clone goes to a `-tmp` folder and replaces the stable folder only when it is complete,
so readers never see a half-cloned repository.

### `GitService.tryUpdateExistingRepository`

The copy is reused only if all of these hold. Otherwise the method returns `false` and the caller clones from scratch:

1. `<folder>/.git` exists and the folder opens as a Git repository;
2. `remote.origin.url` equals the expected URL;
3. the local branch equals the expected branch (`null`/blank branch means "the branch the copy is on",
   used for the project repository, which follows the remote default branch);
4. `ls-remote` finds that branch on the remote.

Then:

| Situation                                              | Action                                                            |
|--------------------------------------------------------|-------------------------------------------------------------------|
| remote head == local `HEAD` and working tree is clean  | nothing; one `ls-remote` request only                             |
| remote moved, or local tree is dirty                   | `fetchAndReset`: `fetch --depth 1 +refs/heads/<b>:refs/remotes/origin/<b>`, `reset --hard`, `clean -fd` |
| any exception                                          | warning log, return `false` → full clone                          |

After an update, `gcIfNeeded` runs `git gc` when there are more than 2000 loose objects or more than 20 pack
files. Each incremental fetch leaves a new pack behind, and without gc they would pile up.

Result: for repositories that did not change, a scheduled sync costs one `ls-remote` per repository instead
of a full clone. A changed repository downloads only the new head commit.

### Failure handling

- **Instance repository**: if the stable folder cannot be replaced, the repository is skipped for this sync (error log).
- **DCL repository**: a failure never stops the sync. If a copy from a previous sync exists, it keeps being read.
- **Project repository**: if the folder cannot be cleaned before cloning, `loadProjects` returns `null`.

## Write path — committing changes

Used by `UpdateEnvironmentService` (`POST /environments/{id}/ui-parameters`, `PATCH /environments/{id}`).
Writes go to the same local copy that the scheduled sync reads.

```
withLock(<instance repository folder>):
  ├─ GitService.syncWithRemote      fetchAndReset on the current branch (drops any local state)
  ├─ modify files in the working copy
  └─ GitService.commitAndPush       add . + commit + push; PushResult is checked
                                    (rejected push → GitPushRejectedException → 409)
```

Redis is updated only after a successful push.

## Locking

`GitRepositoryLocks` keeps one `ReentrantLock` per folder (keyed by the normalized absolute path).
These operations on the same folder never overlap:

| Holder                                     | Why                                                                    |
|--------------------------------------------|------------------------------------------------------------------------|
| `UpdateEnvironmentService` write           | sync + modify + commit + push must be atomic for `add .` and `index.lock` |
| `cloneToStableFolder` → `tryUpdateExistingRepository` | `reset --hard` / `clean` must not wipe files a write is preparing |
| `replaceStableFolder`                      | a write must never see a deleted or half-moved folder                  |

The lock is held only while the folder is being changed. The full clone into `-tmp` runs without it,
so a slow clone does not block writes. The project repository is not locked because nothing writes to it.

## Code map

| What                                   | Where                                                    |
|----------------------------------------|----------------------------------------------------------|
| Clone / update / sync / commit / push  | `GitService`                                             |
| Per-folder locks                       | `GitRepositoryLocks`                                     |
| Instance and DCL repository sync       | `CloudPassportLoader.cloneGitRepositories`, `cloneToStableFolder`, `replaceStableFolder` |
| Folder naming                          | `CloudPassportLoader.repositoryFolderName`               |
| Project repository sync                | `ProjectRepoLoader.loadProjects`                         |
| Writes                                 | `UpdateEnvironmentService`                               |
| Scheduler                              | `CollyStorage.syncAll`                                   |
| Tests                                  | `GitServiceTest`, `CloudPassportLoaderTest`, `ProjectRepoLoaderTest` (`MockGitService` in tests) |
