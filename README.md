# Ory Hydra Testcontainer

[![Maven Central](https://img.shields.io/maven-central/v/com.ardetrick.testcontainers/testcontainers-ory-hydra)](https://central.sonatype.com/artifact/com.ardetrick.testcontainers/testcontainers-ory-hydra)
[![CI](https://github.com/ardetrick/testcontainers-ory-hydra/actions/workflows/gradle.yml/badge.svg)](https://github.com/ardetrick/testcontainers-ory-hydra/actions/workflows/gradle.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

The `OryHydraContainer` is a [Testcontainers](https://testcontainers.com) module for [Ory Hydra](https://www.ory.sh/hydra), the OAuth 2.0 and OpenID Connect provider. It lets you spin up a real Hydra instance in your Java integration tests.

## Prerequisites

* Docker installed and running.
* Java JDK 17 or later.

## Features

* One-liner token minting — `authorizationCodeFlow()` and `clientCredentialsFlow()` return real tokens from the running Hydra instance (see [Requesting Tokens](#requesting-tokens)).
* Full authorization-code flow without a browser or a login/consent app — the flow driver answers Hydra's login and consent challenges through the admin API, with configurable subject, scopes, audience, session claims, PKCE, and denial modes.
* Zero-config startup — runs database migration and the Hydra server in a single container.
* Defaults to an in-container SQLite database, so no external database is needed.
* Automatic setup of Ory Hydra's admin and public ports.
* `createOAuth2Client` convenience method to register OAuth 2.0 clients using the Hydra CLI inside the container — no extra HTTP dependencies needed.
* Convenient methods to fetch base URIs for both the admin and public endpoints.
* Convenience URI helpers for essential OAuth 2.0 and OpenID Connect endpoints (see [Convenience URI Methods](#convenience-uri-methods)).
* Customizable through a builder pattern, allowing configuration of the Docker image, environment variables, and wait strategy.
* Framework-agnostic — plain JDK HTTP under the hood, no framework (or JSON library) dependencies.

## Scope

This library is a test harness for real Hydra, not a Hydra client SDK. In scope is what a test
needs to assert behavior of the system under test: minting real tokens, validating them via
introspection, and resolving endpoints. Administering Hydra — client management, consent
sessions, JWKs — is out of scope; use [Ory's official Java client](#using-orys-official-java-client)
for that. Also out of scope is fabricating invalid tokens or error responses: real denials
(rejected login/consent) come from real Hydra and are fully supported, but forged, malformed, or
expired-on-demand tokens are a mock server's job (e.g.
[navikt/mock-oauth2-server](https://github.com/navikt/mock-oauth2-server)).

## Usage

### Dependency

First, include the `OryHydraContainer` in your project's `build.gradle`:

```groovy
dependencies {
    testImplementation 'com.ardetrick.testcontainers:testcontainers-ory-hydra:0.0.6'
    // Only needed if you use @Testcontainers/@Container annotations (see Basic Usage below)
    testImplementation 'org.testcontainers:junit-jupiter'
}
```

> **Note:** This library does not transitively include `testcontainers-junit-jupiter`. If you want to use the `@Testcontainers` and `@Container` JUnit 5 annotations, you must add the `org.testcontainers:junit-jupiter` dependency yourself. If you use the [Testcontainers BOM](https://java.testcontainers.org/getting_started/#managing-versions-for-multiple-testcontainers-dependencies), the version will be managed for you. The try-with-resources approach does not require this additional dependency.

### Basic Usage

The recommended approach uses Testcontainers' JUnit 5 annotations, which handle starting and stopping the container automatically. This requires the `org.testcontainers:junit-jupiter` dependency:

```java
import com.ardetrick.testcontainers.OryHydraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class HydraIntegrationTest {

    @Container
    static OryHydraContainer hydra = OryHydraContainer.builder().build();

    @Test
    void testOAuthFlow() {
        URI discoveryUri = hydra.getOpenIdDiscoveryUri();
        // Your test logic here...
    }
}
```

Alternatively, use try-with-resources for per-test lifecycle management:

```java
try (var hydra = OryHydraContainer.builder().build()) {
    hydra.start();
    // test logic
}
```

### Requesting Tokens

Both flow helpers run against the started container and return a `FlowResult`, which is either a
`FlowResult.TokenResponse` (a successful token response, RFC 6749 §5.1) or a `FlowResult.OAuthError`
(an error response, RFC 6749 §5.2). OAuth protocol errors are returned as values, not thrown;
transport-level failures throw `HydraFlowException`.

If no client is supplied via `clientId(...)`/`clientSecret(...)`, an ephemeral client with the
requested scopes is registered automatically — so the shortest path to a real token is one line.

#### Authorization code — no browser, no login/consent app required

Ory Hydra normally delegates login and consent to an app you provide, which makes the
authorization-code flow notoriously hard to integration-test. The flow driver removes that
requirement: it performs the real authorization-code exchange against Hydra, following each
redirect manually and answering the login and consent challenges through Hydra's admin API —
asserting the subject you configure and granting the requested scopes.

```java
var result = hydra.authorizationCodeFlow()
        .scopes("openid", "offline_access")
        .subject("user-123")
        .claims(Map.of("email", "user-123@example.com"))
        .execute();

var token = (FlowResult.TokenResponse) result;
String accessToken  = token.accessToken();
String idToken      = token.idToken();      // present because "openid" was granted
String refreshToken = token.refreshToken(); // present because "offline_access" was granted
```

Options: `audience(...)` requests token audiences; `accessTokenClaims(...)`/`idTokenClaims(...)`
target one token's session claims instead of both; `usePkce(true)` enables PKCE with the S256
method; `publicClient(true)` runs the flow as a secret-less public client
(`token_endpoint_auth_method: none`, PKCE implied) — the configuration mobile and single-page
apps use.

The flow instance can also exchange a refresh token it minted (RFC 6749 §6), reusing the same
client — ephemeral or supplied:

```java
var flow = hydra.authorizationCodeFlow().scopes("openid", "offline_access");
var tokens = (FlowResult.TokenResponse) flow.execute();
var refreshed = (FlowResult.TokenResponse) flow.refresh(tokens.refreshToken());
```

Because the tokens come from a real Hydra instance, denial paths are real too — a rejected consent
produces Hydra's actual error redirect, not a fabricated response:

```java
var error = (FlowResult.OAuthError) hydra.authorizationCodeFlow()
        .rejectConsent("access_denied", "user declined")
        .execute();
// error.error() == "access_denied"
```

To exercise a specific pre-registered client instead of an ephemeral one, pass
`clientId(...)`/`clientSecret(...)`. The flow's redirect URI defaults to
`http://localhost/callback` and can be overridden with `redirectUri(...)` to match the client's
registered `redirect_uris` — it is never actually served either way.

#### Client credentials

For machine-to-machine tokens with no end-user, the client-credentials grant is the quickest path:

```java
var result = hydra.clientCredentialsFlow()
        .scopes("read")
        .execute();

var token = (FlowResult.TokenResponse) result;
String accessToken = token.accessToken();
```

#### Validating tokens with introspection

Hydra issues opaque access tokens by default, so resource servers validate them via
introspection (RFC 7662) — and your test can do the same to assert a minted token is real,
active, and carries what you configured:

```java
var introspection = hydra.introspect(token.accessToken());
introspection.active();          // true
introspection.subject();         // "user-123"
introspection.raw().get("ext");  // custom session claims, e.g. {email=user-123@example.com}
```

Unknown or expired tokens return `active() == false` rather than throwing.

#### Resolving endpoints from the discovery document

`openIdConfiguration()` fetches and parses `/.well-known/openid-configuration`. Because Hydra
advertises endpoints using its configured issuer — which cannot know the container's dynamically
mapped port — the typed accessors are re-targeted at the mapped public port and are directly
usable; the as-advertised values remain available via `raw()`:

```java
URI jwksUri = hydra.openIdConfiguration().jwksUri();
URI tokenEndpoint = hydra.openIdConfiguration().tokenEndpoint();
```

These accessors are the migration target for the URI helpers deprecated since 0.0.6.

#### Testing a real login/consent app

The flow driver above replaces the login/consent app so you don't have to write one. If the thing
you are testing *is* your login/consent app, don't use it — point `urlsLogin(...)`/`urlsConsent(...)`
at your app and drive the flow externally (typically with a browser automation tool), letting your
app answer Hydra's challenges via the admin API as it would in production. See
[ory-hydra-refrence-java](https://github.com/ardetrick/ory-hydra-refrence-java) for a complete
reference implementation of a login/consent app tested with this library.

#### Wiring your application under test

The library is framework-agnostic: point whatever OAuth/OIDC configuration your application uses
at the container — `getOpenIdDiscoveryUri()` for discovery-based setups, or
`publicBaseUriString()`/`getOAuth2TokenUri()` for individual endpoints — and feed it a token minted
by one of the flows above.

A path-bearing issuer — mirroring Hydra behind a production gateway, e.g.
`urlsSelfIssuer("https://api.example.com/auth")` — is fully supported: minted tokens carry that
issuer, while the flow driver and the `openIdConfiguration()` accessors strip the gateway's path
prefix so every URL they produce stays reachable on the mapped port (`raw()` keeps the
as-advertised values). For full fidelity including the gateway itself, run a proxy container on a
shared Docker network (see below) and point the issuer at its alias.

#### When your application under test also runs in Docker

Put Hydra and your application on the same Docker network, give Hydra an alias, and set the
issuer to the alias-based URL so in-network consumers resolve endpoints they can actually reach:

```java
Network network = Network.newNetwork();
OryHydraContainer hydra = OryHydraContainer.builder()
        .urlsSelfIssuer("http://hydra:4444")
        .build()
        .withNetwork(network)
        .withNetworkAliases("hydra");
```

Inside the network, your application reaches Hydra at `http://hydra:4444` (public) and
`http://hydra:4445` (admin) — the internal ports, no mapping involved. From the host, everything
above keeps working unchanged: the flow helpers rewrite Hydra's redirects to the mapped port
regardless of the configured issuer, so you can mint a token on the host and have your dockerized
resource server validate it in-network. This setup is guarded by
`OryHydraContainerDockerNetworkTest`.

### Custom Configuration

```java
var hydra = OryHydraContainer.builder()
        .image(DockerImageName.parse("oryd/hydra:v2.3.0"))
        .urlsLogin("http://example.com/login")
        .urlsConsent("http://example.com/consent")
        .urlsSelfIssuer("http://example.com/")
        .dsn("postgres://user:pass@host:5432/hydra")
        .build();
```

## Configuration Options

Using the `Builder` class, you can configure:

* `image(DockerImageName)`: Override the Docker image (default: `oryd/hydra:v25.4.0`).
* `urlsLogin(String)`: Set the login URL (`URLS_LOGIN`). Defaults to a non-resolvable sentinel host; the authorization-code flow helper intercepts login redirects by their challenge parameter, so the sentinel is never contacted.
* `urlsConsent(String)`: Set the consent URL (`URLS_CONSENT`). Defaults to a non-resolvable sentinel host, as above.
* `urlsSelfIssuer(String)`: Set the self-issuer URL (`URLS_SELF_ISSUER`).
* `urlsLogout(String)`: Set the logout URL (`URLS_LOGOUT`).
* `secretsSystem(String)`: Set the system secret used for encryption (`SECRETS_SYSTEM`).
* `dsn(String)`: Set the database connection string (`DSN`, default: SQLite).
* `env(String, String)`: Set an arbitrary environment variable.
* `env(Map<String, String>)`: Merge a map of environment variables.
* `waitStrategy(WaitStrategy)`: Override the readiness wait strategy (defaults to polling `/health/ready`).

## Creating OAuth2 Clients

The flow helpers in [Requesting Tokens](#requesting-tokens) register an ephemeral client automatically, so most tests never need to create one explicitly.

When your application under test has a specific client configured, declare it on the builder — the
client exists as soon as the container has started. The map is Hydra's own client JSON, passed
through verbatim, and fixtures are upserted (matched by `client_id`), which makes them safe for a
shared container serving many test classes — the pattern that keeps suites fast by starting Hydra
once:

```java
@Container
static OryHydraContainer hydra = OryHydraContainer.builder()
        .client(client -> client
                .clientId("my-app")
                .clientSecret("my-secret")
                .grantTypes("authorization_code", "refresh_token")
                .responseTypes("code")
                .redirectUris("https://app.example/callback")
                .scope("openid", "offline_access"))
        .build();
```

The customizer receives an `OAuth2ClientRegistration`, whose typed methods cover the standard
RFC 7591 metadata fields; anything else — including Hydra-specific fields — can be set with
`put(key, value)`/`putAll(map)`, which also override typed values explicitly. A plain `Map` of
Hydra client JSON works everywhere a registration is accepted.

Clients can also be registered on the running container mid-test — useful when a shared container
serves many test classes and a class brings its own client. Registration is an upsert (matched by
`client_id`), so re-runs converge instead of failing on duplicates:

```java
hydra.createOrReplaceClient(client -> client
        .clientId("my-service")
        .clientSecret("my-secret")
        .grantTypes("client_credentials")
        .scope("read"));
```

The CLI-based `createOAuth2Client(id, secret, redirectUris)` is deprecated in favor of the
methods above and scheduled for removal.

## Convenience URI Methods

Once the container is started, the following methods provide ready-to-use URIs for Hydra's endpoints:

| Method | Path |
| --- | --- |
| `publicBaseUriString()` | Public API base URL (host + mapped port) |
| `adminBaseUriString()` | Admin API base URL (host + mapped port) |
| `getOpenIdDiscoveryUri()` | `/.well-known/openid-configuration` |
| `getOAuth2TokenUri()` | `/oauth2/token` |

## Using Ory's Official Java Client

For more advanced interactions with Hydra — such as token introspection, client management, or consent/login request handling — use [Ory's official generated Java client](https://github.com/ory/client-java). Point it at the base URIs provided by the container:

```java
import sh.ory.ApiClient;
import sh.ory.api.OAuth2Api;

var oryClient = new ApiClient();
oryClient.setBasePath(hydra.adminBaseUriString());
var oAuth2Api = new OAuth2Api(oryClient);

// Example: list all registered OAuth2 clients
var clients = oAuth2Api.listOAuth2Clients(null, null, null, null, null);
```

## Building

**JDK 21** is required to run the build. The [google-java-format](https://github.com/google/google-java-format) formatter used by Spotless requires JDK 21+, even though the library itself targets JDK 17 for wider adoption.

To build from the source using the Gradle Wrapper:

```
$ git clone https://github.com/ardetrick/testcontainers-ory-hydra.git
$ cd testcontainers-ory-hydra
$ ./gradlew clean build
```

For Windows:

```
$ git clone https://github.com/ardetrick/testcontainers-ory-hydra.git
$ cd testcontainers-ory-hydra
$ gradlew.bat clean build
```

## Contributing

We welcome contributions! Please submit pull requests or open issues for feedback.

This project is licensed under the MIT License. See the `LICENSE` file for details.

## Contact

For questions or feedback, open an issue on the GitHub repository.
