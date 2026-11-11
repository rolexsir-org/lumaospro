"""C — Instagram stories and reel covers, 1080 × 1920.

Safe areas respected: ~250 px reserved at the top (platform header) and at the
bottom (reply bar / CTA). Reel covers keep their key content inside the central
1080 × 1080 crop that the profile grid shows.
"""

import os

from luma_studio import Frame, load_shot
from campaign import (OUT, SHOTS, T1, T2, T3, INDIGO, VIOLET, MINT, VERMILION,
                      brand_backdrop, orbit_decor, frame_rules)

W, H = 1080, 1920
ST = os.path.join(OUT, "stories")
TOP_SAFE, BOT_SAFE = 250, 260
SHOT_AR = 3120 / 1440


def base(hue="indigo", seed=5, rules=True):
    f = Frame(W, H, "#07080D")
    brand_backdrop(f, hue=hue, vignette=0.5, grain=2.0, seed=seed)
    if rules:
        frame_rules(f, inset=52, alpha=18)
    return f


def dev_bleed(f, cx, top, width, path="home", pad=7, **kw):
    shot = load_shot(SHOTS[path])
    h = width * SHOT_AR + pad * 2
    f.device(shot, cx, top + h / 2, width, body_pad=pad,
             shadow_opacity=205, shadow_blur=0.05, shadow_offset=(0.0, 0.02), **kw)


# --------------------------------------------------------------------------

def st1_launch():
    f = base("indigo", seed=61)
    orbit_decor(f, W / 2, 470, [200, 270, 350, 440], color=INDIGO, alpha=30, width=1.3)
    f.mark(W / 2, 470, 220)
    f.text(W / 2, 660, "NOW ON GOOGLE PLAY", size=16, weight=600, family="sg",
           color=INDIGO, tracking=5.0, anchor="ma")
    f.lines(W / 2, 700, ["A calmer", "home for", "Android."], size=126, weight=600,
            family="sg", color=T1, tracking=-4.0, leading=1.0, anchor="m")
    f.para(W / 2, 1112, "A lightweight launcher and performance platform,\nbuilt for the phones you already own.",
           size=31, color=T2, leading=1.48, max_w=820, anchor="m")
    f.pill(W / 2, 1300, "SWIPE UP · GOOGLE PLAY", size=17, pad=(38, 20),
           fill="rgba(255,255,255,0.92)", color="#0A0C12", tracking=2.6, anchor="m")
    dev_bleed(f, W / 2, 1480, 470, "home", glow_color=INDIGO, glow_strength=0.22)
    f.save(os.path.join(ST, "01-launch-story-1080x1920.png"))


def st2_themes():
    f = base("indigo", seed=68)
    f.glow(W * 0.5, 900, 700, VIOLET, 0.18, 2.3)
    f.brand(64, 300, size=36, light=True)
    f.kicker(64, 386, "Twelve identities", color=MINT, size=17, tracking=4.0, dot=MINT)
    f.lines(62, 424, ["Pick a", "mood."], size=140, weight=600, family="sg",
            color=T1, tracking=-4.4, leading=1.0)
    f.para(64, 740, "Signature, Atmosphere, Monolith —\nplus Obsidian, Ivory and Aurora.",
           size=30, color=T2, leading=1.46, max_w=800)

    f.device(load_shot(SHOTS["monolith"]), 300, 1560, 400, tilt=(18, 0),
             glow_color=VERMILION, glow_strength=0.10, body_pad=6,
             shadow_opacity=205, shadow_blur=0.05, shadow_offset=(0.0, 0.02))
    f.device(load_shot(SHOTS["aurora"]), 780, 1540, 430, tilt=(-18, 0),
             glow_color=INDIGO, glow_strength=0.24, body_pad=6,
             shadow_opacity=205, shadow_blur=0.05, shadow_offset=(0.0, 0.02))
    for cx, label, col in [(300, "MONOLITH", VERMILION), (780, "AURORA", T2)]:
        f.text(cx, 1250, label, size=14, weight=600, family="sg", color=col,
               tracking=3.2, anchor="ma")
    f.save(os.path.join(ST, "02-themes-story-1080x1920.png"))


def st3_privacy():
    f = base("aurora", seed=72)
    f.glow(W / 2, 820, 620, MINT, 0.14, 2.4)
    f.mark(W / 2, 470, 170)
    f.text(W / 2, 620, "PRIVACY BY CONSTRUCTION", size=16, weight=600, family="sg",
           color=MINT, tracking=4.4, anchor="ma")
    f.lines(W / 2, 668, ["Nothing to", "sign up for.", "Nothing to sell."], size=96,
            weight=600, family="sg", color=T1, tracking=-3.0, leading=1.08, anchor="m")
    f.para(W / 2, 1040, "No account, no ads, no analytics SDK, no network permission.\nEverything you set up stays on your phone.",
           size=28, color=T2, leading=1.5, max_w=830, anchor="m")
    dev_bleed(f, W / 2, 1250, 460, "home", glow_color=MINT, glow_strength=0.14)
    f.save(os.path.join(ST, "03-privacy-story-1080x1920.png"))


def st4_studio():
    f = base("indigo", seed=77)
    f.glow(W / 2, 1150, 720, INDIGO, 0.24, 2.2)
    f.brand(64, 300, size=36, light=True)
    f.kicker(64, 388, "The Studio", color=INDIGO, size=17, tracking=4.0, dot=INDIGO)
    f.lines(62, 426, ["Design it", "until it", "feels right."], size=120, weight=600,
            family="sg", color=T1, tracking=-3.6, leading=1.0)
    f.para(64, 812, "Palette, type, icon shape and backdrop —\npreviewed live before you apply.",
           size=30, color=T2, leading=1.46, max_w=820)
    dev_bleed(f, W / 2, 1020, 470, "aurora", glow_color=INDIGO, glow_strength=0.24)
    f.save(os.path.join(ST, "04-studio-story-1080x1920.png"))


def reel_cover():
    """Reel cover: key content inside the central 1080 × 1080 grid crop."""
    f = base("indigo", seed=84)
    orbit_decor(f, W / 2, 950, [260, 340, 430], color=INDIGO, alpha=26, width=1.3)
    f.mark(W / 2, 590, 176)
    f.lines(W / 2, 720, ["A calmer home", "for Android."], size=104, weight=600,
            family="sg", color=T1, tracking=-3.4, leading=1.04, anchor="m")
    f.para(W / 2, 946, "LumaOS Pro · Launcher and\nperformance platform", size=30,
           color=T2, leading=1.45, max_w=760, anchor="m")
    f.pill(W / 2, 1080, "GOOGLE PLAY", size=16, pad=(34, 17),
           fill="rgba(255,255,255,0.10)", color=T1, tracking=3.0, anchor="m",
           stroke="rgba(255,255,255,0.26)")
    dev_bleed(f, W / 2, 1290, 400, "home", glow_color=INDIGO, glow_strength=0.20)
    f.save(os.path.join(ST, "05-reel-cover-1080x1920.png"))


ALL = [st1_launch, st2_themes, st3_privacy, st4_studio, reel_cover]

if __name__ == "__main__":
    for fn in ALL:
        fn()
        print("·", fn.__name__)
