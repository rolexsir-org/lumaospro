"""B — Instagram posts (1080 × 1350) and the launch announcement.

Layout rule for the 4:5 set: type owns the top of the plate, the product enters
from below and bleeds off the bottom edge. Device boxes are computed from the
screenshot's real aspect ratio so nothing overlaps by accident.
"""

import os

from luma_studio import Frame, load_shot
from campaign import (OUT, SHOTS, T1, T2, T3, INDIGO, VIOLET, MINT, VERMILION,
                      brand_backdrop, orbit_decor, frame_rules)

W, H = 1080, 1350
IG = os.path.join(OUT, "instagram")
SHOT_AR = 3120 / 1440          # the reference screenshots, unmodified
PAD = 7


def dev_h(width, pad=PAD):
    return width * SHOT_AR + pad * 2


def bleed(cy_top, width, pad=PAD):
    """Centre-y for a device whose top edge sits at cy_top (bottom may bleed)."""
    return cy_top + dev_h(width, pad) / 2


def post_base(hue="indigo", seed=5, rules=True):
    f = Frame(W, H, "#07080D")
    brand_backdrop(f, hue=hue, vignette=0.44, grain=2.0, seed=seed)
    if rules:
        frame_rules(f, inset=46, alpha=20)
    return f


def lockup_top(f, right=None, light=True):
    f.brand(62, 56, size=32, light=light)
    if right:
        f.text(W - 62, 64, right, size=15, weight=600, family="sg", color=T3,
               tracking=3.2, anchor="ra")


def cta_footer(f, label="AVAILABLE ON GOOGLE PLAY", y=1300, color=T3):
    f.text(W / 2, y, label, size=14, weight=600, family="sg", color=color,
           tracking=3.6, anchor="ma")


# --------------------------------------------------------------------------

def ig1_launch():
    f = post_base("indigo", seed=11)
    orbit_decor(f, W / 2, 200, [140, 196, 256, 322], color=INDIGO, alpha=32, width=1.2)
    f.mark(W / 2, 200, 168)
    lockup_top(f, "LAUNCH")
    f.text(W / 2, 330, "LUMAOS PRO · VERSION 1.2.0", size=14, weight=600, family="sg",
           color=INDIGO, tracking=4.2, anchor="ma")
    f.lines(W / 2, 366, ["A calmer home", "for Android."], size=92, weight=600,
            family="sg", color=T1, tracking=-2.8, leading=1.04, anchor="m")
    f.para(W / 2, 576, "A lightweight launcher and performance platform —\nbuilt for the phones you already own.",
           size=28, color=T2, leading=1.5, max_w=780, anchor="m")

    f.device(load_shot(SHOTS["home"]), W / 2, bleed(742, 496), 496,
             glow_color=INDIGO, glow_strength=0.28, shadow_opacity=200,
             shadow_blur=0.05, shadow_offset=(0.0, 0.018))
    cta_footer(f, y=672, color=T3)
    f.save(os.path.join(IG, "01-launch-announcement-1080x1350.png"))


def ig2_themes():
    f = post_base("indigo", seed=18)
    lockup_top(f, "12 IDENTITIES")
    f.kicker(62, 152, "One grammar, twelve voices", color=MINT, size=15,
             tracking=3.6, dot=MINT)
    f.lines(60, 196, ["Twelve", "identities."], size=100, weight=600, family="sg",
            color=T1, tracking=-3.0, leading=1.02)
    f.para(62, 458, "Signature, Atmosphere and Monolith, plus the premium\ncollection — Obsidian, Ivory and Aurora.",
           size=26, color=T2, leading=1.45, max_w=780)

    trio = [("MONOLITH", SHOTS["monolith"], 300, 852, VERMILION, 16),
            ("SIGNATURE", SHOTS["home"], 372, 780, INDIGO, 0),
            ("AURORA", SHOTS["aurora"], 300, 852, INDIGO, -16)]
    for label, path, wid, top, glow, tilt in trio:
        cx = {"MONOLITH": 200, "SIGNATURE": 540, "AURORA": 880}[label]
        f.text(cx, top - 34, label, size=13, weight=600, family="sg",
               color=glow if label != "AURORA" else T2, tracking=2.8, anchor="ma")
        f.device(load_shot(path), cx, bleed(top, wid), wid, tilt=(tilt, 0) if tilt else None,
                 glow_color=glow, glow_strength=0.22 if label == "SIGNATURE" else 0.12,
                 shadow_opacity=205, shadow_blur=0.05,
                 shadow_offset=(0.0, 0.02), body_pad=PAD)
    f.text(62, 700, "ONE GRAMMAR · TWELVE PRESETS", size=13, weight=600, family="sg",
           color=T3, tracking=2.8)
    f.save(os.path.join(IG, "02-twelve-identities-1080x1350.png"))


def ig3_privacy():
    f = post_base("aurora", seed=25)
    f.glow(W * 0.5, 520, 540, MINT, 0.13, 2.4)
    lockup_top(f, "PRIVACY")
    f.kicker(W / 2, 206, "Privacy by construction", color=MINT, size=15, tracking=4.0,
             anchor="m", dot=MINT)
    f.lines(W / 2, 248, ["No account.", "No ads.", "No tracking."], size=96, weight=600,
            family="sg", color=T1, tracking=-3.0, leading=1.04, anchor="m")
    f.para(W / 2, 566, "No analytics SDK. No advertising ID. No network permission.\nYour layout, history and diagnostics stay on your phone.",
           size=26, color=T2, leading=1.5, max_w=800, anchor="m")

    labels = ["NO ACCOUNT", "NO ADS", "NO ANALYTICS"]
    widths = [f.tw(l, 15, 600, "sg", 2.6) + 60 for l in labels]
    x = W / 2 - (sum(widths) + 24) / 2
    for label, w in zip(labels, widths):
        f.pill(x, 686, label, size=15, pad=(30, 16),
               fill="rgba(255,255,255,0.06)", color="#CFEFE4",
               tracking=2.6, stroke="rgba(70,224,176,0.32)")
        x += w + 12

    f.device(load_shot(SHOTS["home"]), W / 2, bleed(830, 452), 452,
             glow_color=MINT, glow_strength=0.15, shadow_opacity=200,
             shadow_blur=0.05, shadow_offset=(0.0, 0.018))
    cta_footer(f, "EVERY PERMISSION LISTED IN THE README", y=790)
    f.save(os.path.join(IG, "03-privacy-1080x1350.png"))


def ig4_monolith():
    f = post_base("monolith", seed=31)
    f.glow(W * 0.5, 1100, 640, VERMILION, 0.10, 2.6)
    lockup_top(f, "MONOLITH")
    f.lines(62, 156, ["Cut stone,", "not glass."], size=104, weight=600, family="sg",
            color="#F4F2EC", tracking=-3.2, leading=1.0)
    f.rect([62, 388, 148, 392], radius=2, fill=VERMILION)
    f.para(62, 420, "Monolith drops blur entirely: stone neutrals, near-square\ngeometry and one vermilion signal. Motion is exact.",
           size=26, color="#A5A29A", leading=1.46, max_w=780)
    f.device(load_shot(SHOTS["monolith"]), W / 2, bleed(756, 464), 464,
             glow_color=VERMILION, glow_strength=0.09, shadow_opacity=210,
             shadow_blur=0.05, shadow_offset=(0.0, 0.018))
    f.text(62, 700, "SPACE GROTESK 84 SP · NO BLUR · 8 DP RADII", size=13, weight=500,
           family="sg", color="#726F68", tracking=2.6)
    f.save(os.path.join(IG, "04-monolith-feature-1080x1350.png"))


def ig5_light():
    f = post_base("indigo", seed=38)
    orbit_decor(f, W * 0.9, 170, [190, 250, 320], color=VIOLET, alpha=26)
    lockup_top(f, "PERFORMANCE")
    f.lines(62, 150, ["Built for", "the phones", "you own."], size=80, weight=600,
            family="sg", color=T1, tracking=-2.4, leading=1.04)

    rows = [("MIN SDK", "26 · Android 8.0"), ("TARGET SDK", "36 · Android 16"),
            ("HOME UI", "Views"), ("SETTINGS UI", "Compose"),
            ("READING STATES", "Measured · Estimated · Unavailable")]
    y = 488
    for k, v in rows:
        f.text(62, y, k, size=13, weight=600, family="sg", color=T3, tracking=2.6)
        f.text(W - 62, y - 4, v, size=21, weight=500, color=T1, anchor="ra")
        f.line((60, y + 40), (W - 60, y + 40), color="#FFFFFF", width=1, alpha=20)
        y += 68

    f.device(load_shot(SHOTS["drawer"]), 812, bleed(848, 330), 330,
             glow_color=INDIGO, glow_strength=0.22, shadow_opacity=200,
             shadow_blur=0.05, shadow_offset=(0.0, 0.018))
    f.lines(62, 856, ["NO ACCOUNT", "NO ADS", "NO ANALYTICS", "NO NETWORK"], size=16,
            weight=600, family="sg", color=T3, tracking=2.8, leading=1.8)
    f.save(os.path.join(IG, "05-lightweight-1080x1350.png"))


def ig6_manifesto():
    f = post_base("indigo", seed=45)
    orbit_decor(f, W / 2, H * 0.42, [220, 300, 392, 486], color=INDIGO, alpha=22,
                width=1.1)
    f.mark(W / 2, 300, 140)
    f.lines(W / 2, 430, ["A home screen", "should ask", "nothing of you."],
            size=88, weight=600, family="sg", color=T1, tracking=-2.6, leading=1.06,
            anchor="m")
    f.para(W / 2, 742, "No feed. No suggestions. No reason to stay.\nJust your apps, on time, every time.",
           size=26, color=T2, leading=1.5, max_w=720, anchor="m")
    f.line((W / 2 - 56, 892), (W / 2 + 56, 892), color=INDIGO, width=2, alpha=190)
    f.brand(W / 2, 1000, size=38, light=True, anchor="m")
    f.text(W / 2, 1064, "ANDROID LAUNCHER · LOCAL-FIRST · NO ADS", size=13, weight=600,
           family="sg", color=T3, tracking=3.4, anchor="ma")
    cta_footer(f)
    f.save(os.path.join(IG, "06-manifesto-1080x1350.png"))


ALL = [ig1_launch, ig2_themes, ig3_privacy, ig4_monolith, ig5_light, ig6_manifesto]

if __name__ == "__main__":
    for fn in ALL:
        fn()
        print("·", fn.__name__)
