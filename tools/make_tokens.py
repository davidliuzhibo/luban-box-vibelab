"""The tokens: what each relic gives, laid on the bench as sets.

blender -b --factory-startup --python tools/make_tokens.py -- <tokens.glb>
token1..4   鲁班尺 sections (each 12 cm, two of the ruler's eight characters 财病 离义 官劫 害本)
token5..8   quarters of a bronze seal 天工开物, modeled where they sit in the whole seal
            (read right column first: 天 工 on the right, 开 物 on the left)
token9..11  thirds of a star chart, the three enclosures 紫微 太微 天市, modeled in place
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import woodkit as wk  # noqa: E402

out = sys.argv[sys.argv.index("--") + 1:][0]
wk.reset()
lacquer = wk.flat("cinnabar_lacquer", (0.28, 0.035, 0.018), roughness=0.3)
gold = wk.flat("gold", (0.86, 0.66, 0.28), roughness=0.3, metallic=1.0)
bronze = wk.metal("bronze")
indigo = wk.flat("indigo_lacquer", (0.02, 0.04, 0.12), roughness=0.35)
tokens = []
for k, chars in enumerate(["财病", "离义", "官劫", "害本"]):
    t = wk.box(f"token{k + 1}", (0.12, 0.006, 0.03), (0, 0.003, 0), lacquer, bevel_width=0.001)
    marks = [wk.box("rule", (0.116, 0.0004, 0.0012), (0, 0.0062, 0.0115), gold, bevel_width=0)]
    for j in range(12):
        x = -0.055 + j * 0.01
        marks.append(wk.box("tick", (0.0008, 0.0004, 0.004 if j % 5 else 0.007), (x, 0.0062, 0.0115 - (0.002 if j % 5 else 0.0035)), gold, bevel_width=0))
    marks.append(wk.box("bar", (0.0012, 0.0004, 0.018), (0, 0.0062, -0.002), gold, bevel_width=0))
    t = wk.join([t] + marks, f"token{k + 1}")
    t = wk.carve_text(t, chars, (0, 0.006, -0.003), 0.019, gold)
    tokens.append(t)
for k, (ch, x, z) in enumerate([("天", 0.0155, -0.0155), ("工", 0.0155, 0.0155), ("开", -0.0155, -0.0155), ("物", -0.0155, 0.0155)]):
    q = wk.box(f"q{k}", (0.03, 0.025, 0.03), (x, 0.0125, z), bronze, bevel_width=0.0015)
    letter = wk.text_mesh("c", ch, 0.022, (x, 0.0252, z), facing="+y", mat=gold)
    tokens.append(wk.join([q, letter], f"token{k + 5}"))
for k, name in enumerate(["紫微", "太微", "天市"]):
    parts = []
    a0 = 90 + k * 120
    # A 120° sector of a 6 cm disc, from facets.
    pts = [(0, 0, 0)] + [(0.06 * math.cos(math.radians(a0 + j * 120 / 24)), 0, -0.06 * math.sin(math.radians(a0 + j * 120 / 24))) for j in range(25)]
    import bmesh
    import bpy
    bm = bmesh.new()
    low = [bm.verts.new(wk.to_blender((p[0], 0.0, p[2]))) for p in pts]
    high = [bm.verts.new(wk.to_blender((p[0], 0.006, p[2]))) for p in pts]
    bm.faces.new(low)
    bm.faces.new(list(reversed(high)))
    n = len(pts)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new([low[i], low[j], high[j], high[i]])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    sector = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(sector)
    wk.cube_uv(sector)
    wk.assign(sector, indigo)
    parts.append(sector)
    mid = math.radians(a0 + 60)
    parts.append(wk.text_mesh("label", name, 0.012, (0.036 * math.cos(mid), 0.0064, -0.036 * math.sin(mid)), facing="+y", mat=gold))
    for s in range(6):
        a = math.radians(a0 + 12 + s * 17)
        r = 0.016 + 0.007 * ((s * 5) % 6)
        parts.append(wk.sphere(f"star{s}", 0.0016, (r * math.cos(a), 0.0064, -r * math.sin(a)), gold, segments=8))
    tokens.append(wk.join(parts, f"token{k + 9}"))
wk.export(out, tokens)
