"""鲁班盒 and the 鲁班尺 sections (the chapter's tokens).

blender -b --factory-startup --python tools/make_box.py -- <box.json> <box.glb> <tokens.glb>
box.json comes from the unit tests (ModelDataExport): each panel as the union of boxes the game collides
(v0.10.1: corner tabs and notches like a finger joint), the body and its plinth, as in Boxes.kt.
Nodes: box_body (hollow, open at the top, on a plinth), panel0..panel4 (right end, front, left end, back, lid;
a sunk field in each outer face, 鲁班 inlaid in the lid), token1..token4: the four sections of a
鲁班尺, each with two of its eight characters (财病 离义 官劫 害本) in gold on cinnabar.
The end panels are walnut and the side panels and lid rosewood, so the corner joints read at a glance.
"""
import json
import os
import sys

import bmesh
import bpy

sys.path.insert(0, os.path.dirname(__file__))
import woodkit as wk  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
data = json.load(open(args[0]))
out_box, out_tokens = args[1], args[2]


def union_mesh(name, boxes):
    """One watertight solid: the union of axis-aligned [boxes] ([lo, hi] corners, game frame), split on a grid
    of their face planes, with coplanar faces merged so the bevel sees clean edges."""
    xs, ys, zs = (sorted({b[k][a] for b in boxes for k in (0, 1)}) for a in range(3))

    def inside(c):
        return any(all(b[0][a] < c[a] < b[1][a] for a in range(3)) for b in boxes)

    grid = {}
    for i in range(len(xs) - 1):
        for j in range(len(ys) - 1):
            for k in range(len(zs) - 1):
                c = ((xs[i] + xs[i + 1]) / 2, (ys[j] + ys[j + 1]) / 2, (zs[k] + zs[k + 1]) / 2)
                grid[(i, j, k)] = inside(c)
    axes = (xs, ys, zs)
    bm = bmesh.new()
    verts = {}

    def vert(idx):
        v = verts.get(idx)
        if v is None:
            v = bm.verts.new(wk.to_blender((xs[idx[0]], ys[idx[1]], zs[idx[2]])))
            verts[idx] = v
        return v

    for (i, j, k), filled in grid.items():
        if not filled:
            continue
        cell = (i, j, k)
        for axis in range(3):
            for sign in (-1, 1):
                n = list(cell)
                n[axis] += sign
                if grid.get(tuple(n), False):
                    continue
                others = [a for a in range(3) if a != axis]
                base = list(cell)
                if sign > 0:
                    base[axis] += 1
                a, b = others
                quad = []
                for (da, db) in [(0, 0), (1, 0), (1, 1), (0, 1)]:
                    p = list(base)
                    p[a] += da
                    p[b] += db
                    quad.append(tuple(p))
                normal_sign = sign if (a, b) in [(1, 2), (2, 0), (0, 1)] else -sign
                if normal_sign < 0:
                    quad.reverse()
                bm.faces.new([vert(q) for q in quad])
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-7)
    bmesh.ops.dissolve_limit(bm, angle_limit=0.0005, use_dissolve_boundaries=False, verts=bm.verts, edges=bm.edges)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def field(ob, mat, face, center, size):
    """A shallow sunk field (1.5 mm) in an outer face: [face] is the axis and side ('+x', '-z', '+y'...)."""
    axis = "xyz".index(face[1])
    s = list(size)
    s[axis] = 0.003
    cutter = wk.box("field", tuple(s), center, mat, bevel_width=0)
    wk.subtract(ob, cutter)


wk.reset()
dark = wk.wood("dark_wood")
rose = wk.wood("rosewood_veneer1")
# Walnut toned down to a warm mid brown: lighter than the rosewood sides, darker than the bare veneer.
walnut = wk.wood("walnut_veneer", tint=(0.62, 0.48, 0.36))
gold = wk.flat("gold", (0.86, 0.66, 0.28), roughness=0.3, metallic=1.0)

# Body: outer shell minus the cavity (8 mm walls and floor), on a plinth 1 cm in from the panels.
lo, hi = data["body"]
size = [hi[a] - lo[a] for a in range(3)]
center = [(hi[a] + lo[a]) / 2 for a in range(3)]
body = wk.box("box_body", size, center, dark, bevel_width=0.001)
wall = 0.008
cavity = wk.box("cavity", (size[0] - 2 * wall, size[1], size[2] - 2 * wall), (center[0], center[1] + wall, center[2]), dark, bevel_width=0)
wk.subtract(body, cavity)
plo, phi = data["plinth"]
plinth = wk.box("plinth", [phi[a] - plo[a] for a in range(3)], [(phi[a] + plo[a]) / 2 for a in range(3)], dark, bevel_width=0.0015)
body = wk.join([body, plinth], "box_body")

# Sunk fields (center, size) on each panel's outer face, kept 1.2 cm or more from every tab and notch.
FIELDS = [
    ("+x", (0.120, 0.083, 0.0), (0.0, 0.108, 0.1)),
    ("+z", (0.005, 0.0685, 0.082), (0.166, 0.083, 0.0)),
    ("-x", (-0.120, 0.083, 0.0), (0.0, 0.108, 0.1)),
    ("-z", (0.0, 0.071, -0.082), (0.176, 0.088, 0.0)),
    ("+y", (0.0, 0.147, 0.0), (0.176, 0.0, 0.12)),
]
panels = []
for i, boxes in enumerate(data["panels"]):
    mat = walnut if i in (0, 2) else rose
    p = union_mesh(f"panel{i}", boxes)
    wk.bevel(p, width=0.001, segments=2)
    wk.cube_uv(p, 4.0)
    wk.assign(p, mat)
    face, c, sz = FIELDS[i]
    field(p, mat, face, c, sz)
    if i == 4:
        p = wk.carve_text(p, "鲁班", (0.0, 0.147 - 0.0015, 0.0), 0.055, gold)
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
