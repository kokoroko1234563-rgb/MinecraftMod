# Axiomatic: A Peano Tech Mod — Design Document

Oct 13, 2026 · @KOKO

## Vision and pillars

Axiomatic (working title) is a single-player Minecraft tech mod where the player does real mathematics. Starting from Peano's axioms, every definition becomes a machine and every proven theorem makes the factory cheaper. The long-term goal is computing and proving ever larger primes.

The player is a mathematician-engineer. Coal, iron and redstone pay for energy; mathematics decides how wisely that energy is spent.

**Five pillars**

1. **Brute force or proof.** Every property can be checked case by case, which costs energy per case, or proven once, which covers every case forever. Early on brute force is fine; later the cases explode (2ⁿ, n!) and proof becomes the only way forward.
2. **Nothing is known until it is established.** A new design has no certified properties. Each one is Unknown, Tested, Proven or Refuted.
3. **Everything is built from the axioms.** Addition, primes, pairs, sets, relations and graphs are all defined by the player on top of Peano's axioms. Nothing mathematical is handed out for free.
4. **Mathematics and industry feed each other.** Industry pays for computation and reasoning; theorems make machines cheaper and unlock better ones.
5. **Conjectures are a gamble.** You may use an unproven claim for a big shortcut. If it is false, it fails when the counterexample arrives, and everything built on it fails with it.

**Scope rule:** the prime path is the spine of the mod. Graphs, sets and relations are in scope where they serve that path or the factory; general abstract algebra beyond monoids (operators with a proven associative law and identity, see Foundations) is not.

## Foundations

The player starts with Peano's axioms and the rules of equality, and defines everything else. Every mathematical object in the mod is one kind of thing: a pointed graph.

**The axioms**

1. 0 is a number.
2. Every number n has a successor S(n).
3. 0 is not the successor of any number.
4. If S(a) = S(b), then a = b.
5. Induction: if P(0) holds, and P(n) implies P(S(n)), then P(n) holds for every n.
6. Equality: reflexivity, symmetry, transitivity and substitution.

Axioms 3 and 4 are crafted as Axiom Tablets, like any other item (see the Machine index). A tablet in a Drafting Chamber supplies its axiom as a proven statement item for 1 unit. Axioms 1 and 2 are built into the Zero Source and Successor, axiom 5 into the Induction Engine, and equality into the Rewriter and Instantiator.

**ℕ is the universe, not an item.** Every variable ranges over ℕ, and untyped ports accept any natural number. The player never holds ℕ; they hold numerals, its elements. Induction does not need every number in hand: the Induction Engine checks two finite statements, a base case and a step, and axiom 5 licenses the conclusion for all of ℕ. Induction is also what makes ℕ exactly the numbers reachable from 0 by Successor, with nothing else in it. Other sets, such as Even, are subsets of ℕ defined by properties.

**Definitions**

Players define new functions by recursion, for example addition: a + 0 = a and a + S(b) = S(a + b). The checker only accepts well-formed recursive definitions, so every defined function is total. A definition compiles into a machine whose behaviour is that definition.

Definitions can have any number of variables. A function of two inputs, such as addition, is an operation; a statement with two free variables, such as a ≤ b defined as ∃k. a + k = b, is a relation. Players define relations the same way they define properties like Even.

Everything computable in the mod is a function. A set or relation becomes one through its decider, a characteristic function that answers 1 or 0. Some properties, such as "this machine always terminates", cannot have a general decider; those are defined and proven, never computed. Machines handle what can be computed, proofs handle the rest, and link theorems join the two.

**The backbone: sets, relations and operators**

These are defined on top of Peano, in this order:

| Concept | Built as | First use in the mod |
| --- | --- | --- |
| Ordered pair | Cantor pairing, using + and ×; injectivity must be proven | Coordinates, two-input data |
| Finite set | Ackermann encoding: k ∈ n when bit k of n is 1 | Item filters, statements about collections |
| Relation | A set of pairs | Divisibility, ≤, congruence mod n |
| Function | A relation with exactly one output per input (proven) | Machines as mathematical objects |
| Operator | A function of two inputs | Every two-input machine (Adder, Multiplier) |
| Equivalence relation | Reflexive, symmetric, transitive relation | Quotients: arithmetic mod n, integers as pairs |
| Order | Reflexive, antisymmetric, transitive relation | Termination proofs, strong induction |

Sets and relations are also used *inside* proofs, for example "the set of divisors of n" in the definition of prime.

**Structures**

A structure is a set, plus operations and relations over it, plus laws proven over that set. It is the checker's central concept: types, closure, monoids, equivalences, orders and port preconditions are all structures or parts of one.

The set decides what works:

- - over the even numbers is closed (even + even is even), but over the odd numbers it is not.
- × over the primes is not closed, since 3 × 5 is not prime: a natural conjecture trap.
- Subtraction over ℕ is only defined when b ≤ a.
- Divides is an order over the positive numbers, but over all of ℕ everything divides 0.

So every certificate names its set. A **closure theorem**, "if a and b are in A, so is a ⊕ b", certifies that an operation stays inside A, and is what lets certificates flow through a factory. Operations on sets build new sets: the product A × B gives two-input domains, and intersection and union combine types, such as Even ∩ Square.

**Monoids.** An operation over a set with a proven associative law and an identity element is a monoid, and gets a monoid certificate. A theorem proven "for any monoid" compiles with a structure port. Plugging in a certified operation applies the theorem to it with no new proof.

The key example is fast powering by repeated squaring, proven once to match repeated application for every monoid:

- applied to addition, it gives fast multiplication;
- applied to multiplication, it gives fast powers;
- applied to multiplication mod n, it gives the fast modular powers the Fermat test needs (prime path stage 5).

Monoids are the only algebraic structure in scope.

**Relation structures.** A relation over a set proven reflexive, symmetric and transitive gets an equivalence certificate; one proven reflexive, antisymmetric and transitive gets an order certificate. Generic theorems take them through a structure port:

- **Rewriting with any equivalence:** with a certified equivalence that respects + and ×, the Rewriter can rewrite with "≡ mod n", not just =. This is how modular arithmetic proofs work, at prime path stage 5.
- **Chaining any order:** a ≤ b and b ≤ c give a ≤ c in one step.
- **Termination from any well-founded order:** a machine whose measure decreases along such an order is certified to halt.

Relations exist in two forms: defined by a formula (like ≤, possibly infinite, used in proofs) or stored as data, a finite set of pairs (like a scanned network's edges, used in computation). A decider and its link theorem connect them, as with properties.

**One data type: pointed graphs**

A pointed graph is a directed, labeled graph with one distinguished node, the point.

- A numeral is a chain of S nodes ending at 0; the point is the outermost S.
- A set is a membership graph; the point is the set itself (Aczel's picture of sets).
- A machine is a circuit; the point is its output, and input ports are ordered.
- A proof is a tree of inference steps; the point is its conclusion.

One format means one checker, one renderer and one save format. In-game, the **Graph Lens** shows the structure of any object.

## Machines and proofs

Logic is handled through machines, not typed text: a term is a circuit, a statement is a claim about circuits, and a proof is a reasoning factory.

**Computation machines (terms)**

- **Zero Source** and **Successor**: the only built-in number machines.
- Every other computation machine is player-defined: Adder, Multiplier, Double, Divides, Prime and so on.
- Machines take numerals in through ordered ports and output a numeral at their point.

**Getting numbers**

1. The Zero Source outputs a Numeral item with value 0 (axiom 1).
2. The Successor takes a numeral and outputs it one higher (axiom 2), for a small energy cost. Making 5 takes five passes, usually a hopper loop; making 1,000 takes 1,000.
3. Running the Successor b times by hand is tedious. A machine that takes a control numeral b and does it for you is exactly addition: a + S(b) = S(a + b). Players build addition because they need it.
4. Numerals with the same value stack. The Graph Lens shows a numeral as its chain of S nodes.
5. Machines consume their input numerals. The Duplicator copies one, at a cost that grows with the numeral's size: its value in the unary age, its number of binary digits once binary is proven. Copying 1,000,000 drops from about a million steps to about 20.

**Typed ports and preconditions**

- A port can require inputs certified in a set, such as Even or IsList. Untyped ports accept any numeral.
- A machine can require a certified relation between its inputs: a Subtractor needs b ≤ a, a Divider needs d ≠ 0.
- An output is certified in a set when the machine has a closure theorem for it, so certificates flow from input to output.
- Compiled blocks keep their port types, so a player's own machines are typed too.

**Reasoning machines (proofs)**

| Machine | Input | Output |
| --- | --- | --- |
| Equation Bench | Two circuits | Statement item "left = right" |
| Property Bench | A circuit and a property (even, prime, terminates) | Statement item "circuit has property" |
| Rewriter | Statement + proven equation | Rewritten statement |
| Instantiator | Proven statement + a variable + a circuit | The statement with the circuit in place of the variable |
| Conjunction Press | "P" + "Q", or "P ∧ Q" | "P ∧ Q", or "P" and "Q" split apart |
| Modus Ponens Press | "P" + "P → Q" | "Q" |
| Induction Engine | Base case P(0) + step P(n) → P(S(n)) | P(n) with n free, meaning it holds for every n |
| Assumption Chamber | An unproven statement H | Stamped hypothesis items; on discharge "H → Q", or "not H" from ⊥ |
| Witness Bench | A statement "there exists k" + a specific k | Proven existence |
| Test Bench | A statement + a range of inputs | "Tested up to n" stamp, or a counterexample |

Every step is checked by a small, sound proof checker. A factory that outputs a statement item has proven it.

**Compact named blocks**

1. **Draft:** build the circuit or proof factory in a bounded Drafting Chamber.
2. **Compile:** the checker verifies it and compresses it into one block.
3. **Name:** the player gives it a name ("add\_comm", "Prime", "Cantor Pair") and picks a colour or icon.
4. **Wire:** the block exposes its ports and works like any machine.
5. **Nest:** compact blocks can contain other compact blocks, building a hierarchy of the player's own concepts.
6. **Inspect:** any compiled block can be opened to show its insides.

A compiled block runs the stored graph directly. It does not simulate hidden machines in a pocket dimension, which keeps the game fast.

**Theorem cards**

A proven theorem can also be installed as an upgrade card. Slot "add\_comm" into an Adder and it may swap its inputs to recurse on the smaller one.

**Recognition**

Pointed graphs are easy to compare. If a player rebuilds a design that already exists, the game recognises it and its theorems apply automatically.

## Proof mechanics

This section settles how players build proofs. Choices marked *(default)* are starting points to confirm with the checker prototype.

**Statements**

A statement is an item, stored as a pointed graph like everything else; its tooltip shows the formula. Statements are built from:

- equations between circuits, made on the Equation Bench (left = right);
- "and" (∧), made on the Conjunction Press, which also splits a proven P ∧ Q back into P and Q;
- "implies" (→), made by the Assumption Chamber;
- "there exists" (∃), made by sealing a variable (below);
- contradiction (⊥), where "not P" means P → ⊥.

There is no separate "for all". A proven statement with a free variable holds for every value of it: proven "0 + n = n" means it holds for all n. Tooltips and the Notebook display such statements with a leading ∀ for readability: "∀n. 0 + n = n". *(default)*

**Variable blocks**

- Variable blocks are cheap named items (a, b, n, k …). Placed in a circuit, one stands for any number. Within one statement, Variable blocks with the same name are the same variable.
- The **Instantiator** takes a proven statement, a variable and a circuit, and outputs the statement with that circuit in place of the variable, for example a := 0. The checker renames variables automatically so nothing gets captured.
- **Sealing:** on the Equation or Property Bench, the player can seal a variable with ∃, turning "n = k + k" into "∃k. n = k + k". A sealed variable can no longer be instantiated.

**Hypotheses: the Assumption Chamber**

- The player inserts an unproven statement H, and the chamber returns it as a hypothesis item stamped with that chamber.
- Hypothesis items, and everything derived from them, carry the stamp and cannot leave the chamber.
- Variables named in H are locked inside the chamber, so the Instantiator refuses them. Without this rule, assuming P(n) could "prove" P(5).
- **Discharge:** putting a derived statement Q into the output slot gives the proven, unstamped statement H → Q. Deriving ⊥ gives "not H".
- **Opening ∃:** if H is "∃k. φ(k)", the chamber can open it into a fresh locked variable k and the hypothesis φ(k). A conclusion that still mentions k cannot be discharged.
- For several hypotheses, chambers nest: a smaller chamber inside a larger one. *(default)*

**Definitions and recursion**

- Compiling a definition also produces its defining equations as proven statement items, for example "a + 0 = a" and "a + S(b) = S(a + b)". These are the starting material for every proof about it.
- Milestone 1 accepts primitive recursion only: the recursive call is on the argument with its outer S removed. Every such definition is automatically total. *(default)*
- General recursion, such as Euclid's algorithm, unlocks at prime path stage 3. It needs a termination certificate: a measure that provably shrinks with each call.

**Properties: definitions, deciders and the link**

Every property, such as even or prime, exists in two forms joined by a theorem:

| Form | Example | Good for |
| --- | --- | --- |
| Definition: a statement with one or more free variables, using ∃ | Even(n): ∃k. n = k + k | Proofs: a witness proves it in one step |
| Decider: a circuit that outputs 1 or 0 | isEven(0) = 1, isEven(S(n)) = flip(isEven(n)), where flip(0) = 1 and flip(S(x)) = 0 | Machines: computing the answer for a given numeral |
| Link theorem | isEven(n) = 1 → Even(n), and the reverse | Lets the decider stand in for the definition |

Before its link is proven, a decider is just a circuit and its output certifies nothing. Afterwards, running it on a numeral certifies the property for that numeral, paying per computation step. Each property's link theorem is a natural milestone. Prime follows the same pattern: Prime(n) means 2 ≤ n and not ∃d. (2 ≤ d ∧ d < n ∧ d divides n), with ≤ and divides defined by ∃, and trial division as the first decider.

Without a decider, the Test Bench searches for a witness up to a cap the player sets, paying per candidate. Finding one proves the property for that single numeral. For a statement with free variables, it checks each value in the range by computation and stamps "Tested up to n".

**Moving statement items**

- **Early game, by hand.** Machines have input and output slots, and the player carries items between them. Proofs are built inside a Drafting Chamber, which records every inference step and compiles the recorded proof into a named theorem block.
- **Automation later.** The Statement Conveyor unlocks after the first induction proof, zero\_add. *(default)* Conveyors let factories apply theorems to streams of items, for example Double → Instantiator with the "Double outputs evens" card → certified even numerals for Circuit Boards. Every step on the line still costs energy, as it does by hand.

**Worked example: 0 + n = n**

The goal P(n) is "0 + n = n". Compiling the Adder produced "a + 0 = a" and "a + S(b) = S(a + b)".

| Step | Machine | Input | Output |
| --- | --- | --- | --- |
| 1 | Instantiator | "a + 0 = a" with a := 0 | "0 + 0 = 0", the base case P(0) |
| 2 | Assumption Chamber | "0 + n = n" | Hypothesis item, n locked |
| 3 | Instantiator, in the chamber | "a + S(b) = S(a + b)" with a := 0, then b := n | "0 + S(n) = S(0 + n)" |
| 4 | Rewriter, in the chamber | Step 3 and the hypothesis | "0 + S(n) = S(n)", stamped |
| 5 | Chamber output slot | Step 4 | "0 + n = n → 0 + S(n) = S(n)", the induction step |
| 6 | Induction Engine | Base case, step, Variable block n | "0 + n = n" for all n |

The Induction Engine checks that the step has exactly the form P(n) → P(S(n)) for the same P and the chosen variable. The player then compiles the proof and names it zero\_add. That is six machine operations, and every opening proof should stay near this size. If playtests show it is tedious, compiled lemma blocks are the relief valve.

The next proof, even + even = even, shows why induction comes early. It uses nested chambers for Even(a) and Even(b), opens both into a = j + j and b = k + k, and rewrites a + b into (j + k) + (j + k). That rewrite needs associativity and commutativity of +, which are induction lemmas like zero\_add. The Witness Bench with witness j + k finishes it.

## Knowledge, energy and conjectures

Every property of every design has a status, proven theorems cost the same to use as axioms, and conjectures are cheap until they fail.

**Knowledge statuses**

| Status | How it is reached | What it gives |
| --- | --- | --- |
| Unknown | Default for any new design | Nothing |
| Tested | Test Bench over a range of cases | A limited guarantee for that range, e.g. "held for n ≤ 64" |
| Proven | A checked proof | A guarantee for every case, inherited by every copy of the design |
| Refuted | A counterexample was found | Permanently false, with the counterexample shown |

Statuses attach to designs, not to individual blocks. One central record stores "this design, this property: its status".

**Numeral certificates**

Statuses describe designs. A single numeral item carries a certificate instead, and recipes check certificates. A certificate for one numeral is always a full proof of that one fact, so there is no "tested" numeral.

| Certificate source | Example | Cost |
| --- | --- | --- |
| Witness search on the Test Bench | Finds k = 6, so 12 is even | Per candidate tried |
| A decider with a proven link theorem | isEven run on 12 | Per computation step |
| A design with a Proven property | Any output of Double is even | 1 unit |
| A design with a Tested property, inside the tested range | Double on inputs n ≤ 64 | 1 unit |
| A theorem card that passes certificates through a machine | Adder with even\_add: even + even gives even | 1 unit |
| A conjecture block | Outputs of "2n + 1 is prime" | 1 unit plus upkeep; marked conjectural |

Recipes accept conjectural certificates. That is the gamble: parts made from them carry the conjecture's risk.

Certificates also cover tuples. A relation between numerals, such as b ≤ a for a Subtractor's inputs, is certified on the pair, by a witness (here k with b + k = a) or by a relation's decider.

**Energy model**

| Thing | Cost to create | Cost to use |
| --- | --- | --- |
| Axiom | Free (given) | 1 unit |
| Proven theorem | Full cost of its proof, paid once at compile | 1 unit, the same as an axiom |
| Conjecture | A small fee at the Conjecture Bench | 1 unit, plus ongoing upkeep and disaster risk |
| Definition (e.g. Multiply) | Cost to compile | Per computation step |

Applying a theorem is cheap; computing is not. A theorem never makes computation free, it replaces a slow method with a faster one. Gauss's sum is the model case: 1 + 2 + … + n takes n additions by brute force, but after proving it equals n(n+1)/2 it takes a theorem application and a few multiplications.

**Conjectures**

- **Creating:** the Conjecture Bench turns an unproven statement item into a named conjecture block, for a small fee.
- **Allowed statements:** only those the game can check on concrete numbers. That means equations between circuits, and properties with a decider or an ∃ bounded by a circuit (such as k ≤ n). Statements with an unbounded ∃ are refused, because no machine could ever catch them failing.
- **Using:** a conjecture block works like a theorem card. Statements it outputs and certificates it grants are marked conjectural.
- **Watching:** whenever a conjectural statement is applied to concrete numerals, the game computes that instance in the background. The computation is paid from the conjecture's ongoing upkeep, "the cost of uncertainty". Upkeep also removes the loophole of conjecturing true statements forever.
- **Unpaid upkeep:** the block goes dormant and stops working. Dormancy is not a disaster.
- **Proving it:** the block upgrades in place, upkeep stops, and every conjectural mark that depends on it becomes proven.
- **Opening:** a conjecture block has no proof inside, so it cannot be opened.

**Disasters**

Whether a conjecture is true is decided by the design itself, never by chance. A disaster happens when a background check finds a failing instance. The conjecture becomes Refuted, and a post-mortem names the counterexample, for example "input 40: output 1681 = 41 × 41".

- **Cascade:** anything built on a refuted conjecture fails with it, and parts crafted from its conjectural certificates turn into paradox junk. Machines show their risk, for example "Depends on 3 unproven conjectures".
- **Scale:** the bigger the shortcut, the bigger the failure. Small: a jam or a batch of worthless paradox junk. Medium: an energy surge that damages neighbours. Large: a raw contradiction (⊥) that transforms nearby blocks until contained.
- **Defences:** Containment Chambers limit the blast radius; Test Benches hunt counterexamples ahead of time; a config option turns off block damage.

**Built-in traps (real false conjectures)**

- 2n + 1 is always prime: fails at n = 4 (9). Used as the first, cheap lesson.
- n² + n + 41 is always prime: holds for n = 0 to 39, fails at n = 40.
- 2^(2ⁿ) + 1 is always prime (Fermat): holds to n = 4, fails at n = 5.
- A number that passes the Fermat test is prime: fooled by Carmichael numbers such as 561.
- "a + b" is a valid pairing: collisions, since 1 + 2 = 2 + 1.

## Progression

The player goes from a Zero block to proving a huge Mersenne prime and crafting its perfect number. Progression is gated by recipes that need numerals with certified properties.

**The hook: certified numerals**

Higher-tier parts need numerals with certificates (see Numeral certificates). The first Circuit Board needs certified even numerals and the first Prime Core needs certified primes; later parts need large proven primes.

**The opening hour**

1. **Spawn with the Notebook only.** There is no starting kit. Everything is crafted from vanilla materials through normal play, and the Notebook's Getting Started chapter shows each recipe.
2. **Survival basics.** Mine copper, iron and redstone; craft a Coal Dynamo and copper cables for power.
3. **First numbers.** Craft a Zero Source and a Successor and make numerals.
4. **Brute force.** Craft a Test Bench and certify numerals as even by witness search. Every search costs energy, more for bigger numerals. Certified evens make the first Circuit Boards.
5. **First definition.** Craft a Drafting Chamber and Variable blocks, build Double (n + n), compile it and name it. Compiling also yields its defining equation, Double(n) = n + n, as a proven statement.
6. **First proof.** Craft a Property Bench and a Witness Bench. State Even(Double(n)), which reads ∃k. Double(n) = k + k, and give the Witness Bench the witness k := n plus the defining equation. Every Double output is now certified even for 1 unit instead of a search.
7. **First induction.** Circuit Boards unlock the Instantiator, Rewriter, Assumption Chamber and Induction Engine. The player proves zero\_add (0 + n = n), then commutativity and associativity of +.
8. **First closure theorem.** Prove even\_add: if a and b are even, so is a + b. As a theorem card in an Adder, it passes certificates through, so adding two certified evens gives a certified even for 1 unit. This teaches that theorems let certificates flow through a factory, which every later automation line relies on.
9. **First trap.** Define multiplication, divides and a trial-division prime decider, then craft a Conjecture Bench. The player notices 2n + 1 gives 3, 5, 7 and conjectures "2n + 1 is always prime" to feed the first Prime Cores cheaply. At n = 4 the background check finds 9 = 3 × 3, and the Prime Cores made from it turn to paradox junk.

**The prime path**

&#91;embedded content: the prime path · 7 theorem stages\]

Each stage pays off at once: the right column is what the player can build after proving that stage.

**Side branches and when they open**

Pairs, sets, relations and graphs are not stages of their own. Each opens once the prime path provides what it needs.

| Branch | Opens at | Needs | Gives |
| --- | --- | --- | --- |
| Ordered pairs | Stage 1 | Cantor pairing from + and ×, and a proof that unpairing works | Two-input data; opens the Notebook's Sets and Relations chapter |
| Relations and orders | Stage 1 | ≤ and divides, defined with ∃ | Divisor sets, orders for termination proofs |
| Finite sets | Stage 2 | Binary, since k ∈ n reads bit k of n | Item filters, statements about collections |
| Monoid certificates | Stage 2 | Associativity and identity proofs for an operator | Fast powering; fast modular powers at stage 5 |
| Graphs | Stage 2, with sets | A graph is a set of vertices plus a set of edge pairs | Network Surveyor and network properties |
| Equivalence relations | Stage 5 | Congruence mod n | Arithmetic mod n |

**The end goal**

1. **Proven primality.** Prove the theorem behind primality certificates (Lucas / Pratt), so the Prime Forge outputs proven primes of any size.
2. **Mersenne primes.** Prove the Lucas–Lehmer test and use it to prove a large 2ᵖ − 1 prime.
3. **Perfect numbers.** By the Euclid–Euler theorem, each Mersenne prime 2ᵖ − 1 gives the perfect number 2ᵖ⁻¹(2ᵖ − 1). The first perfect-number craft is the "beat the game" moment.
4. **Postgame.** Chase a personal record for the largest proven prime.

The late theorems are long proofs from Peano. The theorem tree must break them into stepping-stone lemmas that each unlock something useful, so the climb feels like progress.

## Graphs

Graphs appear in three places: as the physics of the player's networks, as safety certificates for machines, and as the structure of every object (pointed graphs).

**Network physics**

A **Network Surveyor** turns the player's real cable network into a graph object. Proven properties change how the network behaves.

| Proven property | Effect |
| --- | --- |
| Bipartite (two-colourable) | Unlocks a faster alternating power mode; an unproven odd cycle causes a short circuit |
| Proper colouring of transmitters | Interference-free wireless channels |
| Every node has even degree (Euler) | A courier drone serves every cable exactly once |
| Tree (no cycles) | Lossless transfer, since every route is unique |
| Max-flow / min-cut bound | Guaranteed throughput and the exact bottleneck |

**How network properties are proven**

The Surveyor outputs one concrete, finite graph, so most properties need a witness or a decider, not general graph theory:

- **Bipartite or properly coloured:** the player supplies the colouring, and the checker confirms every edge joins different colours.
- **Even degrees and trees:** a decider counts degrees, or checks the graph is connected with no cycles.
- **Throughput:** the player supplies a flow and a cut of equal size. One short lemma, that no flow exceeds any cut, makes this a proof that both are optimal.

General theorems, such as "an odd cycle rules out two colours", are optional lemmas that make checks cheaper or refutations faster. Changing the network voids its certificates until it is checked again.

**Safety certificates**

A machine's states form a directed graph (idle, working, full, jammed). Proving that the jammed or disaster state is unreachable gives a safety certificate. Running without one is a conjecture.

**Pointed-graph mechanics**

- **Sharing:** identical sub-machines merge and compute once. Fast exponentiation is sharing: x⁸ as ((x²)²)² takes 3 multiplications instead of 7. This is on the prime path, since modular powers drive the Fermat test.
- **Equivalence Bench:** the player supplies a bisimulation, a relation that matches the nodes of two machines step for step, and the bench checks it to prove they behave the same. Finding it is hard; checking it is cheap.
- **Cycles:** forbidden in set graphs (the axiom of foundation); allowed in machines as recursion, but each needs a termination certificate.
- **Implementation Bench:** the player supplies a structure-preserving map from a fast binary machine to its slow Peano original, and the bench checks it to prove the fast one faithful. All theorems about the original then transfer, so the unary-to-binary upgrade keeps the whole library.
- **Library graph:** theorems and their dependencies form a directed graph that the checker keeps acyclic, so circular reasoning is impossible.

**Decision:** pointed graphs stay under the hood. Players get the Graph Lens, automatic recognition, the Equivalence Bench and the Implementation Bench. Machines that take other machines as general data are out of scope for now; the data format already allows adding them later.

## The guide book: The Mathematician's Notebook

Every player gets The Mathematician's Notebook on first join; it is the only item the mod hands out. It explains every machine, teaches the mathematics in plain language, and maps the theorem tree. It is built with [Modonomicon](https://moddex.gg/mod/modonomicon), a data-driven guide-book library with an advancement-style map, unlock conditions and multiblock previews.

**Structure: one map per chapter**

| Chapter | Covers | Unlocks when |
| --- | --- | --- |
| Getting Started | Power, crafting the first machines, what certified numerals are | From the start |
| Machines | Every computation and reasoning machine, with screenshots and recipes | Each entry when its machine is first crafted |
| Logic and Proof | Statements, inference rules, induction, the Assumption Chamber, how compiling works | From the start, deeper entries as machines unlock |
| Numbers | Definitions from Peano: +, ×, ≤, divisibility, even, prime, binary | Each entry when its prerequisite is proven |
| Sets and Relations | Pairs, sets, relations, functions, operators, equivalence, orders | After the first pairing proof |
| Graphs | Network Surveyor, network properties, safety certificates | After the Network Surveyor is crafted |
| The Prime Path | The theorem tree from binary to perfect numbers | Stage by stage, as each milestone theorem is proven |
| Risk and Safety | Statuses, conjectures, disasters, containment, the famous traps | After the first conjecture block is made |

**What each entry contains**

- **Plain explanation first,** then the formal statement with symbols (∀, ∃, →, ⊥).
- **A worked example** with small numbers.
- **A multiblock preview** of an example factory where it helps, for example the first proof factory for "Double outputs evens".
- **Three-step hints** for each milestone theorem: a nudge ("try induction on b"), a strategy (which lemmas you need), then an outline of the proof. Hints never build the proof for you.
- **A glossary and symbol reference** at the back.

**Custom unlock conditions**

Modonomicon supports custom condition types. Axiomatic adds two: "theorem proven" and "machine crafted". The book then grows with the player's mathematics instead of spoiling the tree.

**Static book, live Library**

The book is fixed guidance. Player-specific state, such as your named theorems, open conjectures, dependency map and disaster post-mortems, lives in the in-world Library block, and the book links to it.

## Machine index

Every machine and component is crafted from vanilla materials; the only item a player starts with is the Notebook. Recipes are first proposals, to balance in playtesting. Circuit Boards and Prime Cores gate the tiers.

| Machine | What it does | Crafted from (proposed) | Available |
| --- | --- | --- | --- |
| Coal Dynamo | Burns fuel for energy | Furnace, copper ingots, redstone | Opening hour |
| Copper Cable | Carries energy | Copper ingots | Opening hour |
| Zero Source | Outputs the numeral 0 (axiom 1) | Stone, copper ingot, redstone | Opening hour |
| Successor | Adds one S to a numeral (axiom 2) | Copper ingots, redstone repeater | Opening hour |
| Duplicator | Copies a numeral; cost grows with its size (its value before binary, its digit count after) | Copper ingots, redstone, dropper | Opening hour |
| Test Bench | Witness search for one numeral; tests a design over a range | Crafting table, iron ingots, comparator | Opening hour |
| Drafting Chamber | Records circuits and proofs; compiles named blocks | Iron ingots, glass, lectern | Opening hour |
| Variable block | Stands for any number in a circuit | Paper, lapis lazuli (8 per craft) | Opening hour |
| Equation Bench | Makes "left = right" statements | Smooth stone, lapis lazuli, comparator | Opening hour |
| Property Bench | Makes "circuit has property" statements; seals ∃ | Smooth stone, lapis lazuli, observer | Opening hour |
| Witness Bench | Proves ∃ from a witness | Iron ingots, lapis lazuli, observer | Opening hour |
| Axiom Tablet | Supplies axiom 3 or 4 as a statement | Stone slab, ink sac, lapis lazuli | Opening hour |
| Graph Lens | Shows the pointed graph of any object | Spyglass, copper ingot | Opening hour |
| Circuit Board (component) | Gates tier-2 machines | Copper ingots, redstone, certified even numerals | Opening hour |
| Instantiator | Substitutes a circuit for a variable | Circuit Board, lapis lazuli, piston | Tier 2 |
| Rewriter | Rewrites with a proven equation | Circuit Board, lapis lazuli, sticky piston | Tier 2 |
| Assumption Chamber | Hypotheses and discharge | Circuit Board, glass, hopper | Tier 2 |
| Induction Engine | Base case and step give the general statement (axiom 5) | Circuit Boards, lapis block, observers | Tier 2 |
| Conjunction Press | Builds and splits ∧ | Circuit Board, piston, lapis lazuli | Tier 2 |
| Modus Ponens Press | "P" and "P → Q" give "Q" | Circuit Board, piston, lapis lazuli | Tier 2 |
| Conjecture Bench | Makes conjecture blocks | Circuit Board, amethyst shards, lapis lazuli | Tier 2 |
| Library | Lists named theorems, conjectures, dependencies and post-mortems | Bookshelves, lectern, Circuit Board | Tier 2 |
| Containment Chamber | Multiblock that keeps disaster damage inside it | Obsidian, tinted glass, Circuit Board controller | Tier 2 |
| Statement Conveyor | Moves statement items between machines | Iron ingots, lapis lazuli, Circuit Board | After zero\_add |
| Prime Core (component) | Gates prime-tier machines | Gold ingots, redstone, certified primes | After a prime decider |
| Prime Forge | Turns candidates into primes, with the best certificate its method card allows (trial division, Fermat test, certificate) | Prime Core, blast furnace, iron block | Stage 1 |
| Network Surveyor | Scans a cable network into a graph object | Map, compass, Circuit Board | Stage 2 |
| Equivalence Bench | Checks a bisimulation between two machines | Quartz, Circuit Boards, comparator | Stage 2 |
| Implementation Bench | Checks a map from a fast machine to its slow original | Quartz, Circuit Boards, observer | Stage 2 |
| Infinite Prime Well | Emits new proven primes forever; the proof that primes never run out guarantees each search ends | Prime Cores, eye of ender, gold block | Stage 4 |
| Factorization Vault | Stores many item types as one number: each type gets a proven prime, and its count is that prime's exponent | Prime Cores, chests, iron blocks | Stage 4 |
| Perfect Core (component) | Top crafting tier | A proven Mersenne prime, netherite ingot, Prime Cores | Stage 7 |

## Technical stack, assets and libraries

Build on NeoForge for Minecraft 26.1.x with Java 25, use Modonomicon for the book and JEI for recipes, and keep all third-party art and sound CC0 so licensing stays simple.

**Platform**

- **Loader:** NeoForge. NeoForged expects 26.1 to become the next stable modding version, replacing 1.21.1 ([NeoForge 26.1 post](https://neoforged.net/news/26.1release/)). Newer 26.2 and 26.3 builds exist, but the mod targets 26.1.x for stability.
- **Toolchain:** Java 25 and Gradle 9.1 or newer, as required from 26.1. Mojang removed code obfuscation in 26.1, so no extra mappings are needed.
- **Big numbers:** Java's built-in BigInteger. Heavy computations run spread across ticks so the game never lags.

**Libraries**

| Library | Role | License | Why |
| --- | --- | --- | --- |
| [Modonomicon](https://moddex.gg/mod/modonomicon) | Guide book | MIT code, CC-BY-4.0 assets | Supports NeoForge 26.1 to 26.3, has unlock conditions, an advancement-style map and multiblock pages |
| [JEI](https://modrinth.com/mod/jei) | Recipe viewer | MIT | Supports NeoForge 26.1 to 26.3; shows machine recipes and theorem-card uses |

Patchouli, the older standard, was rejected: its listed support stops at 1.21.1 and its CC-BY-NC-SA license is restrictive ([listing](https://gdlauncher.com/mods/modrinth/patchouli)).

**Assets**

| Asset | Use in the mod | License |
| --- | --- | --- |
| [16x16 block sprite sheet by etqws3](https://opengameart.org/node/35346) | Base shapes for machine casings, panels and vents | CC0 |
| [Kenney Interface Sounds](https://opengameart.org/content/interface-sounds), 100 OGG files | Clicks and confirmations in machine screens, the Notebook and the Library | CC0 |
| [Kenney Sci-Fi Sounds](https://opengameart.org/content/sci-fi-sounds), 70 OGG files | Machine running, compile success, disaster explosions | CC0 |
| GNU Unifont (bundled with Minecraft) | Math symbols in tooltips and screens | Ships with the game ([Font page](https://minecraft.wiki/w/Font)) |

**Art direction**

- **Textures are original 16x16 pixel art,** built from the CC0 base shapes and tuned to look like vanilla blocks.
- **One colour per machine family:** copper for computation, blue for reasoning, gold for proven, cracked purple for conjectures, red for refuted.
- **Compact blocks show their status** with a small corner badge, so risk is visible at a glance.
- **Style reference only:** [Pixel Perfection](https://www.modpackindex.com/mod/125940/pixel-perfection-legacy) is CC-BY-SA 4.0, so its textures must not be copied in; share-alike would force that license onto the mod's art.

**Math symbols**

Minecraft falls back to GNU Unifont for characters its main font lacks, so ∀, ∃, → and ⊥ can display in tooltips without extra work. This needs a quick in-game check. If they look poor, a resource-pack bitmap font provider can add cleaner glyphs.

## Milestone 1, scope and open questions

Milestone 1 is the opening hour, playable end to end: from spawning with only the Notebook to the first proof, the first induction and the first trap.

**Milestone 1 tasks**

- [ ] NeoForge project for Minecraft 26.1.x, building a working jar
- [ ] Pointed-graph data format with save and load
- [ ] Proof checker core: Peano axioms, equality, primitive recursive definitions with any number of variables, structures, induction, ∃ by witness, hypotheses and discharge
- [ ] Energy: Coal Dynamo, copper cables, energy cost per computation and inference step
- [ ] Vanilla crafting recipes for every Milestone 1 machine (Machine index, opening hour and tier 2)
- [ ] Machines: Zero Source, Successor, Duplicator, Test Bench, Drafting Chamber, Variable blocks, Equation Bench, Property Bench, Witness Bench, Axiom Tablets, Graph Lens, Instantiator, Rewriter, Assumption Chamber, Induction Engine, Conjunction Press, Conjecture Bench
- [ ] Compact blocks: compile, name and inspect; typed ports, preconditions, and theorem cards that pass certificates through machines
- [ ] Knowledge record: design statuses, and certificates for numerals and tuples
- [ ] Conjecture blocks with upkeep, background checks and the 2n + 1 trap
- [ ] Circuit Board and Prime Core recipes needing certified numerals
- [ ] The Notebook, given on first join, with the Getting Started, Machines, and Logic and Proof chapters
- [ ] Placeholder textures and sounds from the CC0 assets

**Out of scope for now**

- Multiplayer of any kind, including cryptographic locks and leaderboards
- Abstract algebra beyond monoids (groups and further)
- Machines that take other machines as general data
- Real numbers; the world uses ℤ² and ℤ³
- Typed text proofs; all reasoning goes through machines

**Open questions**

- Settled: Minecraft 26.1.x, chosen for stability (no version preference).
- How large can the Drafting Chamber be before proof factories get tedious?
- Exact energy numbers: what 1 unit is worth against a lump of coal.
- Should disasters ever destroy player-built blocks by default, or only with the config option on?
- Fermat's little theorem, Pratt certificates and Lucas–Lehmer are usually proven with group reasoning, which the scope rule excludes. Allow a narrow "order of a number mod n" toolkit, or find proofs that avoid it?

**Sources**

- [NeoForge for Minecraft 26.1](https://neoforged.net/news/26.1release/)
- [Modonomicon on moddex](https://moddex.gg/mod/modonomicon)
- [JEI on Modrinth](https://modrinth.com/mod/jei)
- [Patchouli listing](https://gdlauncher.com/mods/modrinth/patchouli)
- [Rechiseled files (shows NeoForge 26.3 builds)](https://www.curseforge.com/minecraft/mc-mods/rechiseled/files/all)
- [16x16 block sprite sheet, etqws3](https://opengameart.org/node/35346)
- [Kenney Interface Sounds](https://opengameart.org/content/interface-sounds)
- [Kenney Sci-Fi Sounds](https://opengameart.org/content/sci-fi-sounds)
- [Minecraft Wiki: Font](https://minecraft.wiki/w/Font)
- [Pixel Perfection Legacy](https://www.modpackindex.com/mod/125940/pixel-perfection-legacy)
