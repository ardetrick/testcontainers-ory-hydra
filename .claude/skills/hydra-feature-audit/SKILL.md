---
name: hydra-feature-audit
description: Audit Ory Hydra's latest capabilities and identify gaps in this library's coverage
disable-model-invocation: true
argument-hint: [optional focus area, e.g. "OAuth2 endpoints" or "configuration options"]
---

Audit the latest Ory Hydra capabilities against what this library currently supports, then write findings to `docs/hydra-feature-audit.md`. If $ARGUMENTS specifies a focus area, narrow the audit accordingly.

## Step 1: Understand current library capabilities

Read `OryHydraComposeContainer.java` and the test resources to catalog:
- All exposed convenience URI methods and what Hydra endpoints they map to
- All builder configuration options (environment variables, compose files, wait strategy)
- What Hydra configuration is covered in the docker-compose files and `hydra.yml`

## Step 2: Research latest Ory Hydra features

Use web search and fetch the following:
- The Ory Hydra GitHub releases page for the latest version and recent changelog entries
- The current Ory Hydra REST API reference (admin and public endpoints)
- The current Ory Hydra configuration reference (serve, urls, oauth2, oidc, secrets, etc.)
- Any newly added features, endpoints, or configuration options since the Hydra version used in this project's docker-compose files

## Step 3: Identify gaps

Compare what the library exposes against what Hydra offers. Categorize findings as:
- **New endpoints**: Hydra REST API endpoints not covered by convenience URI methods
- **New configuration options**: Hydra config/env vars not exposed through the builder
- **Version drift**: Difference between the Hydra version in docker-compose and the latest release
- **Breaking changes**: Any Hydra changes that could affect this library's existing functionality

## Step 4: Write the audit report

Write findings to `docs/hydra-feature-audit.md` with this structure:

```markdown
# Ory Hydra Feature Audit

**Date:** [today's date]
**Library Hydra version:** [version from docker-compose]
**Latest Hydra version:** [latest release]

## Summary

[1-2 paragraph overview of findings]

## Version Drift

[Current vs latest version, migration notes if applicable]

## Gap Analysis

### High Priority
[Gaps that most users would benefit from — common endpoints, important config options]

### Medium Priority
[Gaps that are useful but more niche]

### Low Priority
[Gaps that are edge cases or rarely needed in testing]

## Recommended Changes

[Concrete suggestions: new builder methods, new convenience URIs, version bumps, etc.]
```

## Step 5: Present findings

Summarize the key findings in the conversation, highlighting the most impactful gaps and recommended next steps.
