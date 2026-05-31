# Paramset System Architecture

## Overview

A paramset is a set of parameters associated with an environment. It is used to customize UI and deployments. Paramset
files are stored in Git under each environment's `Inventory/parameters/` directory.

---

## Contexts (ParamsetContext)

| Context      | Description                    | Section in `env_definition.yml` |
|--------------|--------------------------------|---------------------------------|
| `DEPLOYMENT` | Deployment parameters          | `envSpecificParamsets`          |
| `RUNTIME`    | Technical / runtime parameters | `envSpecificTechnicalParamsets` |
| `PIPELINE`   | Pipeline (e2e) parameters      | `envSpecificE2EParamsets`       |

---

## Levels (ParamsetLevel)

Level is determined by **file content** and the deployPostfix key in `env_definition.yml`.

| Condition                                             | Level         | What is read from the file                                |
|-------------------------------------------------------|---------------|-----------------------------------------------------------|
| `deployPostfix == "cloud"` (reserved)                 | `ENVIRONMENT` | `parameters` section                                      |
| `deployPostfix != "cloud"` + non-empty `parameters`   | `NAMESPACE`   | `parameters` section                                      |
| `deployPostfix != "cloud"` + non-empty `applications` | `APPLICATION` | `applications[i].parameters` (one record per application) |

**Note:** a single file may contain both sections (`parameters` + `applications`) and will produce **both levels** (
NAMESPACE and APPLICATION) simultaneously.

---

## Paramset file structure

```yaml
name: my-paramset
parameters:           # → namespace-level (or environment-level when deployPostfix = "cloud")
  SOME_PARAM: "value"
applications:         # → application-level (one record per application)
  - appName: "my-app"
    parameters:
      APP_PARAM: "app value"
  - appName: "other-app"
    parameters:
      OTHER_PARAM: "other value"
```

---

## Reserved deployPostfix "cloud"

`cloud` is a reserved word for environment-level paramsets (parameters scoped to the whole environment). All other
deployPostfix values correspond to namespaces within the environment.

---

## READ vs WRITE paths

|                 | READ (`GET /ui-parameters`)                    | WRITE (`POST /ui-parameters`)                                                              |
|-----------------|------------------------------------------------|--------------------------------------------------------------------------------------------|
| Scope           | **All** paramset files listed in `envTemplate` | Only `-ui-override` files                                                                  |
| Level detection | Content-based (see table above)                | Name-based convention                                                                      |
| File names      | Any                                            | `deploy-ui-override`, `runtime-ui-override`, `pipeline-ui-override` with optional prefixes |

Write-path naming convention:

- Environment: `deploy-ui-override.yaml`
- Namespace: `{deployPostfix}-deploy-ui-override.yaml`
- Application: `{deployPostfix}-{appName}-deploy-ui-override.yaml`

---

## GET /ui-parameters — filtering

Query parameters determine the requested level:

| Query params                      | Level         | Filter applied                                                                           |
|-----------------------------------|---------------|------------------------------------------------------------------------------------------|
| (none)                            | `ENVIRONMENT` | `level == ENVIRONMENT`                                                                   |
| `namespaceName`                   | `NAMESPACE`   | `level == NAMESPACE AND deployPostfix == dp(namespace)`                                  |
| `namespaceName + applicationName` | `APPLICATION` | `level == APPLICATION AND deployPostfix == dp(namespace) AND applicationName == appName` |

`namespaceName → deployPostfix` resolution happens via `Namespace.deployPostfix` stored in the database.

---

## Classes

| Class                          | Role                                                               |
|--------------------------------|--------------------------------------------------------------------|
| `ParamsetService`              | Paramset file parsing + write/update utilities                     |
| `CollyStorage.getUiParameters` | Filters loaded paramsets by requested level                        |
| `Paramset`                     | Record: context, level, deployPostfix, applicationName, parameters |
| `ParamsetFileData`             | Jackson deserializer for paramset YAML files                       |
| `ParamsetContext`              | Enum: DEPLOYMENT / RUNTIME / PIPELINE                              |
| `ParamsetLevel`                | Enum: ENVIRONMENT / NAMESPACE / APPLICATION                        |
