# Changelog

All notable changes to this project will be documented in this file.

## [[NEXT]](https://github.com/iExecBlockchainComputing/iexec-commons-containers/releases/tag/vNEXT) 2023

### New Features
- Move `ArgsUtils`, `SgxDriverMode` and `SgxUtils` classes from `iexec-common`. (#14)
### Bug Fixes
- Add missing `lombok.config` file. (#15)
### Quality
- Replace deprecated `ExecStartResultCallback` with `ResultCallback.Adapter` in `DockerClientInstance`. (#17)
### Dependency Upgrades
- Add `maven-shared-utils:3.4.2` dependency. (#14)
- Remove `iexec-common` dependency. (#16)

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
