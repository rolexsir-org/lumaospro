"""
Luma Studio — the generative design system behind the LumaOS Pro campaign.

Everything in /marketing is drawn from this module: real brand tokens
(docs/design/v3.2/tokens), the app's own icon geometry, the app's own fonts
(Inter + Space Grotesk, SIL OFL 1.1) and unmodified device screenshots.

No PIL-only approximation of the brand: colours, type and mark come from the
product's own design-system bundle so the campaign cannot drift from the app.
"""

from __future__ import annotations

import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# --------------------------------------------------------------------------
# Brand tokens — extracted from docs/design/v3.2/tokens/*.json
# --------------------------------------------------------------------------

TOKENS = {
    "signature": {
        "bg0": "#06070B", "bg1": "#0A0C12", "bg2": "#0F1219",
        "t1": "#F2F4F9", "t2": "#A8AEBC", "t3": "#717886",
        "accent": "#7C86FF", "accent2": "#A78BFA", "solid": "#14171F",
    },
    "atmosphere": {
        "bg0": "#04131A", "bg1": "#061A22", "bg2": "#0A2430",
        "t1": "#EAF7F6", "t2": "#A3C0C6", "t3": "#6E8C94",
        "accent": "#46E0B0", "accent2": "#7FB6FF", "solid": "#0B2029",
    },
    "monolith": {
        "bg0": "#0B0B0A", "bg1": "#111110", "bg2": "#171715",
        "t1": "#F4F2EC", "t2": "#A5A29A", "t3": "#726F68",
        "accent": "#FF5A2B", "accent2": "#FF5A2B", "solid": "#1A1A17",
    },
    # premium collection / 2026-11 identities (docs/DESIGN_SYSTEM.md)
    "obsidian": {
        "bg0": "#000000", "bg1": "#050505", "bg2": "#0A0A0A",
        "t1": "#F4F1EA", "t2": "#A8A39A", "t3": "#6F6A62",
        "accent": "#D9BC8C", "accent2": "#D9BC8C", "solid": "#101010",
    },
    "ivory": {
        "bg0": "#F6F3EC", "bg1": "#EFEAE0", "bg2": "#E7E1D4",
        "t1": "#1B1A17", "t2": "#5D5A52", "t3": "#8B867C",
        "accent": "#2E5AAC", "accent2": "#B08A54", "solid": "#FBF9F4",
    },
    "aurora": {
        "bg0": "#06070B", "bg1": "#0B0D16", "bg2": "#12142A",
        "t1": "#F2F4F9", "t2": "#A8AEBC", "t3": "#717886",
        "accent": "#7C86FF", "accent2": "#4FD1A5", "solid": "#14171F",
    },
}

BONE = "#E8EBE4"      # product mark light
SAGE = "#B5C5A9"      # product mark arc
PAPER = "#F6F3EC"

FONT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".build", "fonts")

_FONT_FILES = {
    ("inter", 300): "Inter-300.ttf",
    ("inter", 400): "Inter-400.ttf",
    ("inter", 500): "Inter-500.ttf",
    ("inter", 600): "Inter-600.ttf",
    ("inter", 700): "Inter-700.ttf",
    ("inter", 800): "Inter-800.ttf",
    ("inter", 900): "Inter-900.ttf",
    ("sg", 300): "SpaceGrotesk-300.ttf",
    ("sg", 400): "SpaceGrotesk-400.ttf",
    ("sg", 500): "SpaceGrotesk-500.ttf",
    ("sg", 600): "SpaceGrotesk-600.ttf",
    ("sg", 700): "SpaceGrotesk-700.ttf",
}

_font_cache: dict[tuple[str, int, int], ImageFont.FreeTypeFont] = {}


def font(size: int, weight: int = 400, family: str = "inter") -> ImageFont.FreeTypeFont:
    key = (family, weight, size)
    if key not in _font_cache:
        _font_cache[key] = ImageFont.truetype(
            os.path.join(FONT_DIR, _FONT_FILES[(family, weight)]), size
        )
    return _font_cache[key]


# --------------------------------------------------------------------------
# Colour helpers
# --------------------------------------------------------------------------

def rgb(h: str, a: int | None = None) -> tuple[int, ...]:
    if isinstance(h, tuple):
        return h if a is None else tuple(h[:3]) + (a,)
    if h.startswith("rgba"):
        parts = h[h.index("(") + 1:h.index(")")].split(",")
        c = tuple(int(float(p)) for p in parts[:3])
        al = float(parts[3]) if len(parts) > 3 else 1.0
        return c + (int(al * 255),)
    h = h.lstrip("#")
    t = tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    return t + (a,) if a is not None else t


def mix(a: str, b: str, t: float) -> tuple[int, int, int]:
    ca, cb = rgb(a), rgb(b)
    return tuple(int(round(ca[i] + (cb[i] - ca[i]) * t)) for i in range(3))


def lighten(h: str, t: float) -> tuple[int, int, int]:
    return mix(h, "#FFFFFF", t)


# --------------------------------------------------------------------------
# Canvas
# --------------------------------------------------------------------------

class Canvas:
    def __init__(self, w: int, h: int, base: str = "#06070B"):
        self.w, self.h = w, h
        self.base = base
        self.rgb = np.zeros((h, w, 3), dtype=np.float32)
        self.rgb[:] = rgb(base)

    # -- vector-ish background layers -------------------------------------

    def linear(self, stops, angle: float = 90.0, box=None):
        """Multi-stop linear gradient. stops = [(pos0..1, hex), ...]."""
        x0, y0, x1, y1 = box or (0, 0, self.w, self.h)
        bw, bh = x1 - x0, y1 - y0
        yy, xx = np.mgrid[0:bh, 0:bw].astype(np.float32)
        a = math.radians(angle)
        proj = (xx / max(bw - 1, 1)) * math.cos(a) + (yy / max(bh - 1, 1)) * math.sin(a)
        proj = (proj - proj.min()) / max(proj.max() - proj.min(), 1e-6)
        out = np.zeros((bh, bw, 3), dtype=np.float32)
        for i in range(len(stops) - 1):
            p0, c0 = stops[i]
            p1, c1 = stops[i + 1]
            m = (proj >= p0) & (proj <= p1)
            if not m.any():
                continue
            t = (proj[m] - p0) / max(p1 - p0, 1e-6)
            for k in range(3):
                out[..., k][m] = rgb(c0)[k] + (rgb(c1)[k] - rgb(c0)[k]) * t
        out[proj < stops[0][0]] = rgb(stops[0][1])
        out[proj > stops[-1][0]] = rgb(stops[-1][1])
        self.paste_np(out, x0, y0)
        return self

    def glow(self, cx, cy, r, color, strength=1.0, falloff=2.2, aspect=1.0):
        """Soft radial light bloom added to the canvas."""
        x0, x1 = max(0, int(cx - r)), min(self.w, int(cx + r))
        y0, y1 = max(0, int(cy - r)), min(self.h, int(cy + r))
        if x1 <= x0 or y1 <= y0:
            return self
        yy, xx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
        d = np.sqrt(((xx - cx) / r) ** 2 + ((yy - cy) / (r * aspect)) ** 2)
        f = np.clip(1.0 - d, 0, 1) ** falloff
        c = np.array(rgb(color), dtype=np.float32)
        self.rgb[y0:y1, x0:x1] += f[..., None] * c * strength
        return self

    def bloom(self, color, cx, cy, r, strength=0.5):
        return self.glow(cx, cy, r, color, strength, falloff=1.9)

    def vignette(self, amount=0.55, power=1.6):
        yy, xx = np.mgrid[0:self.h, 0:self.w].astype(np.float32)
        dx = (xx - self.w / 2) / (self.w / 2)
        dy = (yy - self.h / 2) / (self.h / 2)
        d = np.sqrt(dx ** 2 + dy ** 2) / math.sqrt(2)
        f = 1.0 - amount * np.clip(d, 0, 1) ** power
        self.rgb *= f[..., None]
        return self

    def grain(self, amount=3.0, seed=7):
        rng = np.random.default_rng(seed)
        n = rng.normal(0, 1, (self.h // 2, self.w // 2))
        n = np.array(Image.fromarray(((n * 0.5 + 0.5) * 255).astype(np.uint8)).resize(
            (self.w, self.h), Image.BICUBIC), dtype=np.float32)
        n = (n / 255.0 - 0.5) * 2.0
        self.rgb += n[..., None] * amount
        return self

    # -- compositing -------------------------------------------------------

    def paste_np(self, arr: np.ndarray, x: int = 0, y: int = 0, alpha=None):
        ah, aw = arr.shape[:2]
        x0, y0 = max(0, x), max(0, y)
        x1, y1 = min(self.w, x + aw), min(self.h, y + ah)
        if x1 <= x0 or y1 <= y0:
            return self
        sx0, sy0 = x0 - x, y0 - y
        sub = arr[sy0:sy0 + (y1 - y0), sx0:sx0 + (x1 - x0)]
        if alpha is None:
            self.rgb[y0:y1, x0:x1] = sub[..., :3]
        else:
            a = alpha[sy0:sy0 + (y1 - y0), sx0:sx0 + (x1 - x0)][..., None]
            self.rgb[y0:y1, x0:x1] = self.rgb[y0:y1, x0:x1] * (1 - a) + sub[..., :3] * a
        return self

    def paste(self, img: Image.Image, x: int = 0, y: int = 0):
        """Composite an RGBA PIL image (alpha-aware) at x,y."""
        if img.mode != "RGBA":
            img = img.convert("RGBA")
        arr = np.array(img, dtype=np.float32)
        self.paste_np(arr, x, y, alpha=arr[..., 3] / 255.0)
        return self

    def paste_shadow(self, shadow: Image.Image, x: int = 0, y: int = 0):
        arr = np.array(shadow.convert("L"), dtype=np.float32) / 255.0
        col = np.zeros((*arr.shape, 3), dtype=np.float32)
        self.paste_np(col, x, y, alpha=arr)
        return self

    def draw(self) -> ImageDraw.ImageDraw:
        """PIL draw handle bound to an RGB copy; call commit() afterwards."""
        self._im = Image.fromarray(np.clip(self.rgb, 0, 255).astype(np.uint8), "RGB")
        self._d = ImageDraw.Draw(self._im)
        return self._d

    def commit(self):
        self.rgb = np.array(self._im, dtype=np.float32)
        return self

    def image(self) -> Image.Image:
        return Image.fromarray(np.clip(self.rgb, 0, 255).astype(np.uint8), "RGB")

    def finish(self, grain=2.4, vignette=0.0, seed=11):
        if vignette:
            self.vignette(vignette)
        if grain:
            self.grain(grain, seed)
        return self.image()


# --------------------------------------------------------------------------
# Type — tracked text, word-wrapped display lines
# --------------------------------------------------------------------------

def text_width(d: ImageDraw.ImageDraw, s: str, f, tracking: float = 0.0) -> float:
    if tracking == 0:
        return d.textlength(s, font=f)
    return sum(d.textlength(ch, font=f) for ch in s) + tracking * (len(s) - 1)


def tracked(d, xy, s, f, fill, tracking=0.0, anchor="la", line_gap=0.0):
    """Draw text with letter-spacing (em-independent, in px)."""
    x, y = xy
    w = text_width(d, s, f, tracking)
    if anchor[0] == "m":
        x -= w / 2
    elif anchor[0] == "r":
        x -= w
    if anchor[1] == "m":
        y -= f.size * 0.62
    elif anchor[1] == "b":
        y -= f.size * 0.84
    if tracking == 0:
        d.text((x, y), s, font=f, fill=fill)
        return w
    for ch in s:
        d.text((x, y), ch, font=f, fill=fill)
        x += d.textlength(ch, font=f) + tracking
    return w


def wrap(d, s, f, max_w, tracking=0.0) -> list[str]:
    words, lines, cur = s.split(), [], ""
    for word in words:
        trial = (cur + " " + word).strip()
        if text_width(d, trial, f, tracking) <= max_w or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = word
    if cur:
        lines.append(cur)
    return lines


def display_block(d, xy, lines, f, fill, tracking=0.0, leading=1.02, anchor="l",
                  max_w=None, align_center=False):
    """Draw a stack of display lines; returns (bottom_y, total_height)."""
    x, y = xy
    line_h = f.size * leading
    if max_w:
        lines = [l for src in lines for l in wrap(d, src, f, max_w, tracking)]
    cy = y
    for line in lines:
        if align_center:
            tracked(d, (x, cy), line, f, fill, tracking, anchor=("m", "a"))
        else:
            tracked(d, (x, cy), line, f, fill, tracking, anchor=(anchor, "a"))
        cy += line_h
    return cy, cy - y


def kicker(d, xy, s, fill, size=26, tracking=6.0, family="sg", weight=500, anchor="l", dot=None):
    f = font(size, weight, family)
    y = xy[1]
    x = xy[0]
    if dot:
        r = size * 0.17
        cy = y + size * 0.62
        d.ellipse([x, cy - r, x + r * 2, cy + r], fill=dot)
        x += r * 2 + size * 0.55
    if anchor == "m":
        tracked(d, (x, y), s.upper(), f, fill, tracking, anchor=("m", "a"))
    else:
        tracked(d, (x, y), s.upper(), f, fill, tracking, anchor=("l", "a"))


# --------------------------------------------------------------------------
# Device mockups — real perspective, glass bezel, rim light, contact shadow
# --------------------------------------------------------------------------

def _homography(src_quad, dst_size):
    """Return inverse map arrays for a quad -> rectangle fit (PIL COEFFS)."""
    return Image.transform(dst_size, Image.PERSPECTIVE,
                           _find_coeffs(src_quad, dst_size), Image.BICUBIC)


def _find_coeffs(pa, size):
    w, h = size
    pb = [(0, 0), (w, 0), (w, h), (0, h)]
    A, B = [], []
    for (x, y), (u, v) in zip(pa, pb):
        A.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        B.append(u)
        A.append([0, 0, 0, x, y, 1, -v * x, -v * y])
        B.append(v)
    res = np.linalg.solve(np.array(A, dtype=np.float64), np.array(B, dtype=np.float64))
    return tuple(res.tolist())


def project_quad(w, h, yaw=0.0, pitch=0.0, focal=2400.0, scale=1.0):
    """Camera-projected corners of a w×h plane, centred on origin."""
    corners = [(-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)]
    ry, rx = math.radians(yaw), math.radians(pitch)
    out = []
    for x, y in corners:
        # rotate about Y then X
        X = x * math.cos(ry)
        Z = x * math.sin(ry)
        Y = y * math.cos(rx) - Z * math.sin(rx)
        Z = y * math.sin(rx) + Z * math.cos(rx)
        f = focal / (focal + Z)
        out.append((X * f * scale, Y * f * scale))
    return out


def device_frame(shot: Image.Image, width: int, *, radius_ratio=0.105,
                 bezel=3, tilt=None, body="#0A0A0C", glow_color=None,
                 glow_strength=0.55, shadow=True, body_pad=10,
                 screen_brightness=1.0):
    """
    Wrap a full-screen phone screenshot in a premium device body.

    Returns an RGBA image and the box it occupies (for easy placement).
    `tilt` = (yaw_deg, pitch_deg) uses a real perspective projection.
    """
    sw, sh = shot.size
    body_w = width
    body_h = int(round(width * sh / sw))
    bw = body_w + body_pad * 2
    bh = body_h + body_pad * 2
    r = int(body_w * radius_ratio)

    # body plate
    body_img = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
    bd = ImageDraw.Draw(body_img)
    bd.rounded_rectangle([0, 0, bw - 1, bh - 1], radius=r + body_pad, fill=rgb(body) + (255,))
    # metal edge: 1px lighter inner ring
    bd.rounded_rectangle([1, 1, bw - 2, bh - 2], radius=r + body_pad,
                         outline=rgb("#3A3D48") + (255,), width=1)

    # screen
    screen = shot.convert("RGB").resize((body_w, body_h), Image.LANCZOS)
    if screen_brightness != 1.0:
        arr = np.array(screen, dtype=np.float32) * screen_brightness
        screen = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")
    mask = Image.new("L", (body_w, body_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, body_w - 1, body_h - 1], radius=r, fill=255)
    body_img.paste(screen, (body_pad, body_pad), mask)

    # inner bezel hairline over the screen edge
    bd.rounded_rectangle([body_pad, body_pad, body_pad + body_w - 1, body_pad + body_h - 1],
                         radius=r, outline=(255, 255, 255, 26), width=1)

    img, alpha = body_img, body_img.getchannel("A")

    # optional perspective
    if tilt and (abs(tilt[0]) > 0.01 or abs(tilt[1]) > 0.01):
        yaw, pitch = tilt
        ss = 2
        quad = project_quad(bw * ss, bh * ss, yaw, pitch, focal=max(bw, bh) * 2.6)
        minx = min(p[0] for p in quad); maxx = max(p[0] for p in quad)
        miny = min(p[1] for p in quad); maxy = max(p[1] for p in quad)
        ow, oh = int(maxx - minx) + 2, int(maxy - miny) + 2
        shifted = [(p[0] - minx + 1, p[1] - miny + 1) for p in quad]
        big = img.resize((bw * ss, bh * ss), Image.LANCZOS)
        warped = big.transform((ow, oh), Image.PERSPECTIVE,
                               _find_coeffs(shifted, (ow, oh)), Image.BICUBIC)
        img = warped.resize((ow // ss, oh // ss), Image.LANCZOS)
        alpha = img.getchannel("A")

    return img, alpha


def place_device(canvas: Canvas, shot: Image.Image, cx: int, cy: int, width: int,
                 *, tilt=None, radius_ratio=0.105, body="#0A0A0C", body_pad=10,
                 glow_color=None, glow_strength=0.5, glow_r=1.5,
                 shadow_blur=0.085, shadow_offset=(0.02, 0.045), shadow_opacity=200,
                 rim=True, rim_color="#FFFFFF", rim_opacity=0.10):
    """Draw a device centred on (cx, cy) with glow, contact shadow and rim light."""
    dev, alpha = device_frame(shot, width, radius_ratio=radius_ratio, tilt=tilt,
                              body=body, body_pad=body_pad)
    dw, dh = dev.size

    if glow_color:
        canvas.glow(cx, cy, dh * glow_r, glow_color, glow_strength, falloff=2.0,
                    aspect=1.0)

    # soft contact shadow: thumbnailed silhouette, padded then blurred so the
    # falloff always reaches zero inside its own bounds (no clipped edges).
    if shadow_opacity:
        tw, th = max(8, dw // 4), max(8, dh // 4)
        pad = max(3, int(th * shadow_blur * 1.6))
        thumb = Image.new("L", (tw + pad * 2, th + pad * 2), 0)
        thumb.paste(alpha.resize((tw, th), Image.LANCZOS), (pad, pad))
        rad = max(0.8, th * shadow_blur * 0.45)
        sh = thumb.filter(ImageFilter.GaussianBlur(rad))
        sh = sh.point(lambda v: int(v * shadow_opacity / 255))
        full = sh.resize((thumb.width * 4, thumb.height * 4), Image.BICUBIC)
        sx = int(cx - dw / 2 + dw * shadow_offset[0] - pad * 4)
        sy = int(cy - dh / 2 + dh * shadow_offset[1] - pad * 4)
        canvas.paste_shadow(full, sx, sy)

    canvas.paste(dev, int(cx - dw / 2), int(cy - dh / 2))

    if rim:
        rim_im = Image.new("RGBA", (dw, dh), (0, 0, 0, 0))
        rd = ImageDraw.Draw(rim_im)
        rd.rounded_rectangle([0, 0, dw - 1, dh - 1], radius=int(width * radius_ratio),
                             outline=rgb(rim_color) + (int(255 * rim_opacity),), width=2)
        canvas.paste(rim_im, int(cx - dw / 2), int(cy - dh / 2))
    return dev.size


# --------------------------------------------------------------------------
# The product mark — the app's own light-orbit geometry
# --------------------------------------------------------------------------

def draw_mark(d: ImageDraw.ImageDraw, cx, cy, size, *, arc=SAGE, core=BONE,
              dot=SAGE, body=None, ring_w=0.056, gap=0.755, start=-42.0):
    """
    LumaOS Pro's icon, measured from its own adaptive-icon vector
    (app/src/main/res/drawable/ic_launcher_foreground.xml, 108dp grid):
    open orbit ring r=30 / stroke 6 / 272 degrees, four-point sparkle 40 wide,
    centre dot r=3.5. Ratios below are that geometry, not an approximation.
    """
    if body:
        d.rounded_rectangle([cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2],
                            radius=size * 0.235, fill=body)
    R = size * (30 / 108)
    wdt = max(2, int(round(size * (6 / 108))))
    d.arc([cx - R, cy - R, cx + R, cy + R], start=start, end=start + 360 * gap,
          fill=arc, width=wdt)
    star = size * (20 / 108)
    k = 0.25
    d.polygon([(cx, cy - star), (cx + star * k, cy - star * k),
               (cx + star, cy), (cx + star * k, cy + star * k),
               (cx, cy + star), (cx - star * k, cy + star * k),
               (cx - star, cy), (cx - star * k, cy - star * k)], fill=core)
    r = size * (3.5 / 108)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=dot)


def mark_image(size, *, arc=SAGE, core=BONE, dot=SAGE, bg=None, radius=0.235):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    body = bg
    draw_mark(d, size / 2, size / 2, size * 0.88, arc=arc, core=core, dot=dot, body=body)
    if bg is None:
        m = Image.new("L", (size, size), 0)
        ImageDraw.Draw(m).rounded_rectangle([size * .06, size * .06, size * .94, size * .94],
                                            radius=size * radius, fill=255)
    else:
        m = Image.new("L", (size, size), 255)
    im.putalpha(m)
    return im


# --------------------------------------------------------------------------
# Components
# --------------------------------------------------------------------------

def glass_panel(canvas: Canvas, box, radius, *, fill=(255, 255, 255, 12),
                stroke=(255, 255, 255, 34), blur=0, highlight=True):
    """Frosted panel with a top-edge sheen, drawn onto the canvas."""
    x0, y0, x1, y1 = box
    w, h = int(x1 - x0), int(y1 - y0)
    panel = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(panel)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=fill,
                        outline=stroke, width=1)
    if highlight:
        sheen = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        sd = ImageDraw.Draw(sheen)
        sd.rounded_rectangle([1, 1, w - 2, h - 2], radius=radius,
                             fill=(255, 255, 255, 0))
        grad = np.zeros((h, w, 4), dtype=np.uint8)
        t = np.linspace(0, 1, h)[:, None] ** 1.6
        grad[..., 0] = 255; grad[..., 1] = 255; grad[..., 2] = 255
        grad[..., 3] = (t * 16).astype(np.uint8)
        gm = Image.fromarray(grad, "RGBA")
        gmask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(gmask).rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=255)
        panel = Image.alpha_composite(panel, Image.composite(gm, Image.new("RGBA", (w, h), (0, 0, 0, 0)), gmask))
    canvas.paste(panel, int(x0), int(y0))


def pill(d, xy, s, *, f, pad_x, pad_y, fill, text_fill, stroke=None, radius=None,
         tracking=0.0, align="l"):
    w = text_width(d, s, f, tracking)
    h = f.size + pad_y * 2
    x, y = xy
    if align == "m":
        x -= (w + pad_x * 2) / 2
    elif align == "r":
        x -= w + pad_x * 2
    rad = radius if radius is not None else h / 2
    d.rounded_rectangle([x, y, x + w + pad_x * 2, y + h], radius=rad, fill=fill,
                        outline=stroke, width=1 if stroke else 0)
    tracked(d, (x + pad_x, y + pad_y - f.size * 0.10), s, f, text_fill, tracking)
    return w + pad_x * 2


def brand_strip(d, xy, *, light=True, size=34, text="LumaOS Pro", mark_colors=None,
                show_wordmark=True, anchor="l"):
    """Mark + wordmark lockup, faithful to the app icon."""
    arc, core = mark_colors or (SAGE, BONE)
    x, y = xy
    draw_mark(d, x + size * 0.5, y + size * 0.5, size, arc=arc, core=core, dot=arc)
    if show_wordmark:
        f = font(int(size * 0.62), 600, "sg")
        tracked(d, (x + size * 1.28, y + size * 0.5), text, f,
                PAPER if light else "#14171F", tracking=size * 0.012, anchor=("l", "m"))
    return size * 1.28 + (text_width(d, text, font(int(size * 0.62), 600, "sg")) if show_wordmark else 0)


def hairline(d, xy, w, color, thick=1):
    x, y = xy
    d.rectangle([x, y, x + w, y + thick - 1], fill=color)


# --------------------------------------------------------------------------
# Screenshot helpers
# --------------------------------------------------------------------------

def load_shot(path: str) -> Image.Image:
    return Image.open(path).convert("RGB")


# ==========================================================================
# Frame — the design-unit API every campaign asset is laid out in.
# Coordinates, sizes and type sizes are in the asset's own pixels; the frame
# renders at `ss`× internally and downsamples once, so hairlines, arcs and
# type edges are antialiased without any layout code caring about it.
# ==========================================================================

class Frame:
    def __init__(self, w: int, h: int, base: str = "#06070B", ss: int = 2):
        self.w, self.h, self.ss = w, h, ss
        self.c = Canvas(w * ss, h * ss, base)

    # -- helpers ----------------------------------------------------------
    def _s(self, v):
        return int(round(v * self.ss))

    def _pt(self, x, y):
        return (int(round(x * self.ss)), int(round(y * self.ss)))

    def _col(self, c, alpha=None):
        if c is None:
            return None
        if isinstance(c, str):
            return rgb(c, alpha) if alpha is not None else rgb(c)
        if isinstance(c, tuple):
            return c if alpha is None else tuple(c[:3]) + (alpha,)
        raise TypeError(c)

    # -- background -------------------------------------------------------
    def gradient(self, stops, angle=90.0, box=None):
        b = [self._s(v) for v in box] if box else None
        self.c.linear(stops, angle, box=b)
        return self

    def glow(self, cx, cy, r, color, strength=1.0, falloff=2.2, aspect=1.0):
        self.c.glow(cx * self.ss, cy * self.ss, r * self.ss, color, strength, falloff, aspect)
        return self

    def vignette(self, amount=0.5, power=1.6):
        self.c.vignette(amount, power)
        return self

    def grain(self, amount=2.2, seed=7):
        self.c.grain(amount, seed)
        return self

    # -- shapes -----------------------------------------------------------
    # Every shape is composited through an alpha-aware overlay, so rgba fills
    # and low-alpha strokes behave like they would in a real design tool.

    def _ink(self, bbox, fn):
        x0, y0, x1, y1 = [int(round(v)) for v in bbox]
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(self.c.w, x1), min(self.c.h, y1)
        if x1 <= x0 or y1 <= y0:
            return
        layer = Image.new("RGBA", (x1 - x0, y1 - y0), (0, 0, 0, 0))
        fn(ImageDraw.Draw(layer), x0, y0)
        arr = np.array(layer, dtype=np.float32)
        self.c.paste_np(arr, x0, y0, alpha=arr[..., 3] / 255.0)

    def rect(self, box, radius=0, fill=None, stroke=None, width=1, alpha=None):
        x0, y0, x1, y1 = [self._s(v) for v in box]
        w = self._s(width) + 2
        fillc = self._col(fill, alpha) if fill else None
        strokec = self._col(stroke) if stroke else None
        rr = self._s(radius)

        def fn(d, ox, oy):
            d.rounded_rectangle([x0 - ox, y0 - oy, x1 - ox, y1 - oy], radius=rr,
                                fill=fillc, outline=strokec,
                                width=max(1, self._s(width)))
        self._ink((x0 - w, y0 - w, x1 + w, y1 + w), fn)
        return self

    def line(self, a, b, color="#FFFFFF", width=1, alpha=None):
        ax, ay = self._s(a[0]), self._s(a[1])
        bx, by = self._s(b[0]), self._s(b[1])
        w = self._s(width) + 2
        col = self._col(color, alpha)

        def fn(d, ox, oy):
            d.line([(ax - ox, ay - oy), (bx - ox, by - oy)], fill=col,
                   width=max(1, self._s(width)))
        self._ink((min(ax, bx) - w, min(ay, by) - w, max(ax, bx) + w, max(ay, by) + w), fn)
        return self

    def arc(self, box, start, end, color, width=2, alpha=None):
        x0, y0, x1, y1 = [self._s(v) for v in box]
        w = self._s(width) + 2
        col = self._col(color, alpha)

        def fn(d, ox, oy):
            d.arc([x0 - ox, y0 - oy, x1 - ox, y1 - oy], start=start, end=end,
                  fill=col, width=max(1, self._s(width)))
        self._ink((x0 - w, y0 - w, x1 + w, y1 + w), fn)
        return self

    def ellipse(self, box, fill=None, stroke=None, width=1, alpha=None):
        x0, y0, x1, y1 = [self._s(v) for v in box]
        w = self._s(width) + 2
        fillc = self._col(fill, alpha) if fill else None
        strokec = self._col(stroke) if stroke else None

        def fn(d, ox, oy):
            d.ellipse([x0 - ox, y0 - oy, x1 - ox, y1 - oy], fill=fillc,
                      outline=strokec, width=max(1, self._s(width)))
        self._ink((x0 - w, y0 - w, x1 + w, y1 + w), fn)
        return self

    def polygon(self, pts, fill, alpha=None):
        p = [(self._s(x), self._s(y)) for x, y in pts]
        col = self._col(fill, alpha)
        xs = [q[0] for q in p]; ys = [q[1] for q in p]

        def fn(d, ox, oy):
            d.polygon([(x - ox, y - oy) for x, y in p], fill=col)
        self._ink((min(xs) - 3, min(ys) - 3, max(xs) + 3, max(ys) + 3), fn)
        return self

    def glass(self, box, radius=32, fill=(255, 255, 255, 12), stroke=(255, 255, 255, 30),
              width=1, sheen=True):
        x0, y0, x1, y1 = [v * self.ss for v in box]
        glass_panel(self.c, (x0, y0, x1, y1), self._s(radius), fill=fill,
                    stroke=stroke, highlight=sheen)
        return self

    def blur_region(self, box, radius=30, amount=18):
        """Frost whatever is underneath a region (used for glass over backdrop)."""
        x0, y0, x1, y1 = [self._s(v) for v in box]
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(self.c.w, x1), min(self.c.h, y1)
        if x1 <= x0 or y1 <= y0:
            return self
        sub = self.c.image().crop((x0, y0, x1, y1)).filter(ImageFilter.GaussianBlur(amount * self.ss / 2))
        m = Image.new("L", sub.size, 0)
        ImageDraw.Draw(m).rounded_rectangle([0, 0, sub.width - 1, sub.height - 1],
                                            radius=self._s(radius), fill=255)
        self.c.paste(sub.convert("RGBA"), x0, y0)
        return self

    # -- type -------------------------------------------------------------
    def f(self, size, weight=400, family="inter"):
        return font(max(1, self._s(size)), weight, family)

    def text(self, x, y, s, size=32, weight=400, family="inter", color="#F2F4F9",
             tracking=0.0, anchor="la", alpha=None, italic=False):
        d = self.c.draw()
        fnt = self.f(size, weight, family)
        tracked(d, (x * self.ss, y * self.ss), s, fnt, self._col(color, alpha),
                tracking * self.ss, anchor=anchor)
        self.c.commit()
        return self

    def tw(self, s, size, weight=400, family="inter", tracking=0.0):
        d = self.c.draw()
        return text_width(d, s, self.f(size, weight, family), tracking * self.ss) / self.ss

    def lines(self, x, y, rows, size=100, weight=500, family="sg", color="#F4F6FB",
              tracking=-2.0, leading=1.06, anchor="l", max_w=None):
        """Stack of display lines (each item is one line; wraps only if max_w)."""
        d = self.c.draw()
        fnt = self.f(size, weight, family)
        cy = y
        for row in rows:
            parts = wrap(d, row, fnt, max_w * self.ss, tracking * self.ss) if max_w else [row]
            for part in parts:
                tracked(d, (x * self.ss, cy * self.ss), part, fnt, self._col(color),
                        tracking * self.ss, anchor=(anchor, "a"))
                cy += size * leading
        self.c.commit()
        return cy

    def para(self, x, y, s, size=32, weight=400, family="inter", color="#A8AEBC",
             leading=1.5, max_w=None, tracking=0.0, anchor="l"):
        return self.lines(x, y, [s], size=size, weight=weight, family=family,
                          color=color, tracking=tracking, leading=leading,
                          anchor=anchor, max_w=max_w)

    def kicker(self, x, y, s, color="#7C86FF", size=23, tracking=3.4, dot=None,
               anchor="l", family="sg", weight=500):
        d = self.c.draw()
        fnt = self.f(size, weight, family)
        label = s.upper()
        tw = text_width(d, label, fnt, tracking * self.ss) / self.ss
        dot_w = (size * 0.40 + size * 0.62) if dot else 0
        total = tw + dot_w
        x0 = x - (total / 2 if anchor == "m" else total if anchor == "r" else 0)
        if dot:
            r = size * 0.20
            cy = y + size * 0.60
            d.ellipse([self._s(x0), self._s(cy - r), self._s(x0 + r * 2),
                       self._s(cy + r)], fill=self._col(dot))
            x0 += dot_w
        tracked(d, (x0 * self.ss, y * self.ss), label, fnt, self._col(color),
                tracking * self.ss, anchor=("l", "a"))
        self.c.commit()
        return self

    def brand(self, x, y, size=34, light=True, wordmark=True, arc=None, core=None,
              color=None, anchor="l"):
        d = self.c.draw()
        arc = arc or SAGE
        core = core or (BONE if light else "#14171F")
        col = color or (PAPER if light else "#14171F")
        bx = x
        if anchor == "m":
            # measure lockup so the pair is optically centred
            fnt = self.f(size * 0.62, 600, "sg")
            wd = text_width(d, "LumaOS Pro", fnt) / self.ss if wordmark else 0
            bx = x - (size * 1.30 + wd) / 2
        draw_mark(d, (bx + size * 0.5) * self.ss, (y + size * 0.5) * self.ss, size * self.ss,
                  arc=arc, core=core, dot=arc)
        if wordmark:
            fnt = self.f(size * 0.62, 600, "sg")
            tracked(d, ((bx + size * 1.32) * self.ss, (y + size * 0.5) * self.ss),
                    "LumaOS Pro", fnt, self._col(col), 0.4 * self.ss, anchor=("l", "m"))
        self.c.commit()
        return self

    def mark(self, cx, cy, size, arc=SAGE, core=BONE, body=None):
        d = self.c.draw()
        draw_mark(d, cx * self.ss, cy * self.ss, size * self.ss, arc=arc, core=core,
                  dot=arc, body=body)
        self.c.commit()
        return self

    def pill(self, x, y, label, size=30, pad=(34, 20), fill="#F2F4F9", color="#0A0C12",
             stroke=None, radius=None, tracking=0.2, anchor="l", weight=600,
             family="inter"):
        d = self.c.draw()
        fnt = self.f(size, weight, family)
        tw = text_width(d, label, fnt, tracking * self.ss) / self.ss
        h = size + pad[1] * 2
        w = tw + pad[0] * 2
        x0 = x - (w / 2 if anchor == "m" else w if anchor == "r" else 0)
        r = radius if radius is not None else h / 2
        fillc = self._col(fill)
        strokec = self._col(stroke) if stroke else None
        filled = fillc + (255,) if len(fillc) == 3 else fillc
        stroked = (strokec + (255,)) if (strokec and len(strokec) == 3) else strokec

        def fn(dr, ox, oy):
            dr.rounded_rectangle([self._s(x0) - ox, self._s(y) - oy,
                                  self._s(x0 + w) - ox, self._s(y + h) - oy],
                                 radius=self._s(r), fill=filled, outline=stroked,
                                 width=max(1, self._s(1)))
        self._ink((self._s(x0) - 3, self._s(y) - 3, self._s(x0 + w) + 3,
                   self._s(y + h) + 3), fn)
        d = self.c.draw()
        tracked(d, (self._s(x0 + pad[0]), self._s(y + pad[1] - size * 0.08)), label, fnt,
                self._col(color), tracking * self.ss)
        self.c.commit()
        return w

    # -- product ----------------------------------------------------------
    def device(self, shot, cx, cy, width, **kw):
        w2 = width * self.ss
        place_device(self.c, shot, cx * self.ss, cy * self.ss, int(w2), **kw)
        return self

    def shot_crop(self, shot, box, size=None):
        """Faithful UI crop: box is (left, top, right, bottom) in 0..1 of the shot."""
        w, h = shot.size
        c = shot.crop((int(box[0] * w), int(box[1] * h), int(box[2] * w), int(box[3] * h)))
        if size:
            c = c.resize((self._s(size[0]), self._s(size[1])), Image.LANCZOS)
        return c

    def paste_crop(self, shot, box, at, size, radius=24, shadow=False):
        crop = self.shot_crop(shot, box, size)
        mask = Image.new("L", crop.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, crop.width - 1, crop.height - 1],
                                               radius=self._s(radius), fill=255)
        x, y = self._pt(*at)
        if shadow:
            sh = mask.filter(ImageFilter.GaussianBlur(self._s(28)))
            sh = sh.point(lambda v: int(v * 0.55))
            self.c.paste_shadow(sh, x + self._s(6), y + self._s(16))
        self.c.paste(crop.convert("RGBA"), x, y) if False else None
        layer = crop.convert("RGBA")
        layer.putalpha(mask)
        self.c.paste(layer, x, y)
        return self

    # -- output -----------------------------------------------------------
    def out(self):
        im = self.c.image()
        if self.ss != 1:
            im = im.resize((self.w, self.h), Image.LANCZOS)
        return im

    def save(self, path):
        im = self.out()
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        im.save(path, "PNG", optimize=True)
        return path
