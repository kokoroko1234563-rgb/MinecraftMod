# Handoff

Current state of Axiomatic, written so any session (local or cloud) can continue from this file
alone. The full game design is in [docs/design.md](docs/design.md). Update this file when you stop.

## Build and run

- Needs **JDK 25**. Everything else (Gradle 9.2.1, NeoForge, Minecraft) downloads automatically.
- `./gradlew build` builds `build/libs/axiomatic-<version>.jar` and runs the unit tests.
- `./gradlew test` runs only the unit tests (fast, no game needed).
- `./gradlew runClient` starts Minecraft with the mod (local machine only).
- In IntelliJ: open the folder as a Gradle project and set the project SDK to JDK 25.
- Cloud sessions: `.claude/hooks/session-start.sh` installs JDK 25 automatically. The cloud
  environment's network must allow `maven.neoforged.net`, `libraries.minecraft.net`,
  `piston-meta.mojang.com`, `piston-data.mojang.com`, `resources.download.minecraft.net`.

Versions: Minecraft 26.1.2, NeoForge 26.1.2.116, ModDevGradle 2.0.148 (see `gradle.properties`).
Mod ID `axiomatic`, package `io.github.kokoroko1234563.axiomatic`.

## Layout

| Path | What |
| --- | --- |
| `src/main/java/.../axiomatic/Axiomatic.java` | Mod entry point (empty so far) |
| `src/main/java/.../axiomatic/core/` | Pure-Java mathematics. **No Minecraft imports allowed here**, so it stays unit-testable |
| `core/logic/Term`, `Formula` | Circuits (0, S, variables, defined functions) and statements (=, ∧, →, ∃, ⊥, defined properties) |
| `core/logic/Kernel` | The proof checker. One public method per inference rule, i.e. per reasoning machine |
| `core/logic/Theorem` | A proven statement. Only `Kernel` can create one (package-private constructor) |
| `core/logic/Signature`, `Definition` | Accepted definitions: explicit, primitive recursive, or properties/relations |
| `core/logic/Syntax` | Free variables, capture-avoiding substitution, alpha-equivalence, rewriting |
| `core/logic/Evaluator` | Runs circuits on numbers (BigInteger), counting steps for energy, with a budget |
| `core/graph/PointedGraph`, `GraphCodec` | The one data format, binary save/load, and term/statement conversion |
| `src/test/java/...` | JUnit tests. `KernelTest` proves zero_add (exactly the doc's 6 steps), succ_add, add_comm and "Double outputs evens", and checks that cheats are refused |

## Decisions made (not all are in the design doc)

- **Kernel is LCF-style**: theorems are objects only the kernel can construct, so every machine
  just calls a kernel rule; no separate proof objects need checking.
- **Free variables are universal** unless they occur in an open hypothesis ("locked"). Locked
  variables cannot be instantiated or inducted on.
- **Primitive recursion recurses on the last parameter**: f(x̄, 0) = base, f(x̄, S(y)) = step,
  where step may call f only as f(x̄, y).
- **Rewriter** takes an occurrence index (reading order, 0-based; negative = all), and skips
  positions where an ∃ binds one of the equation's variables.
- **Instantiator** substitutes several variables simultaneously (so a := b, b := a swaps).
- **Discharge** may discharge an assumption the conclusion never used (still valid).
- **∃ elimination**: `openExists` makes a fresh kernel variable (`k#7`, names with `#` cannot be
  typed by players); `closeExists` checks it does not escape.
- **No ex falso** (⊥ does not prove everything) for now; add it if a proof needs it.
- **Symmetry/transitivity of =** are derived from reflexivity + Rewriter, not separate rules.
- Theorems record `dependencies` ("axiom:…", "def:…", "conjecture:…"); `isConjectural()` is how
  conjecture taint will cascade.
- Design-doc typo: under Structures, "- - over the even numbers" means "+ over the even numbers".
- The doc's "prime path" table (7 stages) was missing from the copy we have; ask the owner for it.

## Milestone 1 progress (from docs/design.md)

- [x] NeoForge project for Minecraft 26.1.x, building a working jar
- [x] Pointed-graph data format with save and load (binary; NBT wrapper still to do in game code)
- [~] Proof checker core. Done: Peano axioms 3–4, equality, induction, ∧, →, ∃ intro/elim,
      hypotheses and discharge, explicit / primitive-recursive / predicate definitions,
      conjectures, step counting. **Not yet**: structures (closure theorems, monoid/equivalence/
      order certificates), deciders + link theorems, Test Bench range checking, a pretty-printer
      with infix symbols and leading ∀
- [ ] Energy: Coal Dynamo, copper cables, energy per step
- [ ] Vanilla crafting recipes for every Milestone 1 machine
- [ ] Machines (blocks, block entities, GUIs) wrapping the kernel rules
- [ ] Compact blocks: compile, name, inspect; typed ports; theorem cards
- [ ] Knowledge record (design statuses, numeral/tuple certificates), saved per world
- [ ] Conjecture blocks with upkeep, background checks, the 2n + 1 trap
- [ ] Circuit Board and Prime Core recipes needing certified numerals
- [ ] The Notebook (Modonomicon) with Getting Started, Machines, Logic and Proof
- [ ] Placeholder CC0 textures and sounds

## Suggested next steps

1. Finish the checker core: pretty-printer (infix + ∀), deciders with link theorems, Test Bench
   range checks, closure theorems and certificates.
2. Persistence: a per-world `SavedData` holding the signature, named theorems and the kernel's
   fresh-id counter (theorems are saved as formula + dependencies and trusted on load).
3. First playable slice: Numeral item (BigInteger in a data component), Zero Source, Successor,
   Test Bench witness search for Even, then energy.
