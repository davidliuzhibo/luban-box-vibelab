"""华容道: the board and the ten blocks, names carved in and inlaid with gold.

blender -b --factory-startup --python tools/make_klotski.py -- <out.glb>
Nodes: board (play surface at y=0, exit gap in the near edge), cao, guan, general_张飞/赵云/马超/黄忠,
bing. Each block's origin is the middle of its underside; the game puts it on its cell. A cell is
5 cm (KlotskiBoards.CELL); blocks leave a 1.5 mm gap all round.
"""
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(__file__))
import woodkit as wk  # noqa: E402

out = sys.argv[sys.argv.index("--") + 1:][0]
FONT = os.environ.get("LUBAN_FONT", os.path.join(wk.TEX_ROOT, "..", "fonts", "NotoSerifSC-Bold.otf"))
CELL = 0.05
GAP = 0.0015
BLOCK_H = 0.024
W, H = 4, 5

wk.reset()
dark = wk.wood("wood_table_001")
board_wood = wk.wood("walnut_veneer")
gold = wk.flat("gold", (0.86, 0.66, 0.28), roughness=0.3, metallic=1.0)
cinnabar = wk.flat("cinnabar", (0.62, 0.12, 0.06), roughness=0.45)


def slab(name, sx, sy, sz, center, mat, bevel=0.002):
    """A box sx × sy × sz (game frame) centered at [center], beveled."""
    bpy.ops.mesh.primitive_cube_add(size=1)
    ob = bpy.context.object
    ob.name = name
    ob.scale = (sx, sz, sy)
    ob.location = wk.to_blender(center)
    bpy.ops.object.transform_apply(location=True, scale=True)
    wk.bevel(ob, width=bevel, segments=3)
    uv_box(ob)
    wk.assign(ob, mat)
    return ob


def uv_box(ob, scale=4.0):
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.cube_project(cube_size=1.0 / scale)
    bpy.ops.object.mode_set(mode="OBJECT")
    ob.select_set(False)


blocks = [
    ("cao", 2, 2, "曹操", 0.042, True, gold),
    ("guan", 2, 1, "关羽", 0.034, False, gold),
    ("general_张飞", 1, 2, "张飞", 0.032, True, gold),
    ("general_赵云", 1, 2, "赵云", 0.032, True, gold),
    ("general_马超", 1, 2, "马超", 0.032, True, gold),
    ("general_黄忠", 1, 2, "黄忠", 0.032, True, gold),
    ("bing", 1, 1, "兵", 0.034, False, gold),
]
objects = []
for name, w, h, text, size, columns, mat in blocks:
    sx, sz = w * CELL - 2 * GAP, h * CELL - 2 * GAP
    b = slab(name, sx, BLOCK_H, sz, (0, BLOCK_H / 2, 0), dark, bevel=0.003)
    b = wk.carve_text(b, text, (0, BLOCK_H, 0), size, mat, columns=columns)
    objects.append(b)

# Board: a base under the play surface and a rim, open in the middle of the near edge.
inner_x, inner_z = W * CELL, H * CELL
rim = 0.014
base = slab("board", inner_x + 2 * rim, 0.014, inner_z + 2 * rim, (0, -0.007, 0), board_wood)
parts = [base]
rim_h = 0.012
parts.append(slab("rim_back", inner_x + 2 * rim, rim_h, rim, (0, rim_h / 2, -inner_z / 2 - rim / 2), board_wood))
parts.append(slab("rim_left", rim, rim_h, inner_z, (-inner_x / 2 - rim / 2, rim_h / 2, 0), board_wood))
parts.append(slab("rim_right", rim, rim_h, inner_z, (inner_x / 2 + rim / 2, rim_h / 2, 0), board_wood))
side = (inner_x - 2 * CELL) / 2 + rim
parts.append(slab("rim_front_l", side, rim_h, rim, (-inner_x / 2 - rim + side / 2, rim_h / 2, inner_z / 2 + rim / 2), board_wood))
parts.append(slab("rim_front_r", side, rim_h, rim, (inner_x / 2 + rim - side / 2, rim_h / 2, inner_z / 2 + rim / 2), board_wood))
# A cinnabar mark at the exit.
parts.append(slab("exit_mark", 2 * CELL - 0.01, 0.002, 0.006, (0, 0.001, inner_z / 2 + rim / 2), cinnabar, bevel=0.0005))
bpy.ops.object.select_all(action="DESELECT")
for p in parts:
    p.select_set(True)
bpy.context.view_layer.objects.active = base
bpy.ops.object.join()
objects.append(base)

wk.export(out, objects)
