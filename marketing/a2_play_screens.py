"""A2 — Google Play promotional screenshots, 1080 × 1920 (seven-screen series)."""

import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from luma_studio import Frame, load_shot
from campaign import (OUT, SHOTS, T1, T2, T3, INDIGO, VIOLET, MINT, VERMILION,
                      CHAMPAGNE, brand_backdrop)

W, H = 1080, 1920
PLAY = os.path.join(OUT, "play")


def shell(hue="indigo", seed=5):
    f = Frame(W, H, "#07080D")
    brand_backdrop(f, hue=hue, vignette=0.46, grain=2.0, seed=seed)
    f.brand(72, 66, size=34, light=True)
    f.text(W - 72, 74, "V1.2.0", size=17, weight=500, family="sg",
           color=T3, tracking=3.4, anchor="ra")
    return f


def headline(f, kicker, lines, sub=None, *, color=T1, kcolor=INDIGO, y=150,
             size=86, leading=1.03, tracking=-2.4, sub_y=None, sub_color=T2):
    f.kicker(74, y, kicker, color=kcolor, size=19, tracking=3.6, dot=kcolor)
    end = f.lines(70, y + 46, lines, size=size, weight=600, family="sg", color=color,
                  tracking=tracking, leading=leading)
    if sub:
        f.para(74, (sub_y or end + 30), sub, size=26, weight=400, color=sub_color,
               leading=1.42, max_w=830)
    return end


def footer(f, label, *, color=T3, y=1846):
    f.text(72, y, label, size=17, weight=500, family="sg", color=color, tracking=2.6)
    f.text(W - 72, y, "lumaos.pro", size=17, weight=500, family="sg", color=color,
           tracking=2.6, anchor="ra")


def save(f, name):
    f.save(os.path.join(PLAY, name))


# --------------------------------------------------------------------------

def s1_home():
    f = shell("indigo", seed=3)
    headline(f, "The home screen", ["Your phone,", "finally calm."],
             "A restrained launcher that opens fast, then gets out of the way.",
             kcolor=INDIGO)
    shot = load_shot(SHOTS["home"])
    f.device(shot, W / 2, 1150, 520, glow_color=INDIGO, glow_strength=0.30,
             shadow_opacity=205, shadow_blur=0.055, shadow_offset=(0.0, 0.022),
             body_pad=8)
    footer(f, "Luma Glass · Signature theme")
    save(f, "02-home-1080x1920.png")


def s2_drawer():
    """Annotated device: the real drawer with hairline callouts for search and
    the A-Z rail — the campaign's editorial device for feature-led frames."""
    f = shell("indigo", seed=8)
    f.glow(W * 0.5, 1120, 700, VIOLET, 0.18, 2.3)
    headline(f, "The app drawer", ["Every app.", "One swipe."],
             "An alphabetical drawer with instant search and an A–Z rail — no folders to hunt through.",
             kcolor=VIOLET)

    shot = load_shot(SHOTS["drawer"])
    cx, cy, w = W / 2, 1150, 520
    h = w * shot.height / shot.width + 16
    top, bot = cy - h / 2, cy + h / 2
    left, right = cx - w / 2, cx + w / 2

    f.device(shot, cx, cy, w, glow_color=VIOLET, glow_strength=0.24,
             shadow_opacity=205, shadow_blur=0.055, shadow_offset=(0.0, 0.022),
             body_pad=8)

    def callout(x_txt, y_txt, anchor, x_to, y_to, label, color=VIOLET):
        f.text(x_txt, y_txt, label, size=15, weight=600, family="sg", color=T2,
               tracking=2.2, anchor=anchor)
        # elbow leader: horizontal stub then a short diagonal
        x_edge = x_to - 26 if x_to > x_txt else x_to + 26
        f.line((x_txt if anchor == "la" else x_txt, y_txt + 8),
               (x_edge, y_txt + 8), color=color, width=1.4, alpha=110)
        f.line((x_edge, y_txt + 8), (x_to, y_to), color=color, width=1.4, alpha=110)
        f.ellipse([x_to - 3.5, y_to - 3.5, x_to + 3.5, y_to + 3.5], fill=color)

    # search field sits at ~16.5% of the screen height, chips at ~24.5%
    callout(64, top + h * 0.158, "la", left + w * 0.13, top + h * 0.168, "SEARCH AS YOU TYPE")
    callout(W - 64, top + h * 0.238, "ra", right - 20, top + h * 0.242, "ALL / FREQUENT")
    callout(64, top + h * 0.44, "la", left + w * 0.055, top + h * 0.445, "58 APPS INDEXED")
    footer(f, "Drawer · alphabetical A–Z rail")
    save(f, "03-drawer-1080x1920.png")


def s3_themes():
    f = shell("indigo", seed=12)
    f.glow(W * 0.72, 1150, 620, INDIGO, 0.24, 2.2)
    headline(f, "Twelve identities · one grammar",
             ["From cinematic", "to cut stone."],
             "Signature, Atmosphere and Monolith, plus the premium collection.",
             kcolor=MINT, size=78)
    a = load_shot(SHOTS["aurora"])
    b = load_shot(SHOTS["monolith"])
    f.device(b, 300, 1250, 372, tilt=(15, 0), glow_color=VERMILION, glow_strength=0.13,
             shadow_opacity=205, shadow_blur=0.06, shadow_offset=(0.0, 0.026), body_pad=7)
    f.device(a, 736, 1218, 426, tilt=(-15, 0), glow_color=INDIGO, glow_strength=0.28,
             shadow_opacity=210, shadow_blur=0.055, shadow_offset=(0.0, 0.022), body_pad=7)
    footer(f, "Studio · Monolith and Aurora")
    save(f, "04-themes-1080x1920.png")


def s4_studio():
    f = shell("indigo", seed=21)
    f.glow(W * 0.55, 1250, 640, INDIGO, 0.22, 2.2)
    headline(f, "The Studio", ["Design it until", "it feels right."],
             "Palette, type, icon shape, backdrop — previewed live before you apply.",
             kcolor=INDIGO, size=84)
    shot = load_shot(SHOTS["aurora"])
    f.device(shot, W / 2, 1150, 520, glow_color=INDIGO, glow_strength=0.30,
             shadow_opacity=205, shadow_blur=0.055, shadow_offset=(0.0, 0.022),
             body_pad=8)
    footer(f, "Studio · Aurora · Expressive")
    save(f, "05-studio-1080x1920.png")


def s5_private():
    f = shell("indigo", seed=33)
    f.glow(W * 0.5, 1080, 640, MINT, 0.12, 2.4)
    headline(f, "Privacy by construction", ["No account.", "No ads. No tracking."],
             "No analytics SDK, no advertising ID, no network permission. Your layout stays on your phone.",
             kcolor=MINT, size=80)
    shot = load_shot(SHOTS["home"])
    f.device(shot, W / 2, 1150, 520, glow_color=MINT, glow_strength=0.16,
             shadow_opacity=205, shadow_blur=0.055, shadow_offset=(0.0, 0.022),
             body_pad=8)
    footer(f, "Local storage · Android backup only")
    save(f, "06-private-1080x1920.png")


def s6_fast():
    f = shell("indigo", seed=41)
    f.glow(W * 0.5, 1150, 660, INDIGO, 0.26, 2.2)
    headline(f, "Built for the phones you already own", ["Light on the", "hardware."],
             "Views for the home hot path, Compose for settings — and every reading labelled measured, estimated or unavailable.",
             kcolor=VIOLET, size=76)
    shot = load_shot(SHOTS["drawer"])
    f.device(shot, W / 2, 1150, 520, glow_color=VIOLET, glow_strength=0.24,
             shadow_opacity=205, shadow_blur=0.055, shadow_offset=(0.0, 0.022),
             body_pad=8)
    footer(f, "minSdk 26 · Android 8.0 and up")
    save(f, "07-lightweight-1080x1920.png")


def s7_contexts():
    f = shell("indigo", seed=52)
    f.glow(W * 0.42, 1180, 620, INDIGO, 0.22, 2.2)
    headline(f, "Context profiles", ["Work at nine.", "Sleep by ten."],
             "Four contexts, each with its own layout and app visibility, switching on a schedule that never leaves the device.",
             kcolor=INDIGO, size=82)
    shot = load_shot(SHOTS["home"])
    f.device(shot, W / 2, 1150, 520, glow_color=INDIGO, glow_strength=0.26,
             shadow_opacity=205, shadow_blur=0.055, shadow_offset=(0.0, 0.022),
             body_pad=8)
    footer(f, "Work · Study · Personal · Sleep")
    save(f, "08-contexts-1080x1920.png")


ALL = [s1_home, s2_drawer, s3_themes, s4_studio, s5_private, s6_fast, s7_contexts]

if __name__ == "__main__":
    for fn in ALL:
        fn()
        print("·", fn.__name__)
