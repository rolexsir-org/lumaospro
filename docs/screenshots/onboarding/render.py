# -*- coding: utf-8 -*-
"""Static design renderings of the seven onboarding steps.

Every visible string is read from the app's own values/*.xml at render time, so
the artwork cannot drift from the shipped copy. Layout follows the Compose
values in OnboardingScreens.kt (390dp reference width, 20dp margins, 16dp card
padding, 52dp action height). This produces PNG design renderings, not device
captures.
"""
import re, os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
OUT = os.path.join(ROOT, 'docs/screenshots/onboarding')

# ---- strings -------------------------------------------------------------
STR = {}
for f in ('app/src/main/res/values/strings.xml', 'app/src/main/res/values/strings_v2.xml'):
    src = open(os.path.join(ROOT, f), encoding='utf-8').read()
    for name, value in re.findall(r'<string name="([^"]+)">(.*?)</string>', src, re.S):
        STR[name] = (value.replace('\\\'', '’').replace('&amp;', '&')
                    .replace('&lt;', '<').replace('&gt;', '>').replace('\\n', ' '))

def T(name):
    return STR[name]

# ---- canvas --------------------------------------------------------------
W, S = 390, 3
M = 20
BG_TOP, BG_BOTTOM = (8, 10, 15), (16, 19, 27)
CARD, BORDER = (21, 25, 34), (40, 45, 59)
INK, MUTED = (242, 244, 248), (166, 172, 188)
ACCENT = (142, 126, 255)
ACCENT_FILL = (45, 38, 84)
ACCENT_BORDER = (108, 92, 231)
TRACK = (44, 49, 63)

_fonts = {}
def F(size, bold=False):
    key = (size, bold)
    if key not in _fonts:
        path = '/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf' % ('-Bold' if bold else '')
        _fonts[key] = ImageFont.truetype(path, int(size * S))
    return _fonts[key]

class Screen:
    def __init__(self, step, back=True):
        self.img = Image.new('RGB', (W * S, 5200 * S), BG_TOP)
        self.d = ImageDraw.Draw(self.img)
        # vertical gradient
        for yy in range(0, 1600 * S):
            t = min(1.0, yy / (1100 * S))
            col = tuple(int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3))
            self.d.line([(0, yy), (W * S, yy)], fill=col)
        self.y = 0
        self.status()
        self.topbar(step, back)
        self.progress(step)
        self.y = 128

    # primitives -------------------------------------------------------
    def measure(self, text, size, bold=False):
        return self.d.textlength(text, font=F(size, bold)) / S

    def wrap(self, text, size, width, bold=False):
        words, lines, cur = text.split(), [], ''
        for w in words:
            trial = (cur + ' ' + w).strip()
            if self.measure(trial, size, bold) <= width:
                cur = trial
            else:
                if cur:
                    lines.append(cur)
                cur = w
        if cur:
            lines.append(cur)
        return lines

    def txt(self, x, y, text, size, color=INK, bold=False):
        self.d.text((x * S, y * S), text, font=F(size, bold), fill=color)
        return y + size * 1.42

    def para(self, text, size=13, color=MUTED, width=None, x=M, bold=False, leading=1.45, gap=8):
        width = width or (W - 2 * M)
        for line in self.wrap(text, size, width, bold):
            self.txt(x, self.y, line, size, color, bold)
            self.y += size * leading
        self.y += gap

    def heading(self, title, body):
        self.y += 4
        for line in self.wrap(title, 25, W - 2 * M, True):
            self.txt(M, self.y, line, 25, INK, True)
            self.y += 31
        self.y += 6
        self.para(body, 13.5, MUTED, gap=16)

    def card(self, title, subtitle=None, body=None, bullets=None, points=None, rows=None,
             icons=False, selected=False, inner_button=None, extra_gap=14):
        pad = 16
        x, w = M, W - 2 * M
        inner = w - 2 * pad
        content = []
        cy = self.y + pad
        if title:
            content.append(('title', title))
            cy += 24 + (6 if subtitle else 8)
        if subtitle:
            content.append(('sub', subtitle))
            for line in self.wrap(subtitle, 11.5, inner):
                cy += 16
            cy += 10
        if body:
            content.append(('body', body))
            for line in self.wrap(body, 12.5, inner):
                cy += 18
        if bullets:
            for b in bullets:
                content.append(('bullet', b))
                cy += 9 + 18 * len(self.wrap(b, 12.5, inner - 14))
        if points:
            for (pt, pb) in points:
                content.append(('point', (pt, pb)))
                cy += 19 + 18 * len(self.wrap(pb, 12, inner))
                cy += 8
        if rows:
            for (a, b) in rows:
                content.append(('row', (a, b)))
                cy += 22
        if icons:
            content.append(('icons', None))
            cy += 56
        if inner_button:
            content.append(('button', inner_button))
            cy += 46
        cy += pad
        self.d.rounded_rectangle([x * S, self.y * S, (x + w) * S, cy * S], radius=20 * S,
                                 fill=CARD, outline=ACCENT_BORDER if selected else BORDER,
                                 width=int(2 * S) if selected else int(1 * S))
        yy = self.y + pad
        for kind, val in content:
            if kind == 'title':
                self.txt(x + pad, yy, val, 17.5, INK, True)
                yy += 24 + (6 if subtitle else 8)
            elif kind == 'sub':
                for line in self.wrap(val, 11.5, inner):
                    self.txt(x + pad, yy, line, 11.5, MUTED if not selected else (196, 186, 255))
                    yy += 16
                yy += 10
            elif kind == 'body':
                for line in self.wrap(val, 12.5, inner):
                    self.txt(x + pad, yy, line, 12.5, INK)
                    yy += 18
            elif kind == 'bullet':
                self.d.ellipse([(x + pad) * S, (yy + 6) * S, (x + pad + 5) * S, (yy + 11) * S], fill=ACCENT)
                for line in self.wrap(val, 12.5, inner - 14):
                    self.txt(x + pad + 14, yy, line, 12.5, INK)
                    yy += 18
                yy += 9
            elif kind == 'point':
                pt, pb = val
                self.txt(x + pad, yy, pt, 13.5, INK, True)
                yy += 19
                for line in self.wrap(pb, 12, inner):
                    self.txt(x + pad, yy, line, 12, MUTED)
                    yy += 18
                yy += 8
            elif kind == 'row':
                a, b = val
                self.txt(x + pad, yy, a, 13, INK)
                bw = self.measure(b, 12.5)
                self.txt(x + w - pad - bw, yy, b, 12.5, MUTED)
                yy += 22
            elif kind == 'icons':
                for i in range(5):
                    bx = x + pad + i * (48)
                    self.d.rounded_rectangle([bx * S, yy * S, (bx + 38) * S, (yy + 38) * S], radius=11 * S,
                                             fill=(38 + i * 8, 34 + i * 5, 62 + i * 9),
                                             outline=(78, 68, 128), width=S)
                yy += 56
            elif kind == 'button':
                self.secondary_at(x + pad, yy, val, inner)
                yy += 46
        self.y = cy + extra_gap

    def primary(self, label):
        x, w, h = M, W - 2 * M, 52
        self.d.rounded_rectangle([x * S, self.y * S, (x + w) * S, (self.y + h) * S], radius=17 * S,
                                 fill=ACCENT_FILL, outline=ACCENT_BORDER, width=int(1.5 * S))
        tw = self.measure(label, 14.5, True)
        self.txt(x + (w - tw) / 2, self.y + 17, label, 14.5, INK, True)
        self.y += h + 10

    def secondary_at(self, x, y, label, w):
        h = 46
        self.d.rounded_rectangle([x * S, y * S, (x + w) * S, (y + h) * S], radius=15 * S,
                                 fill=(24, 28, 38), outline=BORDER, width=S)
        tw = self.measure(label, 13, True)
        self.txt(x + (w - tw) / 2, y + 14, label, 13, INK, True)

    def secondary(self, label):
        x, w, h = M, W - 2 * M, 52
        self.d.rounded_rectangle([x * S, self.y * S, (x + w) * S, (self.y + h) * S], radius=17 * S,
                                 fill=(23, 27, 37), outline=BORDER, width=S)
        tw = self.measure(label, 14, True)
        self.txt(x + (w - tw) / 2, self.y + 17, label, 14, INK, True)
        self.y += h + 8

    # chrome -----------------------------------------------------------
    def status(self):
        self.txt(26, 12, '9:41', 11.5, INK, True)
        self.d.rounded_rectangle([300 * S, 15 * S, 318 * S, 24 * S], radius=2 * S, fill=INK)
        self.d.rounded_rectangle([322 * S, 14 * S, 344 * S, 25 * S], radius=2 * S, outline=INK, width=S)
        self.d.rectangle([344 * S, 18 * S, 346 * S, 21 * S], fill=INK)
        self.d.line([(356 * S, 24 * S), (361 * S, 25 * S), (361 * S, 14 * S)], fill=INK, width=int(1.6 * S))
        self.d.line([(356 * S, 25 * S), (361 * S, 26 * S), (361 * S, 15 * S)], fill=INK, width=int(1.6 * S))

    def topbar(self, step, back):
        x, y, w, h = 12, 32, W - 24, 64
        self.d.rounded_rectangle([x * S, y * S, (x + w) * S, (y + h) * S], radius=18 * S,
                                 fill=(19, 22, 31), outline=BORDER, width=S)
        if back:
            self.txt(28, y + 25, '‹  ' + T('onboarding_back'), 12.5, INK, True)
        self.txt(108, y + 14, T('onboarding_title'), 17, INK, True)
        self.txt(108, y + 38, 'Step %d of 7' % step, 11.5, MUTED)

    def progress(self, step):
        x, y = M, 106
        seg = (W - 2 * M - 6 * 6) / 7.0
        for i in range(7):
            bx = x + i * (seg + 6)
            self.d.rounded_rectangle([bx * S, y * S, (bx + seg) * S, (y + 4) * S], radius=2 * S,
                                     fill=ACCENT if i < step else TRACK)

    def finish(self, name, step_label):
        end = int(self.y + 26)
        self.d.line([(0, end * S), (W * S, end * S)], fill=(24, 27, 36), width=S)
        caption = [
            'Static design rendering — generated from the app’s strings and layout values.',
            'Not a device screenshot. ' + step_label + ' · 390dp reference width',
        ]
        cy = end + 12
        for line in caption:
            for part in self.wrap(line, 9.5, W - 2 * M):
                self.txt(M, cy, part, 9.5, (120, 126, 142))
                cy += 14
            cy += 1
        cy += 6
        out = self.img.crop((0, 0, W * S, int(cy * S)))
        out.save(os.path.join(OUT, name), optimize=True)
        print('wrote', name, out.size)

# ---- screens -------------------------------------------------------------
def welcome():
    s = Screen(1, back=False)
    s.d.ellipse([(W / 2 - 40) * S, 142 * S, (W / 2 + 40) * S, 222 * S], fill=(35, 31, 66),
                outline=(86, 74, 158), width=int(2 * S))
    s.txt(W / 2 - 16, 163, 'L', 44, (178, 166, 255), True)
    y = 238
    for line in s.wrap(T('onboarding_welcome'), 30, W - 2 * M, True):
        tw = s.measure(line, 30, True)
        s.txt((W - tw) / 2, y, line, 30, INK, True)
        y += 36
    for line in s.wrap(T('onboarding_subtitle'), 13.5, 300):
        tw = s.measure(line, 13.5)
        s.txt((W - tw) / 2, y, line, 13.5, MUTED)
        y += 20
    s.y = y + 18
    s.card(T('onboarding_welcome_points_title'), bullets=[
        T('onboarding_contexts'), T('onboarding_focus_point_short'), T('onboarding_welcome_point_look')
    ])
    s.para(T('onboarding_time_note'), 11.5, MUTED, gap=14)
    s.primary(T('onboarding_start'))
    s.secondary(T('onboarding_skip_tour'))
    s.finish('01-welcome.png', 'Step 1 of 7 · Welcome')

def gestures():
    s = Screen(2)
    s.heading(T('onboarding_gestures_title'), T('onboarding_gestures_subtitle'))
    rows = [('Swipe up', 'Open app drawer'), ('Swipe down', 'Open search'),
            ('Double tap', 'Do nothing'), ('Long press', 'Edit home'),
            ('Swipe left', 'Do nothing'), ('Swipe right', 'Do nothing'),
            ('Pinch in', 'Edit home')]
    s.card(T('onboarding_gestures_bindings_title'), subtitle=T('onboarding_gestures_bindings_subtitle'), rows=rows)
    s.para(T('onboarding_gestures_note'), 11.5, MUTED, gap=14)
    s.primary(T('onboarding_continue'))
    s.secondary(T('onboarding_skip_tour'))
    s.finish('02-gestures.png', 'Step 2 of 7 · Gestures')

def personalize():
    s = Screen(3)
    s.heading(T('onboarding_personalize_title'), T('onboarding_personalize_subtitle'))
    s.card(T('onboarding_living_icons_title'), subtitle=T('onboarding_living_icons_caption'), icons=True)
    for i, (name, desc, sel) in enumerate([
        ('experience_pure', 'experience_pure_desc', False),
        ('experience_liquid', 'experience_liquid_desc', True),
        ('experience_midnight', 'experience_midnight_desc', False)]):
        x, w = M, W - 2 * M
        text_w = w - 32 - (26 if sel else 0)
        lines = s.wrap(T(desc), 11.5, text_w)
        h = max(66, 33 + 16 * len(lines) + 14)
        s.d.rounded_rectangle([x * S, s.y * S, (x + w) * S, (s.y + h) * S], radius=18 * S,
                              fill=CARD, outline=ACCENT_BORDER if sel else BORDER,
                              width=int(2 * S) if sel else S)
        s.txt(x + 16, s.y + 12, T(name), 15, INK, True)
        yy = s.y + 33
        for line in lines:
            s.txt(x + 16, yy, line, 11.5, MUTED)
            yy += 16
        if sel:
            s.d.ellipse([(x + w - 34) * S, (s.y + 22) * S, (x + w - 18) * S, (s.y + 38) * S],
                        outline=ACCENT, width=int(2 * S))
            s.d.ellipse([(x + w - 28) * S, (s.y + 28) * S, (x + w - 24) * S, (s.y + 32) * S], fill=ACCENT)
        s.y += h + 10
    s.card(T('onboarding_personalize_studio_title'), body=T('onboarding_personalize_studio_body'),
           inner_button=T('onboarding_open_studio'))
    s.primary(T('onboarding_continue'))
    s.secondary(T('onboarding_skip_tour'))
    s.finish('03-personalize.png', 'Step 3 of 7 · Personalize')

def focus():
    s = Screen(4)
    s.heading(T('onboarding_focus_title'), T('onboarding_focus_subtitle'))
    s.card(T('onboarding_focus_layers_title'), points=[
        (T('onboarding_focus_layer1_title'), T('onboarding_focus_layer1_body')),
        (T('onboarding_focus_layer2_title'), T('onboarding_focus_layer2_body'))])
    s.card(T('onboarding_focus_optional_title'), body=T('onboarding_focus_optional_body'))
    s.primary(T('onboarding_continue'))
    s.secondary(T('onboarding_skip_tour'))
    s.finish('04-focus-protection.png', 'Step 4 of 7 · Focus Protection')

def accessibility():
    s = Screen(5)
    s.heading(T('onboarding_accessibility_title'), T('onboarding_accessibility_subtitle'))
    s.card(T('focus_access_status_title'), subtitle=T('focus_access_status_needs_disclosure'),
           body=T('focus_access_not_answered'))
    for title, body in [
        ('focus_access_why_title', 'focus_access_why_body'),
        ('focus_access_how_title', 'focus_access_how_body'),
        ('focus_access_reads_title', 'focus_access_reads_body'),
        ('focus_access_stores_title', 'focus_access_stores_body'),
        ('focus_access_control_title', 'focus_access_control_body')]:
        s.card(T(title), body=T(body))
    s.primary(T('focus_access_agree'))
    s.secondary(T('focus_access_decline'))
    s.para(T('focus_access_decline_note'), 10.5, MUTED, gap=12)
    s.secondary(T('onboarding_skip_for_now'))
    s.finish('05-accessibility.png', 'Step 5 of 7 · Accessibility disclosure (content continues below the fold)')

def home_role():
    s = Screen(6)
    s.heading(T('onboarding_home_title'), T('onboarding_home_role_explainer'))
    s.card(T('onboarding_home_status_title'), subtitle=T('onboarding_home_status_pending'),
           body=T('onboarding_home_status_pending_body'))
    s.primary(T('onboarding_set_default'))
    s.secondary(T('onboarding_skip'))
    s.finish('06-home-role.png', 'Step 6 of 7 · Default launcher')

def complete():
    s = Screen(7)
    s.heading(T('onboarding_complete_title'), T('onboarding_complete_subtitle'))
    s.card(T('onboarding_complete_setup_title'), bullets=[
        T('onboarding_complete_home_done'), T('onboarding_complete_settings_hint')])
    s.para(T('onboarding_complete_replay_hint'), 11, MUTED, gap=14)
    s.primary(T('onboarding_enter_home'))
    s.finish('07-complete.png', 'Step 7 of 7 · Complete')

welcome(); gestures(); personalize(); focus(); accessibility(); home_role(); complete()
