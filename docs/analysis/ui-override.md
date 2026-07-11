# Override UI

- [Override UI](#override-ui)
  - [Problem statement](#problem-statement)
  - [Scenarios](#scenarios)
  - [Use cases](#use-cases)
  - [Open questions](#open-questions)
  - [Proposed approach](#proposed-approach)
    - [EnvGene](#envgene)
      - [Option 1. Env Specific Parameters Override](#option-1-env-specific-parameters-override)
        - [Option 1A. Basic variant](#option-1a-basic-variant)
        - [Option 1B. Extended variant](#option-1b-extended-variant)
      - [Option 2. Env Instance Override](#option-2-env-instance-override)
      - [Option 3. Effective Set Override](#option-3-effective-set-override)
      - [Option 4. UI Override Files (Simplified Approach)](#option-4-ui-override-files-simplified-approach)
      - [Comparison of options](#comparison-of-options)
  - [API documentation](#api-documentation)

## Problem statement

1. **UI is more convenient**

   Working through Git requires specific skills and habits. Users who are used to working through a UI
   may find this inconvenient and off-putting.

2. **EnvGene is complex**

   EnvGene includes a large number of diverse objects required to support various use cases. The user
   has to learn and understand these objects in order to perform even simple actions such as changing
   a single parameter. This creates a high entry barrier for new users and requires significant time
   to learn the system.

3. **Changing one parameter takes too long**

   Changing a parameter in EnvGene takes a noticeable amount of time: repository checkout, editing a
   YAML file in Git, push to the remote repository. In development scenarios, when a developer works
   with a single environment, performs a dev test, and needs frequent parameter changes, the current
   parameter workflow introduces significant overhead. Changing one parameter takes a long time, which
   results in lost developer time.

## Scenarios

1. **Dev/QA test**

   A developer/QA engineer or a group of developers/QA engineers has received a cloud-deployed
   environment for testing changes.

   During debugging and testing, individual applications are redeployed multiple times with parameter
   changes in CM in order to reach a working state of the functionality.

   The parameter changes obtained during debugging and testing need to be saved in the environment
   template for reproducibility in future environments and for use in other instances.

2. **CI environment stabilization**

   After automated tests have failed during an automated solution deployment to a CI environment, a
   QA engineer applies fixes/hot-fixes to this solution in order to achieve a successful test run.

   The process involves multiple redeploys of individual applications, which requires parameter
   changes for these applications in CM.

   The parameter changes obtained during debugging and testing need to be saved in the environment
   template for reproducibility in future environments and for use in other instances.

## Use cases

1. Create override.
   1. Add a new parameter to:
      1. Deployment context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to all applications in the namespace)
         3. Application level (applies to a specific application)
      2. Runtime context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to all applications in the namespace)
         3. Application level (applies to a specific application)
      3. Pipeline context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to a specific namespace)
   2. Override a parameter value for:
      1. Deployment context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to all applications in the namespace)
         3. Application level (applies to a specific application)
      2. Runtime context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to all applications in the namespace)
         3. Application level (applies to a specific application)
      3. Pipeline context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to a specific namespace)
   3. ~~Remove a parameter~~
2. View override parameters for:
   1. Deployment context:
      1. Environment level (applies to all namespaces in the environment)
      2. Namespace level (applies to all applications in the namespace)
      3. Application level (applies to a specific application)
   2. Runtime context:
      1. Environment level (applies to all namespaces in the environment)
      2. Namespace level (applies to all applications in the namespace)
      3. Application level (applies to a specific application)
   3. Pipeline context:
      1. Environment level (applies to all namespaces in the environment)
      2. Namespace level (applies to a specific namespace)
3. Update override
   1. Add a new parameter to:
      1. Deployment context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to all applications in the namespace)
         3. Application level (applies to a specific application)
      2. Runtime context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to all applications in the namespace)
         3. Application level (applies to a specific application)
      3. Pipeline context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to a specific namespace)
   2. Override a parameter value for:
      1. Deployment context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to all applications in the namespace)
         3. Application level (applies to a specific application)
      2. Runtime context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to all applications in the namespace)
         3. Application level (applies to a specific application)
      3. Pipeline context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to a specific namespace)
   3. Remove a parameter value for:
      1. Deployment context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to all applications in the namespace)
         3. Application level (applies to a specific application)
      2. Runtime context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to all applications in the namespace)
         3. Application level (applies to a specific application)
      3. Pipeline context:
         1. Environment level (applies to all namespaces in the environment)
         2. Namespace level (applies to a specific namespace)
4. Delete override for
   1. Deployment context:
      1. Environment level (applies to all namespaces in the environment)
      2. Namespace level (applies to all applications in the namespace)
      3. Application level (applies to a specific application)
   2. Runtime context:
      1. Environment level (applies to all namespaces in the environment)
      2. Namespace level (applies to all applications in the namespace)
      3. Application level (applies to a specific application)
   3. Pipeline context:
      1. Environment level (applies to all namespaces in the environment)
      2. Namespace level (applies to a specific namespace)
5. View Effective Set
   1. Deployment context on Application level
   2. Runtime context on Application level
   3. Pipeline context on Environment level
6. View "to-be" Effective Set
   1. Deployment context on Application level
   2. Runtime context on Application level
   3. Pipeline context on Environment level
7. View Effective Set generation date

## Open questions

1. Do any UI override parameters need to be treated as sensitive (encrypted on save to the repository)?
   1. No
2. Is UI override needed for parameters coming from DD?
   1. No
3. How should the inventory be cleaned up from UI overrides when the environment is handed over?
   1. Through delete and recreate
4. What is the process for saving parameter changes obtained during Dev/QA test and CI environment
   stabilization back into the environment template?
   1. OoS for this analysis
5. UI paramsets introduce structural constraints on paramset content. EnvGene is not aware of these
   constraints. This may cause UI and EnvGene views of the parameters to diverge.
   1. **Decision: support these constraints in EnvGene as well (?)**
   2. Alternative: additional association points in the inventory (?)
6. Is typing through naming a bad practice?
   1. Typing through an explicit type allows describing validation rules via a JSON schema
7. ParamSet or ParamSet + ParamSetAssociation?
   1. ParamSet plus environment attributes
8. How to obtain the SHA of the commit that changed a specific file
9. Describe the error on key overlap between Effective Set files
10. Describe the Colly merge principle accounting for:
    1. The fact that keys must not overlap
    2. Exceptions such as `global`
11. What to do when the repository is encrypted and a `sops` section is present
12. Effective Set generation date

## Proposed approach

The solution provides a UI for quickly changing environment parameters without having to work with
Git directly. UI overrides are stored in the instance repository and applied with the highest
priority in the paramset chain.

Three implementation options are proposed, differing in where overrides are stored and when they are
applied. All options preserve the ability to transfer the changes into the environment template
afterwards for reproducibility.

### EnvGene

#### Option 1. Env Specific Parameters Override

Overrides are stored exclusively in separate ParamSet files in the instance repository. The ParamSet
files are associated with the environment through the Inventory and applied last in the parameter
chain, providing the highest priority. Applying changes requires running `env_build` and
`generate_effective_set`.

##### Option 1A. Basic variant

**Override levels:**

- **Deployment and Runtime contexts:** overrides are only set at the Application level
- **Pipeline context:** overrides are only set at the Environment level (applied to all NSs of the
  environment)

1. On UI override creation, values are saved in the instance repository as Env Specific ParamSets

   1. ParamSets are created in `/environments/<cluster>/<env>/Inventory/parameters/` separately per
      namespace/context with the following names:
      1. For the `deployment` context: `<deploy-postfix>-deploy-ui-override.yaml`
      2. For the `runtime` context: `<deploy-postfix>-runtime-ui-override.yaml`
      3. For the `pipeline` context: `pipeline-ui-override.yaml`

   2. The ParamSet is associated in the environment inventory:
      1. The ParamSet is appended to the end of the ParamSet list at the current association points

            ```yaml
            envTemplate:
               envSpecificParamsets:
                  <deploy-postfix>:
                     - ...
                     - <deploy-postfix>-deploy-ui-override
               envSpecificTechnicalParamsets:
                  <deploy-postfix>:
                     - ...
                     - <deploy-postfix>-runtime-ui-override
               envSpecificE2EParamsets:
                  # For all NSs of the Env
                  <deploy-postfix>:
                     - ...
                     - pipeline-ui-override
            ```

      2. During `env_build`, the following validation runs:
         - UI override ParamSets (filenames matching the pattern `*-ui-override.yaml`) must be at the
           end of the list. Otherwise the build fails.
      3. During `env_inventory_generation` (or in the first job?), a validation runs that fails if
         UI-override ParamSets are being created or modified.

   3. The ParamSets have the following structure:
      1. For the `deployment` and `runtime` contexts:

            ```yaml
            name: string
            parameters: <>
            applications:
               - appName: string
                 parameters: map
            ```

      2. For the `pipeline` context: `pipeline-ui-override.yaml`

            ```yaml
            name: string
            parameters: map
            applications: []
            ```

   4. In the BGD case, `<deploy-postfix>-peer|origin` is used instead of `<deploy-postfix>`. The BG
      Domain object is used to process this construct.

2. The saved UI override is displayed from the ParamSet.

3. A UI override may contain macro references to credentials created in Git.

4. Creating credentials is not supported. Working with an encrypted repository is not supported.
   1. **Assumption:** in repositories/sites where Override UI is used, repository encryption is not
      required.

##### Option 1B. Extended variant

**Override levels:**

- **Deployment and Runtime contexts:** overrides may be set at the Environment, Namespace, or
  Application level
- **Pipeline context:** overrides may be set at the Environment or Namespace level

1. On UI override creation, values are saved in the instance repository as Env Specific ParamSets

   1. ParamSets are created in `/environments/<cluster>/<env>/Inventory/parameters/` separately per
      level/context with the following names:
      1. For the deployment and runtime contexts:
         1. At Environment level: `deploy-ui-override.yaml` / `runtime-ui-override.yaml` (via the
            `parameters` section)
         2. At Namespace level: `<deploy-postfix>-deploy-ui-override.yaml` /
            `<deploy-postfix>-runtime-ui-override.yaml` (via the `parameters` section)
         3. At Application level: `<deploy-postfix>-<application-name>-deploy-ui-override.yaml` /
            `<deploy-postfix>-<application-name>-runtime-ui-override.yaml` (via the `applications`
            section)
      2. For the pipeline context:
         1. At Environment level: `pipeline-ui-override.yaml`
         2. At Namespace level: `<deploy-postfix>-pipeline-ui-override.yaml`

   2. The ParamSet is associated in the environment inventory:
      1. The ParamSet is appended to the end of the ParamSet list at the corresponding association
         points:

            ```yaml
            envTemplate:
               envSpecificParamsets:
                  cloud:
                     - ...
                     - deploy-ui-override
                  <deploy-postfix>:
                     - ...
                     - <deploy-postfix>-deploy-ui-override
                     - <deploy-postfix>-<application-name>-deploy-ui-override
               envSpecificTechnicalParamsets:
                  cloud:
                     - ...
                     - runtime-ui-override
                  <deploy-postfix>:
                     - ...
                     - <deploy-postfix>-runtime-ui-override
                     - <deploy-postfix>-<application-name>-runtime-ui-override
               envSpecificE2EParamsets:
                  cloud:
                     - ...
                     - pipeline-ui-override
                  <deploy-postfix>:
                     - ...
                     - <deploy-postfix>-pipeline-ui-override
            ```

      2. During `env_build`, the following validation runs:
         - UI override ParamSets (filenames matching the pattern `*-ui-override.yaml`) must be at the
           end of the list. Otherwise the build fails.
      3. During `env_inventory_generation` (or in the first job?), a validation runs that fails if
         UI-override ParamSets are being created or modified.

   3. The ParamSets have the following structure depending on the level:
      1. Deployment and Runtime contexts:

         1. At Environment level:

               ```yaml
               name: deploy-ui-override  # or runtime-ui-override
               parameters: map  # Environment-level parameters
               applications: []  # Empty, because the parameters are at the Environment level
               ```

         2. At Namespace level:

               ```yaml
               name: <deploy-postfix>-deploy-ui-override  # or <deploy-postfix>-runtime-ui-override
               parameters: map  # Namespace-level parameters
               applications: []  # Empty, because the parameters are at the Namespace level
               ```

         3. At Application level:

               ```yaml
               name: <deploy-postfix>-<application-name>-deploy-ui-override  # or <deploy-postfix>-<application-name>-runtime-ui-override
               parameters: {}  # Empty, because the parameters are at the Application level
               applications:
                  - appName: string
                    parameters: map  # Application-level parameters
               ```

      2. Pipeline context:

         1. At Environment level:

               ```yaml
               name: pipeline-ui-override
               parameters: map  # Environment-level parameters
               applications: []  # Empty
               ```

         2. At Namespace level:

               ```yaml
               name: <deploy-postfix>-pipeline-ui-override
               parameters: map  # Namespace-level parameters
               applications: []  # Empty
               ```

   4. In the BGD case, at Namespace and Application levels, `<deploy-postfix>-peer|origin` is used
      instead of `<deploy-postfix>`. The BG Domain object is used to process this construct. BGD does
      not apply at Environment level (Environment-level parameters are common to the entire
      environment).

2. The saved UI override is displayed from the ParamSet.

3. A UI override may contain macro references to credentials created in Git.

4. Creating credentials is not supported. Working with an encrypted repository is not supported.
   1. **Assumption:** in repositories/sites where Override UI is used, repository encryption is not
      required.

#### Option 2. Env Instance Override

Extends Option 1A or Option 1B with an additional merge of overrides directly into the Application
and Namespace objects of the instance repository. Overrides are stored in two places: ParamSet files
(as in Option 1A/1B) and Application/Namespace objects. Applying changes requires only
`generate_effective_set`, which speeds up the process compared to Option 1A/1B.

1. Everything from **Option 1A** or **Option 1B** applies.
2. On UI override creation, values are merged via Shallow Merge into the Application of the
   environment:
   1. For `deploy`, into the `deployParameters` attribute of the Application object located at
      `environments/<cluster>/<env>/Namespaces/<ns>/Applications/<app>.yml`
   2. For `runtime`, into the `technicalConfigurationParameters` attribute of the Application object
      located at `environments/<cluster>/<env>/Namespaces/<ns>/Applications/<app>.yml`
   3. For `pipeline`, into the `e2eParameters` attribute in every Namespace object of the
      environment, located at `environments/<cluster>/<env>/namespace.yml`

#### Option 3. Effective Set Override

Extends Option 2 with an additional merge of overrides directly into Effective Set files. Overrides
are stored in three places: ParamSet files, Application/Namespace objects, and Effective Set files.
Changes can be applied immediately.

1. Everything from **Option 2. Env Instance Override** applies.
2. On UI override creation, values are merged via Shallow Merge into the ES files:
   1. For `deploy`, into `effective-set/deployment/<ns>/<app>/values/deployment-parameters.yaml`
   2. For `runtime`, into `effective-set/runtime/<ns>/<app>/parameters.yaml`
   3. For `pipeline`, into `effective-set/pipeline/parameters.yaml`

#### Option 4. UI Override Files (Simplified Approach)

Effective Set overrides are stored in a dedicated `ui-overrides/` directory in three files (one per
context). The UI override files are created and managed exclusively by Colly through the API. The
Calculator applies the UI override directly during ES generation with a priority lower than Custom
Params. A `ui-override-original-values.yaml` file is generated to track the original parameter values
before the UI override is applied.

**Storage layout:**

```text
environments/
  <cluster>/
    <environment>/
      ui-overrides/
        deployment.yaml       # Deployment context
        runtime.yaml          # Runtime context
        pipeline.yaml         # Pipeline context
```

**Override levels:**

- **Environment Level** - parameters apply to all namespaces and applications in the environment
- **Namespace Level** - parameters apply to all applications in the namespace
- **Application Level** - parameters apply to a specific application

**Example: `ui-overrides/deployment.yaml`**

```yaml
# Environment level (applies to all namespaces and applications)
environment:
  param_env1: value1
  param_env2: value2
# Namespace level (applies to all applications in the namespace)
namespaces:
  namespace-01:
    param_ns1: value1
    param_ns2: value2
  namespace-02:
    param_ns3: value3
# Application level (applies to a specific application)
applications:
  namespace-01:
    app-01:
      param1: value2
      param4: null        # Parameter deletion
    app-02:
      param5: value5
  namespace-02:
    app-03:
      param6: value6
```

**Calculator changes:**

1. The Calculator reads the UI Override files located at the contract paths:
   - `deployment.yaml` for the deployment context
   - `runtime.yaml` for the runtime context
   - `pipeline.yaml` for the pipeline context

2. The Calculator merges them into the Effective Set on every generation with a priority lower than
   Custom Params.

3. The Calculator produces a `ui-override-original-values.yaml` file containing the original
   parameter values before the UI override is applied:

   ```text
   effective-set/
     ui-override-original-values.yaml    # originalValue for all contexts
   ```

   **File format:**

   ```yaml
   # ui-override-original-values.yaml
   deployment:
     namespace-01:
       app-01:
         param1: value1       # Value before the UI override
         param3: null         # New parameter (not present in the ES)
         param4: value4       # Parameter removed by the UI override
       app-02:
         param5: null
   runtime:
     namespace-01:
       app-01:
         runtime_param1: old_value1
         runtime_param2: old_value2
   pipeline:
     pipeline_param1: old_value
   ```

**Colly changes:**

Colly exposes a REST API for working with UI override parameters and the Effective Set.

1. **UI Parameters API** - manages UI override files:
   - `GET /api/v1/environments/{environmentId}/ui-parameters` - retrieves the UI override parameters
   - `POST /api/v1/environments/{environmentId}/ui-parameters` - creates/updates the UI override
     parameters (commit to Git)
   - Details: `colly-ui-parameters-api.md`

2. **Effective Set API** - retrieves the ES with metadata:
   - Colly computes three attributes for each parameter:
     - `originalValue` - the value before the UI Override is applied
     - `state` - the parameter state (uncommitted/committed/untouched)
     - `value` - the target parameter value
   - Details: `colly-effective-set-api.md`

3. **Versioning and conflicts:**
   - Use of Git commit hash and HTTP ETag
   - Optimistic locking (412 Precondition Failed)
   - Conflict handling on Git push (409 Conflict)
   - Details: `colly-versioning-conflicts.md`

**Properties of Option 4:**

- Does not use the ParamSet mechanism
- Does not require ParamSet associations in `env_definition.yml`
- Simpler storage layout (3 files instead of many ParamSets)
- The UI sends all parameters (including already-committed ones) in `request.parameters`
- Colly exposes `state`, `value`, and `originalValue` for the user

#### Comparison of options

| Criterion                                  | Option 1. Env Specific Parameters Override                 | Option 2. Env Instance Override                                                       | Option 3. Effective Set Override                                                                 | Option 4. UI Override Files                         |
|:-------------------------------------------|:-----------------------------------------------------------|:--------------------------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------|:----------------------------------------------------|
| **Time to apply changes**                  | Slowest - requires `env_build` + `generate_effective_set`  | Medium - requires `generate_effective_set`                                            | Instant - changes apply immediately                                                              | Medium - requires `generate_effective_set`          |
| **Implementation complexity**              | Medium                                                     | High                                                                                  | High                                                                                             | Low                                                 |
| **Drift risk**                             | Low - overrides stored in one place (ParamSet)             | Medium - overrides stored in two places (ParamSet + Application/Namespace)            | High - overrides stored in three places (ParamSet + Application/Namespace + ES)                  | Low - overrides stored in one place (ui-overrides/) |
| **Uses ParamSet**                          | Yes                                                        | Yes                                                                                   | Yes                                                                                              | No                                                  |
| **Inventory changes**                      | Yes - ParamSet association                                 | Yes - ParamSet association + merge into Application/Namespace                         | Yes - ParamSet association + merge into Application/Namespace + merge into ES                    | No                                                  |
| **EnvGene changes**                        | Calculator + validation in env_build                       | Calculator + validation in env_build + merge into objects                             | Calculator + validation in env_build + merge into objects + merge into ES                        | Calculator only                                     |
| **Tracking of original values**            | No                                                         | No                                                                                    | No                                                                                               | Yes - via ui-override-original-values.yaml          |
| **Support for uncommitted UI changes**     | No                                                         | No                                                                                    | No                                                                                               | Yes - via request.parameters                        |

## API documentation

For a detailed API description for working with UI override (Option 4), see the following documents:

1. **`colly-ui-parameters-api.md`** - UI Parameters API
   - GET/POST endpoints for managing UI override files
   - Request and response structures
   - Usage examples
   - Per-level processing logic (Environment/Namespace/Application)

2. **`colly-effective-set-api.md`** - Effective Set API
   - POST endpoint for retrieving the Effective Set with metadata
   - Object model (originalValue, state, value)
   - Algorithms for computing parameter states
   - Response examples

3. **`colly-applications-api.md`** - Applications API
   - GET endpoint for retrieving the list of applications in a namespace
   - Used by the UI to populate the application drop-down list
