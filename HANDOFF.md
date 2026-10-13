# Handoff

Current state of Axiomatic, written so any session (local or cloud) can continue from this file
alone. The full game design is in [docs/design.md](docs/design.md). Update this file when you stop.

## Build and run

- Needs **JDK 25**. Everything else (Gradle 9.2.1, NeoForge, Minecraft) downloads automatically.
- `./gradlew build` builds `build/libs/axiomatic-<version>.jar` and runs the unit tests.
- `./gradlew test` runs only the unit tests (fast, no game needed).
- `./gradlew runClient` starts Minecraft with the mod (local machine only).
- `./gradlew runGameTestServer` runs the in-world game tests headless (works in the cloud too).
- `python3 tools/gen_assets.py` regenerates placeholder textures, models, blockstates, lang,
  loot tables, recipes and the empty game-test stage. Edit the script, not the generated files.
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
| `core/knowledge/` | Truth (3-valued evaluation), Deciders (+ link theorems), Fact/Certifier (numeral certificates), TestBench (range checks), Library (statuses, named theorems, conjectures), MathState (world save format, starter definitions) |
| `registry/ModRegistries` | Every block, item, block entity, menu, component, ingredient type and the creative tab |
| `machine/` | Machine framework (`MachineBlockEntity`: inventory, energy, jobs paid over time, sync; one `MachineMenu`; layouts) and each machine |
| `energy/` | Copper Cable and the network search that delivers dynamo energy |
| `item/` | Numeral item; `NumeralData` component = value + certificates |
| `world/AxiomaticData` | SavedData wrapping `MathState` (base64 in level data) |
| `crafting/CertifiedNumeralIngredient` | Recipe ingredient "numeral certified with property P" |
| `client/` | `MachineScreen` (drawn with rectangles, no texture), screen registration |
| `gametest/AxiomaticGameTests` | In-world tests on an empty stage structure |
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
- **Starter definitions**: new worlds come with `add` and `Even` defined (not proven anything),
  because the opening hour certifies evens on the Test Bench before the Drafting Chamber exists.
- **Energy is the mod's own** simple system (dynamo → cables → machines, no loss), not the NeoForge
  transaction-based EnergyHandler API. 1 energy = 1 axiom use or computation step; coal = 16,000.
- **Jobs**: a machine takes its inputs at once, computes the result immediately, then pays the
  cost over the job's duration before outputting. Test Bench search runs up to 20M steps.
- **Test Bench witness cap** = the numeral's own value (covers Even, ≤-style properties).
- **Conjecture Bench rule**: a statement may be conjectured only if `Truth.isRefutable` (no ∃ in
  a positive position unless behind a linked decider). Bounded ∃ ("∃k ≤ n") is not yet recognised.
- **Theorems on items/saves are trusted on load** via `Kernel.restoreSaved` (single-player).
- Design-doc typo: under Structures, "- - over the even numbers" means "+ over the even numbers".
- The doc's "prime path" table (7 stages) was missing from the copy we have; ask the owner for it.

## Milestone 1 progress (from docs/design.md)

- [x] NeoForge project for Minecraft 26.1.x, building a working jar
- [x] Pointed-graph data format with save and load (world save via MathState)
- [~] Proof checker core. Done: Peano axioms 3–4, equality, induction, ∧, →, ∃ intro/elim,
      hypotheses and discharge, explicit / primitive-recursive / predicate definitions,
      conjectures, step counting, deciders + link theorems, Test Bench range checks, printer.
      **Not yet**: monoid / equivalence / order certificates (structures)
- [x] Energy: Coal Dynamo, copper cables, energy per step
- [~] Vanilla crafting recipes: done for the machines that exist (dynamo, cable, Zero Source,
      Successor, Duplicator, Test Bench, Circuit Board)
- [~] Machines: Zero Source, Successor, Duplicator, Test Bench (numeral mode) done and game-tested.
      Not yet: Drafting Chamber, Variable blocks, benches, reasoning machines, Graph Lens, Axiom Tablets
- [ ] Compact blocks: compile, name, inspect; typed ports; theorem cards
- [~] Knowledge record: core done (statuses, certificates, conjectures) and saved per world;
      not yet surfaced in game (Library block)
- [~] Conjecture blocks: core logic + 2n + 1 trap tested; no block yet
- [~] Circuit Board recipe needs certified evens (done); Prime Core not yet
- [ ] The Notebook (Modonomicon) with Getting Started, Machines, Logic and Proof
- [ ] Placeholder CC0 textures and sounds

## Suggested next steps

1. Statement items (a Theorem or unproven Formula in a data component) and circuit items (a Term).
2. Reasoning machines as 1–3 input machines calling one Kernel rule each (Instantiator,
   Rewriter, Conjunction/Modus Ponens Press, Induction Engine, Witness Bench, Assumption Chamber).
3. Drafting Chamber: assemble circuits from Variable blocks / numerals / defined functions,
   compile definitions and name theorems (needs a name text box).
4. Notebook (Modonomicon) given on first join; Library block; Conjecture Bench + upkeep.
