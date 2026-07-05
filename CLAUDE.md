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

Requires JDK 21 (google-java-format via Spotless needs 21+; the library targets JDK 17). Docker must be running (tests start real containers).

## Architecture

Two packages:

- **`OryHydraContainer`** (`com.ardetrick.testcontainers`) — Extends `GenericContainer`. Runs database migration and the Hydra server in a single container using a compound command (`migrate && serve`). Defaults to SQLite so no external database is required. Uses a `Builder` inner class to configure the Docker image, Hydra environment variables (`URLS_LOGIN`, `URLS_CONSENT`, `URLS_SELF_ISSUER`, `URLS_LOGOUT`, `SECRETS_SYSTEM`, `DSN`, plus arbitrary env vars), and wait strategy. `URLS_LOGIN`/`URLS_CONSENT` default to non-resolvable sentinel hosts (never contacted; the flow driver intercepts challenges by query parameter). Exposes convenience URI methods and the flow-helper factories `clientCredentialsFlow()` / `authorizationCodeFlow()`.

- **`com.ardetrick.testcontainers.oauth2`** — Framework-agnostic OAuth 2.0 flow drivers (`ClientCredentialsFlow`, `AuthorizationCodeFlow`), bound only to a pair of base URIs. `execute()` returns a `FlowResult`: `TokenResponse` (RFC 6749 §5.1) or `OAuthError` (§5.2) — protocol errors are values, transport/flow failures throw `HydraFlowException`. The authorization-code driver follows redirects manually and answers Hydra's login/consent challenges via the admin API, rewriting each Hydra-bound redirect to the mapped host/port. Everything else in the package (`Json`, `JsonWriter`, `Http`, `AdminClient`, `TokenEndpointClient`) is a package-private implementation detail — the hand-rolled JSON code exists only to keep the library dependency-free and must not become public API.

Two ArchUnit rules in `ArchitectureTest` enforce the boundaries: the `oauth2` package must not depend on Testcontainers, Docker, or the container class (dependency is one-way: container → flows), and only the flow API types may be public in `oauth2`.

The library must stay framework-agnostic: no Spring or other framework dependencies in this artifact. Testing a user's real login/consent app (custom `URLS_LOGIN`/`URLS_CONSENT`, externally driven flow) is a core supported use case, guarded by `OryHydraContainerExternalLoginConsentTest` — see [ory-hydra-refrence-java](https://github.com/ardetrick/ory-hydra-refrence-java).

## API Scope and Design Principles

This is a test harness for real Hydra, not a Hydra client SDK. Check every proposed public addition against these principles:

- **Test verbs in, admin verbs out.** A public API must answer yes to: "does a test author need this to assert something about their system under test?" In scope: minting real tokens (flows, refresh), validating tokens (introspection), resolving endpoints (discovery), and just enough client registration for a flow to run. Out of scope: operating Hydra — general client CRUD, consent-session management, JWK management. For those, users get pointed at [Ory's official Java client](https://github.com/ory/client-java) (see README), mirroring how the Keycloak Testcontainers module delegates to the official admin client rather than reimplementing one.
- **Spec-anchored public surface, version-coupled internals quarantined.** Public types mirror the RFCs (RFC 6749 token/error responses, RFC 7662 introspection, OIDC discovery), which are stable across Hydra versions. Hydra-version-specific knowledge — admin paths, the login/consent challenge API — must stay in package-private internals so a Hydra upgrade changes this library's internals, never a user's code. Never expose hardcoded server paths publicly: the 0.0.5 URI helpers made that mistake and are deprecated for removal; endpoints are resolved from the discovery document (`openIdConfiguration()`) instead.
- **Real responses only.** Real denials via Hydra's reject endpoints are in scope. Fabricated, forged, malformed, or expired-on-demand tokens and hand-authored error bodies are a mock server's job (e.g. navikt/mock-oauth2-server) — this library never synthesizes what real Hydra would not produce.
- **Released API gets a deprecation cycle.** Breaking a published signature requires `@Deprecated(forRemoval = true)` with a migration pointer for at least one release before removal — never a silent break. Upgrade safety is enforced by the pinned default image, the reference-app compatibility workflows, and integration tests that pin wire behavior.

## Code Style

Code is formatted with [google-java-format](https://github.com/google/google-java-format) via the [Spotless](https://github.com/diffplug/spotless) Gradle plugin. Run `./gradlew spotlessApply` after making code changes to auto-format. The `build` task includes `spotlessCheck`, so CI will reject unformatted code.

Lombok is available via the `io.freefair.lombok` Gradle plugin. Use Lombok annotations to reduce boilerplate where appropriate.

## Commit Conventions

Follow [Conventional Commits](https://www.conventionalcommits.org). Format: `<type>(<optional scope>): <description>`. Common types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`.

## Publishing

Uses JReleaser (`jreleaser.yml`) to publish to Maven Central via Sonatype. Staging artifacts go to `build/staging-deploy`.

## CI

GitHub Actions runs `./gradlew build --no-daemon` on push/PR to main. Dependabot handles dependency freshness (including the Gradle wrapper).
