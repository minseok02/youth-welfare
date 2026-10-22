---
name: systematic-debugging
description: "Investigate concrete bugs, failed tests, or unexpected runtime behavior, including failures during setup or feature work. Skip work without failures."
license: MIT
metadata:
  upstream: "obra/superpowers"
  upstream-commit: "2ce8aa15d05bd57213e6f746f953aafb7ded7a8b"
  adaptation: "youth-welfare"
---

# Systematic Debugging

Find evidence for the cause before changing behavior. Follow the relevant sections of the [project work guide](../../../docs/work-guide.md); keep investigation proportional to the failure.

## Workflow

1. Establish the symptom, expected behavior, affected code, and actual execution environment. If expected behavior depends on an unresolved product decision, clarify it before implementing that decision.
2. Read the error and reproduce with the smallest useful existing test or command. For intermittent failures, gather evidence and record uncertainty rather than guessing a cause.
3. Check recent changes and trace the first failing boundary through inputs, calls, configuration, and state. Prefer existing evidence; add targeted diagnostics only when needed. Never dump secrets, full environments, or personal data.
4. State one hypothesis with its evidence. Test it using a small, reversible change; keep unrelated refactoring out of the experiment.
5. Fix the supported cause. Use a focused regression test when it can prevent a meaningful recurrence; an existing check or direct reproduction can suffice for a trivial fix. For external or timing failures, justify any timeout/retry handling and account for repeated side effects.
6. Recheck the original failure and affected behavior. Report the cause evidence, change, actual verification results, and remaining uncertainty. A passing narrow check is evidence for its scope.

## Boundaries

- Failed hypotheses require new evidence or a revised hypothesis, not stacked speculative patches. If investigation stalls, explain what remains unknown and the next useful diagnostic. Failure count alone does not prove an architecture defect.
- Preserve user changes, data contracts, and approval boundaries. Do not weaken requirements or tests to hide a failure. Structural exceptions follow the existing approval process.
- Debugging does not authorize production writes, real collection, AI calls, or message delivery. Use the project's test/environment rules and existing authorization.
- No additional skill installs are required. Avoid fixed attempt counts, arbitrary sleeps, and full-suite runs for every small failure. Read only relevant instructions and evidence.

## Source

Adapted from the [reviewed upstream](https://github.com/obra/superpowers/blob/2ce8aa15d05bd57213e6f746f953aafb7ded7a8b/skills/systematic-debugging/SKILL.md). The core investigation/hypothesis/verification workflow is retained; mandatory skill dependencies, architecture conclusions based on attempt counts, and unsupported statistics are omitted. See [LICENSE](LICENSE).
