"""Renders GLB models for a look before they go to the headset (Cycles, CPU).

blender -b --factory-startup --python tools/preview.py -- <out.png> <model.glb> [more.glb ...]
  env PREVIEW_VIEW=front|top|three (default three-quarter), PREVIEW_SIZE=900, PREVIEW_SAMPLES=48
  env PREVIEW_OFFSETS='{"stick0": [0.05, 0, 0]}' moves named nodes (game frame, meters) first.
  env PREVIEW_DIR=x,y,z looks from that direction (game frame); PREVIEW_ZOOM=0.6 moves the camera closer.
Warm key light from the upper left, cool fill, on a neutral backdrop; the camera frames everything.
"""
import json
import math
import os
import sys

import bpy
from mathutils import Vector

args = sys.argv[sys.argv.index("--") + 1:]
out, models = args[0], args[1:]
bpy.ops.wm.read_factory_settings(use_empty=True)
for m in models:
    bpy.ops.import_scene.gltf(filepath=m)

offsets = json.loads(os.environ.get("PREVIEW_OFFSETS", "{}"))
for name, (x, y, z) in offsets.items():
    ob = bpy.data.objects.get(name)
    if ob:
        ob.location += Vector((x, -z, y))

meshes = [o for o in bpy.data.objects if o.type == "MESH"]
lo = Vector((min(min((o.matrix_world @ Vector(c))[i] for c in o.bound_box) for o in meshes) for i in range(3)))
hi = Vector((max(max((o.matrix_world @ Vector(c))[i] for c in o.bound_box) for o in meshes) for i in range(3)))
center = (lo + hi) / 2
radius = (hi - lo).length / 2

scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.samples = int(os.environ.get("PREVIEW_SAMPLES", "48"))
scene.cycles.use_denoising = True
size = int(os.environ.get("PREVIEW_SIZE", "900"))
scene.render.resolution_x = size
scene.render.resolution_y = size
scene.render.film_transparent = False
world = bpy.data.worlds.new("w")
scene.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.16, 0.15, 0.14, 1)
world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.6

# Ground plane under the model.
bpy.ops.mesh.primitive_plane_add(size=radius * 12, location=(center.x, center.y, lo.z - 0.0005))
ground = bpy.context.object
gm = bpy.data.materials.new("ground")
gm.use_nodes = True
gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.35, 0.33, 0.3, 1)
gm.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = 0.8
ground.data.materials.append(gm)

def light(name, kind, energy, loc, color):
    data = bpy.data.lights.new(name, kind)
    data.energy = energy
    data.color = color
    if kind == "AREA":
        data.size = radius * 3
    ob = bpy.data.objects.new(name, data)
    scene.collection.objects.link(ob)
    ob.location = loc
    d = center - Vector(loc)
    ob.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()

light("key", "AREA", 30 * radius * radius * 40, center + Vector((-radius * 3, -radius * 3, radius * 4)), (1.0, 0.9, 0.78))
light("fill", "AREA", 8 * radius * radius * 40, center + Vector((radius * 4, -radius * 2, radius * 1.5)), (0.75, 0.85, 1.0))
light("rim", "AREA", 15 * radius * radius * 40, center + Vector((radius * 1, radius * 4, radius * 3)), (1.0, 0.95, 0.9))

view = os.environ.get("PREVIEW_VIEW", "three")
cam_data = bpy.data.cameras.new("cam")
cam_data.lens = 60
cam = bpy.data.objects.new("cam", cam_data)
scene.collection.objects.link(cam)
scene.camera = cam
dist = radius / math.tan(math.atan(18 / cam_data.lens)) * 1.25
dirs = {"three": Vector((0.55, -1.0, 0.75)), "front": Vector((0, -1, 0.25)), "top": Vector((0, -0.25, 1))}
d = dirs.get(view, dirs["three"]).normalized()
if os.environ.get("PREVIEW_DIR"):
    # A camera direction of its own, game frame (x right, y up, z toward the player).
    gx, gy, gz = (float(v) for v in os.environ["PREVIEW_DIR"].split(","))
    d = Vector((gx, -gz, gy)).normalized()
dist *= float(os.environ.get("PREVIEW_ZOOM", "1"))
cam.location = center + d * dist
cam.rotation_euler = (center - cam.location).to_track_quat("-Z", "Y").to_euler()
scene.render.filepath = out
bpy.ops.render.render(write_still=True)
print("rendered", out)
