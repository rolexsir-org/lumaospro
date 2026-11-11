"""A1 — Google Play feature graphic, 1024 × 500."""

from luma_studio import Frame, load_shot
from campaign import OUT, SHOTS, T1, T2, T3, INDIGO, VIOLET
import os


def build():
    W, H = 1024, 500
    f = Frame(W, H, "#07080D")
    # dark field, lit from the left, with the phone anchoring the right edge
    f.gradient([(0.0, "#0B0D1B"), (0.38, "#0E1024"), (0.72, "#0A0B15"), (1.0, "#06070C")],
               angle=96)
    f.glow(250, 180, 620, INDIGO, 0.46, 2.2)
    f.glow(700, 470, 520, VIOLET, 0.24, 2.4)
    f.vignette(0.34)
    f.grain(1.9, seed=3)

    shot = load_shot(SHOTS["home"])
    f.device(shot, 872, 338, 300, glow_color=INDIGO, glow_strength=0.30,
             shadow_opacity=200, shadow_blur=0.06, shadow_offset=(0.0, 0.02),
             body_pad=7)

    # left column
    f.brand(74, 60, size=40, light=True)
    f.kicker(76, 132, "Android launcher · 1.2.0", color=INDIGO, size=17, tracking=3.2,
             dot=INDIGO)
    f.lines(72, 174, ["A calmer", "home for", "Android."], size=74, weight=600,
            family="sg", color=T1, tracking=-2.0, leading=1.02)
    f.text(76, 424, "Built for phones that still have work to do.", size=23,
           weight=400, color=T2, tracking=-0.1)
    for i, item in enumerate(["NO ACCOUNT", "NO ADS", "NO ANALYTICS"]):
        x = 76 + i * 168
        f.text(x, 462, item, size=14, weight=500, family="sg", color=T3, tracking=2.4)
    f.save(os.path.join(OUT, "play", "01-feature-graphic-1024x500.png"))


if __name__ == "__main__":
    build()
