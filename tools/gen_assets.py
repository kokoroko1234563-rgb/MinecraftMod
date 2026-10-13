#!/usr/bin/env python3
"""Generates placeholder textures, models, blockstates, loot tables, tags and recipes.

Run from the repository root:  python3 tools/gen_assets.py
Re-running overwrites generated files; the language file is written in full too.
Textures are original 16x16 pixel art in the doc's family colours (copper = computation,
blue = reasoning, gold = proven, purple = conjecture, red = refuted), so they are free to use.
Needs Pillow (pip install pillow).
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "axiomatic")
DATA = os.path.join(ROOT, "data", "axiomatic")
NS = "axiomatic"

FAMILIES = {
    "copper": ((193, 106, 58), (128, 62, 30), (236, 160, 110)),
    "blue": ((59, 91, 165), (32, 50, 100), (120, 160, 230)),
    "iron": ((150, 150, 158), (90, 90, 98), (210, 210, 218)),
    "gold": ((222, 177, 45), (150, 110, 20), (255, 230, 120)),
    "purple": ((122, 64, 160), (70, 30, 100), (190, 140, 230)),
}

# 5x7 glyphs, '#' = ink.
GLYPHS = {
    "0": [" ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### "],
    "S": [" ####", "#    ", "#    ", " ### ", "    #", "    #", "#### "],
    "2": [" ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####"],
    "?": [" ### ", "#   #", "    #", "   # ", "  #  ", "     ", "  #  "],
    "~": ["     ", "  #  ", " ### ", "## ##", "#   #", "## ##", " ### "],  # flame
    "E": ["#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####"],
    "D": ["#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### "],
    "=": ["     ", "     ", "#####", "     ", "#####", "     ", "     "],
    "P": ["#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    "],
    "X": ["#####", "    #", "    #", " ####", "    #", "    #", "#####"],  # ∃
    "I": [" ### ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "],
    "R": ["#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #"],
    "A": ["  #  ", " # # ", "#   #", "#   #", "#####", "#   #", "#   #"],
    "N": ["#   #", "##  #", "# # #", "#  ##", "#   #", "#   #", "#   #"],
    "^": ["     ", "  #  ", " # # ", "#   #", "#   #", "     ", "     "],
    ">": ["     ", "#    ", " ##  ", "   ##", " ##  ", "#    ", "     "],
    "f": ["  ## ", " #   ", " #   ", "#### ", " #   ", " #   ", " #   "],
    "M": ["#   #", "## ##", "# # #", "#   #", "#   #", "#   #", "#   #"],
    "!": ["  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "     ", "  #  "],
}


def casing(family, glyph=None):
    base, dark, light = FAMILIES[family]
    img = Image.new("RGBA", (16, 16), base + (255,))
    px = img.load()
    for i in range(16):
        px[i, 0] = light + (255,)
        px[0, i] = light + (255,)
        px[i, 15] = dark + (255,)
        px[15, i] = dark + (255,)
    # Rivets.
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px[x, y] = dark + (255,)
    # Inner panel.
    for x in range(4, 12):
        for y in range(3, 13):
            px[x, y] = (40, 40, 46, 255)
    if glyph:
        rows = GLYPHS[glyph]
        for gy, row in enumerate(rows):
            for gx, c in enumerate(row):
                if c == "#":
                    px[5 + gx, 4 + gy] = light + (255,)
    return img


def cable():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base, dark, light = FAMILIES["copper"]
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = (base if (x + y) % 4 else light) + (255,)
    return img


def numeral():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    # A token: dark disc with an S-chain (three dots and a zero ring).
    for x in range(16):
        for y in range(16):
            d = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if d < 49:
                px[x, y] = (230, 220, 190, 255) if d > 36 else (60, 60, 70, 255)
    for gy, row in enumerate(GLYPHS["0"]):
        for gx, c in enumerate(row):
            if c == "#":
                px[5 + gx, 4 + gy] = (255, 210, 120, 255)
    return img


def circuit_board():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(2, 14):
        for y in range(3, 13):
            px[x, y] = (40, 110, 60, 255)
    for x in range(3, 13):
        px[x, 6] = (210, 150, 70, 255)
        px[x, 10] = (210, 150, 70, 255)
    for y in range(4, 12):
        px[7, y] = (210, 150, 70, 255)
    for x, y in [(4, 5), (10, 5), (5, 9), (11, 9)]:
        px[x, y] = (230, 230, 230, 255)
    return img


NOTEBOOK = [
    "The Mathematician's Notebook\n\nEverything here is built from Peano's axioms. Coal pays for energy; mathematics decides how wisely it is spent.",
    "Power\n\nCraft a Coal Dynamo (copper, furnace, redstone) and Copper Cables (3 copper make 8). Machines touching the dynamo or its cables get energy. One coal gives 16,000 units.",
    "First numbers\n\nThe Zero Source makes 0. The Successor adds one S. A hopper loop counts up. The Duplicator copies a numeral; in the unary age a copy costs its value.",
    "Certificates\n\nNumerals carry certificates. The Test Bench certifies a numeral by witness search: 12 is Even because 12 = 6 + 6. Bigger numbers cost more energy.",
    "Circuit Boards\n\nFour certified even numerals with copper and redstone make a Circuit Board. Boards unlock the reasoning machines.",
    "Circuits\n\nVariable blocks (paper and lapis) stand for any number; use one in the air to change its letter. The Drafting Chamber's Assemble mode builds circuits: pick a function, add inputs.",
    "Definitions\n\nDefine mode: rename a circuit in an anvil, say Double, to define it. For recursion give a base case, a step using the loop sign (the call one step down), and the variable recursed on.",
    "Statements\n\nThe Equation Bench makes left = right; the same circuit twice gives the proven t = t. The Property Bench makes P(circuit), and its Mode button builds and, implies, not and exists.",
    "First proof\n\nEven(Double(n)): state it on the Property Bench, then give the Witness Bench the witness n and Double(n) = n + n. Every Double output can now be certified for free.",
    "Theorem cards\n\nThe Compiled Machine runs a defined function on numerals. A proven statement about it in the card slot certifies each output for 1 unit: no more searching.",
    "Reasoning\n\nInstantiator: put a circuit in for a variable. Rewriter: swap one side of a proven equation for the other; its variables match anything. Conjunction and Modus Ponens Presses: and, and P -> Q with P gives Q.",
    "Assumptions\n\nThe Assumption Chamber assumes a statement and stamps what follows; its variables are locked. Put a conclusion in the discharge slot to get H -> Q.",
    "Opening exists\n\nPut a proven exists, like exists j. a = j + j, in a chamber with a Variable in its open-as slot: you get a = j + j to work with. Close it by discharging a conclusion that does not mention j.",
    "Induction\n\nThe Induction Engine takes P(0), the step P(n) -> P(S(n)) and the variable n, and proves P(n) for every n. Start with 0 + n = n: six machine steps.",
    "The Library\n\nName mode: rename a proven statement in an anvil to file it as a theorem. The Library block lists definitions, theorems, conjectures and tests. The Graph Lens shows any object's graph.",
    "Testing\n\nPut a statement and a bound numeral in the Test Bench: it checks every case up to the bound. Tested statements work as cards inside their range. One counterexample refutes forever.",
    "Deciders\n\nA decider is a circuit answering 1 or 0, like isPrime. Prove both links, f(n) = 1 -> P(n) and P(n) -> f(n) = 1, and link them in the Drafting Chamber: then machines compute P with f.",
    "Conjectures\n\nThe Conjecture Bench turns a named claim into a card and a block. Keep the block powered. Each use is checked: one counterexample and it fails, with everything built on it.",
    "Prime Forge\n\nThe Forge finds the next prime from a candidate, certified by its method card H(n) -> Prime(n). A proven decider link is safe. A conjectured test is fast, until a Carmichael number like 561 comes by.",
    "Structures\n\nThe Structure Bench shows the axioms of monoids, equivalences and orders. Prove a theorem once for any monoid, certify an operation's three laws, and apply the theorem to it for free.",
    "Factories\n\nThe Statement Conveyor moves statements and circuits between machines; it needs a proven 0 + n = n to craft. The Duplicator copies statements for 1 unit. Every step still costs energy.",
    "Safety\n\nAn unpowered conjecture block goes dormant: safe, but its cards stop working. Seal a conjecture block in obsidian or tinted glass near a Containment Controller to keep its failure harmless.",
    "General recursion\n\nEuclid's algorithm calls itself on smaller numbers, not just one step down. Give the Drafting Chamber the body, a measure and a proof that the measure shrinks at each call. Define ifz(t, e, c) by recursion on c to branch.",
    "Traps\n\nFamous false claims: 2n + 1 is always prime. n*n + n + 41 is always prime. a + b is a valid pairing. Test before you trust. A proof is forever.",
]


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# name -> (family, glyph, display name)
MACHINES = {
    "coal_dynamo": ("copper", "~", "Coal Dynamo"),
    "zero_source": ("copper", "0", "Zero Source"),
    "successor": ("copper", "S", "Successor"),
    "duplicator": ("copper", "2", "Duplicator"),
    "test_bench": ("iron", "?", "Test Bench"),
    "drafting_chamber": ("blue", "D", "Drafting Chamber"),
    "equation_bench": ("blue", "=", "Equation Bench"),
    "property_bench": ("blue", "P", "Property Bench"),
    "witness_bench": ("blue", "X", "Witness Bench"),
    "instantiator": ("blue", "I", "Instantiator"),
    "rewriter": ("blue", "R", "Rewriter"),
    "assumption_chamber": ("blue", "A", "Assumption Chamber"),
    "induction_engine": ("blue", "N", "Induction Engine"),
    "conjunction_press": ("blue", "^", "Conjunction Press"),
    "modus_ponens_press": ("blue", ">", "Modus Ponens Press"),
    "compiled_machine": ("gold", "f", "Compiled Machine"),
    "structure_bench": ("blue", "M", "Structure Bench"),
    "prime_forge": ("gold", "P", "Prime Forge"),
    "conjecture_bench": ("purple", "?", "Conjecture Bench"),
    "conjecture_block": ("purple", "!", "Conjecture Block"),
}

def variable():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(3, 13):
        for y in range(2, 14):
            px[x, y] = (235, 230, 215, 255)
    lapis = (40, 70, 170, 255)
    for gy, row in enumerate(["#   #", "## ##", "# # #", "#   #", "#   #"]):
        for gx, c in enumerate(row):
            if c == "#":
                px[5 + gx, 5 + gy] = lapis
    return img


def circuit():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    copper = (210, 130, 70, 255)
    node = (240, 220, 160, 255)
    for x in range(3, 13):
        px[x, 8] = copper
    for y in range(3, 13):
        px[8, y] = copper
    for x, y in [(3, 8), (12, 8), (8, 3), (8, 12), (8, 8)]:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                px[x + dx, y + dy] = node if (dx, dy) != (0, 0) else copper
    return img


def statement():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(3, 13):
        for y in range(2, 14):
            px[x, y] = (245, 235, 200, 255)
    for x in range(3, 13):
        px[x, 2] = (180, 150, 90, 255)
        px[x, 13] = (180, 150, 90, 255)
    for y in (5, 7, 9, 11):
        for x in range(5, 11 if y != 11 else 8):
            px[x, y] = (60, 60, 90, 255)
    return img


def tablet(mark):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(3, 13):
        for y in range(2, 15):
            px[x, y] = (150, 150, 150, 255)
    for x in range(3, 13):
        px[x, 2] = (190, 190, 190, 255)
    for gy, row in enumerate(GLYPHS[mark]):
        for gx, c in enumerate(row):
            if c == "#":
                px[5 + gx, 5 + gy] = (40, 70, 170, 255)
    return img


def prime_core():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d < 7:
                px[x, y] = (255, 215, 80, 255) if d > 4.5 else (200, 60, 40, 255)
    for x, y in [(7, 6), (8, 6), (6, 8), (9, 8), (7, 9), (8, 9)]:
        px[x, y] = (255, 240, 200, 255)
    return img


def paradox_junk():
    import random
    rnd = random.Random(7)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(3, 13):
        for y in range(4, 13):
            if rnd.random() < 0.8:
                px[x, y] = rnd.choice([(122, 64, 160, 255), (70, 30, 100, 255), (200, 60, 60, 255), (40, 40, 40, 255)])
    return img


def lens():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = (x - 6) ** 2 + (y - 6) ** 2
            if 12 < d < 26:
                px[x, y] = (193, 106, 58, 255)
            elif d <= 12:
                px[x, y] = (170, 210, 240, 200)
    for i in range(4):
        px[10 + i, 10 + i] = (110, 70, 40, 255)
        px[11 + i, 10 + i] = (110, 70, 40, 255)
    for x, y in [(4, 5), (7, 5), (6, 8)]:
        px[x, y] = (40, 70, 170, 255)
    return img


def library_texture():
    img = Image.new("RGBA", (16, 16), (110, 80, 50, 255))
    px = img.load()
    colours = [(40, 70, 170), (222, 177, 45), (122, 64, 160), (193, 106, 58), (60, 120, 60)]
    for shelf in (1, 9):
        for x in range(1, 15):
            c = colours[(x + shelf) % len(colours)]
            for y in range(shelf, shelf + 6):
                px[x, y] = c + (255,)
        for x in range(16):
            px[x, shelf + 6] = (80, 55, 30, 255)
    return img


ITEMS = {
    "numeral": (numeral, "Numeral"),
    "circuit_board": (circuit_board, "Circuit Board"),
    "variable": (variable, "Variable"),
    "circuit": (circuit, "Circuit"),
    "statement": (statement, "Statement"),
    "axiom_tablet_succ_not_zero": (lambda: tablet("0"), "Axiom Tablet: S(x) ≠ 0"),
    "axiom_tablet_succ_injective": (lambda: tablet("S"), "Axiom Tablet: S is injective"),
    "prime_core": (prime_core, "Prime Core"),
    "paradox_junk": (paradox_junk, "Paradox Junk"),
    "graph_lens": (lambda: lens(), "Graph Lens"),
}

LANG = {
    "itemGroup.axiomatic": "Axiomatic",
    "block.axiomatic.copper_cable": "Copper Cable",
    "item.axiomatic.numeral.value": "Numeral %s",
    "tooltip.axiomatic.certified": "Certified: %s",
    "tooltip.axiomatic.conjectural": " (conjectural)",
    "tooltip.axiomatic.energy": "Energy: %s / %s",
    "button.axiomatic.next_property": "Property ▶",
    "label.axiomatic.property": "Property: %s",
    "slot.axiomatic.fuel": "Fuel",
    "status.axiomatic.no_energy": "Waiting for energy",
    "status.axiomatic.output_full": "Output full",
    "status.axiomatic.working": "Working",
    "status.axiomatic.burning": "Burning",
    "status.axiomatic.no_fuel": "No fuel",
    "status.axiomatic.cost": "Cost: %s",
    "status.axiomatic.property": "Testing for %s",
    "status.axiomatic.unknown_property": "Unknown property %s",
    "status.axiomatic.too_big": "Too big to search",
    "status.axiomatic.certified": "%s(%s) certified, %s steps",
    "status.axiomatic.not_certified": "No witness: %s(%s) not certified (%s steps)",
    "button.axiomatic.copy": "Copy",
    "button.axiomatic.direction": "Direction",
    "button.axiomatic.function": "Function ▶",
    "button.axiomatic.mode": "Mode ▶",
    "button.axiomatic.occurrence": "Occurrence",
    "button.axiomatic.release": "Release",
    "item.axiomatic.statement.proven": "Theorem",
    "item.axiomatic.statement.stamped": "Stamped Statement",
    "item.axiomatic.statement.unproven": "Statement",
    "item.axiomatic.variable.named": "Variable %s",
    "label.axiomatic.all": "all",
    "label.axiomatic.assemble": "%s: %s",
    "label.axiomatic.assuming": "Assuming %s",
    "label.axiomatic.chamber_empty": "Nothing assumed",
    "label.axiomatic.mode.assemble": "Assemble",
    "label.axiomatic.mode.define": "Define",
    "label.axiomatic.mode.name": "Name theorem",
    "label.axiomatic.mode.tablet": "Axiom tablet",
    "label.axiomatic.mode.link": "Link a decider",
    "label.axiomatic.mode.recurse": "General recursion",
    "status.axiomatic.linked": "%s is now decided by %s",
    "label.axiomatic.rewrite_ltr": "Left → right, %s",
    "label.axiomatic.rewrite_rtl": "Right → left, %s",
    "label.axiomatic.logic_mode.seal": "Seal with ∃ (statement + variable)",
    "label.axiomatic.logic_mode.and": "And: P ∧ Q",
    "label.axiomatic.logic_mode.implies": "Implies: P → Q",
    "label.axiomatic.logic_mode.not": "Not: ¬P",
    "slot.axiomatic.assumption": "Statement to assume",
    "slot.axiomatic.base": "Base case P(0)",
    "slot.axiomatic.circuit": "Circuit",
    "slot.axiomatic.circuit_or_statement": "Circuit, or statement P",
    "slot.axiomatic.discharge": "Conclusion to discharge",
    "slot.axiomatic.drafting_1": "Input 1 (rename in an anvil to define or name)",
    "slot.axiomatic.drafting_2": "Input 2 (step case, or measure)",
    "slot.axiomatic.drafting_3": "Input 3 (variable recursed on, or termination proof)",
    "slot.axiomatic.equation": "Proven equation",
    "slot.axiomatic.implication": "P → Q",
    "slot.axiomatic.instance": "Proof of the instance",
    "slot.axiomatic.left": "Left",
    "slot.axiomatic.premise": "P",
    "slot.axiomatic.right": "Right",
    "slot.axiomatic.second_circuit": "Second circuit (relations), or statement Q",
    "slot.axiomatic.statement": "Proven statement",
    "slot.axiomatic.step": "Step P(n) → P(S(n))",
    "slot.axiomatic.target": "Statement to prove",
    "slot.axiomatic.variable": "Variable",
    "slot.axiomatic.variable_to_seal": "Variable to seal",
    "slot.axiomatic.witness": "Witness circuit",
    "status.axiomatic.applied": "Applied",
    "status.axiomatic.arity": "%s takes %s inputs",
    "status.axiomatic.bad_name": "Not a valid name: %s",
    "status.axiomatic.bad_recursive_call": "↺ must be called as ↺(x, …, y) on plain variables, ending with the one recursed on",
    "status.axiomatic.chamber_busy": "Already assuming something: Release first",
    "status.axiomatic.define_unproven": "Define properties from unproven statements",
    "status.axiomatic.needs_and": "Needs a statement P ∧ Q to split",
    "status.axiomatic.needs_circuit": "Slot %s needs a circuit, variable or small numeral",
    "status.axiomatic.needs_exists": "Needs a statement ∃k. … or a property defined by one",
    "status.axiomatic.needs_name": "Rename the first input in an anvil to name it",
    "status.axiomatic.needs_proven": "Slot %s needs a proven statement",
    "status.axiomatic.needs_statement": "Slot %s needs a statement",
    "status.axiomatic.needs_tablet": "Needs an Axiom Tablet",
    "status.axiomatic.needs_variable": "Slot %s needs a Variable",
    "status.axiomatic.nothing_assumed": "Nothing is assumed yet",
    "status.axiomatic.released": "Released",
    "status.axiomatic.unknown_function": "Unknown function %s",
    "tooltip.axiomatic.assuming": "Assuming %s (stamp %s)",
    "tooltip.axiomatic.axiom.succ_not_zero": "Axiom 3: ∀x. ¬(S(x) = 0)",
    "tooltip.axiomatic.axiom.succ_injective": "Axiom 4: ∀x y. S(x) = S(y) → x = y",
    "tooltip.axiomatic.proven": "Proven (%s steps)",
    "tooltip.axiomatic.proven_conjectural": "Proven from conjectures",
    "tooltip.axiomatic.unproven": "Unproven",
    "tooltip.axiomatic.depends": "Depends on %s unproven conjecture(s)",
    "item.axiomatic.conjecture_block.named": "Conjecture: %s",
    "label.axiomatic.conjecture": "Conjecture %s",
    "label.axiomatic.no_design": "Needs a design f(…)",
    "label.axiomatic.computes": "Computes %s",
    "slot.axiomatic.design": "Design: a circuit f(…)",
    "slot.axiomatic.port_1": "Input 1",
    "slot.axiomatic.port_2": "Input 2",
    "slot.axiomatic.port_3": "Input 3",
    "slot.axiomatic.card": "Theorem card",
    "slot.axiomatic.conjecture_statement": "Unproven statement, renamed in an anvil",
    "status.axiomatic.too_many_ports": "%s has more than 3 inputs",
    "status.axiomatic.computed": "Computed %s (%s steps)",
    "status.axiomatic.card_unproven": "The card must be a proven statement with no open assumptions",
    "status.axiomatic.card_refuted": "Built on refuted conjecture: %s",
    "status.axiomatic.card_dormant": "Conjecture %s is dormant: power its block",
    "status.axiomatic.card_mismatch": "The card must conclude a property of %s(…) of plain variables",
    "status.axiomatic.refuted_now": "Counterexample found! Conjecture %s is refuted",
    "status.axiomatic.conjecture_needs_unproven": "Needs an unproven statement",
    "status.axiomatic.conjecture_proven": "Proven: no more upkeep",
    "status.axiomatic.conjecture_active": "Active, paying upkeep",
    "status.axiomatic.conjecture_dormant": "Dormant: no energy for upkeep",
    "message.axiomatic.disaster": "Disaster! Conjecture %s is refuted by the counterexample %s",
    "slot.axiomatic.test_subject": "Numeral to certify, or statement to test",
    "slot.axiomatic.test_bound": "Bound numeral (range mode)",
    "status.axiomatic.needs_bound": "Range mode needs a bound numeral in the third slot",
    "status.axiomatic.too_many_cases": "Too many cases (%s): lower the bound",
    "status.axiomatic.not_computable": "This statement cannot be computed",
    "status.axiomatic.tested": "Tested up to %s (%s cases)",
    "status.axiomatic.counterexample": "Refuted! Counterexample %s",
    "status.axiomatic.inconclusive": "Inconclusive at %s",
    "status.axiomatic.already_proven": "Already proven",
    "status.axiomatic.outside_tested": "Inputs above the tested range (%s)",
    "message.axiomatic.lens_nothing": "Hold a numeral, circuit or statement in your other hand",
    "message.axiomatic.lens_header": "Pointed graph: %s nodes (● = the point)",
    "message.axiomatic.lens_more": "… and %s more nodes",
    "library.axiomatic.header": "=== Library ===",
    "library.axiomatic.definitions": "Definitions",
    "library.axiomatic.kind.property": "  property",
    "library.axiomatic.kind.recursive": "  by recursion",
    "library.axiomatic.kind.explicit": "  explicit",
    "library.axiomatic.theorems": "Named theorems",
    "library.axiomatic.uses": "  (%s dependencies, %s steps)",
    "library.axiomatic.conjectures": "Conjectures",
    "library.axiomatic.state.active": "[active]",
    "library.axiomatic.state.dormant": "[dormant]",
    "library.axiomatic.state.proven": "[proven]",
    "library.axiomatic.state.refuted": "[REFUTED]",
    "library.axiomatic.postmortem": " counterexample: %s",
    "library.axiomatic.statuses": "Statuses",
    "library.axiomatic.tested": "Tested up to %s",
    "library.axiomatic.proven": "Proven",
    "library.axiomatic.refuted": "Refuted at %s",
    "library.axiomatic.unknown": "Unknown",
    "block.axiomatic.library": "Library",
    "button.axiomatic.structure": "Structure ▶",
    "button.axiomatic.axiom": "Axiom ▶",
    "label.axiomatic.structure_mode": "%s: %s",
    "label.axiomatic.structure_mode.axioms": "Axioms",
    "label.axiomatic.structure_mode.certify": "Certify",
    "label.axiomatic.structure_mode.apply": "Apply",
    "slot.axiomatic.structure_1": "Law 1, or a generic theorem (Apply)",
    "slot.axiomatic.structure_2": "Law 2, or a circuit naming the operation (Apply)",
    "slot.axiomatic.structure_3": "Law 3",
    "status.axiomatic.certified_structure": "%s certified: %s",
    "status.axiomatic.needs_design": "Needs a circuit such as add(a, b)",
    "status.axiomatic.not_certified_structure": "%s is not certified as a %s",
    "message.axiomatic.lens_inside": "Inside %s:",
    "library.axiomatic.certificates": "Structure certificates",
    "block.axiomatic.statement_conveyor": "Statement Conveyor",
    "block.axiomatic.containment_controller": "Containment Controller",
    "slot.axiomatic.open_as": "Variable to open an ∃ as (optional)",
    "slot.axiomatic.candidate": "Candidate numeral",
    "slot.axiomatic.method_card": "Method card: H(n) → Prime(n)",
    "status.axiomatic.forge_none": "No prime found in %s candidates",
    "status.axiomatic.forged": "Forged %s (%s steps)",
    "status.axiomatic.method_shape": "The method card must read H(n) → P(n), with one variable",
    "status.axiomatic.computing": "Computing…",
    "status.axiomatic.compute_failed": "The computation failed",
    "subtitles.axiomatic.applied": "Reasoning machine clicks",
    "subtitles.axiomatic.certified": "Numeral certified",
    "subtitles.axiomatic.proven": "Theorem proven",
    "subtitles.axiomatic.refuted": "Counterexample found",
    "subtitles.axiomatic.disaster": "Conjecture fails",
    "subtitles.axiomatic.working": "Machine hums",
    "message.axiomatic.junk": "%s was built on a refuted conjecture and turned to paradox junk",
}


def main():
    lang = dict(LANG)
    for i, page in enumerate(NOTEBOOK, start=1):
        lang[f"notebook.axiomatic.page.{i}"] = page
    pickaxe = []
    for name, (family, glyph, display) in MACHINES.items():
        save(casing(family, glyph), os.path.join(ASSETS, "textures", "block", name + ".png"))
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"),
                   {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"),
                   {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
        lang[f"block.{NS}.{name}"] = display
        pickaxe.append(f"{NS}:{name}")

    # Library: a plain block with a bookshelf-like texture.
    save(library_texture(), os.path.join(ASSETS, "textures", "block", "library.png"))
    write_json(os.path.join(ASSETS, "models", "block", "library.json"),
               {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/library"}})
    write_json(os.path.join(ASSETS, "blockstates", "library.json"), {"variants": {"": {"model": f"{NS}:block/library"}}})
    write_json(os.path.join(ASSETS, "items", "library.json"),
               {"model": {"type": "minecraft:model", "model": f"{NS}:block/library"}})
    write_json(os.path.join(DATA, "loot_table", "blocks", "library.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:library"}]}],
        "random_sequence": f"{NS}:blocks/library"})
    write_json(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "axe.json"),
               {"replace": False, "values": [f"{NS}:library"]})

    # Statement Conveyor: directional; the front face shows an arrow.
    save(casing("blue", ">"), os.path.join(ASSETS, "textures", "block", "statement_conveyor_front.png"))
    save(casing("blue"), os.path.join(ASSETS, "textures", "block", "statement_conveyor_side.png"))
    front, side = f"{NS}:block/statement_conveyor_front", f"{NS}:block/statement_conveyor_side"
    write_json(os.path.join(ASSETS, "models", "block", "statement_conveyor.json"), {
        "parent": "minecraft:block/orientable", "textures": {"front": front, "side": side, "top": side}})
    write_json(os.path.join(ASSETS, "models", "block", "statement_conveyor_vertical.json"), {
        "parent": "minecraft:block/orientable_vertical", "textures": {"front": front, "side": side}})
    conveyor, vertical = f"{NS}:block/statement_conveyor", f"{NS}:block/statement_conveyor_vertical"
    write_json(os.path.join(ASSETS, "blockstates", "statement_conveyor.json"), {"variants": {
        "facing=north": {"model": conveyor}, "facing=east": {"model": conveyor, "y": 90},
        "facing=south": {"model": conveyor, "y": 180}, "facing=west": {"model": conveyor, "y": 270},
        "facing=up": {"model": vertical}, "facing=down": {"model": vertical, "x": 180}}})
    write_json(os.path.join(ASSETS, "items", "statement_conveyor.json"),
               {"model": {"type": "minecraft:model", "model": f"{NS}:block/statement_conveyor"}})
    pickaxe.append(f"{NS}:statement_conveyor")

    # Containment Controller: obsidian-dark casing.
    img = casing("purple", "!")
    px = img.load()
    for x in range(16):
        for y in range(16):
            r, g, b, a = px[x, y]
            px[x, y] = (r // 3, g // 3, b // 2, a)
    save(img, os.path.join(ASSETS, "textures", "block", "containment_controller.png"))
    write_json(os.path.join(ASSETS, "models", "block", "containment_controller.json"),
               {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/containment_controller"}})
    write_json(os.path.join(ASSETS, "blockstates", "containment_controller.json"),
               {"variants": {"": {"model": f"{NS}:block/containment_controller"}}})
    write_json(os.path.join(ASSETS, "items", "containment_controller.json"),
               {"model": {"type": "minecraft:model", "model": f"{NS}:block/containment_controller"}})
    pickaxe.append(f"{NS}:containment_controller")

    # Cable: a thin cube in the middle of the block.
    save(cable(), os.path.join(ASSETS, "textures", "block", "copper_cable.png"))
    tex = f"{NS}:block/copper_cable"
    faces = {d: {"texture": "#all"} for d in ["north", "south", "east", "west", "up", "down"]}
    write_json(os.path.join(ASSETS, "models", "block", "copper_cable.json"), {
        "parent": "minecraft:block/block",
        "textures": {"all": tex, "particle": tex},
        "elements": [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": faces}],
    })
    write_json(os.path.join(ASSETS, "blockstates", "copper_cable.json"),
               {"variants": {"": {"model": f"{NS}:block/copper_cable"}}})
    write_json(os.path.join(ASSETS, "items", "copper_cable.json"),
               {"model": {"type": "minecraft:model", "model": f"{NS}:block/copper_cable"}})
    pickaxe.append(f"{NS}:copper_cable")

    for name, (painter, display) in ITEMS.items():
        save(painter(), os.path.join(ASSETS, "textures", "item", name + ".png"))
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        lang.setdefault(f"item.{NS}.{name}", display)

    write_json(os.path.join(ASSETS, "lang", "en_us.json"), dict(sorted(lang.items())))

    for block in pickaxe:
        name = block.split(":")[1]
        entry = {"type": "minecraft:item", "name": block}
        if name == "conjecture_block":
            entry["functions"] = [{"function": "minecraft:copy_components", "source": "block_entity",
                                   "include": [f"{NS}:conjecture", "minecraft:custom_name"]}]
        write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0,
                       "conditions": [{"condition": "minecraft:survives_explosion"}],
                       "entries": [entry]}],
            "random_sequence": f"{NS}:blocks/{name}",
        })
    write_json(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"),
               {"replace": False, "values": pickaxe})

    def shaped(name, pattern, key, count=1, category="redstone"):
        write_json(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": category,
            "key": key,
            "pattern": pattern,
            "result": {"id": f"{NS}:{name}", "count": count},
        })

    shaped("coal_dynamo", ["CCC", "CFC", "CRC"],
           {"C": "minecraft:copper_ingot", "F": "minecraft:furnace", "R": "minecraft:redstone"})
    shaped("copper_cable", ["CCC"], {"C": "minecraft:copper_ingot"}, count=8)
    shaped("zero_source", ["SSS", "SCS", "SRS"],
           {"S": "minecraft:stone", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"})
    shaped("successor", ["C C", "CRC", "C C"], {"C": "minecraft:copper_ingot", "R": "minecraft:repeater"})
    shaped("duplicator", ["CCC", "RDR", "CCC"],
           {"C": "minecraft:copper_ingot", "R": "minecraft:redstone", "D": "minecraft:dropper"})
    shaped("test_bench", ["III", "IKI", "ICI"],
           {"I": "minecraft:iron_ingot", "K": "minecraft:crafting_table", "C": "minecraft:comparator"})
    shaped("circuit_board", ["CEC", "ERE", "CEC"], {
        "C": "minecraft:copper_ingot",
        "R": "minecraft:redstone",
        "E": {"neoforge:ingredient_type": f"{NS}:certified_numeral", "property": "Even"},
    }, category="misc")
    board = "axiomatic:circuit_board"
    shaped("drafting_chamber", ["IGI", "GLG", "IGI"],
           {"I": "minecraft:iron_ingot", "G": "minecraft:glass", "L": "minecraft:lectern"})
    shaped("variable", ["PPP", "PLP", "PPP"], {"P": "minecraft:paper", "L": "minecraft:lapis_lazuli"},
           count=8, category="misc")
    shaped("equation_bench", ["SLS", "LCL", "SSS"],
           {"S": "minecraft:smooth_stone", "L": "minecraft:lapis_lazuli", "C": "minecraft:comparator"})
    shaped("property_bench", ["SLS", "LOL", "SSS"],
           {"S": "minecraft:smooth_stone", "L": "minecraft:lapis_lazuli", "O": "minecraft:observer"})
    shaped("witness_bench", ["ILI", "LOL", "III"],
           {"I": "minecraft:iron_ingot", "L": "minecraft:lapis_lazuli", "O": "minecraft:observer"})
    shaped("axiom_tablet_succ_not_zero", ["L", "I", "S"],
           {"L": "minecraft:lapis_lazuli", "I": "minecraft:ink_sac", "S": "minecraft:stone_slab"}, category="misc")
    shaped("axiom_tablet_succ_injective", ["I", "L", "S"],
           {"L": "minecraft:lapis_lazuli", "I": "minecraft:ink_sac", "S": "minecraft:stone_slab"}, category="misc")
    shaped("instantiator", [" B ", "LPL", " L "], {"B": board, "L": "minecraft:lapis_lazuli", "P": "minecraft:piston"})
    shaped("rewriter", [" B ", "LPL", " L "], {"B": board, "L": "minecraft:lapis_lazuli", "P": "minecraft:sticky_piston"})
    shaped("assumption_chamber", ["GBG", "GHG", "GGG"], {"B": board, "G": "minecraft:glass", "H": "minecraft:hopper"})
    shaped("induction_engine", ["BOB", "OKO", "BOB"],
           {"B": board, "O": "minecraft:observer", "K": "minecraft:lapis_block"})
    shaped("conjunction_press", ["PBP", "LLL"], {"B": board, "P": "minecraft:piston", "L": "minecraft:lapis_lazuli"})
    shaped("modus_ponens_press", ["LBL", "PPP"], {"B": board, "P": "minecraft:piston", "L": "minecraft:lapis_lazuli"})
    shaped("library", ["BBB", "LCL", "BBB"],
           {"B": "minecraft:bookshelf", "L": "minecraft:lectern", "C": board})
    shaped("graph_lens", ["S", "C"], {"S": "minecraft:spyglass", "C": "minecraft:copper_ingot"}, category="misc")
    shaped("conjecture_bench", ["AAA", "LBL", "AAA"],
           {"A": "minecraft:amethyst_shard", "L": "minecraft:lapis_lazuli", "B": board})
    shaped("statement_conveyor", ["IZI", "LBL", "III"], {
        "I": "minecraft:iron_ingot", "L": "minecraft:lapis_lazuli", "B": board,
        "Z": {"neoforge:ingredient_type": f"{NS}:proven_statement", "statement": "∀n. 0 + n = n"},
    })
    shaped("containment_controller", ["OTO", "TBT", "OTO"],
           {"O": "minecraft:obsidian", "T": "minecraft:tinted_glass", "B": board})
    shaped("structure_bench", ["QBQ", "BKB", "QBQ"],
           {"Q": "minecraft:quartz", "B": board, "K": "minecraft:lapis_block"})
    shaped("prime_forge", ["KCK", "PFP", "KKK"],
           {"K": "minecraft:iron_block", "C": "axiomatic:prime_core", "P": "minecraft:gold_ingot",
            "F": "minecraft:blast_furnace"})
    shaped("compiled_machine", ["CBC", "BRB", "CBC"],
           {"C": "minecraft:copper_ingot", "B": board, "R": "minecraft:redstone_block"})
    shaped("prime_core", ["GPG", "PRP", "GPG"], {
        "G": "minecraft:gold_ingot",
        "R": "minecraft:redstone",
        "P": {"neoforge:ingredient_type": f"{NS}:certified_numeral", "property": "Prime"},
    }, category="misc")


def nbt_structure(path, size, data_version=4790):
    """Writes an all-air structure template, used as the stage for game tests."""
    import gzip
    import struct

    def name(n):
        b = n.encode()
        return struct.pack(">H", len(b)) + b

    def tag_int(n, v):
        return b"\x03" + name(n) + struct.pack(">i", v)

    def tag_string(n, v):
        b = v.encode()
        return b"\x08" + name(n) + struct.pack(">H", len(b)) + b

    def tag_list(n, elem_type, payloads):
        return b"\x09" + name(n) + bytes([elem_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)

    def int_list_payload(values):
        return b"\x03" + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)

    blocks = []
    for x in range(size[0]):
        for y in range(size[1]):
            for z in range(size[2]):
                # compound payload: pos (list of int), state (int), end
                pos = b"\x09" + name("pos") + int_list_payload([x, y, z])
                blocks.append(pos + tag_int("state", 0) + b"\x00")
    palette = [tag_string("Name", "minecraft:air") + b"\x00"]
    root = (b"\x0a" + name("")
            + tag_int("DataVersion", data_version)
            + b"\x09" + name("size") + int_list_payload(list(size))
            + tag_list("blocks", 10, blocks)
            + tag_list("entities", 10, [])
            + tag_list("palette", 10, palette)
            + b"\x00")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, "wb") as f:
        f.write(root)


if __name__ == "__main__":
    nbt_structure(os.path.join(DATA, "structure", "empty_stage.nbt"), (7, 4, 7))
    main()
