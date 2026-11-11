"""E — feature posters, the premium showcase ensemble, and the square launch card."""

import os

from luma_studio import Frame, load_shot
from campaign import (OUT, SHOTS, T1, T2, T3, INDIGO, VIOLET, MINT, VERMILION,
                      CHAMPAGNE, brand_backdrop, orbit_decor, dot_grid, frame_rules)

POST = os.path.join(OUT, "posters")
SITE = os.path.join(OUT, "site")
SHOT_AR = 3120 / 1440
PW, PH = 1200, 1800


def dev_h(w, pad=7):
    return w * SHOT_AR + pad * 2


def poster_shell(hue, seed, rules=True, plate="dark"):
    f = Frame(PW, PH, "#06070B")
    brand_backdrop(f, hue=hue, vignette=0.46, grain=2.0, seed=seed)
    if rules:
        frame_rules(f, inset=50, alpha=22 if plate == "dark" else 60)
    return f


def plate_index(f, n, total, color, label, *, light=False):
    """Masthead: brand lockup left, plate index and section label right."""
    f.brand(60, 54, size=32, light=not light,
            arc="#5A6B55" if light else None, core=None if not light else "#1B1A17")
    f.text(PW - 60, 52, label, size=14, weight=600, family="sg",
           color=color if not light else "#8B867C", tracking=3.2, anchor="ra")
    f.text(PW - 60, 78, f"PLATE {n:02d} / {total:02d}", size=12, weight=600,
           family="sg", color="#5D626E" if not light else "#9A948A", tracking=2.6,
           anchor="ra")


def poster_01():
    f = poster_shell("indigo", 101)
    plate_index(f, 1, 3, INDIGO, "THE HOME SCREEN")
    orbit_decor(f, PW * 0.5, 300, [170, 230, 300], color=INDIGO, alpha=26)
    f.lines(60, 132, ["A calmer", "home for", "Android."], size=138, weight=600,
            family="sg", color=T1, tracking=-5.0, leading=0.96)
    f.para(64, 596, "A restrained launcher with a light clock, a quiet dock and\none swipe to everything you own.",
           size=29, color=T2, leading=1.46, max_w=820)
    f.device(load_shot(SHOTS["home"]), PW / 2, 900 + dev_h(470) / 2, 470,
             glow_color=INDIGO, glow_strength=0.28, shadow_opacity=150,
             shadow_blur=0.07, shadow_offset=(0.0, 0.012))
    f.text(60, PH - 74, "LUMAOS PRO · VERSION 1.2.0", size=14, weight=600,
           family="sg", color=T3, tracking=3.2)
    f.text(PW - 60, PH - 74, "GOOGLE PLAY", size=14, weight=600, family="sg",
           color=T3, tracking=3.2, anchor="ra")
    f.save(os.path.join(POST, "01-calm-home-1200x1800.png"))


def poster_02():
    """Light plate: the ivory identity, for range across the campaign."""
    f = Frame(PW, PH, "#F6F3EC")
    f.gradient([(0.0, "#FBF9F4"), (0.55, "#F3EEE3"), (1.0, "#E8E1D2")], angle=105)
    f.glow(PW * 0.82, 220, 620, "#FFFFFF", 0.6, 2.0)
    f.glow(PW * 0.10, PH * 0.92, 720, "#C9D6F2", 0.34, 2.3)
    f.vignette(0.16)
    f.grain(1.8, seed=111)
    frame_rules(f, inset=50, color="#1B1A17", alpha=40)

    plate_index(f, 2, 3, "#2E5AAC", "THEMES", light=True)

    f.kicker(60, 156, "One grammar, twelve voices", color="#2E5AAC", size=15,
             tracking=3.6, dot="#2E5AAC")
    f.lines(58, 198, ["Twelve", "identities."], size=136, weight=600, family="sg",
            color="#14141A", tracking=-5.0, leading=0.98)
    f.para(62, 500, "Signature, Atmosphere and Monolith, plus the premium\ncollection — Obsidian, Ivory and Aurora.",
           size=27, color="#5D5A52", leading=1.46, max_w=840)

    trio = [("MONOLITH", SHOTS["monolith"], 300, 726, VERMILION, 14),
            ("IVORY", SHOTS["home"], 360, 672, "#2E5AAC", 0),
            ("AURORA", SHOTS["aurora"], 300, 726, "#7C86FF", -14)]
    for label, path, wid, top, col, tilt in trio:
        cx = {"MONOLITH": 214, "IVORY": 600, "AURORA": 986}[label]
        f.text(cx, top - 32, label, size=13, weight=700, family="sg", color=col,
               tracking=2.8, anchor="ma")
        f.device(load_shot(path), cx, top + dev_h(wid, 6) / 2, wid,
                 tilt=(tilt, 0) if tilt else None, glow_color=None,
                 shadow_opacity=90, shadow_blur=0.06, shadow_offset=(0.0, 0.016),
                 body_pad=6, body="#14141A")

    # premium collection swatches — real authored accents only
    f.text(60, 1660, "PREMIUM COLLECTION", size=13, weight=600, family="sg",
           color="#8B867C", tracking=3.0)
    for i, (name, col) in enumerate([("OBSIDIAN", CHAMPAGNE), ("IVORY", "#2E5AAC"),
                                     ("AURORA", "#7C86FF")]):
        x = 60 + i * 300
        f.ellipse([x, 1700, x + 22, 1722], fill=col)
        f.text(x + 34, 1702, name, size=15, weight=600, family="sg", color="#14141A",
               tracking=2.6)
    f.text(PW - 60, 1702, "LUMAOS PRO", size=15, weight=600, family="sg",
           color="#8B867C", tracking=3.0, anchor="ra")
    f.save(os.path.join(POST, "02-twelve-identities-light-1200x1800.png"))


def poster_03():
    f = poster_shell("aurora", 121)
    plate_index(f, 3, 3, MINT, "PRIVACY")
    f.glow(PW / 2, 420, 560, MINT, 0.14, 2.4)
    f.lines(PW / 2, 190, ["Nothing to", "sign up for.", "Nothing to sell."], size=98,
            weight=600, family="sg", color=T1, tracking=-3.4, leading=1.06, anchor="m")

    # ledger of what is absent — verifiable from the manifest and README
    rows = [("ACCOUNT", "none"), ("ADS", "none"), ("ANALYTICS SDK", "none"),
            ("ADVERTISING ID", "none"), ("NETWORK PERMISSION", "none")]
    y = 620
    for k, v in rows:
        f.text(120, y, k, size=14, weight=600, family="sg", color=T3, tracking=3.0)
        f.text(PW - 120, y, v.upper(), size=14, weight=600, family="sg", color=MINT,
               tracking=3.0, anchor="ra")
        f.line((120, y + 42), (PW - 120, y + 42), color=MINT, width=1, alpha=44)
        y += 66

    f.para(120, 1000, "Your layout, launch history and diagnostics stay on the device.\n"
                      "Android backup is the only thing that leaves — at your choice.",
           size=25, color=T2, leading=1.5, max_w=880)
    f.device(load_shot(SHOTS["home"]), PW / 2, 1130 + dev_h(430) / 2, 430,
             glow_color=MINT, glow_strength=0.14, shadow_opacity=170,
             shadow_blur=0.06, shadow_offset=(0.0, 0.014))
    f.text(60, PH - 74, "LUMAOS PRO · PRIVACY BY CONSTRUCTION", size=14, weight=600,
           family="sg", color=T3, tracking=3.2)
    f.save(os.path.join(POST, "03-privacy-ledger-1200x1800.png"))


def ensemble():
    """Premium showcase artwork: the four real screens, staged as one system."""
    W, H = 2400, 1600
    f = Frame(W, H, "#05060A")
    f.gradient([(0.0, "#080A14"), (0.45, "#0D1022"), (1.0, "#05060A")], angle=100)
    f.glow(W * 0.50, H * 0.42, 1200, INDIGO, 0.34, 2.2)
    f.glow(W * 0.14, H * 0.86, 700, VIOLET, 0.20, 2.4)
    f.glow(W * 0.88, H * 0.14, 620, MINT, 0.10, 2.6)
    dot_grid(f, (100, 120, 560, 460), step=34, alpha=14, r=1.5)
    f.vignette(0.42)
    f.grain(2.0, seed=131)

    f.brand(104, 88, size=40, light=True)
    f.kicker(108, 190, "The system, in four screens", color=INDIGO, size=16,
             tracking=3.6, dot=INDIGO)
    f.lines(104, 228, ["One platform.", "Every surface."], size=90, weight=600,
            family="sg", color=T1, tracking=-3.0, leading=1.02)
    f.para(108, 428, "Home, drawer, Studio and diagnostics share\none design engine, one token set and one ladder.",
           size=25, color=T2, leading=1.5, max_w=660)
    f.line((108, H - 268), (700, H - 268), color="#FFFFFF", width=1, alpha=18)
    ledger = [("12", "DESIGN IDENTITIES"), ("60", "ICON GLYPHS"),
              ("36", "PACKAGED WALLPAPERS"), ("4", "CONTEXT PROFILES")]
    for i, (num, label) in enumerate(ledger):
        x = 108 + i * 182
        y = H - 232
        f.text(x, y, num, size=40, weight=600, family="sg", color=T1, tracking=-1.4)
        f.text(x, y + 48, label, size=10.5, weight=600, family="sg", color=T3,
               tracking=1.9)
    f.text(108, H - 100, "LUMAOS PRO · 1.2.0 · LUMA GLASS v3.2", size=14, weight=600,
           family="sg", color=T3, tracking=3.2)

    # staged deck: four screens stepping up-right, each in front of the last
    deck = [
        (SHOTS["monolith"], 884, 880, 344, 15, VERMILION, 0.08),
        (SHOTS["drawer"], 1196, 934, 388, 9, VIOLET, 0.11),
        (SHOTS["aurora"], 1584, 1004, 430, -12, INDIGO, 0.24),
        (SHOTS["home"], 1990, 1072, 466, 0, INDIGO, 0.28),
    ]
    for path, cx, cy, w, tilt, glow, gs in deck:
        f.device(load_shot(path), cx, cy, w, tilt=(tilt, 0) if tilt else None,
                 glow_color=glow, glow_strength=gs, shadow_opacity=150,
                 shadow_blur=0.06, shadow_offset=(0.0, 0.012))
    f.save(os.path.join(SITE, "showcase-ensemble-2400x1600.png"))


def launch_square():
    W = H = 1080
    f = Frame(W, H, "#06070B")
    brand_backdrop(f, hue="indigo", vignette=0.44, grain=2.0, seed=141)
    orbit_decor(f, W / 2, H * 0.5, [300, 380, 470, 580], color=INDIGO, alpha=24,
                width=1.2)
    f.mark(W / 2, 340, 190)
    f.text(W / 2, 470, "NOW AVAILABLE", size=15, weight=600, family="sg",
           color=INDIGO, tracking=5.0, anchor="ma")
    f.lines(W / 2, 506, ["LumaOS Pro", "1.2.0"], size=104, weight=600, family="sg",
            color=T1, tracking=-3.4, leading=1.02, anchor="m")
    f.para(W / 2, 736, "A calmer home for Android — twelve design identities,\na live Studio, and privacy without conditions.",
           size=24, color=T2, leading=1.5, max_w=780, anchor="m")
    f.pill(W / 2, 862, "GOOGLE PLAY · ANDROID 8.0+", size=15, pad=(32, 17),
           fill="rgba(255,255,255,0.08)", color=T1, tracking=2.8, anchor="m",
           stroke="rgba(255,255,255,0.22)")
    f.brand(W / 2, 960, size=34, light=True, anchor="m")
    f.save(os.path.join(SITE, "launch-square-1080x1080.png"))


if __name__ == "__main__":
    for fn in (poster_01, poster_02, poster_03, ensemble, launch_square):
        fn(); print("·", fn.__name__)
