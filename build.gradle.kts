plugins {
    `maven-publish`
    id("java-library")
    id("io.freefair.lombok") version "8.3"
    id("org.jreleaser") version "1.8.0"
}

group = "com.ardetrick.testcontainers"
version = "0.0.1-SNAPSHOT"

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("Maven") {
            from(components["java"])
        }
    }
}

jreleaser {
    dryrun = true
    release {
        github {
            overwrite = true
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api(platform("org.testcontainers:testcontainers-bom:1.19.0"))
    api("org.testcontainers:testcontainers")
    api("org.testcontainers:junit-jupiter")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

java.toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.test {
    useJUnitPlatform()
}
