# Playtest checklist

A quick way to try the opening hour in a development client (`./gradlew runClient`). Use a
creative world with cheats for speed, or survival to test recipes. `/give @s axiomatic:<id>`
works for every block and item (see the creative tab "Axiomatic").

## 1. Power and numbers
- Place a Coal Dynamo, a line of Copper Cable, and a Zero Source + Successor touching the cable.
- Put coal in the dynamo. The Zero Source fills with 0s; hopper them into the Successor.
- Check: the energy bar fills, numerals count up, the Graph Lens (other hand) shows S-chains.

## 2. Certificates
- Test Bench (powered): put a numeral in; "Property: Even" certifies evens (gold tooltip).
- Put a statement and a bound numeral in the Test Bench to test it over a range.
- Craft a Circuit Board from four certified evens.

## 3. First definition and proof (Even(Double(n)))
- Variable blocks: right-click in the air to change the letter.
- Drafting Chamber, Assemble: Function ▶ to "add", put Variable n twice → circuit n + n.
  Rename it "Double" in an anvil. Mode ▶ Define → "Double(n) = n + n" (proven).
- Assemble Double(n) (Function ▶ Double, Variable n). Property Bench → "Even(Double(n))".
- Witness Bench: [Even(Double(n)), Variable n, Double(n) = n + n] → proven Even(Double(n)).
- Compiled Machine: design = circuit Double(n), card = that theorem; feed numerals → outputs are
  certified Even for 1 unit each.

## 4. Induction (0 + n = n), as in the design doc
- Drafting Chamber, Define, input an unnamed circuit add(a, b) → add's two equations.
- Instantiator, Equation Bench, Assumption Chamber, Rewriter, Induction Engine as in
  docs/design.md "Worked example". The Rewriter's variables match anything (rw-style).
- Name it: rename the result "zero_add", Drafting Chamber Name mode. Check the Library block.

## 5. The trap
- Define Prime (Property Bench Mode ▶ for ∧ / ¬ / ∃), Odd(n) = S(n + n).
- Conjecture Bench: unproven "Prime(Odd(n))" renamed "oddPrimes" → card + Conjecture Block.
- Place and power the block. Compiled Machine with design Odd(n) and the card: 1, 2, 3 give
  conjecturally certified 3, 5, 7; 4 gives 9 → refuted, the block fails (no block damage by
  default; config `disasterBlockDamage`), items built on it turn to Paradox Junk.

## What to look at
- Machine screens: slot hints on hover, status line, buttons, energy bar, progress.
- Tooltips of numerals (certificates) and statements (formula, proven/unproven, stamps).
- Sounds: certify ding, proof chime, refutation buzz, disaster boom.
