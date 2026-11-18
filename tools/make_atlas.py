"""Line drawings for the 营造图谱 window: each relic drawn in ink on paper (Blender Freestyle).

blender -b --factory-startup --python tools/make_atlas.py -- <models_dir> <res_drawable_dir> [id ...]
Every relic is laid out from its model's nodes (placements below, game frame, as in the game's
slots), rendered with flat paper-white surfaces and Freestyle outlines, and written as
atlas_<id>.png (900 × 560). Burr sticks and the censer are drawn a little apart, like an
exploded view in a carpentry manual.
"""
import math
import os
import sys

import bpy
from mathutils import Vector

args = sys.argv[sys.argv.index("--") + 1:]
models, out_dir = args[0], args[1]
only = set(args[2:])

S = 0.05  # klotski cell
K1 = [("cao", 2, 2, 2, 1), ("guan", 2, 1, 2, 0), ("general_张飞", 1, 2, 0, 3), ("general_赵云", 1, 2, 3, 3),
      ("general_马超", 1, 2, 0, 0), ("general_黄忠", 1, 2, 2, 3), ("bing", 1, 1, 1, 4), ("bing", 1, 1, 0, 2),
      ("bing", 1, 1, 1, 0), ("bing", 1, 1, 1, 3)]
DG = [("column", 0, 0, 0, 0), ("栌斗", 0, 0, 0, 0), ("华栱", 0, 0.036, 0.0375, 0), ("泥道栱", 0, 0.036, 0, 0),
      ("散斗", 0, 0.081, 0, 90), ("散斗", -0.09, 0.081, 0, 0), ("散斗", 0.09, 0.081, 0, 0), ("交互斗", 0, 0.081, 0.105, 0),
      ("令栱", 0, 0.096, 0.105, 0), ("耍头", 0, 0.096, 0.04, 0), ("散斗", -0.08, 0.141, 0.105, 0),
      ("散斗", 0.08, 0.141, 0.105, 0), ("柱头枋", 0, 0.096, 0, 0), ("撩檐枋", 0, 0.156, 0.105, 0)]

# relic id -> (glb, [(node, x, y, z, yaw)]); an empty list draws every node where it was modeled.
RELICS = {
    "lock": ("burr.glb", [("stick0", 0.07, 0, 0, 0), ("stick1", -0.07, 0, 0, 0), ("stick2", 0, 0.07, 0, 0),
                          ("stick3", 0, -0.07, 0, 0), ("stick4", 0, 0, 0.07, 0), ("stick5", 0, 0, -0.07, 0)]),
    "klotski": ("klotski.glb", [("board", 0, 0, 0, 0)] + [(n, (x + w / 2 - 2) * S, 0, (y + h / 2 - 2.5) * S, 0) for n, w, h, x, y in K1]),
    "box": ("box.glb", [("box_body", 0, 0, 0, 0), ("panel0", 0, 0.02, 0, 0), ("panel1", 0.02, 0, 0, 0), ("panel2", 0, -0.0, 0, 0),
                        ("panel3", -0.01, 0, 0, 0), ("panel4", -0.12, 0, 0, 0)]),
    "dougong": ("dougong.glb", [("dg_" + n, x, y, z, yaw) for n, x, y, z, yaw in DG]),
    "wordlock": ("wordlock.glb", [("wl_body", 0, 0, 0, 0)] + [(f"wl_ring{i}", -0.0525 + 0.035 * i, 0.075, 0, 0) for i in range(4)]),
    "padlock": ("padlock.glb", [("pl_body", 0, 0, 0, 0), ("pl_0", 0, -0.02, 0, 0), ("pl_1", 0, 0, -0.06, 0), ("pl_2", -0.01, 0, 0, 0),
                                ("pl_3", 0, 0.03, 0, 0), ("pl_4", 0, 0, 0, 0)]),
    "censer": ("censer.glb", [("cs_stand", 0, 0, 0, 0), ("cs_下半球", 0, 0, 0, 0), ("cs_外环", 0, 0.07, 0, 0), ("cs_内环", 0, 0.07, 0, 90),
                              ("cs_香盂", 0, 0.07, 0, 0), ("cs_上半球", 0, 0.13, 0, 0)]),
    "mirror": ("mirror.glb", [("mr_base", 0, 0, 0, 0), ("mr_yoke", 0, 0.12, 0, 0), ("mr_face", 0, 0.12, 0, 0),
                              ("mr_lamp", 0, 0, 0, 0), ("mr_screen", 0, 0, 0, 0)]),
    "chariot": ("chariot.glb", [("ch_body", 0, 0, 0, 0), ("ch_立轮", -0.07, 0.04, 0, 90), ("ch_小平轮", -0.07, 0.075, 0, 0),
                                ("ch_大平轮", 0, 0.075, 0, 0), ("ch_木人", 0, 0.1, 0, 0)]),
    "armillary": ("armillary.glb", [("am_base", 0, 0, 0, 0), ("am_ring", 0, 0.14, 0, 0), ("am_tube", 0, 0.14, 0, 0)]),
    "waterclock": ("waterclock.glb", [("wc_frame", 0, 0, 0, 0), ("wc_退水壶", 0, 0, 0, 0), ("wc_枢轮", 0, 0.13, 0, 0),
                                      ("wc_天池", -0.12, 0.23, 0, 0), ("wc_天衡", 0, 0.25, -0.05, 0), ("wc_天关", 0.08, 0.23, 0, 0),
                                      ("wc_左天锁", -0.04, 0.2, 0.05, 0), ("wc_右天锁", 0.04, 0.2, 0.05, 0), ("wc_浑象", 0, 0.32, -0.05, 0)]),
    "chest": ("chest.glb", [("fc_body", 0, 0, 0, 0), ("fc_door", 0, 0, 0.1, 0), ("fc_鲁班尺", 0, 0.116, 0.05, 0),
                            ("fc_铜印", -0.08, 0.116, -0.045, 0), ("fc_星图", 0.08, 0.116, -0.045, 0)]),
}

for rid, (glb, places) in RELICS.items():
    if only and rid not in only:
        continue
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=os.path.join(models, glb))
    meshes = {o.name: o for o in bpy.data.objects if o.type == "MESH"}
    paper = bpy.data.materials.new("paper")
    paper.use_nodes = True
    bsdf = paper.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (0.95, 0.92, 0.85, 1)
    bsdf.inputs["Roughness"].default_value = 1.0
    used = []
    for node, x, y, z, yaw in places:
        src = meshes.get(node)
        if src is None:
            continue
        ob = src.copy()
        bpy.context.scene.collection.objects.link(ob)
        ob.location = src.location + Vector((x, -z, y))
        ob.rotation_mode = "XYZ"
        ob.rotation_euler = (src.rotation_euler[0], src.rotation_euler[1], src.rotation_euler[2] + math.radians(yaw))
        used.append(ob)
    for o in meshes.values():
        bpy.data.objects.remove(o)
    for o in used:
        o.data.materials.clear()
        o.data.materials.append(paper)
    sc = bpy.context.scene
    bpy.context.view_layer.update()
    lo = Vector((min(min((o.matrix_world @ Vector(c))[i] for c in o.bound_box) for o in used) for i in range(3)))
    hi = Vector((max(max((o.matrix_world @ Vector(c))[i] for c in o.bound_box) for o in used) for i in range(3)))
    center, radius = (lo + hi) / 2, (hi - lo).length / 2
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    cam.data.lens = 50
    sc.collection.objects.link(cam)
    sc.camera = cam
    d = Vector((0.6, -1.0, 0.55)).normalized()
    cam.location = center + d * radius / math.tan(math.atan(18 / 50)) * 1.45
    cam.rotation_euler = (center - cam.location).to_track_quat("-Z", "Y").to_euler()
    light = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    light.data.energy = 2.5
    light.rotation_euler = (math.radians(40), 0, math.radians(30))
    sc.collection.objects.link(light)
    world = bpy.data.worlds.new("w")
    sc.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.95, 0.92, 0.85, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 1.0
    sc.render.engine = "CYCLES"
    sc.cycles.samples = 8
    sc.cycles.use_denoising = True
    sc.view_settings.view_transform = "Standard"
    sc.render.resolution_x, sc.render.resolution_y = 900, 560
    sc.render.use_freestyle = True
    sc.render.line_thickness_mode = "ABSOLUTE"
    sc.render.line_thickness = 1.6
    fs = sc.view_layers[0].freestyle_settings
    ls = fs.linesets[0] if len(fs.linesets) else fs.linesets.new("ink")
    if ls.linestyle is None:
        ls.linestyle = bpy.data.linestyles.new("ink")
    ls.select_by_visibility = True
    ls.select_crease = True
    ls.select_silhouette = True
    ls.select_border = True
    ls.linestyle.color = (0.12, 0.08, 0.05)
    sc.render.film_transparent = False
    sc.render.image_settings.file_format = "PNG"
    sc.render.filepath = os.path.join(out_dir, f"atlas_{rid}.png")
    bpy.ops.render.render(write_still=True)
    print("drew", rid)
