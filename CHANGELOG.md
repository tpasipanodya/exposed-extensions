# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

# 0.14.0

* Make the thread local `CurrentTenantId` accessible to consumers of this library.
* Bump jvm from 1.9.21 to 2.0.0 by @dependabot in https://github.com/tpasipanodya/exposed-extensions/pull/170
* Bump com.fasterxml.jackson.datatype:jackson-datatype-jsr310 from 2.16.0 to 2.17.1 by @dependabot in https://github.com/tpasipanodya/exposed-extensions/pull/169
* Bump org.junit:junit-bom from 5.10.1 to 5.10.2 by @dependabot in https://github.com/tpasipanodya/exposed-extensions/pull/166
* Bump org.jetbrains.dokka from 1.9.10 to 1.9.20 by @dependabot in https://github.com/tpasipanodya/exposed-extensions/pull/168
* Bump org.postgresql:postgresql from 42.7.0 to 42.7.3 by @dependabot in https://github.com/tpasipanodya/exposed-extensions/pull/171
* Bump org.slf4j:slf4j-simple from 2.0.9 to 2.0.13 by @dependabot in https://github.com/tpasipanodya/exposed-extensions/pull/167
* Bump com.fasterxml.jackson.module:jackson-module-kotlin from 2.16.0 to 2.17.1 by @dependabot in https://github.com/tpasipanodya/exposed-extensions/pull/172

# 0.13.0

- `exposed` version `0.10.0`

# 0.12.2

- `jdk` version `20`
- `jvm` version `1.9.0`
- Moved build artifacts to GitHub Packages

# 0.12.1

- `jdk` version `18`
- `jvm` version `1.7.22`
- `kotlin-logging-jvm` version `3.0.4`
- `jackson-datatype-jsr310` version `2.14.1`
- ` jackson-module-kotlin` version `2.14.1`
- `postgresql` version `42.5.1`
- `slf4j-simple` version `2.0.6`

# 0.11.0
- `exposed` version `0.7.0`
- `jackson-module-kotlin` version `2.13.3`
- `kotlin-logging-jvm` version `2.1.23`
- `jackson-datatype-jsr310` version `2.13.3`

# 0.10.0
- `postgresql` version `42.3.3`
- `jackson-module-kotlin` version `2.13.2`
- `jackson-datatype-jsr310` version `2.13.2`
- `slf4j-simple` version `1.7.36`

# 0.9.0
- `io.taff.exposed` version `0.5.0`.
- Split out the wiki page from `WIKI.md`.

## 0.8.2
- Cleanup CI & CD

## 0.8.1
- `org.jetbrains.dokka` version `1.6.10`
- `com.jfrog.artifactory` version `4.25.4`
- Added a build & release step that uploads test results.
- Added a CI badge to `README.md`

## 0.8.0
- `io.taff.exposed` version `0.3.0`.
- Renamed `Model` to `Record`

## 0.7.0
- `jackson-module-kotlin` version `2.13.1`.
- `jackson-datatype-jsr310` version `2.13.1`.
- `com.jfrog.artifactory` version `4.25.3`.
- `kotlin-logging-jvm` version `2.1.21`.

## 0.5.0

- Kotlin 1..6.10.
- spek-expekt 0.5.0.

## 0.5.0

- Resolving an issue witht he release build.

## 0.4.0
- Switch to `spek-expekt` from `hephaestus-test`.
- Add Soft Delete and Multi tenancy support.
- Bump Kotlin to 1.6.0.
- Setup CI & CD.
- Update README.md.

## 0.1.0

- Initiated README.
- Added soft delete support via `SoftDeletableUuidTable` and `SoftDeletableLongIdTable`.
