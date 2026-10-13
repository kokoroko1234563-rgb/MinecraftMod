# Playtest checklist

A route through the mod for a local playtest, following what the game tests do with the real
machines. Run `./gradlew runClient`, make a creative world (everything is in the Axiomatic
creative tab), or a survival world to check recipes and costs. Machines show what they want on
hover over each slot, and a status line under the slots says what went wrong.

Tips:
- The Notebook (Modonomicon) has a chapter map; each machine's entry shows its recipe. New entries
  appear as you earn the milestones. JEI is also loaded in the development client for recipe lookup.
- `/give @s axiomatic:<id>` works for every block and item.
- The Graph Lens (in the other hand) shows the pointed graph of a numeral, circuit or statement.
- Rename an item in an anvil to give a definition or theorem its name.
- Right-click with a Variable to cycle its letter (n, a, b, c, d, j, k, m, p, q, x, y, z).
- Machines need energy: a Coal Dynamo with coal, touching the machine or joined to it by Copper Cable.

## 1. Opening hour: numbers and certificates

1. Coal Dynamo + coal, then a Zero Source next to it: it makes the numeral 0.
2. Successor: put 0 in, get 1. A hopper loop counts up. A Duplicator copies a numeral (cost = its value).
3. Test Bench (property Even by default): put 12 in, get 12 back with an Even certificate
   (hover the numeral to see it).
4. Four certified evens + copper + redstone craft a Circuit Board (advancement "Even Better").

## 2. First proof: "Double outputs evens"

1. Drafting Chamber, Define mode: a circuit `n + n` renamed "Double" in an anvil → the definition of Double.
   (Circuits are assembled in Assemble mode: Function button picks +, inputs are Variable blocks / numerals.)
2. Property Bench (property Even): the circuit `Double(n)` → the unproven statement Even(Double(n)).
3. Witness Bench: that statement + Variable n (the witness k := n) + Double's definition → proven Even(Double(n)).
4. Compiled Machine: design = circuit Double(n), card = that theorem; feed numerals → outputs are
   certified Even for 1 unit each.

## 3. First induction: zero_add (the design doc's worked example)

1. Drafting Chamber, Define mode, unnamed circuit `add(a, b)` → add's two defining equations.
2. Instantiator: `a + 0 = a`, Variable a, numeral 0 → `0 + 0 = 0` (the base case).
3. Equation Bench: circuit `0 + n` and Variable n → the statement `0 + n = n`.
4. Assumption Chamber: assume it (hypothesis item).
5. Instantiator twice on `a + S(b) = S(a + b)`: a := 0, then b := n.
6. Rewriter: that + the hypothesis → `0 + S(n) = S(n)`.
7. Assumption Chamber slot 2: discharge → `0 + n = n → 0 + S(n) = S(n)`.
8. Induction Engine: base + step + Variable n → `∀n. 0 + n = n` ("Domino Effect").
9. Rename it "zero_add" and put it in the Drafting Chamber in Name mode: it enters the Library.
   Use a Library block to see it, with what it rests on. The Notebook gains pages as you go.

## 4. First trap: 2n + 1 is prime

1. Define Odd(n) := S(n + n) (Drafting Chamber). Prime is a starter definition.
2. Property Bench (property Prime) on `Odd(n)`, rename "oddPrimes", into the Conjecture Bench:
   you get a card and a conjecture block. Place the block and power it.
3. Compiled Machine: design circuit `Odd(n)`, the card in the card slot, numeral 3 → 7, certified
   prime (conjecturally; hover shows it). Then try 4: Odd(4) = 9 is checked in the background, the
   conjecture fails, the block blows up (only paradox junk unless the config allows block damage),
   and everything certified with it is marked refuted. Sealing the block in obsidian / tinted glass
   with a Containment Controller nearby makes the failure harmless.

## 5. Later systems worth a look

- **Prime Core / Prime Forge**: certified primes craft a Prime Core; the Forge needs a method card
  `H(n) → Prime(n)` (a decider link is safe, a conjectured test is a gamble: try 561).
- **Structures**: Structure Bench shows Monoid / SetMonoid / Equivalence / Order axioms; certify an
  operation with its three proven laws, then apply a generic theorem to it.
- **Equivalences**: certify a relation as an Equivalence, name a congruence law (a ≡ b → c ≡ d →
  a + c ≡ b + d), and the Rewriter accepts s ≡ t (see `ModArithmeticTest` for mod 5 done by hand).
- **Networks**: Network Surveyor next to cables. A tree is lossless. Click cables to switch phase
  (copper A / silver B): a proper two-colouring runs the dynamo at double power; a clash shorts it
  (survey from a spot touching the dynamo). A cable loop with no loose ends is Eulerian: a Courier
  Station there delivers its cargo to every machine in turn.
- **Certificate Sorter**: filter slot `Even(n)`; certified numerals drop into a hopper below, the
  rest are pushed into a chest beside it.
- **Speed cards**: a proven `a + b = b + a` in a Compiled Machine's card slot makes `3 + 2000`
  cheap (it recurses on the smaller input).
- **Binary**: name a proven `n = (h(n) + h(n)) + p(n) ∧ p(n) ≤ 1` (see `BinaryTest`) and the
  Duplicator, +, × and divisor searches become digit-priced.
- **Interop**: other mods' generators (FE) and pipes work with every machine.

## What to look for

- Anything confusing in slot hints, button labels or status lines (all text is in
  `tools/gen_assets.py`, regenerate with `python3 tools/gen_assets.py`).
- Energy pacing: is coal (16,000 units) too much or too little? `AxiomaticConfig` has cable loss
  and the FE rate; machine costs are in the machine classes.
- How long proofs take by hand, and whether the Drafting Chamber size feels limiting (an open
  question in the design doc).
