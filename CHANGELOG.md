# Changelog

All notable changes to this project will be documented in this file.

## [[1.2.3]](https://github.com/iExecBlockchainComputing/iexec-commons-containers/releases/tag/v1.2.3) 2024-12-19

### Dependency Upgrades

- Upgrade to Gradle 8.10.2. (#43)
- Upgrade to `docker-java` 3.4.1. (#44)

## [[1.2.2]](https://github.com/iExecBlockchainComputing/iexec-commons-containers/releases/tag/v1.2.2) 2024-06-17

### Quality

- Configure Gradle JVM Test Suite Plugin. (#38)

### Dependency Upgrades

- Upgrade to Gradle 8.7. (#39)
- Upgrade to Spring Boot 2.7.18. (#40)

## [[1.2.1]](https://github.com/iExecBlockchainComputing/iexec-commons-containers/releases/tag/v1.2.1) 2023-12-19

### Dependency Upgrades

- Upgrade to Spring Boot 2.7.17. (#35)
- Upgrade to `jenkins-library` 2.7.4. (#33)
- Upgrade to `docker-java` 3.3.4. (#34)

## [[1.2.0]](https://github.com/iExecBlockchainComputing/iexec-commons-containers/releases/tag/v1.2.0) 2023-11-06

### New Features

- Add `HostConfig` member in `DockerRunRequest`, add related deprecations. (#28)
- Add `executionDuration` member in `DockerRunResponse`. (#29)
- Do less Docker API calls when stopping or removing containers or removing images. (#30)

## [[1.1.2]](https://github.com/iExecBlockchainComputing/iexec-commons-containers/releases/tag/v1.1.2) 2023-09-27

### Bug Fixes

- Missed version update in `gradle.properties` in hotfix 1.1.1.

## [[1.1.1]](https://github.com/iExecBlockchainComputing/iexec-commons-containers/releases/tag/v1.1.1) 2023-09-27

### Bug Fixes

- Revert PR #23, the official **SGX devices** coming with the **in-kernel SGX driver** since kernel 5.11
  are not yet supported in SGX enclaves based on the Gramine framework currently in use. (#25)

## [[1.1.0]](https://github.com/iExecBlockchainComputing/iexec-commons-containers/releases/tag/v1.1.0) 2023-09-26

### New Features

- Move `ArgsUtils`, `SgxDriverMode` and `SgxUtils` classes from `iexec-common`. (#14)

### Bug Fixes

- Add missing `lombok.config` file. (#15)
- Use official `/dev/sgx_enclave` and `/dev/sgx_provision` devices. (#23)

### Quality

- Replace deprecated `ExecStartResultCallback` with `ResultCallback.Adapter` in `DockerClientInstance`. (#17)
- Upgrade to Gradle 8.2.1 with up-to-date plugins. (#19)
- Properly handle `InterruptedException` instances in `DockerClientInstance`. (#20)
- Several quality fixes (assertions, code smells, TODOs). (#21)

### Dependency Upgrades

- Add `maven-shared-utils:3.4.2` dependency. (#14)
- Remove `iexec-common` dependency. (#16)
- Upgrade to Spring Boot 2.7.14. (#18)
- Upgrade to `jenkins-library` 2.7.3. (#22)

## [[1.0.3]](https://github.com/iExecBlockchainComputing/iexec-commons-containers/releases/tag/v1.0.3) 2023-06-23

### Dependency Upgrades

- Upgrade to `iexec-common` 8.2.1. (#12)

## [[1.0.2]](https://github.com/iExecBlockchainComputing/iexec-commons-containers/releases/tag/v1.0.2) 2023-04-13

### Bug Fixes

- Remove unused `@Tag` annotations in tests. (#8)
- Remove `WaitUtils` usage. (#9)

### Dependency Upgrades

- Upgrade to `iexec-common` 8.0.0. (#9)

## [[1.0.1]](https://github.com/iExecBlockchainComputing/iexec-commons-containers/releases/tag/v1.0.1) 2023-03-16

- Add `settings.gradle` file to set correct project name for SonarCloud analyses. (#5)

## [[1.0.0]](https://github.com/iExecBlockchainComputing/iexec-commons-containers/releases/tag/v1.0.0) 2023-03-16

- Migrate docker package of `iexec-common` library to this `iexec-commons-containers` library. (#1 #2 #3)
