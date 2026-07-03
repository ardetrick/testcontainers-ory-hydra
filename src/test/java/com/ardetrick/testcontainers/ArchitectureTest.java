package com.ardetrick.testcontainers;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.ardetrick.testcontainers.oauth2.AuthorizationCodeFlow;
import com.ardetrick.testcontainers.oauth2.ClientCredentialsFlow;
import com.ardetrick.testcontainers.oauth2.FlowResult;
import com.ardetrick.testcontainers.oauth2.HydraFlowException;
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
   * The oauth2 flows are pure JDK code bound only to a pair of base URIs, so they work against any
   * Hydra instance and stay unit-testable without Docker. The dependency must remain one-way:
   * container -> flows, never back.
   */
  @ArchTest
  static final ArchRule oauth2_package_is_independent_of_the_container =
      noClasses()
          .that()
          .resideInAPackage("com.ardetrick.testcontainers.oauth2")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.testcontainers..", "com.github.dockerjava..", "com.ardetrick.testcontainers");

  /**
   * Everything in the oauth2 package other than the flow API is an implementation detail — in
   * particular the hand-rolled JSON reader/writer, which exists only to keep this library
   * dependency-free and may be changed or dropped at any time. Package-private visibility keeps
   * internals out of users' reach.
   */
  @ArchTest
  static final ArchRule oauth2_internals_are_not_public =
      classes()
          .that()
          .resideInAPackage("com.ardetrick.testcontainers.oauth2")
          .and(
              not(
                  belongToAnyOf(
                      AuthorizationCodeFlow.class,
                      ClientCredentialsFlow.class,
                      FlowResult.class,
                      HydraFlowException.class)))
          .should()
          .notBePublic();
}
