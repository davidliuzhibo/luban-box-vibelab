"""Generates the metal textures (1024², JPEG) into LUBAN_TEX/<name>/diff.jpg and rough.jpg:
bronze (warm bronze with verdigris in patches), gilt (gilded silver, slightly worn) and brass.

blender -b --factory-startup --python tools/make_metal_tex.py
Fractal value noise from numpy; no external images, so nothing to license.
"""
import os

import bpy
import numpy as np

ROOT = os.environ.get("LUBAN_TEX", os.path.join(os.path.dirname(__file__), "..", "..", "..", "assets_lab", "luban", "tex1k"))
N = 1024
rng = np.random.default_rng(7)


def noise(scale, octaves=5):
    """Tileable fractal value noise in 0..1."""
    out = np.zeros((N, N))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        cells = scale * 2 ** o
        grid = rng.random((cells, cells))
        idx = np.arange(N) * cells / N
        i0 = idx.astype(int) % cells
        i1 = (i0 + 1) % cells
        f = idx - idx.astype(int)
        f = f * f * (3 - 2 * f)
        a = grid[i0][:, i0] * (1 - f)[None, :] + grid[i0][:, i1] * f[None, :]
        b = grid[i1][:, i0] * (1 - f)[None, :] + grid[i1][:, i1] * f[None, :]
        out += amp * (a * (1 - f)[:, None] + b * f[:, None])
        total += amp
        amp *= 0.5
    return out / total


def save(name, file, rgb):
    folder = os.path.join(ROOT, name)
    os.makedirs(folder, exist_ok=True)
    img = bpy.data.images.new(file, N, N)
    px = np.ones((N, N, 4), dtype=np.float32)
    px[..., :3] = np.clip(rgb, 0, 1)
    img.pixels = px.ravel()
    img.filepath_raw = os.path.join(folder, file)
    img.file_format = "JPEG"
    img.save()
    print("wrote", img.filepath_raw)


def mix(a, b, t):
    return a[None, None, :] * (1 - t[..., None]) + b[None, None, :] * t[..., None]


# Bronze: a warm metal with verdigris patches where the noise runs high, and darker grime.
n1, n2 = noise(4), noise(16)
patina = np.clip((n1 - 0.55) * 4, 0, 1) * (0.6 + 0.4 * n2)
bronze = mix(np.array([0.55, 0.36, 0.17]), np.array([0.26, 0.47, 0.4]), patina)
bronze *= (0.8 + 0.25 * noise(32))[..., None]
save("bronze", "diff.jpg", bronze)
save("bronze", "rough.jpg", np.repeat((0.3 + 0.55 * patina + 0.1 * n2)[..., None], 3, axis=2))

# Gilt: gold leaf over silver, rubbed through here and there.
n3 = noise(8)
worn = np.clip((n3 - 0.62) * 5, 0, 1)
gilt = mix(np.array([0.95, 0.72, 0.32]), np.array([0.78, 0.78, 0.76]), worn)
save("gilt", "diff.jpg", gilt * (0.9 + 0.1 * noise(32))[..., None])
save("gilt", "rough.jpg", np.repeat((0.22 + 0.2 * worn + 0.08 * noise(32))[..., None], 3, axis=2))

# Brass: bright yellow metal with fine polishing streaks.
streak = noise(64, octaves=2)
brass = np.array([0.85, 0.62, 0.28])[None, None, :] * (0.85 + 0.15 * streak)[..., None]
save("brass", "diff.jpg", brass)
save("brass", "rough.jpg", np.repeat((0.25 + 0.15 * streak)[..., None], 3, axis=2))
