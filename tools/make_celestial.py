"""Chapter three and the closing chest: 指南车, 浑天仪, 水运仪象台, 总机关匣 (one GLB each).

blender -b --factory-startup --python tools/make_celestial.py -- <out_dir>
Numbers mirror Celestial.kt. Parts are modeled around the point their slot gives (gears and the
water wheel around their hubs, the rest from the middle of their underside):
  chariot.glb     ch_body, ch_立轮 (in the xy plane: its slot turns it across), ch_小平轮,
                  ch_大平轮, ch_中平轮, ch_木人
  armillary.glb   am_base (stand, horizon and meridian rings), am_ring (四游环, about its own y
                  axis: the game tilts it onto the pole), am_tube (窥管, along z)
  waterclock.glb  wc_frame, wc_<part>
  chest.glb       fc_body (recesses on top), fc_door, fc_plaque, fc_鲁班尺, fc_铜印, fc_星图
"""
import math
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(__file__))
import woodkit as wk  # noqa: E402

out = sys.argv[sys.argv.index("--") + 1:][0]


def mats():
    return dict(
        bronze=wk.metal("bronze"), gilt=wk.metal("gilt"), brass=wk.metal("brass"), wood=wk.wood("dark_wood"),
        rose=wk.wood("rosewood_veneer1"), teak=wk.wood("teak_veneer"), walnut=wk.wood("walnut_veneer"),
        lacquer=wk.flat("red_lacquer", (0.3, 0.04, 0.02), roughness=0.35),
        cinnabar=wk.flat("cinnabar_lacquer", (0.28, 0.035, 0.018), roughness=0.3),
        indigo=wk.flat("indigo_lacquer", (0.02, 0.04, 0.12), roughness=0.35),
        water=wk.flat("water", (0.25, 0.45, 0.5), roughness=0.05),
        skin=wk.flat("figure", (0.8, 0.65, 0.45), roughness=0.6),
    )


def gear(name, radius, teeth, thickness, mat, axis="y"):
    """A spur gear: a disc with square teeth round its rim, around its own hub, about [axis]."""
    parts = [wk.cylinder("disc", radius * 0.86, thickness, (0, 0, 0), mat, axis="y", verts=48)]
    for k in range(teeth):
        a = 360.0 * k / teeth
        t = wk.box(f"t{k}", (radius * 0.16, thickness, 2 * math.pi * radius / teeth * 0.5), (radius * 0.92, 0, 0), mat, bevel_width=0)
        wk.turn(t, "y", a)
        parts.append(t)
    parts.append(wk.cylinder("hub", radius * 0.18, thickness * 1.6, (0, 0, 0), mat, axis="y", verts=24))
    ob = wk.join(parts, name)
    if axis == "z":
        wk.turn(ob, "x", 90)
    elif axis == "x":
        wk.turn(ob, "z", 90)
    return ob


# ---- 指南车 -------------------------------------------------------------------------------------
wk.reset()
m = mats()
body = [
    wk.box("floor", (0.17, 0.01, 0.12), (0, 0.006, 0), m["lacquer"], bevel_width=0.0015),
    wk.cylinder("axle", 0.004, 0.24, (0, 0.04, 0), m["bronze"], axis="x", verts=16),
    wk.box("shaft_l", (0.008, 0.008, 0.14), (-0.04, 0.03, 0.12), m["wood"], bevel_width=0.001),
    wk.box("shaft_r", (0.008, 0.008, 0.14), (0.04, 0.03, 0.12), m["wood"], bevel_width=0.001),
    wk.box("yoke", (0.1, 0.008, 0.008), (0, 0.03, 0.19), m["wood"], bevel_width=0.001),
]
for side in (-1, 1):
    x = side * 0.11
    body.append(wk.torus(f"tyre{side}", 0.045, 0.004, (x, 0.045, 0), m["wood"], axis="x"))
    body.append(wk.cylinder(f"nave{side}", 0.008, 0.016, (x, 0.045, 0), m["bronze"], axis="x", verts=24))
    for k in range(12):
        sp = wk.box(f"spoke{side}{k}", (0.004, 0.04, 0.004), (x, 0.045 + 0.022, 0), m["wood"], bevel_width=0)
        wk.turn(sp, "x", k * 30, pivot=(x, 0.045, 0))
        body.append(sp)
objects = [wk.join(body, "ch_body")]
objects.append(gear("ch_立轮", 0.03, 18, 0.008, m["bronze"], axis="z"))
objects.append(gear("ch_小平轮", 0.024, 14, 0.008, m["bronze"]))
objects.append(gear("ch_大平轮", 0.048, 30, 0.009, m["brass"]))
objects.append(gear("ch_中平轮", 0.036, 22, 0.008, m["bronze"]))
figure = [
    wk.cylinder("post", 0.004, 0.02, (0, 0.01, 0), m["bronze"], verts=16),
    wk.cylinder("robe", 0.012, 0.04, (0, 0.04, 0), m["lacquer"], verts=24),
    wk.sphere("head", 0.009, (0, 0.068, 0), m["skin"], segments=16),
    wk.box("hat", (0.02, 0.004, 0.012), (0, 0.078, 0), m["indigo"], bevel_width=0.0008),
    # The arm points toward +z: south, toward the player.
    wk.box("arm", (0.005, 0.005, 0.03), (0.008, 0.05, 0.016), m["lacquer"], bevel_width=0.0008),
]
objects.append(wk.join(figure, "ch_木人"))
wk.export(os.path.join(out, "chariot.glb"), objects)

# ---- 浑天仪 -------------------------------------------------------------------------------------
wk.reset()
m = mats()
C = (0, 0.14, 0)
base = [
    wk.cylinder("plinth", 0.08, 0.012, (0, 0.006, 0), m["wood"], verts=48, bevel_width=0.002),
    wk.cylinder("column", 0.008, 0.035, (0, 0.03, 0), m["bronze"], verts=24),
    wk.torus("horizon", 0.105, 0.004, C, m["bronze"], axis="y"),
    wk.torus("meridian", 0.105, 0.0035, C, m["bronze"], axis="x"),
]
for k in range(4):
    a = math.radians(45 + 90 * k)
    leg = wk.tube(f"leg{k}", [(0.07 * math.cos(a), 0.012, 0.07 * math.sin(a)), (0.1 * math.cos(a), 0.14, 0.1 * math.sin(a))], 0.004, m["bronze"])
    base.append(leg)
# The pole: a rod along the polar axis (tilted back by 90° - 35°).
pole = wk.cylinder("pole", 0.0025, 0.22, (0, 0, 0), m["brass"], verts=16)
wk.turn(pole, "x", -(90 - 35))
wk.move(pole, C)
base.append(pole)
objects = [wk.join(base, "am_base")]
ring = [
    wk.torus("ring", 0.088, 0.003, (0, 0, 0), m["gilt"], axis="x"),
    wk.torus("ring2", 0.088, 0.003, (0.012, 0, 0), m["gilt"], axis="x"),
]
for k in range(24):
    tick = wk.box(f"tick{k}", (0.012, 0.0015, 0.004), (0.006, 0, 0.088), m["gilt"], bevel_width=0)
    wk.turn(tick, "x", k * 15)
    ring.append(tick)
objects.append(wk.join(ring, "am_ring"))
tube = [
    wk.cylinder("tube", 0.006, 0.17, (0, 0, 0), m["bronze"], axis="z", verts=24),
    wk.torus("eye", 0.007, 0.0015, (0, 0, -0.085), m["gilt"], axis="z", segments=24),
    wk.torus("mouth", 0.007, 0.0015, (0, 0, 0.085), m["gilt"], axis="z", segments=24),
    wk.cylinder("pivot", 0.003, 0.03, (0, 0, 0), m["brass"], axis="x", verts=16),
]
objects.append(wk.join(tube, "am_tube"))
wk.export(os.path.join(out, "armillary.glb"), objects)

# ---- 水运仪象台 ---------------------------------------------------------------------------------
wk.reset()
m = mats()
frame = [wk.box("floor", (0.32, 0.01, 0.2), (0, -0.005, 0), m["walnut"], bevel_width=0.002)]
for x in (-0.15, 0.15):
    for z in (-0.09, 0.09):
        frame.append(wk.box(f"post{x}{z}", (0.012, 0.36, 0.012), (x, 0.18, z), m["rose"], bevel_width=0.001))
frame.append(wk.box("top", (0.32, 0.01, 0.1), (0, 0.285, -0.05), m["rose"], bevel_width=0.001))
frame.append(wk.box("roof", (0.34, 0.012, 0.22), (0, 0.366, 0), m["lacquer"], bevel_width=0.002))
frame.append(wk.box("axle_l", (0.012, 0.012, 0.012), (-0.1, 0.13, 0), m["bronze"], bevel_width=0))
frame.append(wk.box("axle_r", (0.012, 0.012, 0.012), (0.1, 0.13, 0), m["bronze"], bevel_width=0))
frame.append(wk.box("pedestal", (0.03, 0.006, 0.03), (0, 0.288, -0.05), m["bronze"], bevel_width=0.0008))
objects = [wk.join(frame, "wc_frame")]
objects.append(wk.join([
    wk.box("basin", (0.16, 0.03, 0.1), (0, 0.015, 0), m["wood"], bevel_width=0.002),
    wk.box("water", (0.15, 0.002, 0.09), (0, 0.027, 0), m["water"], bevel_width=0),
], "wc_退水壶"))
wheel = [
    wk.torus("rim_l", 0.09, 0.003, (-0.015, 0, 0), m["rose"], axis="x"),
    wk.torus("rim_r", 0.09, 0.003, (0.015, 0, 0), m["rose"], axis="x"),
    wk.cylinder("hub", 0.012, 0.05, (0, 0, 0), m["bronze"], axis="x", verts=24),
    wk.cylinder("axle", 0.004, 0.19, (0, 0, 0), m["bronze"], axis="x", verts=16),
]
for k in range(24):
    sp = wk.box(f"spoke{k}", (0.004, 0.08, 0.004), (0, 0.045, 0), m["rose"], bevel_width=0)
    wk.turn(sp, "x", k * 15)
    bucket = wk.box(f"scoop{k}", (0.026, 0.014, 0.016), (0, 0.093, 0), m["teak"], bevel_width=0.0008)
    wk.turn(bucket, "x", k * 15)
    wheel += [sp, bucket]
objects.append(wk.join(wheel, "wc_枢轮"))
objects.append(wk.join([
    wk.box("tank", (0.06, 0.05, 0.06), (0, 0.025, 0), m["wood"], bevel_width=0.0015),
    wk.box("tank_water", (0.05, 0.002, 0.05), (0, 0.046, 0), m["water"], bevel_width=0),
    wk.box("spout", (0.05, 0.008, 0.01), (0.045, 0.01, 0), m["bronze"], bevel_width=0.0008),
], "wc_天池"))
objects.append(wk.join([
    wk.box("beam", (0.2, 0.008, 0.01), (0, 0.004, 0), m["bronze"], bevel_width=0.0008),
    wk.box("fulcrum", (0.012, 0.02, 0.012), (0, -0.006, 0), m["brass"], bevel_width=0.0008),
    wk.sphere("weight", 0.01, (-0.095, 0.004, 0), m["bronze"], segments=16),
], "wc_天衡"))
objects.append(wk.join([wk.box("gate", (0.01, 0.04, 0.012), (0, 0.02, 0), m["brass"], bevel_width=0.0008)], "wc_天关"))
objects.append(wk.join([wk.box("pawl", (0.01, 0.03, 0.008), (0, 0.015, 0), m["brass"], bevel_width=0.0008)], "wc_左天锁"))
objects.append(wk.join([wk.box("pawl", (0.01, 0.03, 0.008), (0, 0.015, 0), m["brass"], bevel_width=0.0008)], "wc_右天锁"))
globe = [wk.sphere("globe", 0.035, (0, 0, 0), m["indigo"], segments=32)]
for k in range(10):
    a = math.radians(k * 36)
    globe.append(wk.sphere(f"star{k}", 0.003, (0.0355 * math.cos(a) * 0.8, 0.0355 * 0.6 * math.sin(3 * a), 0.0355 * math.sin(a) * 0.8), m["gilt"], segments=8))
globe.append(wk.torus("equator", 0.036, 0.0012, (0, 0, 0), m["gilt"], axis="y", segments=48))
objects.append(wk.join(globe, "wc_浑象"))
wk.export(os.path.join(out, "waterclock.glb"), objects)

# ---- 总机关匣 -----------------------------------------------------------------------------------
wk.reset()
m = mats()
body = wk.box("body", (0.3, 0.12, 0.2), (0, 0.06, 0), m["wood"], bevel_width=0.003)
# Recesses in the top for the three treasures (4 mm deep), and the doorway in front.
for name, size, center in [
    ("ruler", (0.25, 0.01, 0.034), (0, 0.12, 0.05)),
    ("seal", (0.068, 0.01, 0.068), (-0.08, 0.12, -0.045)),
    ("chart", (0.13, 0.01, 0.13), (0.08, 0.12, -0.045)),
    ("door", (0.27, 0.105, 0.15), (0, 0.06, 0.035)),
]:
    wk.subtract(body, wk.box("cut_" + name, (size[0], size[1] if name == "door" else 0.008, size[2]), center, m["wood"], bevel_width=0))
inside = wk.box("inside", (0.26, 0.1, 0.004), (0, 0.06, -0.038), m["lacquer"], bevel_width=0)
trim = [wk.box(f"trim{k}", (0.31, 0.008, 0.008), (0, y, z), m["brass"], bevel_width=0.0008) for k, (y, z) in enumerate([(0.004, 0.101), (0.116, 0.101), (0.004, -0.101), (0.116, -0.101)])]
objects = [wk.join([body, inside] + trim, "fc_body")]
door = [
    wk.box("door", (0.268, 0.104, 0.01), (0, 0.054, 0.005), m["rose"], bevel_width=0.0015),
    wk.text_mesh("words", "天工开物", 0.022, (0, 0.06, 0.0104), facing="+z", mat=m["gilt"]),
]
objects.append(wk.join(door, "fc_door"))
plaque = [
    wk.box("plaque", (0.14, 0.07, 0.008), (0, 0.035, 0), m["cinnabar"], bevel_width=0.0015),
    wk.text_mesh("words", "天工", 0.045, (0, 0.036, 0.0042), facing="+z", mat=m["gilt"]),
]
objects.append(wk.join(plaque, "fc_plaque"))
ruler = [wk.box("ruler", (0.24, 0.006, 0.03), (0, 0.003, 0), m["cinnabar"], bevel_width=0.001)]
for k in range(25):
    ruler.append(wk.box(f"tick{k}", (0.0008, 0.0004, 0.005 if k % 5 else 0.009), (-0.12 + k * 0.01, 0.0062, 0.011), m["gilt"], bevel_width=0))
ruler.append(wk.text_mesh("words", "财病离义官劫害本", 0.012, (0, 0.0062, -0.004), facing="+y", mat=m["gilt"]))
objects.append(wk.join(ruler, "fc_鲁班尺"))
seal = [
    wk.box("seal", (0.06, 0.03, 0.06), (0, 0.015, 0), m["bronze"], bevel_width=0.002),
    wk.text_mesh("words", "天工开物", 0.02, (0, 0.0302, 0), facing="+y", mat=m["gilt"]),
]
objects.append(wk.join(seal, "fc_铜印"))
chart = [wk.cylinder("disc", 0.06, 0.006, (0, 0.003, 0), m["indigo"], verts=64, bevel_width=0.001)]
for k in range(3):
    line = wk.box(f"line{k}", (0.06, 0.0005, 0.0012), (0.03, 0.0062, 0), m["gilt"], bevel_width=0)
    wk.turn(line, "y", 90 + k * 120)
    chart.append(line)
for k in range(18):
    a = math.radians(k * 47)
    r = 0.015 + 0.04 * ((k * 37) % 100) / 100
    chart.append(wk.sphere(f"star{k}", 0.0016, (r * math.cos(a), 0.0064, r * math.sin(a)), m["gilt"], segments=8))
objects.append(wk.join(chart, "fc_星图"))
wk.export(os.path.join(out, "chest.glb"), objects)
