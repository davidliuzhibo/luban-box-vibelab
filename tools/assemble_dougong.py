"""Assembles the 斗拱 parts at their slots (numbers from Dougong.kt) into one scene and saves a
GLB, so preview.py can render the finished bracket set.

blender -b --factory-startup --python tools/assemble_dougong.py -- <dougong.glb> <out.glb>
"""
import math
import sys

import bpy

args = sys.argv[sys.argv.index("--") + 1:]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=args[0])
SLOTS = [
    ("column", 0, 0, 0, 0), ("栌斗", 0, 0, 0, 0), ("华栱", 0, 0.036, 0.0375, 0), ("泥道栱", 0, 0.036, 0, 0),
    ("散斗", 0, 0.081, 0, 90), ("散斗", -0.09, 0.081, 0, 0), ("散斗", 0.09, 0.081, 0, 0),
    ("交互斗", 0, 0.081, 0.105, 0), ("令栱", 0, 0.096, 0.105, 0), ("耍头", 0, 0.096, 0.04, 0),
    ("散斗", -0.08, 0.141, 0.105, 0), ("散斗", 0.08, 0.141, 0.105, 0), ("柱头枋", 0, 0.096, 0, 0),
    ("撩檐枋", 0, 0.156, 0.105, 0),
]
templates = {o.name: o for o in bpy.data.objects if o.name.startswith("dg_")}
for t in templates.values():
    t.hide_render = True
for name, x, y, z, yaw in SLOTS:
    src = templates["dg_" + name]
    ob = src.copy()
    bpy.context.scene.collection.objects.link(ob)
    ob.hide_render = False
    ob.location = (x, -z, y)
    ob.rotation_mode = "XYZ"
    ob.rotation_euler = (0, 0, math.radians(yaw))
for t in templates.values():
    bpy.data.objects.remove(t)
bpy.ops.export_scene.gltf(filepath=args[1], export_format="GLB", export_yup=True)
