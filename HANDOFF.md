# Handoff

Current state of Axiomatic, written so any session (local or cloud) can continue from this file
alone. The full game design is in [docs/design.md](docs/design.md). Update this file when you stop.

## Build and run

- Needs **JDK 25**. Everything else (Gradle 9.2.1, NeoForge, Minecraft) downloads automatically.
- `./gradlew build` builds `build/libs/axiomatic-<version>.jar` and runs the unit tests.
- `./gradlew test` runs only the unit tests (fast, no game needed).
- `./gradlew runClient` starts Minecraft with the mod (local machine only).
- `./gradlew runGameTestServer` runs the in-world game tests headless (works in the cloud too).
- `./gradlew runClient -Pdevtour` runs a scripted tour (client/DevTour.java): makes a world,
  places every machine, opens their screens and saves `run/screenshots/tour_*.png`, then quits.
  In the cloud it works headless: `Xvfb :42 -screen 0 1280x720x24 -nolisten tcp &` then
  `DISPLAY=:42 ./gradlew runClient -Pdevtour` (Mesa software rendering; no sound device is fine).
- `bash tools/gen_sounds.sh` regenerates the synthesized sounds (needs ffmpeg with libvorbis).
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
| `machine/ReasoningBlockEntity` + `machine/reasoning/` | Reasoning machines: each applies one Kernel rule. The transformed statement is consumed; tools (equations, variables, circuits) stay |
| `item/MathItems`, `StatementData`, `CircuitData` | Statement items (proven = Theorem, or unproven claim), circuits, Variable blocks (use in air to cycle name), Axiom Tablets |
| `machine/CompiledMachineBlockEntity` | Compact block for a defined function: design slot (circuit f(…)), numeral ports, theorem card slot that certifies outputs |
| `machine/ConjectureBlockEntity`, `reasoning/ConjectureBenchBlockEntity` | Conjectures: bench makes the card + block; block pays upkeep, goes dormant, fails on refutation |
| `risk/Paradox` | Cascade: items depending on a refuted conjecture turn to paradox junk; crafted parts record conjectures (`axiomatic:depends`) |
| `AxiomaticConfig` | `disasterBlockDamage` (default false), `conjectureUpkeep` |
| `block/LibraryBlock` | Library: use it to list definitions, theorems, conjectures (with post-mortems) and statuses in chat |
| `item/GraphLensItem` | Graph Lens: shows the pointed graph of the item in the other hand |
| `book/Notebook` | The Notebook, given on first join: a vanilla written book whose pages are lang keys `notebook.axiomatic.page.N` (placeholder until Modonomicon is reachable) |
| `reasoning/StructureBenchBlockEntity` | Structure Bench: Axioms / Certify (laws → Library certificate) / Apply (generic theorem + circuit naming a certified op) |
| `machine/PrimeForgeBlockEntity`, `machine/Cards` | Prime Forge: next prime ≥ candidate certified by a method card "H(n) → Prime(n)"; Cards = shared theorem-card rules (proven / Tested in range / conjectural with background check) |
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
- **∃ elimination**: the Assumption Chamber opens a proven (possibly stamped) ∃ as the variable
  in its "open as" slot, or as a fresh kernel variable (`k#7`, untypeable) if that slot is
  empty; `closeExists` checks it does not escape. Naming it lets players build witness
  circuits with it (needed for even + even = even).
- **No ex falso** (⊥ does not prove everything) for now; add it if a proof needs it.
- **Symmetry/transitivity of =** are derived from reflexivity + Rewriter, not separate rules.
- Theorems record `dependencies` ("axiom:…", "def:…", "conjecture:…"); `isConjectural()` is how
  conjecture taint will cascade.
- **Deciders** are linked in the Drafting Chamber's Link mode from the two proven link theorems
  (variable names may differ from the definition's).
- **Starter definitions**: new worlds come with `add` and `Even` defined (not proven anything),
  because the opening hour certifies evens on the Test Bench before the Drafting Chamber exists.
- **Energy is the mod's own** simple system (dynamo → cables → machines, no loss), not the NeoForge
  transaction-based EnergyHandler API. 1 energy = 1 axiom use or computation step; coal = 16,000.
- **Jobs**: a machine takes its inputs, computes the result, then pays the cost over the job's
  duration before outputting. Heavy computation (Test Bench, Compiled Machine, Prime Forge)
  runs on worker threads (`machine/Async`, `MachineBlockEntity.runAsync`) over copies of the
  definitions (`Signature.copy`, `Deciders.copy`); results, including conjecture refutations,
  are committed on the server thread after re-checking the inputs. The evaluator does about
  40M steps/s. Refused inputs are remembered so the same work is not redone every 4 ticks.
- **Test Bench witness cap** = the numeral's own value (covers Even, ≤-style properties).
- **Conjecture Bench rule**: a statement may be conjectured only if computation can catch it
  failing on *some* numbers (`Truth.isRefutable`): Prime(2n+1) qualifies (a divisor refutes it),
  "∀n ∃k. n = k + k" does not. Bounded ∃ ("∃k ≤ n") is not recognised, so Prime can be *refuted*
  by search but only *certified* by a linked decider or a card. A conjecture's check never uses a
  decider whose link depends on that same conjecture.
- **Typed ports**: a Compiled Machine card's premises about its inputs must be met by the
  inputs' certificates. The Prime Forge instead computes premises for its bare candidates.
- **Background checks** run when a conjectural card is used in a Compiled Machine or Prime Forge (the card's
  statement is computed for those inputs). Instances reached by other routes are not watched yet.
- **Structures** are theory interpretation: abstract symbols (`Monoid.op`, `Monoid.e`,
  `Equivalence.R`, `Order.R`, names players cannot type) have no defining statements, only
  their structure's axioms (dependency `structure:NAME`). Instantiated templates are named
  like `pow⟨Monoid.e:=zeroC,Monoid.op:=add⟩`. Save format is version 2 (reads version 1).
- **Theorems on items/saves are trusted on load** via `Kernel.restoreSaved` (single-player).
- Design-doc typo: under Structures, "- - over the even numbers" means "+ over the even numbers".
- The doc's "prime path" table (7 stages) was missing from the copy we have; ask the owner for it.

## Milestone 1 progress (from docs/design.md)

- [x] NeoForge project for Minecraft 26.1.x, building a working jar
- [x] Pointed-graph data format with save and load (world save via MathState)
- [~] Proof checker core. Done: Peano axioms 3–4, equality, induction, ∧, →, ∃ intro/elim,
      hypotheses and discharge, explicit / primitive-recursive / predicate definitions,
      conjectures, step counting, deciders + link theorems, Test Bench range checks, printer.
      Structures done: built-in Monoid / Equivalence / Order with abstract symbols and axioms,
      generic theorems, `Kernel.interpret` (applies a generic theorem to concrete definitions
      whose laws are proven; templates such as a generic `pow` are instantiated — pow over
      addition computes multiplication), Library structure certificates (mapping read off the
      laws). Monoids are over all of ℕ only (no subsets yet)
- [x] Energy: Coal Dynamo, copper cables, energy per step
- [~] Vanilla crafting recipes: done for the machines that exist (dynamo, cable, Zero Source,
      Successor, Duplicator, Test Bench, Circuit Board)
- [~] Machines done: Zero Source, Successor, Duplicator (also copies statements/circuits), Test
      Bench (numeral mode), Drafting Chamber (assemble / define / name / tablet modes), Variable
      blocks, Equation/Property/Witness Bench, Axiom Tablets, Instantiator, Rewriter, Assumption
      Chamber (assume, ∃-open, discharge), Induction Engine, Conjunction/Modus Ponens Press.
      Game tests prove zero_add and "Double outputs evens" with the real machines.
      Also done: Conjecture Bench, Graph Lens (also inspects a Compiled Machine), Library block,
      Test Bench range mode (Tested statements work as cards inside their range), Statement
      Conveyor (recipe needs a proven "∀n. 0 + n = n"), Containment Controller (a conjecture
      block sealed in obsidian/tinted glass with a controller within 4 blocks fails harmlessly)
- [~] Compact blocks: Compiled Machine runs any defined function with theorem cards (proven,
      closure, or conjectural with background checks). Not yet: inspect, typed-port enforcement,
      cards slotted into other machines
- [~] Knowledge record: core done (statuses, certificates, conjectures) and saved per world;
      not yet surfaced in game (Library block)
- [x] Conjecture blocks with upkeep, background checks (on card use) and the 2n + 1 trap
      (game test `two_n_plus_one_trap`); disasters drop paradox junk; config for block damage
- [x] Circuit Board and Prime Core recipes needing certified numerals (Prime Core matches the
      property named exactly "Prime")
- [ ] The Notebook (Modonomicon) with Getting Started, Machines, Logic and Proof
- [ ] Placeholder CC0 textures and sounds

## How the player builds things (design gaps filled in, worth reviewing)

- **Circuits** are built in the Drafting Chamber's Assemble mode: pick a function with the
  Function button (S, any defined function, or ↺ = recursive call), put argument circuits /
  Variable blocks / numerals (≤ 64) in the inputs.
- **Names** come from renaming the first input in an anvil (definitions and theorem names), so no
  custom text box was needed.
- **Define mode**: circuit alone → explicit definition (parameters = its variables, alphabetical);
  base + step (using ↺) + Variable → primitive recursion (fixed parameters in the order of the ↺
  call); unproven statement → property/relation. An *unnamed* `f(…)` gives f's defining statements
  again (how the starter `add` gets its equations).
- Hypothesis items are not yet physically locked inside the chamber (the kernel still tracks and
  enforces their stamps and locked variables).

## Suggested next steps

2. The Rewriter using a certified equivalence (≡ mod n) at prime path stage 5; monoids over a
   subset (closure + relativised laws).
3. A real GUI for the Library (it prints to chat now).
3. Notebook (Modonomicon) given on first join: needs `maven.blamejared.com`/`modmaven.dev` allowed.
4. Theorem cards and compact blocks; Prime Core recipe; Graph Lens.
