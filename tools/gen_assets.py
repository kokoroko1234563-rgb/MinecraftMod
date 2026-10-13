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
    "Y": ["#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  ", "  #  "],  # a fork
    "X": ["#####", "    #", "    #", " ####", "    #", "    #", "#####"],  # ∃
    "I": [" ### ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "],
    "R": ["#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #"],
    "A": ["  #  ", " # # ", "#   #", "#   #", "#####", "#   #", "#   #"],
    "N": ["#   #", "##  #", "# # #", "#  ##", "#   #", "#   #", "#   #"],
    "^": ["     ", "  #  ", " # # ", "#   #", "#   #", "     ", "     "],
    ">": ["     ", "#    ", " ##  ", "   ##", " ##  ", "#    ", "     "],
    "W": ["#   #", "#   #", "#   #", "# # #", "# # #", "## ##", "#   #"],
    "V": ["#   #", "#   #", "#   #", "#   #", " # # ", " # # ", "  #  "],
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


def cable(phase_b=False):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base, dark, light = FAMILIES["copper"]
    if phase_b:
        # Phase B: tinned copper, silver with copper stripes.
        base, light = (178, 186, 192), base
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




# The advancement tree: (id, parent, icon, criterion, title, description, frame).
# A criterion is ("item", item id) for inventory_changed or ("milestone", name) for machines.
ADVANCEMENTS = [
    ("power", "root", "axiomatic:coal_dynamo", ("item", "axiomatic:coal_dynamo"),
     "Let There Be Power", "Craft a Coal Dynamo", "task"),
    ("zero", "power", "axiomatic:zero_source", ("milestone", "numeral"),
     "Zero", "Make the number 0 in a Zero Source", "task"),
    ("certified", "zero", "axiomatic:test_bench", ("milestone", "certified"),
     "Twelve Is Even", "Certify a numeral on the Test Bench", "task"),
    ("circuit_board", "certified", "axiomatic:circuit_board", ("item", "axiomatic:circuit_board"),
     "Even Better", "Craft a Circuit Board from certified evens", "task"),
    ("defined", "zero", "axiomatic:drafting_chamber", ("milestone", "defined"),
     "By Definition", "Compile your first definition", "task"),
    ("proven", "defined", "axiomatic:witness_bench", ("milestone", "proven"),
     "Q.E.D.", "Prove a statement outright", "task"),
    ("induction", "proven", "axiomatic:induction_engine", ("milestone", "induction"),
     "Domino Effect", "Prove a statement for every n by induction", "goal"),
    ("named", "induction", "axiomatic:library", ("milestone", "named"),
     "A Library of One", "Name a theorem in the Drafting Chamber", "task"),
    ("conjecture", "proven", "axiomatic:conjecture_bench", ("milestone", "conjecture"),
     "A Calculated Risk", "Make a conjecture", "task"),
    ("disaster", "conjecture", "axiomatic:paradox_junk", ("milestone", "disaster"),
     "Counterexample", "Watch a conjecture fail", "challenge"),
    ("prime_core", "circuit_board", "axiomatic:prime_core", ("item", "axiomatic:prime_core"),
     "Prime Time", "Craft a Prime Core from certified primes", "goal"),
    ("prime_forged", "prime_core", "axiomatic:prime_forge", ("milestone", "prime_forged"),
     "Forging Primes", "Forge a prime with a method card", "task"),
    ("prime_well", "prime_forged", "axiomatic:infinite_prime_well", ("item", "axiomatic:infinite_prime_well"),
     "Euclid's Theorem", "Craft the Infinite Prime Well, with a proof that primes never run out", "challenge"),
    ("perfect", "prime_well", "axiomatic:perfect_core", ("item", "axiomatic:perfect_core"),
     "Perfect", "Craft a Perfect Core from a proven Mersenne prime: 2^(p-1)(2^p - 1) is perfect", "challenge"),
    ("structure", "named", "axiomatic:structure_bench", ("milestone", "structure"),
     "Any Monoid Will Do", "Certify an operation as a monoid, equivalence or order", "goal"),
    ("implemented", "named", "axiomatic:implementation_bench", ("milestone", "implemented"),
     "Faster Machines", "Prove a faster function equal to a slower one", "goal"),
    ("general_recursion", "named", "axiomatic:drafting_chamber", ("milestone", "general_recursion"),
     "Euclid Would Be Proud", "Define a function by general recursion", "challenge"),
]


def write_advancements(lang):
    adv_dir = os.path.join(DATA, "advancement")
    write_json(os.path.join(adv_dir, "root.json"), {
        "criteria": {"tick": {"trigger": "minecraft:tick"}},
        "display": {
            "icon": {"id": f"{NS}:numeral"},
            "title": {"translate": "advancements.axiomatic.root.title"},
            "description": {"translate": "advancements.axiomatic.root.description"},
            "background": "minecraft:gui/advancements/backgrounds/stone",
            "show_toast": False, "announce_to_chat": False},
        "requirements": [["tick"]],
    })
    lang["advancements.axiomatic.root.title"] = "Axiomatic"
    lang["advancements.axiomatic.root.description"] = "From Peano's axioms to ever larger primes"
    for adv_id, parent, icon, (kind, value), title, description, frame in ADVANCEMENTS:
        if kind == "item":
            criterion = {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": value}]}}
        else:
            criterion = {"trigger": f"{NS}:milestone", "conditions": {"milestone": value}}
        write_json(os.path.join(adv_dir, adv_id + ".json"), {
            "parent": f"{NS}:{parent}",
            "criteria": {adv_id: criterion},
            "display": {
                "icon": {"id": icon},
                "title": {"translate": f"advancements.axiomatic.{adv_id}.title"},
                "description": {"translate": f"advancements.axiomatic.{adv_id}.description"},
                "frame": frame},
            "requirements": [[adv_id]],
        })
        lang[f"advancements.axiomatic.{adv_id}.title"] = title
        lang[f"advancements.axiomatic.{adv_id}.description"] = description


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
    "implementation_bench": ("blue", "=", "Implementation Bench"),
    "equivalence_bench": ("blue", "~", "Equivalence Bench"),
    "network_surveyor": ("copper", "N", "Network Surveyor"),
    "infinite_prime_well": ("gold", "W", "Infinite Prime Well"),
    "factorization_vault": ("gold", "V", "Factorization Vault"),
    "certificate_sorter": ("iron", "Y", "Certificate Sorter"),
    "courier_station": ("copper", "E", "Courier Station"),
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


def perfect_core():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 7:
                t = d / 7
                px[x, y] = (int(255 - 60 * t), int(240 - 120 * t), int(150 + 80 * t), 255)
    for i in range(3, 13):
        px[i, 7] = (255, 255, 255, 255)
        px[7, i] = (255, 255, 255, 255)
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
    "perfect_core": (lambda: perfect_core(), "Perfect Core"),
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
    "slot.axiomatic.equation": "Proven equation (or ≡)",
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
    "slot.axiomatic.numeral": "Numeral",
    "slot.axiomatic.filter": "Filter: a property statement like Even(n)",
    "slot.axiomatic.sort_yes": "Certified (out below)",
    "slot.axiomatic.sort_no": "Not certified (pushed out the sides)",
    "button.axiomatic.certificates": "Strict",
    "label.axiomatic.sorts_proven": "Sorts by %s (proven only)",
    "label.axiomatic.sorts_any": "Sorts by %s (any cert.)",
    "slot.axiomatic.cargo": "Cargo for the machines on the network",
    "status.axiomatic.courier_needs_euler": "Needs a network surveyed as Eulerian: every cable joined to an even number of cables",
    "status.axiomatic.courier_delivered": "Delivering to %s machines",
    "status.axiomatic.courier_nowhere": "No machine on the network takes this item",
    "status.axiomatic.test_conjectural": "Not recorded: the test used a decider resting on %s",
    "status.axiomatic.machine_refuted": "Built from parts resting on refuted %s: it no longer works",
    "ingredient.axiomatic.certified": "Any numeral certified %s",
    "ingredient.axiomatic.certified_proven": "Any numeral certified %s (proven, not conjectural)",
    "ingredient.axiomatic.proven_statement": "A proven statement: %s",
    "status.axiomatic.needs_filter": "Put a property statement like Even(n) in the filter slot",
    "slot.axiomatic.conjecture_statement": "Unproven statement, renamed in an anvil",
    "status.axiomatic.too_many_ports": "%s has more than 3 inputs",
    "status.axiomatic.computed": "Computed %s (%s steps)",
    "status.axiomatic.computed_swapped": "Computed %s (%s steps, inputs swapped by the card)",
    "status.axiomatic.speed_card_unproven": "A symmetry card swaps inputs, so it must be fully proven (no conjectures)",
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
    "library.axiomatic.kind.abstract": "  abstract (structure)",
    "library.axiomatic.kind.general": "  by general recursion",
    "library.axiomatic.title": "Library",
    "status.axiomatic.not_an_equivalence": "%s is not a certified equivalence (certify it in the Structure Bench)",
    "library.axiomatic.empty": "Nothing here yet.",
    "library.axiomatic.truncated": "... and %s more",
    "library.axiomatic.scroll": "Scroll for more",
    "library.axiomatic.theorems": "Named theorems",
    "library.axiomatic.uses": "  (%s dependencies, %s steps)",
    "library.axiomatic.rests_on": "    rests on:",
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
    "library.axiomatic.certificates": "Structures",
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
    "slot.axiomatic.implementation_proof": "Proof of fast(x) = slow(x)",
    "status.axiomatic.implemented": "%s is now computed by %s",
    "button.axiomatic.survey": "Survey",
    "label.axiomatic.network": "Network: %s nodes, %s edges",
    "status.axiomatic.no_network": "Touch a cable network to survey it",
    "status.axiomatic.network_bipartite": "; phases alternate: certified for alternating power",
    "status.axiomatic.network_clash": "; phases clash at %s %s %s (odd cycle or same-phase neighbours)",
    "status.axiomatic.short_circuit": "Short circuit! Mixed phases need a surveyed two-colouring",
    "status.axiomatic.burning_alternating": "Burning: alternating power (x2)",
    "message.axiomatic.phase_a": "Cable phase A",
    "message.axiomatic.phase_b": "Cable phase B",
    "status.axiomatic.network_tree": "A tree (%s cycles): certified, lossless. ",
    "status.axiomatic.network_cycles": "Not a tree: %s independent cycles, energy is lost. ",
    "status.axiomatic.network_even": "Every cable has an even number of cable neighbours: Eulerian, certified for couriers.",
    "status.axiomatic.network_odd": "Some cable has an odd number of cable neighbours.",
    "status.axiomatic.burning_lossless": "Burning (lossless tree network)",
    "label.axiomatic.well_last": "Last prime: %s",
    "status.axiomatic.well_stuck": "Cannot decide the next prime (out of energy or computation)",
    "label.axiomatic.vault_empty": "Empty: the number 1",
    "slot.axiomatic.vault_in": "Items to store",
    "status.axiomatic.vault_number": "Stored as one number of %s digits",
    "slot.axiomatic.design_a": "A design: circuit f(…) or statement P(…)",
    "slot.axiomatic.design_b": "Another design",
    "message.axiomatic.junk": "%s was built on a refuted conjecture and turned to paradox junk",
}


# ---- The Mathematician's Notebook (a Modonomicon book) -------------------------------------
#
# The content lives in tools/notebook/<chapter>.json (format in tools/notebook/SPEC.md): entries
# with text, spotlight and recipe pages. An entry's "gate" names an advancement; the entry stays
# hidden until it is earned, through Modonomicon research: advancement hook -> fact -> node.

BOOK = "notebook"
CHAPTERS = [
    ("basics", "Getting Started", "axiomatic:coal_dynamo"),
    ("language", "The Language of Mathematics", "axiomatic:drafting_chamber"),
    ("logic", "Machines of Proof", "axiomatic:induction_engine"),
    ("risk", "Testing and Risk", "axiomatic:conjecture_bench"),
    ("factory", "Factories and Networks", "axiomatic:compiled_machine"),
    ("primes", "The Prime Path", "axiomatic:prime_core"),
    ("structures", "Structures and Speed", "axiomatic:structure_bench"),
]
NOTEBOOK_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "notebook")


def book_text(text):
    """Modonomicon text is Markdown: keep underscores literal (zero_add, add_comm)."""
    return text.replace("_", "\\_")


def load_chapters():
    chapters = []
    for cat, cat_name, icon in CHAPTERS:
        path = os.path.join(NOTEBOOK_DIR, cat + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        assert data["chapter"] == cat, path
        chapters.append((cat, cat_name, icon, data["entries"]))
    return chapters


def write_book(lang):
    import re
    import shutil
    chapters = load_chapters()
    root = os.path.join(DATA, "modonomicon", "books", BOOK)
    shutil.rmtree(root, ignore_errors=True)
    recipes = {f[:-5] for f in os.listdir(os.path.join(DATA, "recipe"))}
    advancements = {a[0] for a in ADVANCEMENTS}
    entry_ids = {f"{cat}/{e['id']}" for cat, _, _, entries in chapters for e in entries}
    assert len(entry_ids) == sum(len(c[3]) for c in chapters), "duplicate entry ids"

    key = f"book.{NS}.{BOOK}"
    write_json(os.path.join(root, "book.json"), {
        "name": f"{key}.name", "tooltip": f"{key}.tooltip", "description": f"{key}.description",
        "model": "modonomicon:modonomicon_purple", "creative_tab": f"{NS}:main",
        "generate_book_item": True, "display_mode": "node", "page_display_mode": "double_page",
        "default_allow_page_split": True, "default_auto_scale": True, "show_recently_unlocked": True,
        "allow_open_book_with_invalid_links": True, "font": "modonomicon:default",
        "turn_page_sound": "modonomicon:turn_page",
    })
    lang[f"{key}.name"] = "The Mathematician's Notebook"
    lang[f"{key}.tooltip"] = "Mathematics from Peano's axioms, one machine at a time"
    lang[f"{key}.description"] = "Chapters open as you prove things."

    gates = sorted({e["gate"] for _, _, _, entries in chapters for e in entries if e.get("gate")})
    for g in gates:
        assert g in advancements, f"unknown gate {g}"
    research = os.path.join(DATA, "modonomicon", "research", BOOK)
    shutil.rmtree(research, ignore_errors=True)
    write_json(os.path.join(research, "facts.json"), [{"id": f"{NS}:{BOOK}/{g}_done"} for g in gates])
    write_json(os.path.join(research, "values.json"), [])
    write_json(os.path.join(research, "hooks.json"), [
        {"id": f"{NS}:{BOOK}/{g}_hook", "fact_id": f"{NS}:{BOOK}/{g}_done",
         "trigger_type": "modonomicon:advancement", "trigger_target": f"{NS}:{g}"} for g in gates])
    write_json(os.path.join(research, "nodes.json"), [
        {"id": f"{NS}:{BOOK}/{g}", "required_facts": [f"{NS}:{BOOK}/{g}_done"]} for g in gates])

    none = {"type": "modonomicon:none"}
    long_pages = []
    for ci, (cat, cat_name, icon, entries) in enumerate(chapters):
        cat_key = f"{key}.{cat}"
        write_json(os.path.join(root, "categories", cat + ".json"), {
            "name": f"{cat_key}.name", "description": "", "icon": {"id": icon}, "sort_number": ci,
            "display_mode": "node", "show_category_button": True,
            "background": "modonomicon:textures/gui/dark_slate_seamless.png",
            "background_width": 512, "background_height": 512, "background_overscan": 1.25,
            "background_parallax_layers": [], "background_rendering_mode": "fit",
            "background_texture_zoom_multiplier": 1.0, "max_scroll_x": 512, "max_scroll_y": 512,
        })
        lang[f"{cat_key}.name"] = cat_name
        previous = None
        for ei, e in enumerate(entries):
            eid = e["id"]
            ekey = f"{cat_key}.{eid}"
            lang[f"{ekey}.name"] = e["title"]
            lang[f"{ekey}.description"] = e.get("description", "")
            pages = []
            for pi, page in enumerate(e["pages"]):
                pkey = f"{ekey}.p{pi}"
                text = page.get("text")
                if text:
                    for link in re.findall(r"entry://axiomatic:notebook/([a-z_]+/[a-z_0-9]+)", text):
                        assert link in entry_ids, f"{cat}/{eid}: broken link {link}"
                    for item in re.findall(r"item://([a-z_]+:[a-z_0-9]+)", text):
                        assert not item.startswith(NS + ":") or os.path.exists(
                            os.path.join(ASSETS, "items", item.split(":")[1] + ".json")), f"{cat}/{eid}: no item {item}"
                    lang[f"{pkey}.text"] = book_text(text)
                    if len(text) > 900:
                        long_pages.append(f"{cat}/{eid} p{pi}: {len(text)}")
                if page.get("title"):
                    lang[f"{pkey}.title"] = page["title"]
                common = {"id": f"p{pi}", "condition": none, "sort_number": pi}
                kind = page["type"]
                if kind == "text":
                    pages.append({"type": "modonomicon:text", **common, "title": f"{pkey}.title" if page.get("title") else "",
                                  "text": f"{pkey}.text", "show_title_separator": bool(page.get("title"))})
                elif kind == "spotlight":
                    pages.append({"type": "modonomicon:spotlight", **common, "item": {"id": page["item"]},
                                  "title": f"{pkey}.title" if page.get("title") else "", "text": f"{pkey}.text" if text else ""})
                elif kind == "recipe":
                    ids = page["recipes"]
                    assert 1 <= len(ids) <= 2 and all(r in recipes for r in ids), f"{cat}/{eid}: recipes {ids}"
                    rp = {"type": "modonomicon:crafting_recipe", **common, "recipe_id_1": f"{NS}:{ids[0]}"}
                    if len(ids) > 1:
                        rp["recipe_id_2"] = f"{NS}:{ids[1]}"
                    if text:
                        rp["text"] = f"{pkey}.text"
                    pages.append(rp)
                else:
                    raise AssertionError(f"{cat}/{eid}: page type {kind}")
            gate = e.get("gate")
            entry = {
                "type": "modonomicon:content", "id": f"{NS}:{cat}/{eid}", "category": f"{NS}:{cat}",
                "name": f"{ekey}.name", "description": f"{ekey}.description", "icon": {"id": e["icon"]},
                "background": {"sprite": "modonomicon:modonomicon/themes/default/node/entry_backgrounds/"
                               + ("square_gray" if gate is None else "square_gold"), "width": 26, "height": 26},
                "x": 2 * (ei % 5), "y": 2 * (ei // 5), "sort_number": ei,
                "condition": {"type": "modonomicon:research_node_unlocked", "node_id": f"{NS}:{BOOK}/{gate}"}
                if gate else none,
                "marker_condition": none,
                "pages": pages,
            }
            if previous:
                entry["parents"] = [{"entry": previous}]
            write_json(os.path.join(root, "entries", cat, eid + ".json"), entry)
            previous = f"{NS}:{cat}/{eid}"
    for line in long_pages:
        print("long Notebook page (consider splitting):", line)


def main():
    lang = dict(LANG)
    write_book(lang)
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
    save(cable(True), os.path.join(ASSETS, "textures", "block", "copper_cable_b.png"))
    tex_b = f"{NS}:block/copper_cable_b"
    write_json(os.path.join(ASSETS, "models", "block", "copper_cable_b.json"), {
        "parent": "minecraft:block/block",
        "textures": {"all": tex_b, "particle": tex_b},
        "elements": [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": faces}],
    })
    # Arms toward neighbours: one model per phase, pointing north, rotated by the multipart blockstate.
    for suffix, tex_name in (("", tex), ("_b", tex_b)):
        write_json(os.path.join(ASSETS, "models", "block", f"copper_cable_arm{suffix}.json"), {
            "parent": "minecraft:block/block",
            "textures": {"all": tex_name, "particle": tex_name},
            "elements": [{"from": [5, 5, 0], "to": [11, 11, 5], "faces": faces}],
        })
    rotations = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270},
                 "up": {"x": 270}, "down": {"x": 90}}
    multipart = []
    for phase, suffix in (("false", ""), ("true", "_b")):
        multipart.append({"when": {"phase": phase}, "apply": {"model": f"{NS}:block/copper_cable{suffix}"}})
        for side, rot in rotations.items():
            multipart.append({"when": {side: "true", "phase": phase},
                              "apply": {"model": f"{NS}:block/copper_cable_arm{suffix}", **rot}})
    write_json(os.path.join(ASSETS, "blockstates", "copper_cable.json"), {"multipart": multipart})
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

    write_advancements(lang)
    write_json(os.path.join(ASSETS, "lang", "en_us.json"), dict(sorted(lang.items())))

    for block in pickaxe:
        name = block.split(":")[1]
        entry = {"type": "minecraft:item", "name": block}
        if name == "conjecture_block":
            entry["functions"] = [{"function": "minecraft:copy_components", "source": "block_entity",
                                   "include": [f"{NS}:conjecture", "minecraft:custom_name"]}]
        if name == "factorization_vault":
            entry["functions"] = [{"function": "minecraft:copy_components", "source": "block_entity",
                                   "include": [f"{NS}:vault_contents", f"{NS}:depends"]}]
        elif name in MACHINES:
            # Machines keep the conjectures their parts rested on (see MachineBlockEntity).
            entry["functions"] = [{"function": "minecraft:copy_components", "source": "block_entity",
                                   "include": [f"{NS}:depends"]}]
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
            "type": f"{NS}:risky_shaped",
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
    shaped("courier_station", ["CMC", "HBH", "CCC"],
           {"C": "minecraft:copper_ingot", "M": "minecraft:minecart", "H": "minecraft:hopper", "B": board})
    shaped("certificate_sorter", ["IHI", "CBC", "III"],
           {"I": "minecraft:iron_ingot", "H": "minecraft:hopper", "C": "minecraft:comparator", "B": board})
    shaped("network_surveyor", ["CMC", "OBO", "CCC"],
           {"C": "minecraft:copper_ingot", "M": "minecraft:map", "O": "minecraft:compass", "B": board})
    shaped("equivalence_bench", ["QBQ", "BCB", "QBQ"],
           {"Q": "minecraft:quartz", "B": board, "C": "minecraft:comparator"})
    shaped("implementation_bench", ["QBQ", "BOB", "QBQ"],
           {"Q": "minecraft:quartz", "B": board, "O": "minecraft:observer"})
    shaped("perfect_core", ["CNC", "CMC", "CCC"], {
        "C": "axiomatic:prime_core", "N": "minecraft:netherite_ingot",
        "M": {"neoforge:ingredient_type": f"{NS}:certified_numeral", "property": "Prime", "also": ["Mersenne"],
              "proven_only": True},
    }, category="misc")
    shaped("infinite_prime_well", ["CEC", "GZG", "CEC"], {
        "C": "axiomatic:prime_core", "E": "minecraft:ender_eye", "G": "minecraft:gold_block",
        "Z": {"neoforge:ingredient_type": f"{NS}:proven_statement", "statement": "∀n. ∃p. n ≤ p ∧ Prime(p)"},
    })
    shaped("factorization_vault", ["ICI", "CPC", "ICI"],
           {"I": "minecraft:iron_block", "C": "minecraft:chest", "P": "axiomatic:prime_core"})
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
