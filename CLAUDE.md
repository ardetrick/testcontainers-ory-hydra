# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A [Testcontainers](https://testcontainers.com) community module for [Ory Hydra](https://www.ory.sh/hydra), an open-source OAuth 2.0 and OpenID Connect server. This library makes it easy to spin up a real Hydra instance in Java integration tests. The single public class `OryHydraContainer` extends Testcontainers' `GenericContainer` with a fluent builder API, sensible defaults, and convenience methods for Hydra's admin/public endpoints.

## Build Commands

```bash
./gradlew clean build          # Full build (compile + test)
./gradlew test                 # Run all tests
./gradlew test --tests 'com.ardetrick.testcontainers.OryHydraContainerTest.containerStarts'  # Single test
```

Requires JDK 17. Docker must be running (tests start real containers).

## Architecture

This is a single-class library:

- **`OryHydraContainer`** (`src/main/java/com/ardetrick/testcontainers/`) — Extends `GenericContainer`. Runs database migration and the Hydra server in a single container using a compound command (`migrate && serve`). Defaults to SQLite so no external database is required. Uses a `Builder` inner class to configure the Docker image, Hydra environment variables (`URLS_LOGIN`, `URLS_CONSENT`, `URLS_SELF_ISSUER`, `URLS_LOGOUT`, `SECRETS_SYSTEM`, `DSN`, plus arbitrary env vars), and wait strategy. Exposes convenience URI methods for OAuth 2.0 and OIDC endpoints on both the public and admin APIs.

## Code Style

Code is formatted with [google-java-format](https://github.com/google/google-java-format) via the [Spotless](https://github.com/diffplug/spotless) Gradle plugin. Run `./gradlew spotlessApply` after making code changes to auto-format. The `build` task includes `spotlessCheck`, so CI will reject unformatted code.

Lombok is available via the `io.freefair.lombok` Gradle plugin. Use Lombok annotations to reduce boilerplate where appropriate.

## Commit Conventions

Follow [Conventional Commits](https://www.conventionalcommits.org). Format: `<type>(<optional scope>): <description>`. Common types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`.

## Publishing

Uses JReleaser (`jreleaser.yml`) to publish to Maven Central via Sonatype. Staging artifacts go to `build/staging-deploy`.

## CI

GitHub Actions runs `./gradlew build --no-daemon` on push/PR to main. Dependabot and a Gradle wrapper update workflow handle dependency freshness.
