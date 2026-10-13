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
  `-Pdevtour=library,certificate_sorter` takes only the named shots (names as in DevTour).
- `runGameTestServer` re-saves `data/axiomatic/structure/empty_stage.nbt`; `git checkout` that
  file before committing.
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
| `energy/` | Copper Cable and the network search that delivers dynamo energy; `ForeignEnergy` exposes machines (insert only) via `Capabilities.Energy.BLOCK` so other mods' generators can power them |
| `item/` | Numeral item; `NumeralData` component = value + certificates |
| `world/AxiomaticData` | SavedData wrapping `MathState` (base64 in level data) |
| `crafting/CertifiedNumeralIngredient` | Recipe ingredient "numeral certified with property P" |
| `machine/ReasoningBlockEntity` + `machine/reasoning/` | Reasoning machines: each applies one Kernel rule. The transformed statement is consumed; tools (equations, variables, circuits) stay |
| `item/MathItems`, `StatementData`, `CircuitData` | Statement items (proven = Theorem, or unproven claim), circuits, Variable blocks (use in air to cycle name), Axiom Tablets |
| `machine/CompiledMachineBlockEntity` | Compact block for a defined function: design slot (circuit f(…)), numeral ports, theorem card slot that certifies outputs |
| `machine/ConjectureBlockEntity`, `reasoning/ConjectureBenchBlockEntity` | Conjectures: bench makes the card + block; block pays upkeep, goes dormant, fails on refutation |
| `risk/Paradox` | Cascade: items depending on a refuted conjecture turn to paradox junk; crafted parts record conjectures (`axiomatic:depends`) |
| `AxiomaticConfig` | `disasterBlockDamage` (default false), `conjectureUpkeep`, `cableLossPerBlock` (0.02), `maxCableLoss` (0.5), `foreignEnergyPerUnit` (4 FE = 1 unit) |
| `block/LibraryBlock`, `network/LibraryPayload`, `client/LibraryScreen` | Library: use it to open a tabbed screen of definitions, theorems, conjectures (with post-mortems), structure certificates and statuses |
| `item/GraphLensItem` | Graph Lens: shows the pointed graph of the item in the other hand |
| `book/Notebook` | The Notebook, given on first join: a vanilla written book whose pages are lang keys `notebook.axiomatic.page.N` (placeholder until Modonomicon is reachable) |
| `reasoning/StructureBenchBlockEntity` | Structure Bench: Axioms / Certify (laws → Library certificate) / Apply (generic theorem + circuit naming a certified op) |
| `machine/PrimeForgeBlockEntity`, `machine/Cards` | Prime Forge: next prime ≥ candidate certified by a method card "H(n) → Prime(n)"; Cards = shared theorem-card rules (proven / Tested in range / conjectural with background check) |
| `progress/Milestones` | Advancements: machines report milestones (`axiomatic:milestone` criterion) to players within 24 blocks; the tree is generated by tools/gen_assets.py (`ADVANCEMENTS`) |
| `machine/NetworkSurveyorBlockEntity`, `energy/EnergyNetwork.Graph` | Graphs of cable networks: nodes, edges, degrees, tree check; certified trees are lossless. Cables have a phase (click to switch, blockstate `phase`): a network using both phases is a claimed two-colouring; the Surveyor certifies it if no neighbouring cables share a phase (`NetworkData.bipartite`), and the dynamo then runs alternating power (2× output, 2× fuel burn). Uncertified mixed phases short: only machines touching the dynamo get power |
| `machine/PrimeWellBlockEntity`, `FactorizationVaultBlockEntity` | Stage 4: the Well emits successive primes certified by computation (recipe needs a proven "∀n. ∃p. n ≤ p ∧ Prime(p)", matched up to variable names); the Vault stores item kinds as one number Π pᵢ^countᵢ (breaking it spills up to 64 stacks per kind) |
| `machine/CourierStationBlockEntity` | Delivers its cargo, one item per visit, to the machines on its cable network in turn; only on a network the Surveyor certified Eulerian (`Graph.eulerian`: every cable has an even number of cable neighbours; machines are terminals and do not conduct) |
| `machine/CertificateSorterBlockEntity` | Item filter: a property statement P(n) in the filter slot; certified numerals go to the yes slot (pulled by a hopper below), the rest to the no slot (pushed into containers beside it, via `HopperBlockEntity.addItem`). Button: whether conjectural certificates count |
| Perfect Core | Endgame item: recipe needs a numeral certified both Prime and Mersenne, proven only (`certified_numeral` with `also` and `proven_only`) |
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
- **General recursion** (prime path stage 3): `Definition.GeneralRecursive` f(x̄) = body with a
  measure μ. `Kernel.terminationObligation` lists, for every call f(ā) under the selector
  conditions leading to it, "μ(ā) < μ(x̄)" written ∃k. μ(ā) + S(k) = μ(x̄) using whichever
  definition has exactly the shape of addition (`Signature.canonicalAddition`). `defineRecursive`
  needs a closed, non-conjectural proof of it. Selectors (definitions by recursion on the last
  input whose cases just pick an input, like ifz(t, e, c)) are evaluated lazily. Nested calls and
  calls in the measure are refused. In game: Drafting Chamber "General recursion" mode.
- **Rewriter** (machine) rewrites by pattern (`core/logic/Tactics.rewriteMatching`, built only
  from kernel rules): the equation's free variables match any subterm, except variables locked
  by its assumptions. The occurrence button picks a match in reading order (or all). The
  kernel's own `rewrite` is literal and skips positions where an ∃ binds an equation variable.
  Given s ≡ t for a relation with an Equivalence certificate it rewrites inside u ≡ v or u = v
  (`core/logic/EquivalenceRewriting`, also only kernel rules): each function around the match
  needs a congruence law named in the Library (game test `rewriter_uses_certified_equivalences`).
- **Equation Bench** gives the proven "t = t" when both circuits are the same (reflexivity).
- **Instantiator** substitutes several variables simultaneously (so a := b, b := a swaps).
- **Discharge** may discharge an assumption the conclusion never used (still valid).
- **∃ elimination**: the Assumption Chamber opens a proven (possibly stamped) ∃ as the variable
  in its "open as" slot, or as a fresh kernel variable (`k#7`, untypeable) if that slot is
  empty; `closeExists` checks it does not escape. Naming it lets players build witness
  circuits with it (needed for even + even = even).
- **Ex falso** is a kernel rule (`exFalso`), on the Modus Ponens Press: ⊥ in the first slot
  makes the second slot's statement proven. The logic is intuitionistic (no excluded middle).
- **Symmetry/transitivity of =** are derived from reflexivity + Rewriter, not separate rules.
- Theorems record `dependencies` ("axiom:…", "def:…", "conjecture:…"); `isConjectural()` is how
  conjecture taint will cascade.
- **Deciders** are linked in the Drafting Chamber's Link mode from the two proven link theorems
  (variable names may differ from the definition's).
- **Recognition** (Equivalence Bench, `Kernel.sameDesign`, `core/logic/Designs`): two definitions
  whose defining equations are identical up to renaming (their name, inputs, bound variables,
  and the definitions they use, compared recursively) are proven equal (f(x̄) = g(x̄), or both
  implications for properties). This replaces the doc's bisimulation checking, which has no
  machine-as-data counterpart here.
- **Implementations** (Implementation Bench, `Kernel.implement`): a closed, non-conjectural
  proof of ∀x̄. fast(x̄) = slow(x̄) makes the Evaluator compute slow with fast (`Signature
  .implementation`). Refused if fast itself computes slow. Saved (format version 3). This is the
  doc's unary-to-binary upgrade in general form.
- **Binary** (`Library.binaryProven`): a named, fully proven theorem of the shape
  ∀n. n = (h(n) + h(n)) + p(n) ∧ p(n) ≤ 1 (any functions h, p; canonical + and ≤). The
  Duplicator then costs bit count + 1, not value + 1. `BinaryTest` proves it with kernel rules
  (flip/par/half by primitive recursion, flip∘flip∘par = par, par + flip(par) = 1, then induction).
  Binary also switches on `Signature.nativeArithmetic` (kept in sync by `MathState.refreshArithmetic`
  via `Library.onNamed`; not saved, recomputed on load): the Evaluator then computes the canonical
  + and × with BigInteger, charging max bit length + 1 steps for + and the product of bit lengths
  for ×. Prime(997) by trial division drops from > 2·10⁹ steps to within 2·10⁸.
  (An earlier version unlocked this by implementing add, which a renamed copy of add could do.)
- **Starter definitions**: new worlds come with `add`, `mul`, `pow`, `Even`, `le` (≤), `Divides`
  (|), `Mersenne` (∃p. n + 1 = 2^p) and `Prime` defined exactly as in the design doc (definitions only, nothing proven), because the
  opening hour certifies evens before the Drafting Chamber exists, and because recipes ask for
  numerals certified "Even"/"Prime": fixing these names stops players defining a trivial
  "Prime" to get Prime Cores. Players still define deciders (isPrime …) themselves.
- **Energy is the mod's own** system (dynamo → cables → machines), not the NeoForge
  transaction-based EnergyHandler API. 1 energy = 1 axiom use or computation step; coal = 16,000.
  Cables lose 2% per cable on the way to each machine (at most 50%) unless the network is
  certified a tree by a Network Surveyor (`world/NetworkData`, keyed by the network's exact
  shape, so any change voids it). Machines touching the dynamo directly never lose energy.
- **Jobs**: a machine takes its inputs, computes the result, then pays the cost over the job's
  duration before outputting. Heavy computation (Test Bench, Compiled Machine, Prime Forge)
  runs on worker threads (`machine/Async`, `MachineBlockEntity.runAsync`) over copies of the
  definitions (`Signature.copy`, `Deciders.copy`); results, including conjecture refutations,
  are committed on the server thread after re-checking the inputs. The evaluator does about
  40M steps/s. Refused inputs are remembered so the same work is not redone every 4 ticks.
- **Test Bench witness cap** = the numeral's own value (covers Even, ≤-style properties).
- **Bounded ∃** (`core/knowledge/Bounds`): an ∃v whose body has a conjunct x + v = t,
  x × v = t or S…S(v) ≤ t is searched exactly up to t, so "no witness" is FALSE, not unknown.
  Recognised only when +, × and ≤ have the canonical shapes (checked structurally). So the
  starter Even, ≤, | and Prime are decidable by brute force: the Test Bench certifies primes by
  trial division, expensively.
- **Conjecture Bench rule**: a statement may be conjectured only if computation can catch it
  failing on *some* numbers (`Truth.isRefutable`, with bounded ∃ counting as checkable): Prime
  (2n+1) qualifies, "∀n ∃k. k = S(n)" does not. A conjecture's check never uses a decider whose
  link depends on that same conjecture.
- **Speed cards**: a fully proven f(x, y) = f(y, x) (no conjectures, no assumptions) in a
  Compiled Machine's card slot swaps the inputs of a primitive-recursive f so it recurses on the
  smaller one (game tests `add_comm_speed_card`, `conjectured_speed_card_refused`).
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
      laws). `SetMonoid` is a monoid over a set A (a property): three laws, each relativised to A
      (A(e) ∧ closure; associativity in A; identities in A)
- [x] Energy: Coal Dynamo, copper cables, energy per step
- [~] Vanilla crafting recipes: done for the machines that exist (dynamo, cable, Zero Source,
      Successor, Duplicator, Test Bench, Circuit Board)
- [~] Machines done: Zero Source, Successor, Duplicator (also copies statements/circuits), Test
      Bench (numeral mode), Drafting Chamber (assemble / define / name / tablet modes), Variable
      blocks, Equation/Property/Witness Bench, Axiom Tablets, Instantiator, Rewriter, Assumption
      Chamber (assume, ∃-open, discharge), Induction Engine, Conjunction/Modus Ponens Press.
      Game tests prove zero_add, "Double outputs evens" and even + even = even (nested
      chambers, ∃ opened as named variables, pattern rewrites) with the real machines.
      Also done: Conjecture Bench, Graph Lens (also inspects a Compiled Machine), Library block,
      Test Bench range mode (Tested statements work as cards inside their range), Statement
      Conveyor (recipe needs a proven "∀n. 0 + n = n"), Containment Controller (a conjecture
      block sealed in obsidian/tinted glass with a controller within 4 blocks fails harmlessly)
- [~] Compact blocks: Compiled Machine runs any defined function with theorem cards (proven,
      closure, or conjectural with background checks). Not yet: inspect, typed-port enforcement,
      cards slotted into other machines
- [~] Knowledge record: core done (statuses, certificates, conjectures) and saved per world;
      shown in game by the Library block's screen (`client/LibraryScreen`, sent as `network/LibraryPayload`)
- [x] Conjecture blocks with upkeep, background checks (on card use) and the 2n + 1 trap
      (game test `two_n_plus_one_trap`); disasters drop paradox junk; config for block damage
- [x] Circuit Board and Prime Core recipes needing certified numerals (Prime Core matches the
      property named exactly "Prime")
- [~] The Notebook: a written book given on first join (`book/Notebook`, pages in `tools/gen_assets.py`);
      Modonomicon still blocked (its maven host is unreachable from the cloud session)
- [x] Placeholder textures (`tools/gen_assets.py`) and sounds (synthesized, `tools/gen_sounds.sh`);
      own work, so no licence questions

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

## Known minor issues (from a code review, not fixed)

- A Coal Dynamo touching two separate cable networks hashes them as one graph, so a Surveyor
  (which sees one network) can never certify it; with mixed phases it stays shorted.
- A hopper that feeds a Certificate Sorter's rejects back into it loops them (1 unit per pass).
- The Library payload caps lines per tab (1000) but not bytes; NeoForge splits large packets,
  so only enormous libraries could disconnect a player.

## Suggested next steps

1. Compact blocks: inspecting a Compiled Machine's ports, theorem cards slotted into other
   machines (speed cards exist for the Compiled Machine).
2. A worked mod-n example (multiplication mod n as a SetMonoid, ≡ mod n as an equivalence, laws
   proven) would make a good game test and Notebook chapter.
3. More network physics from the design: max-flow /
   min-cut throughput certificates, machine safety certificates (state graphs).
4. Lock hypothesis items inside the Assumption Chamber physically.
5. Modonomicon book and JEI: need `maven.blamejared.com` / `modmaven.dev` reachable.
