---
name: feature-workflow
description: Implement a feature in a new worktree branch with tests and review
disable-model-invocation: true
argument-hint: [feature description]
---

Implement $ARGUMENTS following this workflow:

## Phase 1: Plan
1. Create a new git worktree and branch (do not include "worktree" in the branch name)
2. Enter plan mode to design the implementation approach. The plan must incorporate all remaining steps from this workflow (Phases 2 and 3) so they are followed after plan mode exits.
3. If you encounter questions or notable tradeoff concerns, raise them before proceeding

## Phase 2: Implement
4. Implement the feature
5. Run the full test suite: `./gradlew clean build`
6. After a successful build, git commit all changes using conventional commit format
7. Let me know the change is ready for review, including the branch name — do not open a PR yet

## Phase 3: Post-approval
After I approve the change:
8. Rebase onto main
9. Re-run the full test suite: `./gradlew clean build`
10. If changes are needed, fix them and show me the diff before proceeding
11. Open a PR and flag me for final review
12. Ask whether auto-merge should be enabled on the PR
