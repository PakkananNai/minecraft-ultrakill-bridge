# Post-M12 Functional Expansion — Work Log

**Project:** minecraft-ultrakill-bridge  
**Date:** 2026-10-07  
**Phase:** Post-M12 Functional Expansion (planning + behavior audit)  
**Guiding method:** Ponytail — inspect first, reuse existing behavior, smallest useful change, no speculative abstractions.

## 1. Current baseline

- Official roadmap M0–M12: **PASS**.
- `prompt.md` ends at M12; this phase is intentionally **not** called M13.
- Core architecture is already working:
  - Minecraft 1.21.1 Fabric guest.
  - ULTRAKILL/BepInEx host.
  - MCUB TCP control plane with canonical 16-byte header.
  - Shared-memory triple-buffer framebuffer.
  - Guest rendering → shared memory → Unity texture.
  - Camera, input focus, block interaction, entity sync, damage, reconnect/recovery.
- M12 live validation already passed after the guest OpenGL fence fix and host framebuffer allocation reuse.

## 2. Shared vision

The target is not merely displaying Minecraft inside ULTRAKILL. The target is a **playable Minecraft experience hosted/integrated by ULTRAKILL**, while Minecraft remains authoritative for Minecraft gameplay.

The bridge should connect the two games rather than duplicate Minecraft systems inside Unity.

Target experience:

1. ULTRAKILL runs as the host.
2. Minecraft runs as the guest.
3. Minecraft frames appear through the ULTRAKILL integration surface.
4. A focus toggle switches control between ULTRAKILL and Minecraft.
5. When Minecraft has focus, normal Minecraft gameplay should feel playable: movement, look, hotbar, inventory, use/attack, drop, and interaction.
6. Cross-world physics/movement should only be added if a concrete gameplay requirement proves it is necessary.

## 3. Work completed in this session

### 3.1 Repository/architecture audit

Re-read the authoritative project instructions and relevant current implementation before changing code.

### 3.2 Runtime smoke test

Started a fresh Minecraft instance and ULTRAKILL host.

Observed:

- ULTRAKILL loaded `MinecraftBridge v0.1.0`.
- Host listener active on `127.0.0.1:47653`.
- Render surface created.
- Camera bridge created with guest-authoritative coordinate mapping.
- Minecraft connected successfully.
- Session established: `3651947225`.
- Shared framebuffer identity validated for that session.
- Guest reported `M7_GUEST_INPUT_FOCUS focus=false releaseHeldKeys=true` initially, which is expected while Minecraft is not focused.
- Host continued issuing M9 raycast requests, confirming the live bridge remained active.

### 3.3 Important finding

The existing input implementation already contains the core input forwarding path. Therefore, according to Ponytail, **do not create a new input abstraction yet**.

The next job is behavior verification: prove which normal Minecraft controls already work end-to-end, then patch only the missing behavior.

## 4. Next execution plan

### Step A — Input behavior audit

Verify the existing path for:

- WASD movement.
- Space/jump.
- Shift/sneak.
- Mouse look.
- Left click / attack / break.
- Right click / use / place.
- Mouse wheel hotbar selection.
- Number keys 1–9.
- E / inventory.
- Q / drop.
- F8 focus switching and release of held keys.

### Step B — Evidence

Use a fresh runtime and logs to distinguish:

- Already working.
- Forwarded but semantically incomplete.
- Not forwarded at all.
- Broken by focus state.

Do not infer success from compilation alone.

### Step C — Minimal implementation

Only after a failing/missing behavior is identified:

- Reuse existing protocol/event types.
- Prefer existing Minecraft APIs.
- Avoid adding dependencies.
- Avoid a new generic input framework.
- Keep edits surgical.

### Step D — Regression checks

After any code change:

1. Guest tests/build.
2. Host build.
3. Relevant protocol/framebuffer validation if affected.
4. Fresh live runtime.
5. `git diff --check`.

### Step E — Gameplay integration decision

After controls are proven, evaluate whether cross-world collision/movement synchronization is actually required by the intended experience. Do not implement a second physics system unless the requirement is demonstrated.

## 5. Ponytail rules for this phase

- **Delete/reuse before adding.**
- **Standard/native APIs before custom machinery.**
- **No speculative extensibility.**
- **One problem per change.**
- **Compile is not proof; runtime evidence is proof.**
- **Do not touch unrelated untracked validation artifacts.**
- **Do not invent M13 in the official roadmap.**

## 6. Current status

**Phase status:** Planning + behavior audit started.  
**Code changes this session:** None.  
**Reason:** Existing implementation must be behavior-tested before adding code.

**Immediate next action:** complete the end-to-end Minecraft input audit in a fresh runtime, then implement only the first confirmed missing behavior.


## 7. Behavior-audit result — 2026-10-07

### 7.1 Host → guest input path

Inspected the existing host `InputBridge` and guest `GuestInputBridge`. No missing protocol layer was found.

Existing implementation already covers:

- Keyboard press/release using GLFW-compatible key codes.
- WASD/Space/Shift and other normal Minecraft keys through the same keyboard path.
- Number-row keys 1–9 through the same keyboard path.
- E inventory and Q drop through the same keyboard path.
- Relative mouse movement.
- Left/right/middle mouse press/release.
- Mouse wheel.
- F8 focus switching.
- Held-key/button release when focus is lost.
- Automatic closing of the Minecraft pause screen when focus is gained.

A fresh runtime produced real guest-side input application evidence for mouse movement, mouse button events, and keyboard events, plus repeated focus transitions. No input exception or protocol failure was observed.

### 7.2 Ponytail decision

**No code change is justified yet.** The implementation already contains the required control semantics, and adding another input abstraction would duplicate working code.

The remaining gap is not protocol coverage but **physical end-to-end gameplay verification of each control** under the current Wayland desktop session. The remote terminal cannot safely inject normal desktop keyboard/mouse events into the running graphical session without introducing a new dependency or invasive test machinery. Per Ponytail, that is not sufficient reason to modify production code.

Therefore the correct next step is to keep the existing input implementation and move to the next user-visible behavior audit rather than inventing a test-only input stack.

## 8. Regression gate — 2026-10-07

All available automated gates passed without source changes:

- Host build: **PASS**
- Guest Gradle tests: **PASS**
- TCP protocol validation: **PASS** — canonical 16-byte header, both directions
- Cross-language protocol fixtures: **PASS**
- Shared-memory lifecycle/cleanup: **PASS**
- Cross-process shared framebuffer stress: **PASS** — 10,000 frames per direction
- `git diff --check`: included in the regression command and no failure reported

Expected Wine headless GUI warnings appeared during the host build; they do not affect compilation and are the same known build-environment warnings.

## 9. Current checkpoint

**Status:** Input behavior audit completed at the implementation/protocol level; no production code defect demonstrated.  
**Code changes:** none.  
**Regression:** PASS.  
**Ponytail verdict:** keep the existing input path; do not add machinery without a demonstrated defect.

**Next target:** audit the actual playable Minecraft interaction flow that sits immediately above input — inventory/hotbar/use/drop and block/entity interaction — and fix only a behavior that can be demonstrated as missing or incorrect.
