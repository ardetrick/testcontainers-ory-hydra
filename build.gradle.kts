plugins {
    `maven-publish`
    `java-library`
    id("io.freefair.lombok") version "9.2.0"
    id("org.jreleaser") version "1.21.0"
    id("com.diffplug.spotless") version "8.2.1"
}

spotless {
    java {
        // Requires JDK 21+ to run. Gradle must be invoked with JDK 21 even though
        // the java toolchain targets JDK 17 for compilation.
        googleJavaFormat("1.34.1")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

group = "com.ardetrick.testcontainers"
version = "0.0.5-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(platform("org.testcontainers:testcontainers-bom:2.0.3"))
    api("org.testcontainers:testcontainers")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java.toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.test {
    useJUnitPlatform()
}

java {
    withJavadocJar()
    withSourcesJar()
}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = groupId
            artifactId = artifactId
            description = "Test Container for Ory Hydra"
        }
        withType<MavenPublication> {
            pom {
                packaging = "jar"
                name.set("testcontainers-ory-hydra")
                description.set("testcontainers ory hydra")
                url.set("https://github.com/ardetrick/testcontainers-ory-hydra")
                inceptionYear.set("2023")
                licenses {
                    license {
                        name.set("MIT license")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("ardetrick")
                        name.set("Alex Detrick")
                        email.set("oss@ardetrick.com")
                    }
                }
                scm {
                    connection.set("scm:git:git@github.com:ardetrick/testcontainers-ory-hydra/.git")
                    developerConnection.set("scm:git:ssh:git@github.com:ardetrick/testcontainers-ory-hydra/.git")
                    url.set("https://github.com/ardetrick/testcontainers-ory-hydra/")
                }
            }
        }
    }
    repositories {
        maven {
            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
        }
    }
}

jreleaser {
    configFile.set(file("jreleaser.yml"))
}
