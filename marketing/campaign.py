"""Shared composition helpers for the campaign (used by every asset module)."""

from __future__ import annotations

import os

from luma_studio import Canvas, Frame, TOKENS, mix, rgb, SAGE, BONE, PAPER

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, ".build", "src")
OUT = os.path.join(ROOT, "assets")

SHOTS = {
    "home": os.path.join(SRC, "a_home.jpg"),
    "drawer": os.path.join(SRC, "b_drawer.jpg"),
    "aurora": os.path.join(SRC, "c_studio_aurora.jpg"),
    "monolith": os.path.join(SRC, "d_studio_monolith.jpg"),
}

# --- palette -------------------------------------------------------------
INK = "#0A0B10"
INK_DEEP = "#05060A"
INDIGO = "#7C86FF"
VIOLET = "#A78BFA"
MINT = "#46E0B0"
VERMILION = "#FF5A2B"
CHAMPAGNE = "#D9BC8C"
T1 = "#F4F6FB"
T2 = "#A8AEBC"
T3 = "#717886"


def brand_backdrop(f: Frame, *, hue="indigo", vignette=0.42, grain=2.0, seed=5):
    """The campaign's signature surface: one luminous field over near-black."""
    if hue == "indigo":
        f.gradient([(0.0, "#0A0C18"), (0.42, "#0E1024"), (0.72, "#0B0C17"), (1.0, "#05060A")],
                   angle=100)
        f.glow(f.w * 0.30, f.h * 0.34, f.w * 0.72, INDIGO, 0.40, 2.3)
        f.glow(f.w * 0.86, f.h * 0.78, f.w * 0.60, VIOLET, 0.26, 2.4)
    elif hue == "aurora":
        f.gradient([(0.0, "#04131A"), (0.45, "#061A22"), (1.0, "#03090D")], angle=100)
        f.glow(f.w * 0.26, f.h * 0.30, f.w * 0.72, MINT, 0.30, 2.3)
        f.glow(f.w * 0.82, f.h * 0.80, f.w * 0.60, "#7FB6FF", 0.24, 2.4)
    elif hue == "monolith":
        f.gradient([(0.0, "#111110"), (0.5, "#0B0B0A"), (1.0, "#070706")], angle=100)
        f.glow(f.w * 0.18, f.h * 0.12, f.w * 0.55, VERMILION, 0.14, 2.6)
    elif hue == "ivory":
        f.gradient([(0.0, "#FBF9F4"), (0.55, "#F2EDE2"), (1.0, "#E6DFD0")], angle=105)
        f.glow(f.w * 0.86, f.h * 0.10, f.w * 0.55, "#FFFFFF", 0.55, 2.2)
        f.glow(f.w * 0.10, f.h * 0.95, f.w * 0.60, "#C9D6F2", 0.30, 2.4)
    elif hue == "obsidian":
        f.gradient([(0.0, "#000000"), (1.0, "#070707")], angle=100)
        f.glow(f.w * 0.80, f.h * 0.18, f.w * 0.55, CHAMPAGNE, 0.16, 2.6)
    if vignette:
        f.vignette(vignette)
    if grain:
        f.grain(grain, seed)
    return f


def hairline_rule(f: Frame, x, y, w, color=INDIGO, thick=2, alpha=None):
    f.rect([x, y, x + w, y + thick], radius=thick / 2, fill=color, alpha=alpha)


def play_badge(f: Frame, cx, y, *, w=300, dark=False):
    """Google Play 'GET IT ON' badge, drawn (no third-party artwork)."""
    h = w * 0.296
    x = cx - w / 2
    fill = "#111318" if dark else "#F4F6FB"
    txt = "#F4F6FB" if dark else "#0A0C12"
    sub = "#9AA0AE" if dark else "#5B6270"
    f.rect([x, y, x + w, y + h], radius=h * 0.22, fill=fill)
    # play glyph
    gy = y + h * 0.5
    gx = x + w * 0.135
    s = h * 0.30
    f.polygon([(gx - s * 0.45, gy - s), (gx + s * 0.85, gy), (gx - s * 0.45, gy + s)],
              fill="#7C86FF")
    f.polygon([(gx - s * 0.45, gy - s), (gx + s * 0.10, gy - s * 0.10),
               (gx + s * 0.10, gy + s * 0.10), (gx - s * 0.45, gy + s)],
              fill="#4FD1A5", alpha=170)
    f.text(x + w * 0.265, gy - h * 0.30, "GET IT ON", size=h * 0.145, weight=500,
           tracking=h * 0.012, color=sub)
    f.text(x + w * 0.262, gy - h * 0.02, "Google Play", size=h * 0.30, weight=600,
           color=txt)
    return h


def caption(f: Frame, x, y, label, *, color=T3, size=19, tracking=3.2, anchor="l"):
    f.kicker(x, y, label, color=color, size=size, tracking=tracking, anchor=anchor)
    return f


def index_badge(f: Frame, x, y, n, *, color=INDIGO, size=17):
    """Tiny numbered index used in the campaign's editorial layouts."""
    f.text(x, y, f"{n:02d}", size=size, weight=600, family="sg", color=color, tracking=2)


def safe_area(f: Frame, pad=64, color=(255, 255, 255, 10), dashed=False):
    f.rect([pad, pad, f.w - pad, f.h - pad], radius=0, stroke=color, width=1)


# --- graphic language: orbits, grid, editorial furniture -------------------

def orbit_decor(f, cx, cy, radii, color="#7C86FF", alpha=26, width=1.2, start=-42,
                span=272):
    """Concentric broken orbits — the mark's arc blown up as a layout device."""
    for i, r in enumerate(radii):
        a = int(alpha * (1.0 - i * 0.16))
        f.arc([cx - r, cy - r, cx + r, cy + r], start + i * 26, start + i * 26 + span,
              color=color, width=width, alpha=max(6, a))


def dot_grid(f, box, step=36, color="#FFFFFF", alpha=16, r=1.6):
    x0, y0, x1, y1 = box
    x = x0
    while x <= x1:
        y = y0
        while y <= y1:
            f.ellipse([x - r, y - r, x + r, y + r], fill=color, alpha=alpha)
            y += step
        x += step


def frame_rules(f, inset=54, color="#FFFFFF", alpha=26):
    """Corner crop marks — the campaign's editorial 'plate' signature."""
    L = 26
    for sx, sy in [(inset, inset), (f.w - inset, inset), (inset, f.h - inset),
                   (f.w - inset, f.h - inset)]:
        dx = 1 if sx < f.w / 2 else -1
        dy = 1 if sy < f.h / 2 else -1
        f.line((sx, sy), (sx + L * dx, sy), color=color, width=1.2, alpha=alpha)
        f.line((sx, sy), (sx, sy + L * dy), color=color, width=1.2, alpha=alpha)


def logo_lockup(f, y, *, size=92, light=True, center=None, wordmark=True):
    f.brand(center if center is not None else 74, y, size=size, light=light,
            wordmark=wordmark, anchor="m" if center is not None else "l")
