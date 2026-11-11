# LumaOS Pro — Promotional Campaign v1.2.0

A complete, reference-driven marketing campaign for LumaOS Pro. Every plate in
`assets/` is generated from **the real application screenshots** supplied for this
brief and **the product's own design tokens** (`docs/design/v3.2/tokens/*.json`),
drawn with the app's own typefaces (Inter, Space Grotesk — both SIL OFL 1.1, as
shipped in `app/src/main/res/font/`).

Nothing in this folder changes the app. No screenshot was retouched, no UI was
redrawn, and no rating, review, download count, award or testimonial appears
anywhere in the campaign — the product has not been published yet.

---

## 1. What was analysed

Four 1440 × 3120 device captures (Luma Glass / Aurora / Monolith):

| Capture | What it proves |
| --- | --- |
| Home screen | Light clock, contextual date line (`Study · 50%`), 4 × 2 app grid, glass search pill, four-icon dock |
| App drawer | `Apps · 58 apps`, search field, All/Frequent filters, alphabetical grid, sheet over the home |
| Studio — Aurora | Live theme preview, applied-state line (`Aurora · Expre… / Applied · 15.3:1 AAA`), undo/redo/apply, preset thumbnails |
| Studio — Monolith | Same grammar, different material: no blur, mono labels, vermilion rule, square geometry |

**Visual language extracted** → a near-black field with one large luminous
gradient, glass surfaces with 1 px hairlines, an oversized light numeral as the
hero motif, monospace micro-labels, and a strict two-column rhythm of
*statement → product*.

**What was deliberately not copied** → no other brand's device frames, no
lifestyle stock photography, no invented "as featured in" strip, no feature
tables the app does not implement.

---

## 2. Positioning

> **A calmer home for Android.**
> A lightweight launcher and performance platform for the phones you already own.

- **Category** — launcher, not a phone-cleaner. The optimizer touches only its own
  process, caches and database, and says so on screen.
- **Category tension** — launchers compete on more: more widgets, feeds, icons.
  LumaOS competes on *less*: fewer reasons to stay, more honesty about what it
  measures.
- **Proof, not adjectives** — the campaign leans on things a reader can check:
  twelve shipped identities, `minSdk 26`, a real applied-contrast line in the
  screenshot, zero network permission, twelve documented themes, a
  measured/estimated/unavailable badge on every reading.

### Audience

| Segment | Who | What they need to hear |
| --- | --- | --- |
| **The hardware realist** | Owns a 3–5 year old mid-range Android | "This was built for your phone, not last year's flagship." |
| **The de-Googler / privacy-first user** | Avoiding accounts, ad IDs and telemetry | "No account, no ads, no analytics SDK, no network permission." |
| **The design-literate tinkerer** | Customises wallpaper, icons, type | "Twelve authored identities and a Studio that previews before it applies." |
| **The focus seeker** | Work/study/sleep separation | "Context profiles switch the home screen on a schedule that never leaves the device." |

### Unique selling propositions (all verifiable in the repo)

1. **Twelve authored design identities, one grammar** — Signature, Atmosphere,
   Monolith, plus the premium trio Obsidian, Ivory, Aurora and the retained
   palettes; each changes material, radii, type and motion, not just hue.
2. **A Studio, not a settings list** — palette, type, icon shape and backdrop are
   previewed live, with undo/redo and an applied-contrast readout.
3. **Honest diagnostics** — every reading is *Measured*, *Estimated* or
   *Unavailable*, with the source named. `Unavailable` is a first-class result.
4. **Privacy by construction** — no account, no backend, no analytics, no
   advertising ID, no network permission; a separate encrypted Vault for private
   files.
5. **Built for weak hardware** — Views on the home hot path, Compose only where it
   earns its place; Android 8.0 → 16.

### Brand identity

- **Name / wordmark** — `LumaOS Pro`, Space Grotesk SemiBold, +0.4 tracking.
- **Mark** — the app's own adaptive-icon geometry: a 30/108 orbit with a 272°
  opening, four-point sparkle (40 wide) and a 3.5/108 dot; bone `#E8EBE4` on sage
  `#B5C5A9` over plate `#171B18`.
- **Campaign palette** — product neutrals (bone, sage, plate) plus the three
  signature accents: indigo `#7C86FF`, mint `#46E0B0`, vermilion `#FF5A2B`, with
  champagne `#D9BC8C` for the premium register.
- **Voice** — short declaratives, no exclamation marks, no "revolutionary".
  Copy is written to be defensible in a support thread.

---

## 3. Design system (as used by this campaign)

| Token | Value | Where it shows |
| --- | --- | --- |
| Field | `#05060A → #0F1226 → #06070C`, 100° | every dark plate |
| Light | one radial bloom, indigo 34–46 % / violet 22–26 % | behind the product |
| Glass | `rgba(255,255,255,.06)` fill, `rgba(255,255,255,.22)` hairline | pills, rules |
| Type — display | Space Grotesk 600, tracking −2.4 to −5.0 | headlines |
| Type — text | Inter 400/500 | body, 26–29 px at 1080 w |
| Type — micro | Space Grotesk 500/600, 10.5–16 px, +2.0 to +5.0 tracking, caps | kickers, labels |
| Rules | 1 px @ 16–26 % white; 2 px accent for emphasis | section dividers |
| Plates | corner crop marks at 46–54 px inset | posters, feeds |
| Device | black body, 10.5 % radius, 1 px rim light at 10 %, blurred contact shadow | all mockups |

Layout grid: 62–74 px outer margin at 1080 w, 12-column feel, headline block in
the top third, product entering from below. Stories reserve 250 px top and 260 px
bottom for platform chrome.

---

## 4. Assets

| Folder | Asset | Size | Use |
| --- | --- | --- | --- |
| `assets/play` | `01-feature-graphic-1024x500.png` | 1024 × 500 | Play Store feature graphic (replaces the placeholder in `store-assets/`) |
| | `02-home` … `08-contexts` | 1080 × 1920 | Play Store phone screenshots, in listing order |
| `assets/instagram` | `01-launch-announcement` | 1080 × 1350 | launch post / pinned announcement |
| | `02-twelve-identities` | 1080 × 1350 | themes feature post |
| | `03-privacy` | 1080 × 1350 | trust post |
| | `04-monolith-feature` | 1080 × 1350 | single-identity poster |
| | `05-lightweight` | 1080 × 1350 | spec ledger post |
| | `06-manifesto` | 1080 × 1350 | brand plate (grid rhythm, no UI) |
| `assets/stories` | `01-launch-story` | 1080 × 1920 | story with swipe-up |
| | `02-themes-story` | 1080 × 1920 | story |
| | `03-privacy-story` | 1080 × 1920 | story |
| | `04-studio-story` | 1080 × 1920 | story |
| | `05-reel-cover` | 1080 × 1920 | reel cover (safe inside 1080 × 1080 grid crop) |
| `assets/posters` | `01-calm-home` | 1200 × 1800 | feature poster |
| | `02-twelve-identities-light` | 1200 × 1800 | light-plate poster (range) |
| | `03-privacy-ledger` | 1200 × 1800 | proof-ledger poster |
| `assets/site` | `hero-banner-2400x1000` | 2400 × 1000 | landing-page hero (with nav) |
| | `hero-16x9-1600x900` | 1600 × 900 | press / 16:9 hero |
| | `showcase-ensemble-2400x1600` | 2400 × 1600 | premium app showcase |
| | `announcement-1200x630` | 1200 × 630 | Open Graph / press announcement |
| | `launch-square-1080x1080` | 1080 × 1080 | square announcement |
| `assets/brand` | `play-icon-512.png`, `app-icon-1024.png` | 512 / 1024 | listing icon + master |
| | `icon-system-2400x1500.png` | 2400 × 1500 | mark, clear space, scale ladder, lockups, palette |
| `assets` | `00-campaign-contact-sheet.png` | 2400 × ~3200 | one-page review of the whole set |

All PNGs are sRGB, flattened, and exported at the exact pixel dimensions in the
filename. Sizes are printed in each filename so a mis-upload is obvious.

---

## 5. Launch campaign (organic, zero budget)

**Week 0 — seed.** Post the manifesto plate and the ensemble on X/Threads/Mastodon
with a plain-language build note (what is measured, what is not). Publish the
design-system page and the contact sheet; design communities share process, not
slogans.

**Week 1 — launch.** Play listing live → Instagram carousel (launch → themes →
privacy → manifesto), story set with a link sticker, and the announcement card as
the link preview everywhere. Reddit (r/androidthemes, r/AndroidQuestions, device
subreddits) uses the *light* poster and a plain text post; no reposted ad art.

**Week 2–4 — proof.** One feature per post, always with the real screen and one
number the reader can check (`12 identities`, `26 · Android 8.0`,
`no network permission`). Publish a short screen-recording reel: home → drawer →
Studio → apply.

**Evergreen.** The icon-system plate doubles as the press-kit page; the hero
banner drives the landing page; the 1080 × 1080 square is the share card for
changelogs so every release is one post, not a campaign.

### Calls to action

Primary: **Get it on Google Play.** Secondary: **Explore the Studio.** Tertiary:
**Read the design system** (for the design audience). All three are on the hero;
social uses one CTA per plate, never two.

### Guardrails honoured

- No fabricated ratings, reviews, download counts, awards, press logos or
  testimonials — the product has not shipped.
- No claim beyond what the repository documents (e.g. "twelve identities",
  "no network permission", "minSdk 26"). The optimizer's limited scope is not
  described as a phone-wide cleaner.
- Screenshots are unmodified crops of the supplied captures.

---

## 6. Reproducing the campaign

```bash
cd marketing
python3 -m pip install --break-system-packages pillow numpy fonttools brotli
npm install --prefix .build @fontsource/inter @fontsource/space-grotesk   # font sources
cp /path/to/screenshots/*.jpg .build/src/                                 # a_home, b_drawer,
                                                                          # c_studio_aurora,
                                                                          # d_studio_monolith
python3 build_all.py
```

`luma_studio.py` is the design kernel (tokens, alpha-correct primitives,
perspective device mockups, supersampled `Frame`); `campaign.py` holds the
palette and shared furniture; `a1…f_brand.py` are the asset modules in the order
the Play listing, the feed and the site need them. Change a token in one place
and rebuild — every plate updates together.
