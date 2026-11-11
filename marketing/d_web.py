"""D — Website / landing-page hero banner and the product announcement card."""

import os

from luma_studio import Frame, load_shot
from campaign import (OUT, SHOTS, T1, T2, T3, INDIGO, VIOLET, MINT, VERMILION,
                      brand_backdrop, orbit_decor, dot_grid, frame_rules)

SITE = os.path.join(OUT, "site")
SHOT_AR = 3120 / 1440


def hero_wide():
    """Landing-page hero, 2400 × 1000. Product anchored right, copy on the left,
    a nav bar and a single conversion CTA."""
    W, H = 2400, 1000
    f = Frame(W, H, "#06070B")
    f.gradient([(0.0, "#0B0D1B"), (0.40, "#0F1226"), (0.74, "#0A0B16"), (1.0, "#05060A")],
               angle=100)
    f.glow(W * 0.24, H * 0.30, W * 0.55, INDIGO, 0.34, 2.3)
    f.glow(W * 0.80, H * 0.86, W * 0.42, VIOLET, 0.22, 2.4)
    orbit_decor(f, W * 0.80, H * 0.42, [420, 520, 640, 780], color=INDIGO, alpha=16,
                width=1.2)
    f.vignette(0.4)
    f.grain(2.0, seed=91)

    # nav
    f.brand(104, 56, size=38, light=True)
    nav = ["Overview", "Themes", "Studio", "Privacy"]
    x = W - 104
    for item in reversed(nav):
        w = f.tw(item, 19, 500)
        f.text(x - w, 70, item, size=19, weight=500, color=T2)
        x -= w + 56
    f.line((104, 132), (W - 104, 132), color="#FFFFFF", width=1, alpha=18)

    # copy
    f.kicker(104, 210, "Android launcher · v1.2.0", color=INDIGO, size=18,
             tracking=3.6, dot=INDIGO)
    f.lines(100, 254, ["A calmer home", "for Android."], size=118, weight=600,
            family="sg", color=T1, tracking=-3.8, leading=1.02)
    f.para(104, 512, "A standalone launcher and performance platform: twelve design identities,\n"
                     "a live theme Studio, and privacy that needs no caveats.",
           size=27, color=T2, leading=1.5, max_w=1180)
    f.pill(104, 654, "GET IT ON GOOGLE PLAY", size=20, pad=(40, 24), fill=T1,
           color="#08090F", tracking=2.2, weight=600)
    f.pill(104 + 400, 654, "EXPLORE THE STUDIO", size=20, pad=(40, 24),
           fill="rgba(255,255,255,0.06)", color=T1, tracking=2.2,
           stroke="rgba(255,255,255,0.22)", weight=500)
    for i, item in enumerate(["NO ACCOUNT", "NO ADS", "NO ANALYTICS", "ANDROID 8.0+"]):
        f.text(104 + i * 214, 776, item, size=14, weight=600, family="sg", color=T3,
               tracking=2.8)
    f.line((104, 866), (W - 104, 866), color="#FFFFFF", width=1, alpha=16)
    for i, (k, v) in enumerate([("VERSION", "1.2.0"), ("MIN SDK", "26 · Android 8.0"),
                                ("TARGET SDK", "36 · Android 16"),
                                ("DENOMINATION", "Luma Glass v3.2")]):
        x = 104 + i * 300
        f.text(x, 900, k, size=13, weight=600, family="sg", color="#5A6070", tracking=2.6)
        f.text(x, 928, v, size=18, weight=500, color=T2)

    # device: bottom-anchored, bleeding off the lower edge
    shot = load_shot(SHOTS["aurora"])
    f.device(shot, 1738, 586, 392, glow_color=INDIGO, glow_strength=0.34,
             shadow_opacity=120, shadow_blur=0.07, shadow_offset=(0.0, 0.012))
    # secondary device, smaller, further back, bleeding off the right edge
    f.device(load_shot(SHOTS["monolith"]), 2112, 716, 306, tilt=(-14, 0),
             glow_color=VERMILION, glow_strength=0.10, shadow_opacity=120,
             shadow_blur=0.07, shadow_offset=(0.0, 0.012))
    f.save(os.path.join(SITE, "hero-banner-2400x1000.png"))


def hero_16x9():
    """16:9 website/press hero, 1600 × 900."""
    W, H = 1600, 900
    f = Frame(W, H, "#06070B")
    brand_backdrop(f, hue="indigo", vignette=0.42, grain=2.0, seed=93)
    orbit_decor(f, W * 0.84, H * 0.44, [300, 380, 470], color=INDIGO, alpha=18)
    f.brand(88, 62, size=36, light=True)
    f.kicker(92, 190, "Launcher · Performance · Privacy", color=INDIGO, size=17,
             tracking=3.8, dot=INDIGO)
    f.lines(88, 236, ["A calmer home", "for Android."], size=104, weight=600,
            family="sg", color=T1, tracking=-3.4, leading=1.02)
    f.para(92, 468, "Twelve design identities. A live theme Studio.\nNo account, no ads, no analytics.",
           size=27, color=T2, leading=1.5, max_w=760)
    f.pill(92, 600, "GET IT ON GOOGLE PLAY", size=19, pad=(38, 22), fill=T1,
           color="#08090F", tracking=2.2)
    f.text(92, 760, "LUMAOS PRO · VERSION 1.2.0", size=14, weight=600, family="sg",
           color=T3, tracking=3.0)

    width = 400
    h = width * SHOT_AR + 14
    f.device(load_shot(SHOTS["home"]), 1230, H - 250, width, glow_color=INDIGO,
             glow_strength=0.32, shadow_opacity=205, shadow_blur=0.05,
             shadow_offset=(0.0, 0.02))
    f.save(os.path.join(SITE, "hero-16x9-1600x900.png"))


def announcement_card():
    """Product announcement card, 1200 × 630 (link previews / press / OG)."""
    W, H = 1200, 630
    f = Frame(W, H, "#06070B")
    f.gradient([(0.0, "#0B0D1B"), (0.5, "#101329"), (1.0, "#06070C")], angle=100)
    f.glow(W * 0.22, H * 0.30, 640, INDIGO, 0.36, 2.3)
    f.glow(W * 0.9, H * 0.9, 520, VIOLET, 0.22, 2.4)
    dot_grid(f, (60, 470, 700, 588), step=26, alpha=18, r=1.4)
    f.vignette(0.36)
    f.grain(1.8, seed=97)

    f.brand(72, 58, size=32, light=True)
    f.kicker(76, 160, "Announcing", color=INDIGO, size=16, tracking=4.2, dot=INDIGO)
    f.lines(72, 196, ["LumaOS Pro 1.2"], size=86, weight=600, family="sg",
            color=T1, tracking=-3.0, leading=1.0)
    f.para(74, 318, "Launcher, themes, Studio, Vault and diagnostics —\n"
                    "shipping as a complete, local-first platform.",
           size=24, color=T2, leading=1.48, max_w=640)
    f.line((74, 470), (330, 470), color=INDIGO, width=2, alpha=190)
    f.text(74, 500, "GOOGLE PLAY · ANDROID 8.0+", size=14, weight=600, family="sg",
           color=T3, tracking=3.2)

    width = 300
    h = width * SHOT_AR + 12
    f.device(load_shot(SHOTS["aurora"]), 1010, H - 120, width, glow_color=INDIGO,
             glow_strength=0.30, shadow_opacity=205, shadow_blur=0.05,
             shadow_offset=(0.0, 0.02))
    f.save(os.path.join(SITE, "announcement-1200x630.png"))


if __name__ == "__main__":
    hero_wide(); print("· hero_wide")
    hero_16x9(); print("· hero_16x9")
    announcement_card(); print("· announcement_card")
