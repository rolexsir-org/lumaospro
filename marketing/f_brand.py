"""F — icon set, clear-space sheet and the campaign contact sheet."""

import os

from PIL import Image

from luma_studio import Frame, mark_image, load_shot, draw_mark
from campaign import (OUT, SHOTS, T1, T2, T3, INDIGO, MINT, VERMILION, CHAMPAGNE,
                      brand_backdrop, dot_grid, frame_rules)

BRAND = os.path.join(OUT, "brand")
os.makedirs(BRAND, exist_ok=True)


def play_icon():
    """512 × 512 listing icon: the app's own mark on the app's own icon plate."""
    from PIL import ImageDraw
    im = Image.new("RGBA", (2048, 2048), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, 2047, 2047], radius=2048 * 0.235, fill="#171B18")
    # subtle top light on the plate, clipped to the plate itself
    plate = Image.new("L", (2048, 2048), 0)
    ImageDraw.Draw(plate).rounded_rectangle([0, 0, 2047, 2047], radius=2048 * 0.235,
                                            fill=255)
    sheen = Image.new("RGBA", (2048, 2048), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sheen)
    for i in range(760):
        a = int(18 * (1 - i / 760) ** 1.6)
        sd.line([(0, i), (2048, i)], fill=(255, 255, 255, a))
    im = Image.alpha_composite(im, Image.composite(sheen, Image.new("RGBA", (2048, 2048), (0, 0, 0, 0)), plate))
    d = ImageDraw.Draw(im)
    draw_mark(d, 1024, 1024, 2048 * 0.66, arc="#B5C5A9", core="#E8EBE4", dot="#B5C5A9")
    im.resize((512, 512), Image.LANCZOS).save(os.path.join(BRAND, "play-icon-512.png"))
    # 1024 master for press kits / adaptive foreground use
    im.resize((1024, 1024), Image.LANCZOS).save(os.path.join(BRAND, "app-icon-1024.png"))


def icon_sheet():
    W, H = 2400, 1500
    f = Frame(W, H, "#06070B")
    brand_backdrop(f, hue="indigo", vignette=0.4, grain=1.8, seed=151)
    f.brand(100, 84, size=40, light=True)
    f.kicker(104, 186, "Brand system", color=INDIGO, size=16, tracking=3.8, dot=INDIGO)
    f.lines(100, 222, ["The mark, at every scale."], size=76, weight=600, family="sg",
            color=T1, tracking=-2.6)
    f.para(104, 336, "Drawn from the app's own adaptive-icon vector: a 30/108 orbit with a\n"
                     "272° opening, a four-point sparkle and a 3.5/108 centre dot.",
           size=23, color=T2, leading=1.5, max_w=900)

    # primary icon, large
    icon = mark_image(1024, bg="#171B18")
    big = icon.resize((300, 300), Image.LANCZOS)
    f.c.paste(big, int(108 * f.ss), int(436 * f.ss))

    # tonal surface studies
    variants = [("#171B18", "#B5C5A9", "#E8EBE4", "DEFAULT PLATE"),
                ("#0A0C12", "#B5C5A9", "#E8EBE4", "DARK UI"),
                ("#F6F3EC", "#5A6B55", "#14141A", "ON IVORY")]
    f.text(108, 762, "512 PX MASTER · 128 PX DRAWER", size=13, weight=600, family="sg",
           color=T3, tracking=2.6)
    f.text(920, 436, "SURFACE VARIANTS", size=13, weight=600, family="sg", color=T3,
           tracking=2.6)
    for i, (bg, arc, core, label) in enumerate(variants):
        x = 920 + i * 240
        im = mark_image(600, bg=bg, arc=arc, core=core, dot=arc)
        im = im.resize((180, 180), Image.LANCZOS)
        f.c.paste(im, int(x * f.ss), int(482 * f.ss))
        f.text(x + 90, 682, "600 PX", size=13, weight=600, family="sg", color=T3,
               tracking=2.6, anchor="ma")
        f.text(x + 90, 708, label, size=11, weight=500, family="sg", color="#5D626E",
               tracking=2.2, anchor="ma")

    # clear space + scale ladder
    f.text(108, 830, "CLEAR SPACE", size=13, weight=600, family="sg", color=T3,
           tracking=2.6)
    f.rect([108, 866, 428, 1186], radius=0, stroke="rgba(124,134,255,0.55)", width=1)
    f.rect([138, 896, 398, 1156], radius=0, stroke="rgba(255,255,255,0.16)", width=1)
    im = mark_image(520, bg="#171B18").resize((260, 260))
    f.c.paste(im, int(138 * f.ss), int(896 * f.ss))
    f.text(108, 1206, "x = ¼ icon width of clear space on all sides", size=15,
           color=T3)

    f.text(560, 830, "SCALE LADDER", size=13, weight=600, family="sg", color=T3,
           tracking=2.6)
    for i, s in enumerate([180, 120, 84, 56, 36]):
        im = mark_image(400, bg="#171B18").resize((s, s))
        f.c.paste(im, int((560 + i * 150) * f.ss), int(866 * f.ss))
        f.text(560 + i * 150, 866 + 196, f"{s} px", size=12, weight=600, family="sg",
               color="#5D626E", tracking=1.8)
    f.text(560, 1120, "Legible to 36 px. Below that, use the wordmark alone.", size=15,
           color=T3)

    # monochrome lockup
    f.text(1520, 830, "LOCKUPS", size=13, weight=600, family="sg", color=T3,
           tracking=2.6)
    f.brand(1520, 872, size=64, light=True)
    f.brand(1520, 968, size=44, light=True)
    f.brand(1520, 1044, size=32, light=False, color="#14141A")
    f.text(1520, 1120, "Wordmark: Space Grotesk 600, +0.4 tracking", size=15, color=T3)

    # colour ledger
    f.text(108, 1290, "PALETTE", size=13, weight=600, family="sg", color=T3,
           tracking=2.6)
    swatches = [("BONE", "#E8EBE4"), ("SAGE", "#B5C5A9"), ("PLATE", "#171B18"),
                ("INDIGO", "#7C86FF"), ("VIOLET", "#A78BFA"), ("MINT", "#46E0B0"),
                ("VERMILION", "#FF5A2B"), ("CHAMPAGNE", "#D9BC8C")]
    for i, (name, col) in enumerate(swatches):
        x = 108 + i * 174
        f.rect([x, 1330, x + 150, 1390], radius=8, fill=col)
        f.text(x, 1404, name, size=12, weight=600, family="sg", color=T3, tracking=1.8)
        f.text(x, 1424, col, size=11, weight=500, family="sg", color="#4E5462",
               tracking=1.0)
    f.save(os.path.join(BRAND, "icon-system-2400x1500.png"))


def contact_sheet():
    """One page that shows the whole campaign, in reading order."""
    groups = [
        ("GOOGLE PLAY · 1024 × 500 / 1080 × 1920",
         ["play/01-feature-graphic-1024x500.png"] +
         [f"play/0{i}-{n}-1080x1920.png" for i, n in
          enumerate(["home", "drawer", "themes", "studio", "private", "lightweight",
                     "contexts"], start=2)]),
        ("INSTAGRAM · 1080 × 1350",
         ["instagram/01-launch-announcement-1080x1350.png",
          "instagram/02-twelve-identities-1080x1350.png",
          "instagram/03-privacy-1080x1350.png",
          "instagram/04-monolith-feature-1080x1350.png",
          "instagram/05-lightweight-1080x1350.png",
          "instagram/06-manifesto-1080x1350.png"]),
        ("STORIES & REELS · 1080 × 1920",
         ["stories/01-launch-story-1080x1920.png",
          "stories/02-themes-story-1080x1920.png",
          "stories/03-privacy-story-1080x1920.png",
          "stories/04-studio-story-1080x1920.png",
          "stories/05-reel-cover-1080x1920.png"]),
        ("POSTERS · 1200 × 1800",
         ["posters/01-calm-home-1200x1800.png",
          "posters/02-twelve-identities-light-1200x1800.png",
          "posters/03-privacy-ledger-1200x1800.png"]),
        ("WEB & ANNOUNCEMENT",
         ["site/hero-banner-2400x1000.png", "site/hero-16x9-1600x900.png",
          "site/showcase-ensemble-2400x1600.png", "site/announcement-1200x630.png",
          "site/launch-square-1080x1080.png"]),
        ("BRAND",
         ["brand/icon-system-2400x1500.png", "brand/app-icon-1024.png",
          "brand/play-icon-512.png"]),
    ]

    W = 2400
    MARGIN, GAP, LABEL_H, MAX_H = 90, 26, 62, 430
    inner = W - MARGIN * 2

    # every group is scaled to fill exactly one row
    packed = []
    for label, files in groups:
        imgs = [Image.open(os.path.join(OUT, r)).convert("RGBA") for r in files]
        aspects = [im.width / im.height for im in imgs]
        scale = (inner - GAP * (len(imgs) - 1)) / sum(aspects)
        h = min(MAX_H, int(1000 * scale / 1000 * 1000 * scale / (1000 * scale) * scale * (1000 / 1000)))
        h = min(MAX_H, int(scale))
        row = [(im.resize((max(1, int(a * h)), h), Image.LANCZOS), int(a * h))
               for im, a in zip(imgs, aspects)]
        packed.append((label, row, h))

    body = sum(LABEL_H + h + 56 for _, _, h in packed)
    H = 320 + body + 80

    f = Frame(W, H, "#06070B")
    brand_backdrop(f, hue="indigo", vignette=0.32, grain=1.6, seed=161)
    f.brand(90, 74, size=40, light=True)
    f.text(W - 90, 84, "PROMOTIONAL CAMPAIGN · V1.2.0", size=14, weight=600,
           family="sg", color=T3, tracking=3.2, anchor="ra")
    f.lines(86, 154, ["LumaOS Pro — campaign contact sheet"], size=72, weight=600,
            family="sg", color=T1, tracking=-2.6)
    f.text(90, 250, "Every plate is generated from the real app screenshots and the product's own design tokens.",
           size=21, color=T2)

    y = 322
    for label, row, h in packed:
        f.text(MARGIN, y + 4, label, size=13, weight=600, family="sg", color=INDIGO,
               tracking=3.0)
        f.text(W - MARGIN, y + 4, f"{len(row)} PLATES", size=12, weight=600,
               family="sg", color="#4E5462", tracking=2.6, anchor="ra")
        f.line((MARGIN, y + 28), (W - MARGIN, y + 28), color="#FFFFFF", width=1,
               alpha=18)
        y += LABEL_H
        x = MARGIN
        for im, w in row:
            f.c.paste(im, int(x * f.ss), int(y * f.ss))
            x += w + GAP
        y += h + 56
    f.save(os.path.join(OUT, "00-campaign-contact-sheet.png"))


if __name__ == "__main__":
    play_icon(); print("· play_icon")
    icon_sheet(); print("· icon_sheet")
    contact_sheet(); print("· contact_sheet")
