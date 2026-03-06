# Ory Hydra Testcontainer

The `OryHydraContainer` is a Testcontainer for the Ory Hydra OAuth 2.0 and OpenID Connect provider. It allows you to quickly integrate and test Ory Hydra functionalities in Java applications — no Docker Compose file required.

## Prerequisites

* Docker installed and running.
* Java JDK 17 or later.

## Features

* Zero-config startup — runs database migration and the Hydra server in a single container.
* Defaults to an in-container SQLite database, so no external database is needed.
* Automatic setup of Ory Hydra's admin and public ports.
* `createOAuth2Client` convenience method to register OAuth 2.0 clients using the Hydra CLI inside the container — no extra HTTP dependencies needed.
* Convenient methods to fetch base URIs for both the admin and public endpoints.
* Convenience URI helpers for essential OAuth 2.0 and OpenID Connect endpoints (see [Convenience URI Methods](#convenience-uri-methods)).
* Customizable through a builder pattern, allowing configuration of the Docker image, environment variables, and wait strategy.

## Usage

### Dependency

First, include the `OryHydraContainer` in your project's `build.gradle`:

```groovy
dependencies {
    testImplementation 'com.ardetrick.testcontainers:testcontainers-ory-hydra:0.0.5'
}
```

### Basic Usage

The recommended approach uses Testcontainers' JUnit 5 annotations, which handle starting and stopping the container automatically:

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
* `urlsLogin(String)`: Set the login URL (`URLS_LOGIN`).
* `urlsConsent(String)`: Set the consent URL (`URLS_CONSENT`).
* `urlsSelfIssuer(String)`: Set the self-issuer URL (`URLS_SELF_ISSUER`).
* `urlsLogout(String)`: Set the logout URL (`URLS_LOGOUT`).
* `secretsSystem(String)`: Set the system secret used for encryption (`SECRETS_SYSTEM`).
* `dsn(String)`: Set the database connection string (`DSN`, default: SQLite).
* `env(String, String)`: Set an arbitrary environment variable.
* `env(Map<String, String>)`: Merge a map of environment variables.
* `waitStrategy(WaitStrategy)`: Override the readiness wait strategy (defaults to polling `/health/ready`).

## Creating OAuth2 Clients

Register an OAuth 2.0 client in the running Hydra instance using `createOAuth2Client`. This uses the Hydra CLI inside the container, so no additional HTTP client or dependencies are needed:

```java
hydra.createOAuth2Client("my-client", "my-secret", List.of("http://localhost/callback"));
```

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
