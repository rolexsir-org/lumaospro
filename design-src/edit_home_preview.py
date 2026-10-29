#!/usr/bin/env python3
"""
Render a pixel-accurate preview of the redesigned LumaOS Pro "Edit home" screen.

This is a design deliverable, not app code: every number below is read from the
same token set the app uses (app/src/main/java/.../home/EditModeDesign.kt and
res/values/dimens.xml), so the picture matches the layout the device will draw.

    python3 design-src/edit_home_preview.py

Output: design-src/edit-home-preview.png
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ---------------------------------------------------------------- canvas
DP = 6                      # px per dp, rendered 2x then downsampled (AA)
W_DP, H_DP = 393, 852       # logical phone size (Pixel-class)
W, H = W_DP * DP, H_DP * DP

# ---------------------------------------------------------------- tokens
SCREEN_MARGIN = 16          # EditModeDesign.screenMarginDp
SECTION_GAP = 12            # EditModeDesign.sectionGapDp
HEADER_TOP = 12             # EditModeDesign.headerTopMarginDp
HEADER_RADIUS = 24
HEADER_PADDING = 18
TITLE_SP, SUBTITLE_SP = 20, 13
DONE_W, DONE_H, DONE_SP = 76, 40, 14
HEADER_TO_CLOCK = 16        # EditModeDesign.headerToClockGapDp
CLOCK_SP, DATE_SP = 72, 14
GRID_GUTTER, SLOT_RADIUS = 12, 20
GRID_ROWS = 5               # LumaPrefs.DEFAULT_GRID_ROWS
HINT_TEXT_SP, HINT_PAD_V, HINT_PAD_H = 14, 14, 20
DOCK_RADIUS, DOCK_PADDING, DOCK_ICON = 28, 14, 52
TOOLBAR_RADIUS, TOOLBAR_PADDING = 24, 10
BUTTON_H, BUTTON_RADIUS, ICON = 64, 16, 22
LABEL_SP = 12
TOOLBAR_BOTTOM = 16
SAFE_TOP, SAFE_BOTTOM = 28, 24

# Colour system (dark glass).
GLASS = (15, 23, 32, 217)           # rgba(15,23,32,0.85)
BORDER = (255, 255, 255, 20)        # white 8%
WHITE = (255, 255, 255, 255)
TEXT = (255, 255, 255, 255)
TEXT_2 = (255, 255, 255, 166)       # 65%
TEXT_3 = (255, 255, 255, 217)       # 85%
SLOT_LINE = (255, 255, 255, 38)     # 15%
SLOT_FILL = (255, 255, 255, 8)      # 3%
ACCENT = (94, 234, 212)             # neon cyan, used only as a tiny accent

FONT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "font")


def font(name, sp):
    return ImageFont.truetype(os.path.join(FONT_DIR, name), int(round(sp * DP)))


F_LIGHT = font("inter_light.ttf", CLOCK_SP)
F_TITLE = font("inter_semibold.ttf", TITLE_SP)
F_SUB = font("inter_regular.ttf", SUBTITLE_SP)
F_DONE = font("inter_medium.ttf", DONE_SP)
F_DATE = font("inter_regular.ttf", DATE_SP)
F_HINT = font("inter_regular.ttf", HINT_TEXT_SP)
F_LABEL = font("inter_medium.ttf", LABEL_SP)


def px(dp):
    return int(round(dp * DP))


# ---------------------------------------------------------------- helpers
def rounded(draw, box, radius, fill=None, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def dashed_round_rect(draw, box, radius, colour, width, dash=5.0, gap=4.0):
    """Dash a rounded rectangle with a uniform rhythm along its real perimeter
    (mirrors Android's DashPathEffect on a rounded-rect outline)."""
    x0, y0, x1, y1 = box
    r = radius
    on, off = px(dash), px(gap)
    period = on + off

    pts = [(x0 + r, y0)]
    pts += [(x1 - r, y0)]
    for i in range(1, 25):
        a = math.radians(-90 + 90 * i / 24)
        pts.append((x1 - r + r * math.cos(a), y0 + r + r * math.sin(a)))
    pts.append((x1, y1 - r))
    for i in range(1, 25):
        a = math.radians(90 * i / 24)
        pts.append((x1 - r + r * math.cos(a), y1 - r + r * math.sin(a)))
    pts.append((x0 + r, y1))
    for i in range(1, 25):
        a = math.radians(90 + 90 * i / 24)
        pts.append((x0 + r + r * math.cos(a), y1 - r + r * math.sin(a)))
    pts.append((x0, y0 + r))
    for i in range(1, 25):
        a = math.radians(180 + 90 * i / 24)
        pts.append((x0 + r + r * math.cos(a), y0 + r + r * math.sin(a)))

    acc = 0.0
    prev = pts[0]
    for i in range(1, len(pts)):
        cur = pts[i]
        seg_len = math.hypot(cur[0] - prev[0], cur[1] - prev[1])
        if seg_len <= 0:
            continue
        steps = max(1, int(seg_len / (0.5 * DP)))
        for s_i in range(1, steps + 1):
            t = s_i / steps
            nxt = (prev[0] + (cur[0] - prev[0]) * t, prev[1] + (cur[1] - prev[1]) * t)
            if acc % period < on:
                draw.line([prev, nxt], fill=colour, width=width)
            acc += seg_len / steps
            prev = nxt
        prev = cur
    return


def shadow_layer(size, draw_fn, blur=18, alpha=120):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    # darken what the shape covers
    solid = Image.new("RGBA", size, (0, 0, 0, 0))
    return layer if alpha else solid


def card(size, box, radius, blur=16, strength=110):
    """Soft drop shadow behind a card."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    x0, y0, x1, y1 = box
    d.rounded_rectangle((x0, y0 + px(3), x1, y1 + px(6)), radius=radius,
                        fill=(0, 0, 0, strength))
    return layer.filter(ImageFilter.GaussianBlur(blur))


# ---------------------------------------------------------------- backdrop
def blend(img, layer):
    """Alpha-composite a layer onto the RGB canvas."""
    img.paste(layer, (0, 0), layer)


def backdrop():
    img = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(img)
    # midnight ramp
    for y in range(H):
        t = y / H
        r = int(9 + 8 * t)
        g = int(13 + 11 * t)
        b = int(20 + 16 * t)
        d.line([(0, y), (W, y)], fill=(r, g, b, 255))

    # the "neon symbols" wallpaper the reference used: too bright at full
    # strength, drawn here at ~30% and then veiled by the 55% edit scrim.
    symbols = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(symbols)
    glyph_alpha = 52
    colours = [(94, 234, 212), (129, 140, 248), (244, 114, 182)]
    cx, cy, rad = W * 0.5, H * 0.42, W * 0.30
    for ring in range(5):
        rr = rad * (1 - ring * 0.17)
        col = colours[ring % len(colours)] + (glyph_alpha,)
        for k in range(6):
            a = math.radians(k * 60 + ring * 12)
            gx, gy = cx + rr * math.cos(a), cy + rr * math.sin(a)
            size = W * 0.055
            if (ring + k) % 4 == 0:      # triangle
                pts = [(gx, gy - size), (gx - size * 0.87, gy + size * 0.5),
                       (gx + size * 0.87, gy + size * 0.5)]
                sd.polygon(pts, outline=col, width=px(1))
            elif (ring + k) % 4 == 1:    # circle
                sd.ellipse((gx - size, gy - size, gx + size, gy + size),
                           outline=col, width=px(1))
            elif (ring + k) % 4 == 2:    # cross
                sd.line((gx - size, gy - size, gx + size, gy + size), fill=col, width=px(1))
                sd.line((gx + size, gy - size, gx - size, gy + size), fill=col, width=px(1))
            else:                        # square
                sd.rounded_rectangle((gx - size * 0.8, gy - size * 0.8,
                                      gx + size * 0.8, gy + size * 0.8),
                                     radius=px(2), outline=col, width=px(1))
    blend(img, symbols)

    # EditModeDesign.scrimAlpha: 55% black over the wallpaper only.
    veil = Image.new("RGBA", (W, H), (0, 0, 0, int(255 * 0.55)))
    blend(img, veil)
    return img


# ---------------------------------------------------------------- sections
def draw_header(img, top):
    d = ImageDraw.Draw(img, "RGBA")
    left, right = px(SCREEN_MARGIN), W - px(SCREEN_MARGIN)
    title_w = d.textlength("Edit home", font=F_TITLE)
    sub = "Drag to arrange · Hold to group · Drag here to remove"

    # Wrap the subtitle inside the room the Done pill leaves. Phrases are
    # unbreakable (the resource uses non-breaking spaces), so the line can only
    # break at a "·" — and the text auto-shrinks 13sp→10sp to stay on two lines.
    inner_w = (right - left) - px(HEADER_PADDING) * 2 - px(DONE_W) - px(12)
    phrases = [p.strip() for p in sub.split("·")]
    sub_font = F_SUB
    while True:
        lines, line = [], ""
        for i, phrase in enumerate(phrases):
            chunk = phrase + (" ·" if i < len(phrases) - 1 else "")
            probe = (line + " " + chunk).strip()
            if d.textlength(probe, font=sub_font) <= inner_w:
                line = probe
            else:
                if line:
                    lines.append(line)
                line = chunk
        if line:
            lines.append(line)
        if len(lines) <= 2 or sub_font.size <= px(10):
            break
        sub_font = font("inter_regular.ttf", sub_font.size / DP - 0.5)

    title_h = px(TITLE_SP * 1.25)
    sub_line_h = px(18)
    height = px(HEADER_PADDING) * 2 + title_h + px(4) + sub_line_h * len(lines)
    box = (left, top, right, top + height)

    blend(img, card(img.size, box, px(HEADER_RADIUS)))
    d = ImageDraw.Draw(img, "RGBA")
    rounded(d, box, px(HEADER_RADIUS), fill=GLASS, outline=BORDER, width=max(1, px(0.4)))

    x = left + px(HEADER_PADDING)
    y = top + px(HEADER_PADDING)
    d.text((x, y), "Edit home", font=F_TITLE, fill=TEXT)
    y += title_h + px(4)
    for ln in lines:
        d.text((x, y), ln, font=sub_font, fill=TEXT_2)
        y += sub_line_h

    # Done pill, vertically centred
    done_box = (right - px(HEADER_PADDING) - px(DONE_W),
                top + (height - px(DONE_H)) // 2,
                right - px(HEADER_PADDING),
                top + (height - px(DONE_H)) // 2 + px(DONE_H))
    rounded(d, done_box, px(DONE_H / 2), fill=(15, 23, 32, 204),
            outline=(255, 255, 255, 31), width=max(1, px(0.4)))
    tw = d.textlength("Done", font=F_DONE)
    d.text((done_box[0] + (px(DONE_W) - tw) / 2,
            done_box[1] + (px(DONE_H) - px(DONE_SP) * 1.3) / 2 - px(1)),
           "Done", font=F_DONE, fill=WHITE)
    return top + height


def draw_clock(img, top):
    d = ImageDraw.Draw(img, "RGBA")
    clock = "9:41"
    tw = d.textlength(clock, font=F_LIGHT)
    d.text(((W - tw) / 2, top - px(6)), clock, font=F_LIGHT, fill=(255, 255, 255, 240))
    clock_h = px(CLOCK_SP * 1.16)
    date = "Thursday, October 29"
    dw = d.textlength(date, font=F_DATE)
    d.text(((W - dw) / 2, top + clock_h + px(2)), date, font=F_DATE, fill=TEXT_3)
    return top + clock_h + px(2) + px(DATE_SP * 1.4)


def draw_grid(img, top, bottom, columns=4):
    """Dashed drop zones, 12dp gutters, 20dp corners, 3% fill."""
    d = ImageDraw.Draw(img, "RGBA")
    left, right = px(SCREEN_MARGIN), W - px(SCREEN_MARGIN)
    cell_w = (right - left) / columns
    # The device divides the grid's own height by the configured row count, so
    # the last row always lands exactly on the bottom edge — no ragged tail.
    # The app gives the configured rows the grid's whole height, so the last row
    # always lands on the bottom edge (homeCellHeightPx).
    rows = GRID_ROWS
    cell_h = (bottom - top) / rows
    half = px(GRID_GUTTER) / 2
    for r in range(rows):
        for c in range(columns):
            x0 = left + c * cell_w + half
            y0 = top + r * cell_h + half
            x1 = left + (c + 1) * cell_w - half
            y1 = top + (r + 1) * cell_h - half
            if y1 > bottom + 1:
                continue
            rounded(d, (x0, y0, x1, y1), px(SLOT_RADIUS), fill=SLOT_FILL)
            dashed_round_rect(d, (x0, y0, x1, y1), px(SLOT_RADIUS), SLOT_LINE,
                              width=max(1, px(0.5)))
    return cell_h


def draw_hint(img, center_y):
    d = ImageDraw.Draw(img, "RGBA")
    text = "Swipe up to explore your apps"
    tw = d.textlength(text, font=F_HINT)
    th = px(HINT_TEXT_SP * 1.4)
    w = tw + px(HINT_PAD_H) * 2
    h = th + px(HINT_PAD_V) * 2
    box = ((W - w) / 2, center_y - h / 2, (W + w) / 2, center_y + h / 2)
    blend(img, card(img.size, box, h / 2, blur=px(5), strength=90))
    d = ImageDraw.Draw(img, "RGBA")
    rounded(d, box, h / 2, fill=(15, 23, 32, 224),
            outline=(255, 255, 255, 26), width=max(1, px(0.4)))
    d.text((box[0] + px(HINT_PAD_H), box[1] + px(HINT_PAD_V) - px(1)), text,
           font=F_HINT, fill=TEXT_3)


def draw_dock(img, top, icons=5):
    d = ImageDraw.Draw(img, "RGBA")
    left, right = px(SCREEN_MARGIN), W - px(SCREEN_MARGIN)
    height = px(DOCK_ICON + DOCK_PADDING * 2)
    box = (left, top, right, top + height)
    blend(img, card(img.size, box, px(DOCK_RADIUS)))
    d = ImageDraw.Draw(img, "RGBA")
    rounded(d, box, px(DOCK_RADIUS), fill=GLASS, outline=BORDER, width=max(1, px(0.4)))

    inner_l = box[0] + px(DOCK_PADDING)
    inner_r = box[2] - px(DOCK_PADDING)
    icon = px(DOCK_ICON)
    step = (inner_r - inner_l - icon) / (icons - 1)
    palettes = [
        ((129, 140, 248), (79, 70, 229), "calendar"),
        ((94, 234, 212), (13, 148, 136), "mail"),
        ((251, 191, 36), (217, 119, 6), "chat"),
        ((244, 114, 182), (190, 24, 93), "music"),
        ((148, 163, 184), (71, 85, 105), "cam"),
    ]
    for i in range(icons):
        x = inner_l + step * i
        y = top + (height - icon) / 2
        tile, tx, ty = draw_app_icon((x, y, x + icon, y + icon), *palettes[i])
        blend(img, tile, ) if False else img.paste(tile, (tx, ty), tile)
    return top + height


def draw_app_icon(box, top_col, bottom_col, kind):
    x0, y0, x1, y1 = [int(v) for v in box]
    tile = Image.new("RGBA", (x1 - x0, y1 - y0), (0, 0, 0, 0))
    td = ImageDraw.Draw(tile)
    h = y1 - y0
    for i in range(h):
        t = i / max(1, h - 1)
        col = tuple(int(top_col[k] + (bottom_col[k] - top_col[k]) * t) for k in range(3)) + (255,)
        td.line([(0, i), (x1 - x0, i)], fill=col)
    mask = Image.new("L", (x1 - x0, y1 - y0), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, x1 - x0 - 1, y1 - y0 - 1),
                                           radius=int((x1 - x0) * 0.30), fill=255)
    glyph = Image.new("RGBA", tile.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glyph)
    cw, ch = tile.size
    c = (255, 255, 255, 235)
    if kind == "calendar":
        gd.rounded_rectangle((cw * .28, ch * .30, cw * .72, ch * .72), radius=int(cw * .06),
                             outline=c, width=max(1, px(1.2)))
        gd.line((cw * .28, ch * .44, cw * .72, ch * .44), fill=c, width=max(1, px(1.2)))
    elif kind == "mail":
        gd.rounded_rectangle((cw * .26, ch * .34, cw * .74, ch * .66), radius=int(cw * .06),
                             outline=c, width=max(1, px(1.2)))
        gd.line((cw * .28, ch * .37, cw * .5, ch * .55, cw * .72, ch * .37), fill=c,
                width=max(1, px(1.2)))
    elif kind == "chat":
        gd.rounded_rectangle((cw * .27, ch * .30, cw * .73, ch * .64), radius=int(cw * .12),
                             outline=c, width=max(1, px(1.2)))
        gd.polygon([(cw * .40, ch * .64), (cw * .40, ch * .74), (cw * .52, ch * .64)], fill=c)
    elif kind == "music":
        gd.line((cw * .44, ch * .36, cw * .44, ch * .64), fill=c, width=max(1, px(1.4)))
        gd.line((cw * .44, ch * .36, cw * .62, ch * .32), fill=c, width=max(1, px(1.4)))
        gd.ellipse((cw * .35, ch * .60, cw * .47, ch * .70), fill=c)
        gd.ellipse((cw * .53, ch * .56, cw * .65, ch * .66), fill=c)
    else:
        gd.rounded_rectangle((cw * .27, ch * .36, cw * .73, ch * .66), radius=int(cw * .08),
                             outline=c, width=max(1, px(1.2)))
        gd.ellipse((cw * .42, ch * .44, cw * .58, ch * .60), outline=c, width=max(1, px(1.2)))
    tile.putalpha(mask)
    tile = Image.alpha_composite(tile, glyph)
    return tile, x0, y0


def draw_toolbar(img, top):
    d = ImageDraw.Draw(img, "RGBA")
    left, right = px(SCREEN_MARGIN), W - px(SCREEN_MARGIN)
    height = px(BUTTON_H + TOOLBAR_PADDING * 2)
    box = (left, top, right, top + height)
    blend(img, card(img.size, box, px(TOOLBAR_RADIUS)))
    d = ImageDraw.Draw(img, "RGBA")
    rounded(d, box, px(TOOLBAR_RADIUS), fill=GLASS, outline=BORDER, width=max(1, px(0.4)))

    actions = ["Widgets", "Layout", "Studio", "Wallpaper", "Settings"]
    inner_l = box[0] + px(TOOLBAR_PADDING)
    inner_w = (box[2] - px(TOOLBAR_PADDING)) - inner_l
    button_w = inner_w / len(actions)

    for i, name in enumerate(actions):
        bx0 = inner_l + i * button_w
        by0 = top + px(TOOLBAR_PADDING)
        # one quiet 8% fill on the second button, to show the state
        if i == 1:
            rounded(d, (bx0 + px(2), by0, bx0 + button_w - px(2), by0 + px(BUTTON_H)),
                    px(BUTTON_RADIUS), fill=(255, 255, 255, 20))
        icon_cx = bx0 + button_w / 2
        icon_top = by0 + px(11)
        draw_toolbar_icon(d, icon_cx, icon_top + px(ICON) / 2, i)
        label = name
        f = F_LABEL
        # auto-shrink, exactly like the TextView: never wrap, never truncate
        while d.textlength(label, font=f) > button_w - px(8) and f.size > px(9):
            f = font("inter_medium.ttf", f.size / DP - 0.5)
        lw = d.textlength(label, font=f)
        d.text((icon_cx - lw / 2, icon_top + px(ICON) + px(4)), label, font=f,
               fill=(255, 255, 255, 245))
    return top + height


def draw_toolbar_icon(d, cx, cy, kind):
    """One outlined icon set: same stroke, same 22dp box."""
    s = px(ICON) / 2
    w = max(1, px(1.4))
    c = (255, 255, 255, 235)
    x0, y0, x1, y1 = cx - s, cy - s, cx + s, cy + s
    if kind == 0:      # widgets: 4 tiles
        g = s * 0.22
        for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            d.rounded_rectangle((cx + dx * s * 0.48 - g, cy + dy * s * 0.48 - g,
                                 cx + dx * s * 0.48 + g, cy + dy * s * 0.48 + g),
                                radius=int(px(1.6)), outline=c, width=w)
    elif kind == 1:    # layout: a grid
        d.rounded_rectangle((x0, y0, x1, y1), radius=int(px(3)), outline=c, width=w)
        d.line((cx, y0, cx, y1), fill=c, width=w)
        d.line((x0, cy, x1, cy), fill=c, width=w)
    elif kind == 2:    # studio: a sparkle
        d.line((cx, y0, cx, y1), fill=c, width=w)
        d.line((x0, cy, x1, cy), fill=c, width=w)
        d.line((cx - s * .55, cy - s * .55, cx + s * .55, cy + s * .55), fill=c, width=w)
        d.line((cx + s * .55, cy - s * .55, cx - s * .55, cy + s * .55), fill=c, width=w)
    elif kind == 3:    # wallpaper: landscape
        d.rounded_rectangle((x0, y0 + s * .2, x1, y1 - s * .2), radius=int(px(3)),
                            outline=c, width=w)
        d.ellipse((x0 + s * .18, y0 + s * .38, x0 + s * .46, y0 + s * .66), outline=c, width=w)
        d.line((x0 + s * .1, y1 - s * .28, cx, cy - s * .05), fill=c, width=w)
    else:              # settings: a gear
        d.ellipse((cx - s * .42, cy - s * .42, cx + s * .42, cy + s * .42), outline=c, width=w)
        d.ellipse((cx - s * .16, cy - s * .16, cx + s * .16, cy + s * .16), outline=c, width=w)
        for k in range(8):
            a = math.radians(k * 45)
            d.line((cx + s * .52 * math.cos(a), cy + s * .52 * math.sin(a),
                    cx + s * .74 * math.cos(a), cy + s * .74 * math.sin(a)), fill=c, width=w)


def draw_status_bar(img):
    d = ImageDraw.Draw(img, "RGBA")
    f = font("inter_medium.ttf", 13)
    d.text((px(SCREEN_MARGIN + 4), px(9)), "9:41", font=f, fill=(255, 255, 255, 230))
    # signal / wifi / battery, as quiet dots and bars
    bx = W - px(SCREEN_MARGIN + 4)
    d.rounded_rectangle((bx - px(20), px(11), bx, px(19)), radius=px(3),
                        outline=(255, 255, 255, 140), width=max(1, px(1)))
    d.rounded_rectangle((bx - px(18), px(13), bx - px(7), px(17)), radius=px(1),
                        fill=(255, 255, 255, 200))
    for i in range(3):
        d.line((bx - px(34) + i * px(3), px(18), bx - px(34) + i * px(3), px(18 - i * 3)),
               fill=(255, 255, 255, 200), width=px(1.6))
    d.arc((bx - px(52), px(11), bx - px(42), px(21)), 200, 340, fill=(255, 255, 255, 200),
          width=px(1.4))


def draw_gesture_bar(img):
    d = ImageDraw.Draw(img, "RGBA")
    w, h = px(108), px(4)
    y = H - px(SAFE_BOTTOM - 8)
    d.rounded_rectangle(((W - w) / 2, y, (W + w) / 2, y + h), radius=h / 2,
                        fill=(255, 255, 255, 110))


# ---------------------------------------------------------------- compose
def render():
    img = backdrop()
    draw_status_bar(img)

    header_bottom = draw_header(img, px(SAFE_TOP + HEADER_TOP))
    clock_bottom = draw_clock(img, header_bottom + px(HEADER_TO_CLOCK))

    toolbar_top = H - px(SAFE_BOTTOM + TOOLBAR_BOTTOM + BUTTON_H + TOOLBAR_PADDING * 2)
    dock_bottom = toolbar_top - px(SECTION_GAP)
    dock_top = dock_bottom - px(DOCK_ICON + DOCK_PADDING * 2)

    # In edit mode the search pill is faded out but still occupies its slot, so
    # the grid (and the drop zones) end above it — exactly as on the device.
    # EditModeController hides the (empty) search pill with GONE, so the grid —
    # and its drop zones — run all the way down to the dock gap.
    grid_top = clock_bottom + px(8)
    grid_bottom = dock_top - px(SECTION_GAP)
    draw_grid(img, grid_top, grid_bottom)
    draw_hint(img, (grid_top + grid_bottom) / 2)

    draw_dock(img, dock_top)
    draw_toolbar(img, toolbar_top)
    draw_gesture_bar(img)

    # Downsample 2x for clean antialiasing of the dashed outlines.
    img = img.resize((W // 2, H // 2), Image.LANCZOS)
    out = os.path.join(os.path.dirname(__file__), "edit-home-preview.png")
    img.convert("RGB").save(out, "PNG", optimize=True)
    print("wrote", out, img.size)


if __name__ == "__main__":
    render()
