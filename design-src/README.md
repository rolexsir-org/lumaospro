# Design sources

Two things live here:

* `v3.2/LumaOS-Design-System.html` — the single-file build of the LumaOS Design
  System v3.2 studio, exactly as it arrived in the owner's `design.zip`
  (SHA-256 `1fee66a8f2a5f1acae512748ca80dc24a42234e617997673311ed98f949e093d`).
  It is browsable offline and is the visual reference for the tokens in
  `docs/design/v3.2/`. Only the single-file build is kept; the split
  `index.html` + `app.js` + `style.css` duplicate is not, to keep the repository
  from carrying ~470 KB of the same content twice.
* `wallpapers/` — the packaged wallpaper sources described below.

## v3.2 studio vs the app

The studio is a **design** artefact, not product code: it is not shipped in the
APK, is not referenced by Gradle, and no JavaScript from it runs in the app.
Screens it shows that the app does not implement yet are design intent — see
`docs/DESIGN_SYSTEM.md` §14.

`wallpapers/` holds the full-resolution renders the packaged WebP assets were
exported from (portrait 704×1520, landscape 1376×768). They are stored as WebP to
keep the repository small; the packaged files live in
`app/src/main/res/drawable-nodpi` and `app/src/main/res/drawable-land-nodpi` with
the same resource stem in both:

| Source | Packaged |
| --- | --- |
| `aurora-portrait.webp` | `drawable-nodpi/luma_wall_aurora.webp` |
| `aurora-landscape.webp` | `drawable-land-nodpi/luma_wall_aurora.webp` |
| `zen-portrait.webp` | `drawable-nodpi/luma_wall_zen.webp` |
| `zen-landscape.webp` | `drawable-land-nodpi/luma_wall_zen.webp` |
| `void-portrait.webp` | `drawable-nodpi/luma_wall_void.webp` |
| `void-landscape.webp` | `drawable-land-nodpi/luma_wall_void.webp` |

## Re-export

```bash
python3 - <<'PY'
from PIL import Image
for name in ("aurora", "zen", "void"):
    for size, folder in (("portrait", "drawable-nodpi"),
                         ("landscape", "drawable-land-nodpi")):
        im = Image.open(f"design-src/wallpapers/{name}-{size}.webp").convert("RGB")
        im.save(f"app/src/main/res/{folder}/luma_wall_{name}.webp",
                "WEBP", quality=84, method=6)
PY
```

## Art direction rules

1. Keep a quiet central band for icons and the clock.
2. Compose portrait and landscape separately; never crop one from the other.
3. Declare the artwork's lightness in `WallpaperArt` — the image is never tinted at
   runtime.
4. Set the focal point and scrim strengths in `WallpaperArt`, not in the view.

The packaged assets are the artefacts of record; the sources exist so a future
change can re-crop without regenerating the art.
