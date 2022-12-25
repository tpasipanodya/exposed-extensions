# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

# 0.12.0

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
