"""Mesh audit of the builds (v0.10.1): every part placed and every snap and show-off animation, on the real models.

blender -b --factory-startup --python tools/audit_meshes.py -- <models_dir> <out.tsv>

For each build (斗拱, 被中香炉, 指南车, 水运仪象台, 总机关匣) the parts go into their slots in the game's order (the first
ready slot each time, as the hints and AutoPilot do), along the way AssemblyView takes them (up clear of what is
there, over, straight down; the 水运仪象台 in from the front), sampled every 2 mm, and every pose is checked against
everything already there: the deepest overlap of surface points (about 1 mm apart) inside another part. Then the
animations a finished build plays (the censer rolling on its gimbal, the water wheel stepping, the chest's door and
plaque). Pins in their sockets and axles in their hubs ("joints", listed below) overlap by design; for those the
check is that they never go deeper than where they rest. Writes one row per check and each model's SHA-256;
MeshAuditTest fails if a row is over 0.5 mm or a model changed since the audit.
"""
import hashlib
import math
import os
import sys

import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, os.path.dirname(__file__))
from extract_points import nodes, samples  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
MODELS, OUT = args[0], args[1]
# Optional: only these relics (their rows are replaced in an existing OUT; the others are kept).
ONLY = set(args[2:])
STEP = 0.002


def rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class Rigid:
    """A part's mesh in its own frame, its surface samples and a BVH for inside tests."""

    def __init__(self, name, p, t):
        self.name = name
        self.p = p
        self.bvh = BVHTree.FromPolygons([Vector(v) for v in p], [tuple(int(i) for i in tr) for tr in t], epsilon=0.0)
        self.s = samples(p, t, 0.001, 0.002).astype(np.float64) * 1e-5
        self.lo = p.min(0) - 1e-4
        self.hi = p.max(0) + 1e-4


def inside_depth(points, b):
    """Deepest of [points] (in b's frame) inside b."""
    m = np.all((points >= b.lo) & (points <= b.hi), axis=1)
    best = 0.0
    for q in points[m]:
        v = Vector(q)
        loc, nor, _, dist = b.bvh.find_nearest(v)
        if loc is None or dist <= best or (v - loc).dot(nor) >= 0:
            continue
        votes = 0
        for d in (Vector((1, 0.0137, 0.0071)), Vector((-0.011, 1, 0.017)), Vector((0.013, -0.019, 1))):
            n = 0
            o = v.copy()
            for _ in range(64):
                hit = b.bvh.ray_cast(o, d)
                if hit[0] is None:
                    break
                n += 1
                o = hit[0] + d.normalized() * 1e-6
            votes += n % 2
        if votes >= 2:
            best = dist
    return best


def pen(a, pa, b, pb):
    """Overlap of rigid [a] at pose [pa] = (R, t) and [b] at [pb]."""
    (ra, ta), (rb, tb) = pa, pb
    wa = a.s @ ra.T + ta
    wb = b.s @ rb.T + tb
    # Quick reject on the world boxes.
    if np.any(wa.max(0) < wb.min(0) - 1e-3) or np.any(wb.max(0) < wa.min(0) - 1e-3):
        return 0.0
    in_b = (wa - tb) @ rb
    in_a = (wb - ta) @ ra
    return max(inside_depth(in_b, b), inside_depth(in_a, a))


def world_box(r, pose):
    (R, t) = pose
    corners = np.array([[x, y, z] for x in (r.lo[0], r.hi[0]) for y in (r.lo[1], r.hi[1]) for z in (r.lo[2], r.hi[2])])
    w = corners @ R.T + t
    return w.min(0), w.max(0)


rows = []
hashes = {}


def model(name):
    path = os.path.join(MODELS, name + ".glb")
    hashes[name] = hashlib.sha256(open(path, "rb").read()).hexdigest()
    return {k: (p, t) for k, (p, t) in nodes(path).items()}


def record(relic, check, worst, pair, joint=False, rest=0.0):
    over = worst - rest if joint else worst
    rows.append((relic, check, pair, round(worst * 1000, 2), round(rest * 1000, 2), int(joint), round(over * 1000, 2)))
    print(f"{relic:6s} {check:28s} {pair:24s} {worst * 1000:6.2f} mm" + (f" (joint, rests at {rest * 1000:.2f})" if joint else ""))


CLEAR, DROP, FRONT_LIFT = 0.008, 0.02, 0.0


def build(relic, glb, fixed, slots, joints=(), front=None, drops=((0.02, 0.0, 0.02), (-0.025, 0.01, -0.01)), shift=None):
    """[slots]: (node, pos, yaw) in build order. Parts come from [drops] offsets beside their slot and from the bench."""
    nd = model(glb)
    rig = {}

    def r(name):
        if name not in rig:
            rig[name] = Rigid(name, *nd[name])
        return rig[name]

    placed = [(f, r(f), (np.eye(3), np.zeros(3))) for f in fixed]
    joint = {frozenset(j) for j in joints}
    rest = {}

    def base(n):
        return n.split("#")[0]

    for k, (node, pos, yaw) in enumerate(slots):
        part = r(node)
        R = rot("y", yaw)
        dest = np.array(pos, dtype=float)
        lo, hi = world_box(part, (R, np.zeros(3)))
        # At rest in its slot first: joints (a pin in its socket) are allowed what they overlap there.
        allowed = {}
        for name, o, pose in placed:
            d = pen(part, (R, dest), o, pose)
            key = frozenset((base(name), node))
            if key in joint:
                allowed[name] = d
                rest[key] = d
                record(relic, f"{node} resting (joint)", d, f"{node}×{base(name)}", joint=True, rest=d)
            else:
                record(relic, f"{node} resting", d, f"{node}×{base(name)}")
        for drop in drops:
            start = dest + np.array(drop)
            # The hand brings it there riding over everything (AssemblyView.liftY, v0.10.1): on top of the boxes of what
            # stands under it, then lets go.
            for name, o, pose in placed:
                olo, ohi = world_box(o, pose)
                if olo[0] < hi[0] + start[0] - 1e-6 and lo[0] + start[0] < ohi[0] - 1e-6 and olo[2] < hi[2] + start[2] - 1e-6 and lo[2] + start[2] < ohi[2] - 1e-6:
                    start[1] = max(start[1], ohi[1] - lo[1])
            if front is None:
                # Up clear of everything standing in the strip between here and there, over, straight down.
                strip_lo = np.minimum(lo + start, lo + dest) - 0.005
                strip_hi = np.maximum(hi + start, hi + dest) + 0.005
                h = max(start[1], dest[1] + DROP)
                for name, o, pose in placed:
                    olo, ohi = world_box(o, pose)
                    if olo[0] < strip_hi[0] and ohi[0] > strip_lo[0] and olo[2] < strip_hi[2] and ohi[2] > strip_lo[2]:
                        h = max(h, ohi[1] + CLEAR - lo[1])
                way = [start, np.array([start[0], h, start[2]]), np.array([dest[0], h, dest[2]]), dest]
            else:
                out = max(start[2], front + CLEAR - lo[2])
                lane = dest[1] + FRONT_LIFT
                s0 = np.array([start[0], max(start[1], lane), start[2]])
                sx = dest[0] + (shift or {}).get(node, 0.0)
                way = [start, s0, np.array([s0[0], s0[1], out]), np.array([sx, lane, out]), np.array([sx, lane, dest[2]]),
                       np.array([dest[0], lane, dest[2]]), dest]
            poses = []
            for a, b in zip(way, way[1:]):
                n = max(1, int(math.ceil(np.linalg.norm(b - a) / STEP)))
                poses += [a + (b - a) * (i / n) for i in range(n + 1)]
            # The part is let go next to its slot: where it starts may already overlap (it was carried there by hand);
            # from the first point where it is clear, it must stay clear.
            worst, pair = 0.0, "-"
            clear_yet = False
            for q in poses[:: max(1, len(poses) // 300)] + [dest]:
                d_here, p_here = 0.0, "-"
                for name, o, pose in placed:
                    d = pen(part, (R, q), o, pose) - allowed.get(name, 0.0)
                    if d > d_here:
                        d_here, p_here = d, base(name)
                if d_here <= 0.0005:
                    clear_yet = True
                if clear_yet and d_here > worst:
                    worst, pair = d_here, p_here
            record(relic, f"{node} into slot {k} from {drop}", worst, f"{node}×{pair}")
        placed.append((f"{node}#{k}", part, (R, dest)))
    return rig, placed, rest


LU_H, SEAT, ARM, DOU, S = 0.06, 0.024, 0.045, 0.03, 0.015
Y1 = LU_H - SEAT
Y1T = Y1 + ARM
Y2 = Y1T + DOU - S
Y2T = Y2 + ARM
Y3 = Y2T + DOU - S
FZ = 0.105
if not ONLY or "斗拱" in ONLY:
  build("斗拱", "dougong", ["dg_column"], [
    ("dg_栌斗", (0, 0, 0), 0), ("dg_华栱", (0, Y1, (-0.06 + 0.135) / 2), 0), ("dg_泥道栱", (0, Y1, 0), 0),
    ("dg_交互斗", (0, Y1T, 0), 0), ("dg_散斗", (-0.09, Y1T, 0), 0), ("dg_散斗", (0.09, Y1T, 0), 0),
    ("dg_交互斗", (0, Y1T, FZ), 0), ("dg_令栱", (0, Y2, FZ), 0), ("dg_耍头", (0, Y2, 0.04), 0),
    ("dg_散斗", (-0.08, Y2T, FZ), 0), ("dg_散斗", (0.08, Y2T, FZ), 0), ("dg_柱头枋", (0, Y2, 0), 0),
    ("dg_撩檐枋", (0, Y3, FZ), 0),
])

if not ONLY or "指南车" in ONLY:
  build("指南车", "chariot", ["ch_body"], [
    ("ch_立轮", (-0.07, 0.04, 0), 90), ("ch_立轮", (0.07, 0.04, 0), 90),
    ("ch_小平轮", (-0.07, 0.077, 0), 180 / 14), ("ch_小平轮", (0.07, 0.077, 0), 180 / 14),
    ("ch_大平轮", (0, 0.077, 0), 0), ("ch_木人", (0, 0.102, 0), 0),
], joints=[("ch_body", "ch_立轮")])

wc_slots = [
    ("wc_退水壶", (0, 0, 0), 0), ("wc_枢轮", (0, 0.13, 0), 0), ("wc_天池", (-0.114, 0.23, 0), 0), ("wc_天衡", (0, 0.25, -0.05), 0),
    ("wc_天关", (0.08, 0.23, 0), 0), ("wc_左天锁", (-0.04, 0.2, 0.05), 0), ("wc_右天锁", (0.04, 0.2, 0.05), 0),
    ("wc_浑象", (0, 0.323, -0.05), 0),
]
WC = not ONLY or "水运仪象台" in ONLY
if WC:
    wc_rig, wc_placed, _ = build("水运仪象台", "waterclock", ["wc_frame"], wc_slots, front=0.11,
                                 drops=((0.0, 0.0, 0.03), (0.02, 0.01, 0.02)), shift={"wc_天衡": 0.03})

if not ONLY or "总机关匣" in ONLY:
  build("总机关匣", "chest", ["fc_body"], [
    ("fc_鲁班尺", (0, 0.116, 0.05), 0), ("fc_铜印", (-0.08, 0.116, -0.035), 0), ("fc_星图", (0.08, 0.116, -0.035), 0),
])

R = 0.07
CZ = not ONLY or "被中香炉" in ONLY
if CZ:
    # Lower half and outer ring the usual way; then the fitting of CenserScene (v0.10.1): the inner ring and the bowl
    # go in edge-on while the rings lie flat on their pins, then the rings turn back up; then the upper half.
    cz_rig, cz_placed, cz_rest = build("被中香炉", "censer", ["cs_stand"], [
        ("cs_下半球", (0, 0, 0), 0), ("cs_外环", (0, R, 0), 0),
    ], joints=[("cs_下半球", "cs_外环")])
    cz_n = model("censer")
    rg = {k: Rigid(k, *cz_n[k]) for k in ["cs_stand", "cs_下半球", "cs_外环", "cs_内环", "cs_香盂", "cs_上半球"]}
    c = np.array([0.0, R, 0.0])
    I3 = np.eye(3)
    final = {"cs_stand": (I3, np.zeros(3)), "cs_下半球": (I3, np.zeros(3)), "cs_外环": (I3, c), "cs_内环": (rot("y", 90), c),
             "cs_香盂": (I3, c), "cs_上半球": (I3, c)}
    jp = [("cs_下半球", "cs_外环"), ("cs_外环", "cs_上半球"), ("cs_外环", "cs_内环"), ("cs_内环", "cs_香盂"), ("cs_下半球", "cs_上半球")]
    cz_rest = {frozenset(p): pen(rg[p[0]], final[p[0]], rg[p[1]], final[p[1]]) for p in jp}
    HIGH = R + 0.13

    def ease(x):
        x = min(max(x, 0.0), 1.0)
        return x * x * (3 - 2 * x)

    def fit(part, bowl, present, drop):
        """CenserScene.fitStep, sampled every 30 ms: worst overlap beyond touching (joints: beyond their rest)."""
        lo_, hi_ = world_box(rg[part], (rot("z", 0) if bowl else rot("y", 90), np.zeros(3)))
        start = c + np.array(drop)
        for n in present:
            olo, ohi = world_box(rg[n], final[n])
            if olo[0] < hi_[0] + start[0] and lo_[0] + start[0] < ohi[0] and olo[2] < hi_[2] + start[2] and lo_[2] + start[2] < ohi[2]:
                start[1] = max(start[1], ohi[1] - lo_[1])
        up = np.array([start[0], max(start[1], HIGH), start[2]])
        high = np.array([0, HIGH, 0])
        rise, over = 0.5, 1.1
        flip, down = over + 0.7, over + 1.4
        turn = down + 0.5 if bowl else down
        back = turn + 0.7
        worst, pair, clear_yet = 0.0, "-", False
        t = 0.0
        while t <= back + 1e-9:
            if t < rise:
                p = start + (up - start) * ease(t / rise)
            elif t < over:
                p = up + (high - up) * ease((t - rise) / (over - rise))
            elif t < flip:
                p = high
            elif t < down:
                p = high + (c - high) * ease((t - flip) / (down - flip))
            else:
                p = c
            if not bowl:
                rp = rot("y", 90)
            elif t < rise:
                rp = I3
            elif t < over:
                rp = rot("z", 90 * ease((t - rise) / (over - rise)))
            elif t < down:
                rp = rot("z", 90)
            elif t < turn:
                rp = rot("z", 90 * (1 - ease((t - down) / (turn - down))))
            else:
                rp = I3
            tilt = 90 * (0.0 if t < over else ease((t - over) / (flip - over)) if t < flip else 1.0 if t < turn else 1 - ease((t - turn) / (back - turn)))
            pose = dict((n, final[n]) for n in present)
            pose["cs_外环"] = (rot("x", tilt), c)
            if bowl:
                pose["cs_内环"] = (rot("x", tilt) @ rot("y", 90), c)
            pose[part] = (rp, p)
            moving = [part, "cs_外环"] + (["cs_内环"] if bowl else [])
            here, hp = 0.0, "-"
            names = list(pose)
            for i in range(len(names)):
                for j in range(i + 1, len(names)):
                    a_, b_ = names[i], names[j]
                    if a_ not in moving and b_ not in moving:
                        continue
                    d = pen(rg[a_], pose[a_], rg[b_], pose[b_]) - cz_rest.get(frozenset((a_, b_)), 0.0)
                    if d > here:
                        here, hp = d, f"{a_}×{b_}"
            if here <= 0.0005:
                clear_yet = True
            if clear_yet and here > worst:
                worst, pair = here, hp
            t += 0.03
        record("被中香炉", f"{part} fitted in from {drop}", worst, pair)

    for drop in ((0.02, 0.0, 0.02), (-0.025, 0.01, -0.01)):
        fit("cs_内环", False, ["cs_stand", "cs_下半球", "cs_外环"], drop)
    for drop in ((0.02, 0.0, 0.02), (-0.025, 0.01, -0.01)):
        fit("cs_香盂", True, ["cs_stand", "cs_下半球", "cs_外环", "cs_内环"], drop)
    for a_, b_ in jp:
        record("被中香炉", "resting (joint)", cz_rest[frozenset((a_, b_))], f"{a_}×{b_}", joint=True, rest=cz_rest[frozenset((a_, b_))])
    # The upper half: up, over, straight down onto the lower.
    up_lo, up_hi = world_box(rg["cs_上半球"], (I3, np.zeros(3)))
    for drop in ((0.02, 0.0, 0.02), (-0.025, 0.01, -0.01)):
        start = c + np.array(drop)
        top = max(world_box(rg[n], final[n])[1][1] for n in ["cs_下半球", "cs_外环", "cs_内环", "cs_香盂"])
        h = max(start[1], top + CLEAR - up_lo[1], c[1] + DROP)
        start[1] = max(start[1], top - up_lo[1])
        way = [start, np.array([start[0], h, start[2]]), np.array([0, h, 0]), c]
        worst, pr = 0.0, "-"
        for a_, b_ in zip(way, way[1:]):
            n = max(1, int(math.ceil(np.linalg.norm(b_ - a_) / STEP)))
            for k in range(n + 1):
                q = a_ + (b_ - a_) * (k / n)
                for nm in ["cs_stand", "cs_下半球", "cs_外环", "cs_内环", "cs_香盂"]:
                    d = pen(rg["cs_上半球"], (I3, q), rg[nm], final[nm]) - cz_rest.get(frozenset(("cs_上半球", nm)), 0.0)
                    if d > worst:
                        worst, pr = d, f"cs_上半球×{nm}"
        record("被中香炉", f"cs_上半球 into slot 4 from {drop}", worst, pr)
    cz_placed = [(n, rg[n], final[n]) for n in ["cs_stand", "cs_下半球", "cs_外环", "cs_内环", "cs_香盂", "cs_上半球"]]

# The fixed parts' boxes a held part rides over (core.BuildProps): each model lies inside its box.
PROPS = {"dougong": ("dg_column", (-0.055, -0.12, -0.055), (0.055, 0.0, 0.055)), "censer": ("cs_stand", (-0.045, -0.03, -0.045), (0.045, 0.0005, 0.045)),
         "chariot": ("ch_body", (-0.12, -0.004, -0.06), (0.12, 0.094, 0.194)), "waterclock": ("wc_frame", (-0.17, -0.01, -0.11), (0.17, 0.372, 0.11)),
         "chest": ("fc_body", (-0.155, 0.0, -0.105), (0.155, 0.12, 0.105))}
RELIC_NAME = {"dougong": "斗拱", "censer": "被中香炉", "chariot": "指南车", "waterclock": "水运仪象台", "chest": "总机关匣"}
for glb, (node, blo, bhi) in PROPS.items():
    if ONLY and RELIC_NAME[glb] not in ONLY:
        continue
    pts = model(glb)[node][0]
    out = max(float(np.max(np.array(blo) - pts.min(0))), float(np.max(pts.max(0) - np.array(bhi))), 0.0)
    record(RELIC_NAME[glb], "fixed part inside its box", out, f"{node}×BuildProps")

# ---- Show-off animations ------------------------------------------------------------------------------------------

if CZ:
    # The censer rolls on its gimbal (CenserScene.frame): inner = Rx(t1)·Ry(90), outer = Rx(t1)·Ry(t2), shell = outer·Rx(t3),
    # all about the ball's middle; the bowl stays level.
    c = np.array([0, R, 0])
    parts = {name.split("#")[0]: o for name, o, _ in cz_placed}
    pairs_joint = {frozenset(p) for p in [("cs_下半球", "cs_外环"), ("cs_外环", "cs_上半球"), ("cs_外环", "cs_内环"), ("cs_内环", "cs_香盂"), ("cs_下半球", "cs_上半球")]}
    worst = {}
    for k in range(0, 48):
        t = 0.25 * k
        t1 = 35 * math.sin(t * 1.3)
        t2 = 30 * math.sin(t * 0.9 + 1)
        t3 = 25 * math.sin(t * 0.7 + 2)
        inner = rot("x", t1) @ rot("y", 90)
        outer = rot("x", t1) @ rot("y", t2)
        shell = outer @ rot("x", t3)
        pose = {
            "cs_stand": (np.eye(3), np.zeros(3)),
            "cs_下半球": (shell, c + shell @ np.array([0, -R, 0])),
            "cs_上半球": (shell, c),
            "cs_外环": (outer, c),
            "cs_内环": (inner, c),
            "cs_香盂": (np.eye(3), c),
        }
        names = list(pose)
        for i in range(len(names)):
            for j in range(i + 1, len(names)):
                a, b = names[i], names[j]
                d = pen(parts[a], pose[a], parts[b], pose[b])
                key = frozenset((a, b))
                worst[key] = max(worst.get(key, 0.0), d)
    for key, d in worst.items():
        a, b = sorted(key)
        if key in pairs_joint:
            record("被中香炉", "rolling (joint)", d, f"{a}×{b}", joint=True, rest=cz_rest.get(key, cz_rest.get(frozenset((b, a)), 0.0)))
        else:
            record("被中香炉", "rolling", d, f"{a}×{b}")

if WC:
    # The water wheel steps a scoop at a time (15°: the wheel repeats every scoop), the 天衡 dips 6°, the globe spins.
    wc = {name.split("#")[0]: (o, pose) for name, o, pose in wc_placed}
    wheel, wheel_pose = wc["wc_枢轮"]
    worst_w = {}
    for deg in range(0, 16):
        pose = (rot("x", deg), wheel_pose[1])
        for name, (o, p) in wc.items():
            if name == "wc_枢轮":
                continue
            worst_w[name] = max(worst_w.get(name, 0.0), pen(wheel, pose, o, p))
    for name, d in worst_w.items():
        record("水运仪象台", "wheel turning", d, f"wc_枢轮×{name}")
    beam, beam_pose = wc["wc_天衡"]
    for name, (o, p) in wc.items():
        if name == "wc_天衡":
            continue
        d = max(pen(beam, (rot("z", -6 * f), beam_pose[1]), o, p) for f in (0.0, 0.5, 1.0))
        record("水运仪象台", "天衡 dipping", d, f"wc_天衡×{name}")

if not ONLY or "总机关匣" in ONLY:
    # The chest: the door falls open about its bottom edge, the plaque slides out and up.
    fc = model("chest")
    body = Rigid("fc_body", *fc["fc_body"])
    door = Rigid("fc_door", *fc["fc_door"])
    plaque = Rigid("fc_plaque", *fc["fc_plaque"])
    # Hinge at the door's bottom back edge, 1.1 cm up, in front of the trim (ChestScene, v0.10.1); the door model is
    # shifted down by that so it turns about its own edge.
    HY = 0.011
    hinge = np.array([0, HY, 0.105])
    wd = wp = wdp = 0.0
    for k in range(0, 11):
        e = k / 10
        Rd = rot("x", 90 * e)
        dpose = (Rd, hinge + Rd @ np.array([0, -HY, 0]))

        def ease(x):
            x = min(max(x, 0.0), 1.0)
            return x * x * (3 - 2 * x)
        # The plaque comes out once the door is open: straight out through the doorway, then up (ChestScene.plaquePos).
        ppose = (np.eye(3), np.array([0, 0.02 + 0.1 * ease((e - 0.6) / 0.4), 0.16 * ease(e / 0.6)]))
        wd = max(wd, pen(door, dpose, body, (np.eye(3), np.zeros(3))))
        wp = max(wp, pen(plaque, ppose, body, (np.eye(3), np.zeros(3))))
        # The door is fully open while the plaque moves.
        wdp = max(wdp, pen(plaque, ppose, door, (rot("x", 90), hinge + rot("x", 90) @ np.array([0, -HY, 0]))))
    record("总机关匣", "door opening", wd, "fc_door×fc_body")
    record("总机关匣", "plaque coming out", wp, "fc_plaque×fc_body")
    record("总机关匣", "plaque past the door", wdp, "fc_plaque×fc_door")

keep, old_hashes = [], {}
if ONLY and os.path.exists(OUT):
    for line in open(OUT, encoding="utf-8"):
        if line.startswith("#sha256"):
            _, f, h = line.rstrip("\n").split("\t")
            old_hashes[f[:-4]] = h
        elif not line.startswith("#") and line.strip() and line.split("\t")[0] not in ONLY:
            keep.append(line.rstrip("\n"))
old_hashes.update(hashes)
with open(OUT, "w", encoding="utf-8") as f:
    f.write("# tools/audit_meshes.py (v0.10.1): relic, check, pair, deepest mm, joint rest mm, joint, over mm\n")
    for name, h in sorted(old_hashes.items()):
        f.write(f"#sha256\t{name}.glb\t{h}\n")
    for line in keep:
        f.write(line + "\n")
    for row in rows:
        f.write("\t".join(str(x) for x in row) + "\n")
print("wrote", OUT, len(keep) + len(rows), "rows")
