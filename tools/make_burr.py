"""The six burr sticks, notched exactly as the game's rules say, in one GLB.

blender -b --factory-startup --python tools/make_burr.py -- <burr.json> <out.glb>
burr.json comes from the unit tests (ModelDataExport): {"cell": meters, "pieces": [{"name",
"axis", "cells": [[x,y,z], ...]}]}. Each stick is a node named stick0..stick5, modeled where it
sits in the assembled lock (origin = lock center), so the game moves it by its offset only.
Sticks of each pair share a wood: across (x) rosewood, upright (y) sapele, front-back (z) dark red.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import woodkit as wk  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
data = json.load(open(args[0]))
out = args[1]

wk.reset()
cell = data["cell"]
woods = {"X": wk.wood("rosewood_veneer1"), "Y": wk.wood("sapele_veneer"), "Z": wk.wood("dark_wood")}
grain = {"X": 0, "Y": 1, "Z": 2}
objects = []
for i, p in enumerate(data["pieces"]):
    ob = wk.voxel_mesh(f"stick{i}", p["cells"], cell, grain_axis=grain[p["axis"]])
    wk.bevel(ob, width=0.0012, segments=2)
    wk.assign(ob, woods[p["axis"]])
    objects.append(ob)
wk.export(out, objects)
