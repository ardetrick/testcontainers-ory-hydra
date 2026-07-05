buildscript {
    configurations.classpath {
        resolutionStrategy {
            // Spotless and JReleaser request conflicting JGit majors on the shared buildscript
            // classpath (https://github.com/jreleaser/jreleaser/issues/1643). Pin JReleaser's own
            // version: JGit 7 removed the GpgObjectSigner API JReleaser needs, while Spotless only
            // uses JGit for ratchetFrom, which this build does not use. Guarded by the
            // "jreleaser classpath smoke check" CI step. Kept as a literal because version-catalog
            // accessors are not available inside the buildscript block.
            force("org.eclipse.jgit:org.eclipse.jgit:5.13.5.202508271544-r")
        }
    }
}

plugins {
    `maven-publish`
    `java-library`
    alias(libs.plugins.freefair.lombok)
    alias(libs.plugins.jreleaser)
    alias(libs.plugins.spotless)
}

spotless {
    java {
        // Requires JDK 21+ to run. Gradle must be invoked with JDK 21 even though
        // the java toolchain targets JDK 17 for compilation.
        googleJavaFormat(libs.versions.googleJavaFormat.get())
        formatAnnotations()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint()
    }
    // Whitespace hygiene for the non-code files nothing else formats.
    format("misc") {
        target("*.md", "*.yml", ".github/workflows/*.yml", "gradle/*.toml", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

group = "com.ardetrick.testcontainers"
version = "0.0.7-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(platform(libs.testcontainers.bom))
    api(libs.testcontainers)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)

    testRuntimeOnly(libs.junit.platform.launcher)
}

java.toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.test {
    useJUnitPlatform()
    // The suite is one-container-per-test and fully independent; parallel forks cut wall time
    // on cache-miss runs (warm runs skip test execution entirely via the build cache).
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
        withType<MavenPublication> {
            pom {
                packaging = "jar"
                name.set("testcontainers-ory-hydra")
                description.set("Testcontainers module for Ory Hydra")
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
            url =
                layout.buildDirectory
                    .dir("staging-deploy")
                    .get()
                    .asFile
                    .toURI()
        }
    }
}

jreleaser {
    configFile.set(file("jreleaser.yml"))
}
