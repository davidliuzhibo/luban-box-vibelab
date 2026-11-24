"""Surface points of the game's models, for the unit tests that check the collision shapes against them.

python3 tools/extract_points.py <out_dir> <model.glb> [more.glb ...]   (needs numpy: Blender's bundled python works)
env EXTRACT_EDGE / EXTRACT_FACE change the spacings (the turning relics use 1 mm / 2 mm).
For every mesh node: all its vertices, points along every edge 2 mm apart and over every triangle 5 mm apart, in the
object frame (glTF is y-up like the game). Written as <out_dir>/<model>.pts.gz (gzip): per node, a UTF-8 name (u16 length + bytes),
a u32 count, then int32 triples in units of 0.01 mm. GeometryAuditTest reads them (v0.10.1).
"""
import gzip
import json
import os
import struct
import sys

import numpy as np



def load(path):
    b = open(path, "rb").read()
    off = 12
    js = binc = None
    while off < len(b):
        ln, typ = struct.unpack_from("<II", b, off)
        off += 8
        chunk = b[off:off + ln]
        off += ln
        if typ == 0x4E4F534A:
            js = json.loads(chunk)
        else:
            binc = chunk
    return js, binc


def accessor(js, binc, i):
    a = js["accessors"][i]
    bv = js["bufferViews"][a["bufferView"]]
    o = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
    n = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[a["type"]]
    dt = {5126: np.float32, 5125: np.uint32, 5123: np.uint16, 5121: np.uint8}[a["componentType"]]
    size = np.dtype(dt).itemsize
    stride = bv.get("byteStride") or n * size
    raw = np.frombuffer(binc, dtype=np.uint8, count=stride * (a["count"] - 1) + n * size, offset=o)
    rows = np.lib.stride_tricks.as_strided(raw, shape=(a["count"], n * size), strides=(stride, 1))
    return rows.copy().view(dt).reshape(a["count"], n)


def quat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def matrix(n):
    if "matrix" in n:
        return np.array(n["matrix"]).reshape(4, 4).T
    m = np.eye(4)
    m[:3, :3] = quat(n.get("rotation", [0, 0, 0, 1])) @ np.diag(n.get("scale", [1, 1, 1]))
    m[:3, 3] = n.get("translation", [0, 0, 0])
    return m


def nodes(path):
    js, binc = load(path)
    out = {}

    def walk(i, parent):
        n = js["nodes"][i]
        m = parent @ matrix(n)
        if "mesh" in n:
            pts, tris, base = [], [], 0
            for prim in js["meshes"][n["mesh"]]["primitives"]:
                p = accessor(js, binc, prim["attributes"]["POSITION"]).astype(np.float64)
                idx = accessor(js, binc, prim["indices"]).reshape(-1).astype(np.int64) if "indices" in prim else np.arange(len(p))
                pts.append(p)
                tris.append(idx.reshape(-1, 3) + base)
                base += len(p)
            p = np.vstack(pts)
            out[n.get("name", str(i))] = ((m[:3, :3] @ p.T).T + m[:3, 3], np.vstack(tris))
        for c in n.get("children", []):
            walk(c, m)

    for s in js["scenes"][js.get("scene", 0)]["nodes"]:
        walk(s, np.eye(4))
    return out


def samples(p, t, edge=0.002, face=0.005):
    """Vertices, points along every edge no more than [edge] apart, and a grid over each triangle [face] apart."""
    out = [p]
    a, b, c = p[t[:, 0]], p[t[:, 1]], p[t[:, 2]]
    for u, v in ((a, b), (b, c), (c, a)):
        n = np.ceil(np.linalg.norm(v - u, axis=1) / edge).astype(int)
        for k in np.unique(n):
            if k < 2:
                continue
            sel = n == k
            f = (np.arange(1, k) / k)[None, :, None]
            out.append((u[sel][:, None, :] + f * (v[sel] - u[sel])[:, None, :]).reshape(-1, 3))
    longest = np.maximum(np.maximum(np.linalg.norm(b - a, axis=1), np.linalg.norm(c - a, axis=1)), np.linalg.norm(c - b, axis=1))
    n = np.minimum(np.ceil(longest / face), 60).astype(int)
    for k in np.unique(n):
        if k < 3:
            continue
        sel = n == k
        uv = np.array([(i / k, j / k) for i in range(1, k) for j in range(1, k - i)])
        if len(uv) == 0:
            continue
        aa, bb, cc = a[sel], b[sel], c[sel]
        out.append((aa[:, None, :] + uv[None, :, :1] * (bb - aa)[:, None, :] + uv[None, :, 1:] * (cc - aa)[:, None, :]).reshape(-1, 3))
    return np.unique(np.round(np.vstack(out) * 1e5).astype(np.int64), axis=0)


def main():
    out_dir = sys.argv[1]
    for path in sys.argv[2:]:
        name = os.path.splitext(os.path.basename(path))[0]
        with gzip.open(os.path.join(out_dir, name + ".pts.gz"), "wb", compresslevel=9) as f:
            for node, (p, t) in nodes(path).items():
                q = samples(p, t, float(os.environ.get("EXTRACT_EDGE", "0.002")), float(os.environ.get("EXTRACT_FACE", "0.005")))
                nb = node.encode("utf-8")
                f.write(struct.pack(">H", len(nb)) + nb + struct.pack(">I", len(q)))
                f.write(q.astype(">i4").tobytes())
                print(f"{name}/{node}: {len(q)} points")


if __name__ == "__main__":
    main()
