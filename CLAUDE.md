# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A [Testcontainers](https://testcontainers.com) community module for [Ory Hydra](https://www.ory.sh/hydra), an open-source OAuth 2.0 and OpenID Connect server. This library makes it easy to spin up a real Hydra instance in Java integration tests. The single public class `OryHydraComposeContainer` extends Testcontainers' `ComposeContainer` with a fluent builder API, sensible defaults, and convenience methods for Hydra's admin/public endpoints.

## Build Commands

```bash
./gradlew clean build          # Full build (compile + test)
./gradlew test                 # Run all tests
./gradlew test --tests 'com.ardetrick.testcontainers.OryHydraComposeContainerTest.containerStartsWithDockerComposeFile'  # Single test
```

Requires JDK 21. Docker must be running (tests start real containers).

## Architecture

This is a single-class library:

- **`OryHydraComposeContainer`** (`src/main/java/com/ardetrick/testcontainers/`) — Extends `ComposeContainer`. Uses a `Builder` inner class to configure docker-compose files, Hydra environment variables (`URLS_LOGIN`, `URLS_CONSENT`, `URLS_SELF_ISSUER`), and wait strategy. Exposes helper methods for admin/public base URIs and common endpoints (`/oauth2/auth`, `/.well-known/jwks.json`).

- **Test resources** (`src/test/resources/`) — Multiple docker-compose files testing different configurations: with/without config volume binds, and multi-service stacks. The `volume/hydra.yml` file provides Ory Hydra configuration.

## Code Style

Lombok is available via the `io.freefair.lombok` Gradle plugin. Use Lombok annotations to reduce boilerplate where appropriate.

## Commit Conventions

Follow [Conventional Commits](https://www.conventionalcommits.org). Format: `<type>(<optional scope>): <description>`. Common types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`.

## Publishing

Uses JReleaser (`jreleaser.yml`) to publish to Maven Central via Sonatype. Staging artifacts go to `build/staging-deploy`.

## CI

GitHub Actions runs `./gradlew build --no-daemon` on push/PR to main. Dependabot and a Gradle wrapper update workflow handle dependency freshness.
