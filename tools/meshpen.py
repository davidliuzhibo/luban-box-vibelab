"""How deep two meshes overlap (run inside Blender: numpy + mathutils). Used by tools/audit_meshes.py (v0.10.1).

Surface points of one mesh (every vertex, plus points spread over each triangle about 1 mm apart) are tested
against the other: inside (closest-surface normal and ray parity agree) at what depth. The deepest such point, both
ways round, is the overlap.
"""
import math
import os
import sys

import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, os.path.dirname(__file__))
from extract_points import nodes as _nodes  # noqa: E402


def nodes(path):
    return {k: (p, t, None) for k, (p, t) in _nodes(path).items()}

def rotY(deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def rotX(deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def rotZ(deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])

class Part:
    def __init__(self, name, pts, tris, R=np.eye(3), t=(0, 0, 0)):
        self.name = name
        self.p = (R @ pts.T).T + np.array(t)
        self.t = tris
        self.bvh = BVHTree.FromPolygons([Vector(v) for v in self.p], [tuple(int(i) for i in tr) for tr in tris], epsilon=0.0)
        self.lo = self.p.min(0); self.hi = self.p.max(0)
    def samples(self, spacing=0.001):
        out = [self.p]
        P = self.p
        for tr in self.t:
            a, b, c = P[tr[0]], P[tr[1]], P[tr[2]]
            m = max(np.linalg.norm(b - a), np.linalg.norm(c - a), np.linalg.norm(c - b))
            n = int(m / spacing)
            if n < 1:
                out.append(((a + b + c) / 3)[None]); continue
            n = min(n, 60)
            uv = [(i / n, j / n) for i in range(n + 1) for j in range(n + 1 - i)]
            uv = np.array(uv)
            out.append(a + uv[:, :1] * (b - a) + uv[:, 1:] * (c - a))
        return np.vstack(out)

def depth_in(points, B):
    """Max depth of points inside B (closed mesh), and the point."""
    best = 0.0; where = None
    lo = B.lo - 1e-4; hi = B.hi + 1e-4
    m = np.all((points >= lo) & (points <= hi), axis=1)
    for q in points[m]:
        v = Vector(q)
        loc, nor, idx, dist = B.bvh.find_nearest(v)
        if loc is None: continue
        if (v - loc).dot(nor) < 0 and dist > best:
            # Confirm with ray parity along a few directions (normals near edges can lie).
            inside = 0
            for d in (Vector((1, 0.0137, 0.0071)), Vector((-0.011, 1, 0.017)), Vector((0.013, -0.019, 1))):
                cnt = 0; o = v.copy()
                for _ in range(64):
                    h = B.bvh.ray_cast(o, d)
                    if h[0] is None: break
                    cnt += 1; o = h[0] + d.normalized() * 1e-6
                inside += cnt % 2
            if inside >= 2:
                best = dist; where = q
    return best, where

def pen(A, B, spacing=0.001):
    if np.any(A.hi < B.lo - 1e-4) or np.any(B.hi < A.lo - 1e-4):
        return 0.0, None
    d1, w1 = depth_in(A.samples(spacing), B)
    d2, w2 = depth_in(B.samples(spacing), A)
    return (d1, w1) if d1 >= d2 else (d2, w2)

def load(path):
    return nodes(path)
