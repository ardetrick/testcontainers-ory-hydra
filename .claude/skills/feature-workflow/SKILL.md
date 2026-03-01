---
name: feature-workflow
description: Implement a feature in a new worktree branch with tests and review
disable-model-invocation: true
argument-hint: [feature description]
---

Implement $ARGUMENTS following this workflow:

## Phase 1: Plan
1. Create a new git worktree and branch (do not include "worktree" in the branch name)
2. Explore the codebase to understand the relevant code, then present your proposed implementation approach and ask for approval before writing any code
3. If you encounter questions or notable tradeoff concerns, raise them before proceeding

## Phase 2: Implement
4. Implement the feature
5. Run the full test suite: `./gradlew clean build`
6. After a successful build, git commit all changes using conventional commit format
7. Evaluate whether `README.md` and `CLAUDE.md` are still accurate given the changes made, and update them if needed
8. Let me know the change is ready for review, including the branch name — do not open a PR yet

## Phase 3: Post-approval
After I approve the change:
9. Rebase onto main
10. Re-run the full test suite: `./gradlew clean build`
11. If changes are needed, fix them and show me the diff before proceeding
12. Open a PR and flag me for final review
13. Ask whether auto-merge should be enabled on the PR
