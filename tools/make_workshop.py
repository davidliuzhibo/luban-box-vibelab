"""The workshop around the bench, rendered as a 360° panorama from where the player sits.

blender -b --factory-startup --python tools/make_workshop.py -- <out.jpg> [width]
A timber-framed room (6 × 6 m, 3.4 m high): plank floor, plastered walls between dark posts,
lattice windows with warm afternoon light on the left, shelves of tools and boxes behind,
paper lanterns and a plaque 巧夺天工 over the back wall. The bench itself is not in it (the game
draws it). Equirectangular, Cycles; the game wraps it on an inside-out sphere.
"""
import math
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(__file__))
import woodkit as wk  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
out = args[0]
width = int(args[1]) if len(args) > 1 else 2048

wk.reset()
dark = wk.wood("dark_wood")
walnut = wk.wood("walnut_veneer")
teak = wk.wood("teak_veneer")
rose = wk.wood("rosewood_veneer1")
plaster = wk.flat("plaster", (0.78, 0.72, 0.62), roughness=0.9)
paper = wk.flat("window_paper", (1.0, 0.92, 0.75), roughness=0.8, emission=(1.0, 0.86, 0.62))
lantern = wk.flat("lantern", (0.75, 0.06, 0.02), roughness=0.6, emission=(0.85, 0.12, 0.02))
scroll = wk.flat("scroll", (0.9, 0.86, 0.76), roughness=0.85)
ink = wk.flat("ink", (0.03, 0.03, 0.03), roughness=0.7)
gold = wk.flat("gold", (0.86, 0.66, 0.28), roughness=0.3, metallic=1.0)
lacquer = wk.flat("red_lacquer", (0.3, 0.04, 0.02), roughness=0.35)

W, H = 3.0, 3.4  # half width, height
parts = []
# Floor planks and ceiling.
parts.append(wk.box("floor", (2 * W, 0.04, 2 * W), (0, -0.02, 0), teak, bevel_width=0, uv_scale=0.8))
parts.append(wk.box("ceiling", (2 * W, 0.04, 2 * W), (0, H, 0), dark, bevel_width=0, uv_scale=0.5))
# Walls: plaster panels behind a timber frame.
for name, size, center in [
    ("wall_back", (2 * W, H, 0.05), (0, H / 2, -W)),
    ("wall_front", (2 * W, H, 0.05), (0, H / 2, W)),
    ("wall_left", (0.05, H, 2 * W), (-W, H / 2, 0)),
    ("wall_right", (0.05, H, 2 * W), (W, H / 2, 0)),
]:
    parts.append(wk.box(name, size, center, plaster, bevel_width=0))
for k in range(-2, 3):
    x = k * 1.4
    for z in (-W + 0.06, W - 0.06):
        parts.append(wk.box(f"post{k}{z}", (0.16, H, 0.16), (x, H / 2, z), dark, bevel_width=0.01, uv_scale=1))
    for xx in (-W + 0.06, W - 0.06):
        parts.append(wk.box(f"postx{k}{xx}", (0.16, H, 0.16), (xx, H / 2, x), dark, bevel_width=0.01, uv_scale=1))
for z in (-1.5, 0.0, 1.5):
    parts.append(wk.box(f"beam{z}", (2 * W, 0.2, 0.18), (0, H - 0.12, z), dark, bevel_width=0.01, uv_scale=1))
for side in (-1, 1):
    parts.append(wk.box(f"girt{side}", (2 * W, 0.12, 0.1), (0, 0.9, side * (W - 0.08)), dark, bevel_width=0.005, uv_scale=1))
    # Dark wooden wainscot along the lower walls.
    parts.append(wk.box(f"wainscot_z{side}", (2 * W, 0.85, 0.03), (0, 0.425, side * (W - 0.04)), rose, bevel_width=0, uv_scale=1))
    parts.append(wk.box(f"wainscot_x{side}", (0.03, 0.85, 2 * W), (side * (W - 0.04), 0.425, 0), rose, bevel_width=0, uv_scale=1))

# Lattice windows on the left wall, glowing with daylight.
for zc in (-1.4, 0.0, 1.4):
    parts.append(wk.box(f"pane{zc}", (0.02, 1.4, 1.2), (-W + 0.04, 1.85, zc), paper, bevel_width=0))
    parts.append(wk.box(f"wframe{zc}", (0.05, 1.5, 0.06), (-W + 0.07, 1.85, zc - 0.63), dark, bevel_width=0))
    parts.append(wk.box(f"wframe2{zc}", (0.05, 1.5, 0.06), (-W + 0.07, 1.85, zc + 0.63), dark, bevel_width=0))
    for k in range(9):
        parts.append(wk.box(f"lv{zc}{k}", (0.03, 1.4, 0.02), (-W + 0.06, 1.85, zc - 0.56 + k * 0.14), dark, bevel_width=0))
    for k in range(11):
        parts.append(wk.box(f"lh{zc}{k}", (0.03, 0.02, 1.2), (-W + 0.06, 1.2 + k * 0.13, zc), dark, bevel_width=0))
# Hanging scrolls on the right wall: 规 and 矩, the carpenter's compass and square.
for zc, ch in ((-0.7, "规"), (0.7, "矩")):
    parts.append(wk.box(f"scroll{ch}", (0.01, 1.1, 0.5), (W - 0.06, 1.9, zc), scroll, bevel_width=0))
    parts.append(wk.box(f"rod{ch}", (0.03, 0.03, 0.56), (W - 0.06, 2.46, zc), dark, bevel_width=0))
    letter = wk.text_mesh(f"ink{ch}", ch, 0.32, (0, 1.9, 0), facing="+z", mat=ink, depth=0.004)
    wk.turn(letter, "y", -90)
    wk.move(letter, (W - 0.07, 0, zc))
    parts.append(letter)

# Shelves on the back wall, with boxes and tools.
for level in range(4):
    y = 0.5 + level * 0.55
    parts.append(wk.box(f"shelf{level}", (3.6, 0.04, 0.4), (0.4, y, -W + 0.25), teak, bevel_width=0.004, uv_scale=1))
    for k in range(7):
        x = -1.2 + k * 0.5 + 0.08 * ((level * 3 + k) % 3)
        h = 0.12 + 0.08 * ((level + k) % 3)
        mat = [rose, walnut, dark, lacquer][(level + k) % 4]
        parts.append(wk.box(f"item{level}{k}", (0.3, h, 0.26), (x, y + 0.02 + h / 2, -W + 0.25), mat, bevel_width=0.006, uv_scale=2))
for x in (-1.4, 2.2):
    parts.append(wk.box(f"upright{x}", (0.05, 2.3, 0.4), (x, 1.15, -W + 0.25), teak, bevel_width=0.004, uv_scale=1))
# A plaque over the shelves: 巧夺天工.
parts.append(wk.box("plaque", (1.6, 0.42, 0.05), (0.4, 2.95, -W + 0.08), lacquer, bevel_width=0.01))
parts.append(wk.text_mesh("words", "巧夺天工", 0.3, (0.4, 2.95, -W + 0.11), facing="+z", mat=gold, depth=0.01))
# Saws and squares hanging on the right wall; a stool and a chest on the floor.
for k in range(4):
    parts.append(wk.box(f"tool{k}", (0.02, 0.5, 0.12 + 0.05 * k), (W - 0.06, 1.4, -2.2 + k * 0.25), rose, bevel_width=0.004))
parts.append(wk.box("chest", (0.9, 0.5, 0.5), (2.0, 0.25, 1.6), rose, bevel_width=0.01, uv_scale=1))
parts.append(wk.box("stool", (0.4, 0.45, 0.4), (-1.8, 0.225, 1.8), teak, bevel_width=0.01, uv_scale=1))
# Paper lanterns.
for x, z in [(-1.2, -1.0), (1.4, -0.8), (0.2, 1.4)]:
    parts.append(wk.sphere(f"lantern{x}", 0.18, (x, 2.6, z), lantern, segments=24))
    parts.append(wk.cylinder(f"cord{x}", 0.006, 0.6, (x, 3.0, z), dark, verts=8))

# Light: warm sun through the windows, a soft fill, the lanterns glow.
sc = bpy.context.scene
sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
sun.data.energy = 3.5
sun.data.color = (1.0, 0.85, 0.65)
sun.rotation_euler = (math.radians(60), math.radians(0), math.radians(-70))
sc.collection.objects.link(sun)
for x, z in [(-1.2, -1.0), (1.4, -0.8), (0.2, 1.4)]:
    pt = bpy.data.objects.new(f"glow{x}", bpy.data.lights.new(f"glow{x}", "POINT"))
    pt.data.energy = 40
    pt.data.color = (1.0, 0.55, 0.3)
    pt.location = wk.to_blender((x, 2.6, z))
    sc.collection.objects.link(pt)
fill = bpy.data.objects.new("fill", bpy.data.lights.new("fill", "AREA"))
fill.data.energy = 300
fill.data.size = 4
fill.data.color = (1.0, 0.9, 0.8)
fill.location = wk.to_blender((0, H - 0.3, 0))
sc.collection.objects.link(fill)
world = bpy.data.worlds.new("w")
sc.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.9, 0.8, 0.65, 1)
world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.3

cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
cam.data.type = "PANO"
cam.data.panorama_type = "EQUIRECTANGULAR"
# Seated eye height; looking along -z (the bench side) at the image middle.
cam.location = wk.to_blender((0, 1.2, 0))
cam.rotation_euler = (math.radians(90), 0, 0)
sc.collection.objects.link(cam)
sc.camera = cam
sc.render.engine = "CYCLES"
# Standard view keeps the lanterns red (AgX washes bright colors toward white).
sc.view_settings.view_transform = "Standard"
sc.cycles.samples = int(os.environ.get("SAMPLES", "48"))
sc.cycles.use_denoising = True
sc.cycles.max_bounces = 4
sc.render.resolution_x = width
sc.render.resolution_y = width // 2
sc.render.image_settings.file_format = "JPEG"
sc.render.image_settings.quality = 88
sc.render.filepath = out
bpy.ops.render.render(write_still=True)
print("rendered", out)
