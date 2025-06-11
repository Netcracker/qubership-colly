[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Netcracker_qubership-colly&metric=coverage)](https://sonarcloud.io/summary/new_code?id=Netcracker_qubership-colly)

# qubership-colly

The tool is designed to track the usage of environments within clusters. This project uses Quarkus, the Supersonic Subatomic Java Framework and React.

## Features

- The tool is designed to track the usage of environments within clusters.
- Support several clusters
- Support Cloud Passport concept
- ability to group several namespaces into one environment
- show additional custom UI parameters for the environment (owner, description, status)
- collect metrics from monitoring related to environments
- (todo) ability to show information (name, version) about deployed helm packages
    - (optional) support argo packages


## Environment visualization details
![img.png](img.png)
Columns:
- **Environment** - Name of the environment. Information is taken from the cloud passport configuration.
- **Environment Type** - Type of the environment. Type is calculated based on namespace labels. 
- **Namespace(s)** - Name of the namespace(s). Information is taken from the cloud passport configuration.
- **Cluster** - Name of the cluster. Information is taken from the cloud passport configuration.
- **Owner** - Owner of the environment. Owner can be specified in Colly and persisted in the database. 
- **Status** - Status of the environment. Status can be specified in Colly and persisted in the database.
- **Labels** - Labels of the environment. Labels can be specified in Colly and persisted in the database.
- **Description** - Description of the environment. Information is taken from the cloud passport configuration and also can be specified in Colly.



## Run latest version in Docker
```shell script
docker run -d --rm --name colly-db -p 5432:5432 -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:17
docker run -v ~/.kube:/kubeconfigs -e ENV_INSTANCES_REPO=https://github.com/ormig/cloud-passport-samples.git -i --rm -p 8080:8080 ghcr.io/netcracker/qubership-colly:latest
```

## Run using helm 
1. Install Keycloak
   ```shell script
   helm repo add bitnami https://charts.bitnami.com/bitnami
   helm repo update
   helm install keycloak bitnami/keycloak --namespace keycloak --create-namespace
   ```
2. Install Colly
```shell script

helm install qubership-colly netcracker/qubership-colly --set colly.db.password=<DB_PASSWORD> --set colly.db.user=<DB_USERNAMER> --set colly.db.host=<DB_HOST> --set colly.keycloak.url=http://<KEYCLOAK_HOST>:<KEYCLOAK_PORT>/realms/colly-realm 
```
## Clusters configuration
There are two ways to specify clusters:
1. (!!! deprecated for now) Specify folder with kubeconfig files in `/kubeconfigs` and run the application. The application will read all kubeconfig files and connect to clusters. Example:
   ```shell
   docker run -v ~/.kube:/kubeconfigs -i --rm -p 8080:8080 ghcr.io/netcracker/qubership-colly:latest
   ```
   The application will read all kubeconfig files in `~/.kube` folder and connect to clusters.
2. Specify `ENV_INSTANCES_REPO` environment variable with URL to git repository with Cloud Passports files. The application will clone the repository and read all cloud passports for each cluster. In this option application read all cloud passports, environments and namespaces from the repository. Based on this information, it will create clusters, environments and namespaces in the database. Then using information from the cloud passport application will connect to each cluster and read all namespaces, deployments, pods, configmaps. Example:
    ```shell
   docker run -e ENV_INSTANCES_REPO=https://github.com/ormig/cloud-passport-samples.git -i --rm -p 8080:8080 ghcr.io/netcracker/qubership-colly:latest
    ```
   multiple repositories can be specified using comma-separated values. For example:
    ```shell
   docker run -e ENV_INSTANCES_REPO=https://github.com/repo1.git,https://github.com/repo2.git -i --rm -p 8080:8080 ghcr.io/netcracker/qubership-colly:latest
    ```
   The application will clone the repository `https://github.com/ormig/cloud-passport-samples.git` and read all cloud passports for each cluster. If authentication is required to clone a repository, you can specify it in URL:
    ```shell
      docker run -e ENV_INSTANCES_REPO=https://myusername:mypassword@github.com/ormig/cloud-passport-samples.git -i --rm -p 8080:8080 ghcr.io/netcracker/qubership-colly:latest
    ```
    
## Environment Resolver Strategy
You can configure how to resolve an environment by namespace using two strategies:
1. **ByName** - Uses the first part of the namespace name as the environment name if suffix is in the scope of values: oss, bss, data-management, core. For example, `dev-bss` becomes `dev`.
2. **ByLabel** - Uses the `environmentName` label from the namespace. For example, if the namespace is `dev-namespace` and the label is `environmentName=dev`, the environment name will be `dev`.
Example:
    ```shell
    docker run -e ENVIRONMENT_RESOLVER_STRATEGY=byName -i --rm -p 8080:8080 ghcr.io/netcracker/qubership-colly:latest
    ```
