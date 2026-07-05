package com.ardetrick.testcontainers;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.ardetrick.testcontainers",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest static final ArchRule no_standard_streams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  @ArchTest static final ArchRule no_java_util_logging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

  @ArchTest
  static final ArchRule no_generic_exceptions = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

  /**
   * The OAuth flow and result types are pure JDK code bound only to a pair of base URIs, so they
   * work against any Hydra instance and stay unit-testable without Docker. Only the container may
   * touch Testcontainers/Docker, and nothing may depend back on the container — the dependency is
   * strictly one-way: container -> everything else.
   */
  @ArchTest
  static final ArchRule only_the_container_touches_testcontainers_and_nothing_depends_on_it =
      noClasses()
          .that(not(belongToAnyOf(OryHydraContainer.class)))
          .should()
          .dependOnClassesThat(
              resideInAnyPackage("org.testcontainers..", "com.github.dockerjava..")
                  .or(belongToAnyOf(OryHydraContainer.class)));

  /**
   * Everything other than the container and the flow API is an implementation detail — in
   * particular the hand-rolled JSON reader/writer, which exists only to keep this library
   * dependency-free and may be changed or dropped at any time. Package-private visibility keeps
   * internals out of users' reach.
   */
  @ArchTest
  static final ArchRule internals_are_not_public =
      classes()
          .that(
              not(
                  belongToAnyOf(
                      OryHydraContainer.class,
                      AuthorizationCodeFlow.class,
                      ClientCredentialsFlow.class,
                      FlowResult.class,
                      HydraFlowException.class,
                      IntrospectionResponse.class,
                      OAuth2ClientRegistration.class,
                      OpenIdConfiguration.class)))
          .should()
          .notBePublic();
}
