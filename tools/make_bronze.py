"""Chapter two models: 文字锁, 花旗锁, 被中香炉, 透光镜 (one GLB each).

blender -b --factory-startup --python tools/make_bronze.py -- <out_dir>
Numbers mirror Bronze.kt (object frame: meters, +y up, +z toward the player). Parts that move are
modeled around the point the game turns or slides them from:
  wordlock.glb  wl_body, wl_ring0..3 (each around its own center on the shaft; face k shows
                character k and faces the player when the ring is turned k stops)
  padlock.glb   pl_body, pl_0..4 (cover, key, catch, shackle, ring) where they sit locked
  censer.glb    cs_stand, cs_下半球 (origin at its bottom), cs_外环, cs_内环, cs_香盂, cs_上半球
                (origin at the sphere's center)
  mirror.glb    mr_base, mr_yoke and mr_face (around the pivot), mr_lamp, mr_screen, mr_words
"""
import math
import os
import sys

import bmesh
import bpy

sys.path.insert(0, os.path.dirname(__file__))
import woodkit as wk  # noqa: E402

out = sys.argv[sys.argv.index("--") + 1:][0]

RINGS = ["天地玄黄宇宙", "工巧匠心木石", "开合机关金玉", "物器象形春秋"]


def mats():
    return dict(
        bronze=wk.metal("bronze"), gilt=wk.metal("gilt"), brass=wk.metal("brass"), wood=wk.wood("dark_wood"),
        teak=wk.wood("teak_veneer"), dark=wk.flat("shadow", (0.02, 0.015, 0.01), roughness=0.9),
        paper=wk.flat("paper", (0.86, 0.8, 0.66), roughness=0.85),
        polished=wk.flat("polished_bronze", (0.86, 0.78, 0.62), roughness=0.08, metallic=1.0),
        flame=wk.flat("flame", (1.0, 0.75, 0.35), roughness=0.5, emission=(1.0, 0.62, 0.25)),
        glow=wk.flat("glow_text", (1.0, 0.85, 0.5), roughness=0.4, emission=(1.0, 0.8, 0.45)),
        lacquer=wk.flat("red_lacquer", (0.3, 0.04, 0.02), roughness=0.35),
    )


# ---- 文字锁 -------------------------------------------------------------------------------------
wk.reset()
m = mats()
SHAFT_Y = 0.075
APOTHEM, WIDTH = 0.025, 0.026
objects = []
for i, chars in enumerate(RINGS):
    x = -0.0525 + 0.035 * i
    # A hexagonal ring around the shaft (x axis); face k's normal is at k·60° from +z toward +y.
    bm = bmesh.new()
    R = APOTHEM / math.cos(math.radians(30))
    rings = []
    for side in (-1, 1):
        ring = []
        for k in range(6):
            a = math.radians(k * 60 + 30)
            ring.append(bm.verts.new(wk.to_blender((side * WIDTH / 2, R * math.sin(a), R * math.cos(a)))))
        rings.append(ring)
    bm.faces.new(rings[0])
    bm.faces.new(list(reversed(rings[1])))
    for k in range(6):
        j = (k + 1) % 6
        bm.faces.new([rings[0][k], rings[0][j], rings[1][j], rings[1][k]])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(f"wl_ring{i}")
    bm.to_mesh(me)
    bm.free()
    ring = bpy.data.objects.new(f"wl_ring{i}", me)
    bpy.context.scene.collection.objects.link(ring)
    hole = wk.cylinder("hole", 0.0075, WIDTH * 2, (0, 0, 0), m["bronze"], axis="x")
    wk.subtract(ring, hole)
    wk.bevel(ring, width=0.0012, segments=2)
    wk.cube_uv(ring, 8)
    wk.assign(ring, m["bronze"])
    letters = []
    for k, ch in enumerate(chars):
        t = wk.text_mesh(f"c{k}", ch, 0.019, (0, 0, APOTHEM + 0.0002), facing="+z", mat=m["gilt"])
        # Face k sits k·60° round from the front (toward +y): turn the letter there.
        wk.turn(t, "x", -k * 60)
        letters.append(t)
    ring.data.materials.append(m["gilt"])
    ring = wk.join([ring] + letters, f"wl_ring{i}")
    objects.append(ring)

# Body: wooden base, bronze posts and shaft, a gilt window frame, and the word on a plate.
parts = [
    wk.box("base", (0.19, 0.012, 0.09), (0, 0.006, 0), m["wood"], bevel_width=0.002),
    wk.box("post_l", (0.012, 0.1, 0.03), (-0.082, 0.062, 0), m["bronze"], bevel_width=0.0015),
    wk.box("post_r", (0.012, 0.1, 0.03), (0.082, 0.062, 0), m["bronze"], bevel_width=0.0015),
    wk.cylinder("shaft", 0.0065, 0.17, (0, SHAFT_Y, 0), m["brass"], axis="x"),
    wk.box("bar_top", (0.16, 0.008, 0.004), (0, SHAFT_Y + 0.016, 0.031), m["gilt"], bevel_width=0.0008),
    wk.box("bar_low", (0.16, 0.008, 0.004), (0, SHAFT_Y - 0.016, 0.031), m["gilt"], bevel_width=0.0008),
    wk.box("plate", (0.1, 0.022, 0.004), (0, 0.024, 0.04), m["lacquer"], bevel_width=0.0008),
    wk.text_mesh("word", "天工开物", 0.015, (0, 0.024, 0.0422), facing="+z", mat=m["gilt"]),
]
objects.append(wk.join(parts, "wl_body"))
wk.export(os.path.join(out, "wordlock.glb"), objects)

# ---- 花旗锁 -------------------------------------------------------------------------------------
# v0.10.1: the shapes match Bronze.padlockParts / padlockBodyBoxes, which the game collides: a real keyhole (round
# hole for the shaft, slot under it for the bit) the key fits into, holes for the shackle's legs, a pocket for the
# catch; the plate stands just proud of the face and the gilt 平安, and the ring hangs clear of both.
wk.reset()
m = mats()
KEY_TIP, KEYHOLE_Y, KEYHOLE_R, FLOOR = 0.045, 0.05, 0.0042, -0.0155
COVER_Z = 0.0254


def cut_dark(ob, cutter):
    """Cuts [cutter] out of [ob]; the new inner faces take the cutter's (dark) material."""
    md = ob.modifiers.new("cut", "BOOLEAN")
    md.operation = "DIFFERENCE"
    md.object = cutter
    md.solver = "EXACT"
    md.material_mode = "TRANSFER"
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.modifier_apply(modifier="cut")
    bpy.data.objects.remove(cutter)


body = [
    wk.box("body", (0.1, 0.08, 0.04), (0, 0.04, 0), m["bronze"], bevel_width=0.006),
    # Raised border and face on the front.
    wk.box("border", (0.086, 0.066, 0.002), (0, 0.04, 0.0205), m["brass"], bevel_width=0.0008),
    wk.box("face", (0.08, 0.06, 0.002), (0, 0.04, 0.0215), m["bronze"], bevel_width=0.0005),
    wk.text_mesh("words", "平安", 0.016, (0, 0.022, 0.0228), facing="+z", mat=m["gilt"]),
]
lock_body = wk.join(body, "pl_body")
depth = 0.03 - FLOOR
cutters = [
    wk.cylinder("keyhole", KEYHOLE_R, depth, (0, KEYHOLE_Y, FLOOR + depth / 2), m["dark"], axis="z", verts=32),
    wk.box("keyslot", (0.004, KEYHOLE_Y - 0.0378, depth), (0, (KEYHOLE_Y + 0.0378) / 2, FLOOR + depth / 2), m["dark"], bevel_width=0),
    wk.cylinder("hole_l", 0.0052, 0.031, (-0.035, 0.0745, 0), m["dark"], verts=24),
    wk.cylinder("hole_r", 0.0052, 0.031, (0.035, 0.0745, 0), m["dark"], verts=24),
    wk.box("pocket", (0.0145, 0.0206, 0.0166), (0.05275, 0.04, 0), m["dark"], bevel_width=0),
]
for c in cutters:
    cut_dark(lock_body, c)
objects = [lock_body]
cover = [
    wk.box("cover", (0.03, 0.03, 0.004), (0, 0.05, COVER_Z), m["gilt"], bevel_width=0.001),
    wk.torus("cover_ring", 0.009, 0.0012, (0, 0.05, COVER_Z + 0.0025), m["brass"], axis="z", segments=32),
]
objects.append(wk.join(cover, "pl_0"))
key = [
    wk.cylinder("shaft", 0.0035, 0.06, (0, KEYHOLE_Y, KEY_TIP + 0.03), m["brass"], axis="z", verts=16),
    wk.torus("bow", 0.009, 0.0025, (0, KEYHOLE_Y, KEY_TIP + 0.068), m["brass"], axis="x", segments=32),
    wk.box("bit", (0.003, 0.011, 0.012), (0, 0.0445, KEY_TIP + 0.006), m["brass"], bevel_width=0.0005),
]
objects.append(wk.join(key, "pl_1"))
objects.append(wk.join([wk.box("catch", (0.006, 0.02, 0.016), (0.053, 0.04, 0), m["gilt"], bevel_width=0.001)], "pl_2"))
shackle = [
    wk.cylinder("leg_l", 0.005, 0.075, (-0.035, 0.0975, 0), m["brass"], verts=24),
    wk.cylinder("leg_r", 0.005, 0.075, (0.035, 0.0975, 0), m["brass"], verts=24),
    wk.tube("top", wk.arc((0, 0.135, 0), 0.035, 0, 180, plane="xy", n=24), 0.005, m["brass"]),
]
objects.append(wk.join(shackle, "pl_3"))
objects.append(wk.join([wk.torus("ring", 0.012, 0.0022, (-0.035, 0.026, 0.0255), m["brass"], axis="z", segments=40)], "pl_4"))
wk.export(os.path.join(out, "padlock.glb"), objects)

# ---- 被中香炉 -----------------------------------------------------------------------------------
wk.reset()
m = mats()
R = 0.07
C = (0, R, 0)


def lattice(name, lower):
    """An openwork half-shell: latitude rings and meridian arcs, gilt silver."""
    lats = [-70, -45, -20, 0] if lower else [20, 45, 70]
    rods = []
    for la in lats:
        r = R * math.cos(math.radians(la))
        y = R * math.sin(math.radians(la))
        rods.append(wk.tube(f"lat{la}", wk.arc((0, R + y, 0), r, 0, 360, plane="xz", n=48), 0.0022, m["gilt"], closed=True))
    a0, a1 = (-90, 0) if lower else (0, 90)
    for k in range(8):
        arc = wk.arc(C, R, a0, a1, plane="yz", n=16)
        rod = wk.tube(f"mer{k}", arc, 0.0018, m["gilt"])
        wk.turn(rod, "y", k * 22.5, pivot=C)
        rods.append(rod)
    if not lower:
        rods.append(wk.sphere("knob", 0.008, (0, 2 * R + 0.004, 0), m["gilt"], segments=16))
    # v0.10.1: no foot under the lower half: the ball rolls in its cradle once built, and the foot's rim swept 2.4 mm
    # into the cradle.
    ob = wk.join(rods, name)
    if not lower:
        wk.move(ob, (0, -R, 0))  # origin at the sphere's center
    return ob


objects = [lattice("cs_下半球", True), lattice("cs_上半球", False)]
# v0.10.1: the outer ring stands in the x-y plane, so its pins (along x) leave its rim and reach the shell (it stood in
# the y-z plane and the pins floated on its axis). At rest all three rings and the bowl line up, as a gimbal does.
outer = [
    wk.torus("ring", 0.058, 0.003, (0, 0, 0), m["gilt"], axis="z"),
    wk.cylinder("pin_l", 0.0028, 0.014, (-0.064, 0, 0), m["brass"], axis="x", verts=16),
    wk.cylinder("pin_r", 0.0028, 0.014, (0.064, 0, 0), m["brass"], axis="x", verts=16),
]
objects.append(wk.join(outer, "cs_外环"))
inner = [
    wk.torus("ring", 0.047, 0.0028, (0, 0, 0), m["gilt"], axis="x"),
    wk.cylinder("pin_t", 0.0025, 0.012, (0, 0.051, 0), m["brass"], verts=16),
    wk.cylinder("pin_b", 0.0025, 0.012, (0, -0.051, 0), m["brass"], verts=16),
]
objects.append(wk.join(inner, "cs_内环"))
bpy.ops.mesh.primitive_uv_sphere_add(radius=0.03, segments=32, ring_count=16)
bowl = bpy.context.object
bowl.location = wk.to_blender((0, -0.004, 0))
bpy.ops.object.transform_apply(location=True)
cutter = wk.box("cut", (0.08, 0.04, 0.08), (0, 0.016, 0), m["gilt"], bevel_width=0)
wk.subtract(bowl, cutter)
bowl.data.materials.append(m["gilt"])
bowl_parts = [
    bowl,
    wk.torus("rim", 0.03, 0.0025, (0, -0.004, 0), m["gilt"], axis="y", segments=48),
    wk.cylinder("ember", 0.026, 0.004, (0, -0.012, 0), m["dark"], verts=32),
    wk.cylinder("pin_l", 0.0025, 0.016, (-0.038, -0.004, 0), m["brass"], axis="x", verts=16),
    wk.cylinder("pin_r", 0.0025, 0.016, (0.038, -0.004, 0), m["brass"], axis="x", verts=16),
]
for p in bowl_parts:
    p.data.materials.clear()
    p.data.materials.append(m["dark"] if p.name.startswith("ember") else m["gilt"])
objects.append(wk.join(bowl_parts, "cs_香盂"))
stand = [
    wk.cylinder("disc", 0.045, 0.008, (0, -0.026, 0), m["wood"], verts=48, bevel_width=0.0015),
    wk.cylinder("neck", 0.012, 0.02, (0, -0.012, 0), m["wood"], verts=32),
    # The cradle 3 mm lower (v0.10.1): the ball's lowest ring sat 1 mm into it, and rolling it needs room.
    wk.torus("cradle", 0.024, 0.003, (0, -0.003, 0), m["brass"], axis="y", segments=48),
]
objects.append(wk.join(stand, "cs_stand"))
wk.export(os.path.join(out, "censer.glb"), objects)

# ---- 透光镜 -------------------------------------------------------------------------------------
wk.reset()
m = mats()
MIRROR = (0, 0.12, 0)
LAMP = (-0.32, 0.26, -0.06)
SCREEN = (0.09, 0.222, -0.29)
base = [
    wk.box("base", (0.12, 0.015, 0.08), (0, 0.0075, 0), m["wood"], bevel_width=0.002),
    wk.cylinder("post", 0.008, 0.035, (0, 0.0325, 0), m["bronze"], verts=24),
]
objects = [wk.join(base, "mr_base")]
yoke = [
    wk.cylinder("spindle", 0.006, 0.012, (0, -0.064, 0), m["bronze"], verts=24),
    wk.box("bottom", (0.132, 0.008, 0.01), (0, -0.064, 0), m["bronze"], bevel_width=0.001),
    wk.box("arm_l", (0.008, 0.07, 0.01), (-0.064, -0.03, 0), m["bronze"], bevel_width=0.001),
    wk.box("arm_r", (0.008, 0.07, 0.01), (0.064, -0.03, 0), m["bronze"], bevel_width=0.001),
    wk.cylinder("pin_l", 0.003, 0.01, (-0.059, 0, 0), m["brass"], axis="x", verts=16),
    wk.cylinder("pin_r", 0.003, 0.01, (0.059, 0, 0), m["brass"], axis="x", verts=16),
]
objects.append(wk.join(yoke, "mr_yoke"))
face = [
    wk.cylinder("disc", 0.055, 0.008, (0, 0, -0.001), m["bronze"], axis="z", verts=64, bevel_width=0.001),
    wk.cylinder("glass", 0.052, 0.001, (0, 0, 0.0035), m["polished"], axis="z", verts=64),
    wk.sphere("knob", 0.009, (0, 0, -0.006), m["bronze"], segments=16),
    wk.torus("band", 0.03, 0.0012, (0, 0, -0.0055), m["bronze"], axis="z", segments=48),
    wk.text_mesh("words", "见日之光", 0.011, (0, -0.042 + 0.042, -0.0056), facing="-z", mat=m["gilt"]),
]
wk.move(face[-1], (0, 0.018, 0))
objects.append(wk.join(face, "mr_face"))
lamp = [
    wk.cylinder("foot", 0.025, 0.006, (LAMP[0], 0.003, LAMP[2]), m["bronze"], verts=32),
    wk.cylinder("stem", 0.004, 0.23, (LAMP[0], 0.12, LAMP[2]), m["bronze"], verts=16),
    wk.cylinder("dish", 0.018, 0.006, (LAMP[0], LAMP[1] - 0.012, LAMP[2]), m["bronze"], verts=32),
    wk.sphere("flame", 0.008, LAMP, m["flame"], segments=16),
]
objects.append(wk.join(lamp, "mr_lamp"))


def screen_parts(local):
    """The screen built facing +z around its own middle, then turned to face the mirror."""
    out_parts = []
    for ob in local:
        yaw = math.degrees(math.atan2(MIRROR[0] - SCREEN[0], MIRROR[2] - SCREEN[2]))
        wk.turn(ob, "y", yaw)
        wk.move(ob, SCREEN)
        out_parts.append(ob)
    return out_parts


frame_parts = screen_parts([
    wk.box("paper", (0.15, 0.1, 0.002), (0, 0, 0), m["paper"], bevel_width=0),
    wk.box("top", (0.17, 0.01, 0.01), (0, 0.055, 0), m["wood"], bevel_width=0.001),
    wk.box("bottom", (0.17, 0.01, 0.01), (0, -0.055, 0), m["wood"], bevel_width=0.001),
    wk.box("left", (0.01, 0.12, 0.01), (-0.08, 0, 0), m["wood"], bevel_width=0.001),
    wk.box("right", (0.01, 0.12, 0.01), (0.08, 0, 0), m["wood"], bevel_width=0.001),
    wk.box("leg_l", (0.008, SCREEN[1] - 0.06, 0.008), (-0.075, -0.06 - (SCREEN[1] - 0.06) / 2, 0), m["wood"], bevel_width=0.001),
    wk.box("leg_r", (0.008, SCREEN[1] - 0.06, 0.008), (0.075, -0.06 - (SCREEN[1] - 0.06) / 2, 0), m["wood"], bevel_width=0.001),
])
objects.append(wk.join(frame_parts, "mr_screen"))
words = screen_parts([
    wk.text_mesh("words", "见日之光", 0.026, (0, 0.012, 0.0025), facing="+z", mat=m["glow"]),
    wk.torus("halo", 0.045, 0.0012, (0, 0, 0.0022), m["glow"], axis="z", segments=48),
])
objects.append(wk.join(words, "mr_words"))
wk.export(os.path.join(out, "mirror.glb"), objects)
