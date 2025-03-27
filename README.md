# qubership-colly

This project uses Quarkus, the Supersonic Subatomic Java Framework.

## Goal

- The tool is designed for tracking the usage of clusters and environments within clusters.
- Support several clusters
- ability to group several namespaces into one environment
- (todo) ability to show information (name, version) about deployed helm packages
  - (optional) support argo packages
- (todo) collect resources and metrics from kubernetes and monitoring
- (todo) show additional custom UI parameters for environment (owner, description, purpose)

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_** Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/code-with-quarkus-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Run in Docker

```shell script
docker run -d --rm --name colly-db -p 5432:5432 -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:17
docker build -f src/main/docker/Dockerfile.jvm -t qubership/qubership-colly .
docker run -v ~/.kube:/kubeconfigs -i --rm -p 8080:8080 qubership/qubership-colly
docker run -v ./src/test/resources/kubeconfigs:/kubeconfigs -e ENV_INSTANCES_REPO=https://github.com/ormig/cloud-passport-samples.git -i --rm -p 8080:8080 qubership/qubership-colly
```
