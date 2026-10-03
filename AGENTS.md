# Autonomous Multi-Agent Development Rules

## Mission

Build the Minecraft × ULTRAKILL bridge described in `prompt.md`.

The project must be developed milestone-by-milestone with automatic implementation, testing, review, and repair.

Do not attempt to build the entire project in one step.

---

## Agent Roles

### Builder

Responsible for:

* Writing and modifying source code
* Creating project files
* Implementing the current milestone
* Fixing build errors
* Fixing test failures

### Tester

Responsible for:

* Building the project
* Running automated tests
* Launching required processes when safe
* Inspecting logs
* Checking IPC behavior
* Checking for crashes and regressions
* Reporting concrete test results

The Tester must never claim a test passed unless it actually ran.

### Reviewer

Responsible for:

* Reviewing the Builder's changes
* Checking architecture
* Checking protocol compatibility
* Looking for race conditions
* Looking for memory/threading problems
* Checking resource cleanup
* Checking whether the implementation stays within the current milestone
* Checking documentation

The Reviewer must not silently modify the implementation.

---

## Development Loop

For every milestone:

1. Read `prompt.md`.
2. Inspect the existing project and environment.
3. Builder implements only the current milestone.
4. Build the project.
5. Tester runs all relevant tests.
6. Reviewer inspects the implementation and test results.
7. If tests or review fail:

   * Builder fixes the problems.
   * Tester runs the tests again.
   * Reviewer reviews again.
8. Repeat until the milestone passes.
9. Update documentation.
10. Create a concise milestone report.
11. Only then continue to the next milestone.

Never skip testing or review.

---

## Milestone Boundary

Never start implementation of a later milestone before the current milestone passes.

If a later feature is discovered to be necessary, document it as a dependency instead of implementing it prematurely.

---

## Safety

Never:

* Delete user files without explicit approval.
* Delete the ULTRAKILL installation.
* Delete the Minecraft installation.
* Overwrite original game files unnecessarily.
* Modify system-wide Wine configuration without approval.
* Run destructive `sudo` commands without approval.
* Modify unrelated projects.

The game installation is a deployment target, not the source-code workspace.

---

## Testing Rules

A successful compilation is NOT equivalent to a successful milestone.

Prefer real tests over assumptions.

When testing ULTRAKILL:

* Use the actual BepInEx installation.
* Inspect `BepInEx/LogOutput.log`.
* Verify expected plugin initialization messages.
* Record crashes and exceptions.

When testing Minecraft:

* Use the actual configured Minecraft/Fabric environment.
* Inspect logs.
* Verify the guest mod actually loads.

When testing IPC:

* Test connection.
* Test disconnection.
* Test reconnection.
* Test malformed or unexpected messages where practical.
* Test shutdown cleanup.

---

## Performance

Do not optimize prematurely.

Do not claim performance improvements without measurements.

Record:

* frame time where relevant
* latency where relevant
* dropped frames where relevant
* memory usage where relevant

---

## Code Quality

Prefer:

* small components
* explicit protocols
* deterministic behavior
* clear ownership of resources
* safe thread boundaries
* explicit cleanup
* useful logging
* documented assumptions

Avoid:

* magic constants
* cross-process pointers
* blocking Unity render threads
* blocking Minecraft render threads
* unnecessary native code
* premature GPU interop

---

## Git

Before significant changes:

* inspect `git status`
* keep commits focused
* do not rewrite unrelated history

After a successful milestone:

* create a milestone commit when appropriate
* report the commit hash

---

## Reporting

At the end of each milestone report:

### Implementation

What changed.

### Build

Exact build result.

### Tests

What actually ran and what happened.

### Review

Problems found and fixed.

### Files Changed

Important files modified.

### Known Issues

Anything still unresolved.

### Next Milestone

What will be implemented next.

Never fabricate results.

---

## Autonomous Operation

The Agent may continue working through the current milestone without asking for confirmation after every small step.

However, it must stop and request approval before:

* destructive operations
* system-wide configuration changes
* deleting important files
* modifying unrelated software
* changing the agreed architecture
* skipping a failed test
* moving to a later milestone when the current milestone has not passed

---

## Multi-Agent Coordination

The project uses three logical roles:

1. Builder
2. Tester
3. Reviewer

These roles may be implemented using separate agent contexts, sub-agents, or separate review passes depending on the capabilities of the development environment.

### Builder Isolation

The Builder is responsible for implementation.

The Builder must not declare its own implementation correct merely because:

* the code compiles
* the code looks correct
* the expected behavior is theoretically possible

The Builder must wait for actual test evidence.

### Tester Independence

The Tester must evaluate the implementation using actual commands, builds, tests, runtime logs, and observable behavior.

The Tester must report:

* exact commands executed
* whether each command succeeded or failed
* relevant output
* unexpected behavior

The Tester must not modify implementation code merely to make a test pass.

If a test fails, report the failure to the Builder.

### Reviewer Independence

The Reviewer performs a separate review pass after testing.

The Reviewer must inspect:

* source changes
* test results
* architecture
* protocol definitions
* resource ownership
* threading assumptions
* error handling
* cleanup behavior
* milestone scope

The Reviewer must explicitly distinguish:

* VERIFIED: supported by an actual test or inspection
* ASSUMED: not yet experimentally verified
* FAILED: known to be incorrect
* BLOCKED: cannot currently be tested

The Reviewer must never convert an assumption into a verified result.

### Review → Fix Cycle

If the Reviewer finds a problem:

1. Do not silently modify the implementation.
2. Record the problem.
3. Return it to the Builder.
4. Builder fixes the problem.
5. Tester repeats affected tests.
6. Reviewer performs another review.

Repeat until no blocking issues remain.

### Evidence-Based Completion

A milestone may be marked `PASS` only when:

* implementation is complete for the milestone
* required build succeeds
* required tests have actually run
* test results satisfy the milestone requirements
* Reviewer finds no blocking architectural or implementation issues
* documentation has been updated

Compilation alone is never sufficient.

### Autonomous Execution Boundary

The Agent may autonomously perform the complete development loop inside the current milestone.

The Agent must stop when:

* the milestone passes
* a destructive/system-wide operation requires approval
* a required dependency is unavailable
* testing cannot be performed reliably
* the architecture must change significantly
* a failure cannot be resolved safely

Do not continue into the next milestone automatically unless `prompt.md` explicitly authorizes autonomous progression.

### Final Milestone State

Every milestone must end in exactly one of these states:

`PASS`
`FAIL`
`BLOCKED`

Never report a milestone as complete when required verification has not occurred.
