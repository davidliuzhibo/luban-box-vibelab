"""斗拱 parts (Song style, 《营造法式》 proportions), one node per part type, plus the bench.

blender -b --factory-startup --python tools/make_dougong.py -- <dougong.glb> <bench.glb>
Every part's origin is the middle of its underside, matching the slots in Dougong.kt; the same
numbers are repeated here (arm section 3 × 4.5 cm, blocks 3 cm tall with a 1.5 cm seat).
Nodes: dg_栌斗, dg_华栱, dg_泥道栱, dg_散斗, dg_交互斗, dg_令栱, dg_耍头, dg_柱头枋, dg_撩檐枋.
Bench: node bench (top face at y=0, 1.0 × 0.6 m) and turntable (radius 0.2 m, 3 cm tall).
"""
import math
import os
import sys

import bmesh
import bpy

sys.path.insert(0, os.path.dirname(__file__))
import woodkit as wk  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
out_dougong, out_bench = args[0], args[1]

ARM_W, ARM_H = 0.03, 0.045
DOU_H, SEAT = 0.03, 0.015
LU_H, LU_SEAT = 0.06, 0.024
HUA_BACK, HUA_FRONT, FRONT_Z = -0.06, 0.135, 0.105

wk.reset()
arm_wood = wk.wood("rosewood_veneer1")
dou_wood = wk.wood("dark_wood")
beam_wood = wk.wood("wood_table_001")


def box(sx, sy, sz, center):
    bpy.ops.mesh.primitive_cube_add(size=1)
    ob = bpy.context.object
    ob.scale = (sx, sz, sy)
    ob.location = wk.to_blender(center)
    bpy.ops.object.transform_apply(location=True, scale=True)
    return ob


def cut(ob, cutter):
    m = ob.modifiers.new("cut", "BOOLEAN")
    m.operation = "DIFFERENCE"
    m.object = cutter
    m.solver = "EXACT"
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.modifier_apply(modifier="cut")
    bpy.data.objects.remove(cutter)


def finish(ob, name, mat, grain="x"):
    ob.name = name
    wk.bevel(ob, width=0.0012, segments=2)
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.cube_project(cube_size=0.25)
    bpy.ops.object.mode_set(mode="OBJECT")
    ob.select_set(False)
    wk.assign(ob, mat)
    return ob


def profile_solid(name, pts, width, along):
    """Extrudes a side-view outline [pts] ((u along the member, y up), counter-clockwise) across
    [width]; the member runs along x or z."""
    bm = bmesh.new()
    def p3(u, y, w):
        return wk.to_blender((u, y, w) if along == "x" else (w, y, u))
    front = [bm.verts.new(p3(u, y, -width / 2)) for (u, y) in pts]
    back = [bm.verts.new(p3(u, y, width / 2)) for (u, y) in pts]
    bm.faces.new(front)
    bm.faces.new(list(reversed(back)))
    n = len(pts)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new([front[i], back[i], back[j], front[j]])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def outline(length, end="juansha"):
    """Side view of a 栱 (end="juansha": the lower ends rounded off in four facets, 《营造法式》's
    卷杀 四瓣), a 枋 (end="square") or the 耍头 (end="beak": front end cut back in a slant)."""
    L, H = length / 2, ARM_H
    if end == "square":
        return [(-L, 0), (L, 0), (L, H), (-L, H)]
    if end == "beak":
        # Back end square; front end slopes back from the top edge to the bottom.
        return [(-L, 0), (L - 0.7 * H, 0), (L, 0.55 * H), (L, H), (-L, H)]
    c, r = 1.25 * H, 0.42 * H
    right = [(L - c + c * math.cos(math.radians(a)), r - r * math.sin(math.radians(a))) for a in (0, 22.5, 45, 67.5, 90)]
    # Counter-clockwise from the bottom left: bottom, right arc (bottom to end), top, left arc.
    bottom_right = list(reversed(right))
    left = [(-u, y) for (u, y) in right]
    return [(-L + c, 0)] + bottom_right + [(L, H), (-L, H)] + left[:-1]


def arm(name, length, along, notch_top=(), notch_bottom=(), end="juansha", mat=None):
    """A 栱 or 枋 of [length] along x or z, 3 × 4.5 cm, with halving notches (half the height,
    one arm wide) where another member crosses it."""
    ob = profile_solid(name, outline(length, end), ARM_W, along)
    def at(u):
        return (u, 0, 0) if along == "x" else (0, 0, u)
    for u in notch_top:
        c = box(ARM_W + 0.0006, ARM_H / 2 + 0.001, ARM_W + 0.0006, tuple(a + b for a, b in zip(at(u), (0, ARM_H * 0.75 + 0.0005, 0))))
        cut(ob, c)
    for u in notch_bottom:
        c = box(ARM_W + 0.0006, ARM_H / 2 + 0.001, ARM_W + 0.0006, tuple(a + b for a, b in zip(at(u), (0, ARM_H * 0.25 - 0.0005, 0))))
        cut(ob, c)
    return finish(ob, name, mat or arm_wood, grain=along)


def dou(name, top, height, seat, slot="x", lu=False):
    """A 斗: square block, top [top] wide, tapering in its lower part (欹), with a seat cut in its
    top for the arm(s) it carries: along x, along z, or both ("+")."""
    ear, flat = height * 0.4, height * 0.2
    bottom = top * 0.75
    bm = bmesh.new()
    # Profile from the bottom: tapered 欹 (slightly concave), then 平 and 耳 straight.
    rings = []
    steps = 6
    for k in range(steps + 1):
        t = k / steps
        y = (height - ear - flat) * t
        # 欹: the lower part curves inward (concave), as on 宋 blocks.
        w = bottom + (top - bottom) * (t ** 1.8)
        rings.append((y, w))
    rings.append((height, top))
    verts = []
    for (y, w) in rings:
        h = w / 2
        ring = [bm.verts.new(wk.to_blender(p)) for p in [(-h, y, -h), (h, y, -h), (h, y, h), (-h, y, h)]]
        verts.append(ring)
    for a, b in zip(verts, verts[1:]):
        for i in range(4):
            bm.faces.new([a[i], a[(i + 1) % 4], b[(i + 1) % 4], b[i]])
    bm.faces.new(list(reversed(verts[0])))
    bm.faces.new(verts[-1])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    for axis in (["x", "z"] if slot == "+" else [slot]):
        sx, sz = (top + 0.01, ARM_W + 0.0006) if axis == "x" else (ARM_W + 0.0006, top + 0.01)
        c = box(sx, seat + 0.002, sz, (0, height - seat / 2 + 0.001, 0))
        cut(ob, c)
    return finish(ob, name, dou_wood)


parts = [
    dou("dg_栌斗", 0.096, LU_H, LU_SEAT, slot="+", lu=True),
    arm("dg_华栱", HUA_FRONT - HUA_BACK, "z", notch_top=[-(HUA_BACK + HUA_FRONT) / 2]),
    arm("dg_泥道栱", 0.22, "x", notch_bottom=[0.0]),
    dou("dg_散斗", 0.048, DOU_H, SEAT, slot="x"),
    dou("dg_交互斗", 0.054, DOU_H, SEAT, slot="+"),
    arm("dg_令栱", 0.20, "x", notch_top=[0.0]),
    # 耍头 runs from behind the column (z=-0.06) to past the front row (0.14); its origin is
    # its middle (z=0.04): halving under 令栱 at z=0.105, over 柱头枋 at z=0.
    arm("dg_耍头", 0.20, "z", notch_bottom=[FRONT_Z - 0.04], notch_top=[-0.04], end="beak"),
    arm("dg_柱头枋", 0.24, "x", notch_bottom=[0.0], end="square", mat=beam_wood),
    arm("dg_撩檐枋", 0.24, "x", end="square", mat=beam_wood),
]
# The column the bracket set stands on (not a part to place): top at y=0.
lacquer = wk.flat("column_lacquer", (0.32, 0.05, 0.03), roughness=0.4)
bpy.ops.mesh.primitive_cylinder_add(radius=0.055, depth=0.12, vertices=64)
col = bpy.context.object
col.location = wk.to_blender((0, -0.06, 0))
bpy.ops.object.transform_apply(location=True)
wk.bevel(col, width=0.003, segments=3)
wk.assign(col, lacquer)
col.name = "dg_column"
parts.append(col)
wk.export(out_dougong, parts)

# ---- Bench -------------------------------------------------------------------------------------
wk.reset()
teak = wk.wood("teak_veneer")
walnut = wk.wood("walnut_veneer")
top = box(1.0, 0.04, 0.6, (0, -0.02, 0))
wk.bevel(top, width=0.004, segments=3)
bpy.context.view_layer.objects.active = top
top.select_set(True)
bpy.ops.object.mode_set(mode="EDIT")
bpy.ops.mesh.select_all(action="SELECT")
bpy.ops.uv.cube_project(cube_size=1.0)
bpy.ops.object.mode_set(mode="OBJECT")
top.select_set(False)
wk.assign(top, teak)
top.name = "bench"
bpy.ops.mesh.primitive_cylinder_add(radius=0.2, depth=0.03, vertices=96)
disk = bpy.context.object
disk.location = wk.to_blender((0, 0.015, 0))
bpy.ops.object.transform_apply(location=True)
wk.bevel(disk, width=0.004, segments=3)
bpy.context.view_layer.objects.active = disk
disk.select_set(True)
bpy.ops.object.mode_set(mode="EDIT")
bpy.ops.mesh.select_all(action="SELECT")
bpy.ops.uv.cube_project(cube_size=0.5)
bpy.ops.object.mode_set(mode="OBJECT")
disk.select_set(False)
wk.assign(disk, walnut)
disk.name = "turntable"
wk.export(out_bench, [top, disk])
