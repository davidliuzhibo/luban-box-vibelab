"""鲁班盒 and the 鲁班尺 sections (the chapter's tokens).

blender -b --factory-startup --python tools/make_box.py -- <box.json> <box.glb> <tokens.glb>
box.json comes from the unit tests (ModelDataExport): panel boxes and the body, as in Boxes.kt.
Nodes: box_body (hollow, open at the top), panel0..panel4 (right end, front, left end, back, lid;
a sunk field in each outer face, 鲁班 inlaid in the lid), token1..token4: the four sections of a
鲁班尺, each with two of its eight characters (财病 离义 官劫 害本) in gold on cinnabar.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import woodkit as wk  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
data = json.load(open(args[0]))
out_box, out_tokens = args[1], args[2]

wk.reset()
dark = wk.wood("dark_wood")
rose = wk.wood("rosewood_veneer1")
gold = wk.flat("gold", (0.86, 0.66, 0.28), roughness=0.3, metallic=1.0)

# Body: outer shell minus the cavity (8 mm walls and floor).
size, center = data["body"]
body = wk.box("box_body", size, center, dark, bevel_width=0.001)
wall = 0.008
cavity = wk.box("cavity", (size[0] - 2 * wall, size[1], size[2] - 2 * wall), (center[0], center[1] + wall, center[2]), dark, bevel_width=0)
wk.subtract(body, cavity)

panels = []
for i, (psize, pcenter) in enumerate(data["panels"]):
    p = wk.box(f"panel{i}", psize, pcenter, rose, bevel_width=0.0015)
    # Sunk field: a shallow rectangle 1.2 cm in from the edges of the outer face.
    thin = min(range(3), key=lambda a: psize[a])
    outward = [0, 0, 0]
    if thin == 1:
        outward[1] = 1
    else:
        outward[thin] = 1 if pcenter[thin] > 0 else -1
    field = list(psize)
    for a in range(3):
        if a != thin:
            field[a] = psize[a] - 0.024
    field[thin] = 0.003
    fc = [pcenter[a] + outward[a] * (psize[thin] / 2) if a == thin else pcenter[a] for a in range(3)]
    cutter = wk.box("field", field, fc, rose, bevel_width=0)
    wk.subtract(p, cutter)
    if i == 4:
        top = (pcenter[0], pcenter[1] + psize[1] / 2 - 0.0013, pcenter[2])
        p = wk.carve_text(p, "鲁班", top, 0.055, gold)
    panels.append(p)
wk.export(out_box, [body] + panels)

# ---- Tokens: four sections of a 鲁班尺 ---------------------------------------------------------
wk.reset()
lacquer = wk.flat("cinnabar_lacquer", (0.28, 0.035, 0.018), roughness=0.3)
gold = wk.flat("gold", (0.86, 0.66, 0.28), roughness=0.3, metallic=1.0)
tokens = []
for k, chars in enumerate(["财病", "离义", "官劫", "害本"]):
    t = wk.box(f"token{k + 1}", (0.12, 0.006, 0.03), (0, 0.003, 0), lacquer, bevel_width=0.001)
    # Gold rule along the near edge with ticks every centimetre, and a bar between the two characters.
    marks = [wk.box("rule", (0.116, 0.0004, 0.0012), (0, 0.0062, 0.0115), gold, bevel_width=0)]
    for j in range(12):
        x = -0.055 + j * 0.01
        marks.append(wk.box("tick", (0.0008, 0.0004, 0.004 if j % 5 else 0.007), (x, 0.0062, 0.0115 - (0.002 if j % 5 else 0.0035)), gold, bevel_width=0))
    marks.append(wk.box("bar", (0.0012, 0.0004, 0.018), (0, 0.0062, -0.002), gold, bevel_width=0))
    t = wk.join([t] + marks, f"token{k + 1}")
    t = wk.carve_text(t, chars, (0, 0.006, -0.003), 0.019, gold)
    tokens.append(t)
wk.export(out_tokens, tokens)
