plugins {
    `maven-publish`
    `java-library`
    id("io.freefair.lombok") version "8.14.2"
    id("org.jreleaser") version "1.19.0"
}

group = "com.ardetrick.testcontainers"
version = "0.0.3-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    api("org.testcontainers:testcontainers")
    api("org.testcontainers:junit-jupiter")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.4")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java.toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
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
