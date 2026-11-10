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

## Wallpaper sources

`wallpapers/` holds source renders for the **three theme-linked legacy artworks
only** — aurora, zen and void, the `WallpaperArt` enum (portrait 704×1520,
landscape 1376×768). They are stored as WebP to keep the repository small; the
packaged files live in `app/src/main/res/drawable-nodpi` and
`app/src/main/res/drawable-land-nodpi` with the same resource stem in both:

| Source | Packaged |
| --- | --- |
| `aurora-portrait.webp` | `drawable-nodpi/luma_wall_aurora.webp` |
| `aurora-landscape.webp` | `drawable-land-nodpi/luma_wall_aurora.webp` |
| `zen-portrait.webp` | `drawable-nodpi/luma_wall_zen.webp` |
| `zen-landscape.webp` | `drawable-land-nodpi/luma_wall_zen.webp` |
| — *(source removed)* | `drawable-nodpi/luma_wall_void.webp` |
| `void-landscape.webp` | `drawable-land-nodpi/luma_wall_void.webp` |

**`void-portrait.webp` was deleted from this directory**, so Void's portrait
source is no longer in the repository. The packaged asset it produced,
`drawable-nodpi/luma_wall_void.webp`, is still present and still shipped —
`WallpaperArt.VOID_FLUX` resolves to it through `WallpaperArtResources` — so the
app is unaffected, but that one packaged file can no longer be re-exported or
re-cropped from source. Void's landscape source is still here.

The 30 Wallpaper Studio compositions (`luma_wall_01_*` … `luma_wall_30_*`,
mapped by `WallpaperArtResources.resForKey()` and catalogued in
`wallpaper/WallpaperCatalog.kt`) are **portrait-only and have no sources here** —
the packaged WebP files under `drawable-nodpi` are the only copy in the
repository. Re-cropping or re-exporting them is not possible from this tree; treat
the packaged assets as the artefacts of record for that set.

## Re-export (legacy artworks only)

Only the source files that still exist are listed below. Iterating `void` over
both orientations would fail, because that portrait source was removed.

```bash
python3 reexport.py
```

```python
# reexport.py
from PIL import Image

SOURCES = {
    "aurora": ("portrait", "landscape"),
    "zen":    ("portrait", "landscape"),
    "void":   ("landscape",),   # portrait source removed
}
FOLDER = {"portrait": "drawable-nodpi", "landscape": "drawable-land-nodpi"}

for name, sizes in SOURCES.items():
    for size in sizes:
        src = f"design-src/wallpapers/{name}-{size}.webp"
        dst = f"app/src/main/res/{FOLDER[size]}/luma_wall_{name}.webp"
        Image.open(src).convert("RGB").save(dst, "WEBP", quality=84, method=6)
```

## Art direction rules

1. Keep a quiet central band for icons and the clock.
2. Compose portrait and landscape separately; never crop one from the other.
3. Declare the artwork's lightness in `WallpaperArt` — the image is never tinted at
   runtime.
4. Set the focal point and scrim strengths in `WallpaperArt`, not in the view.

The packaged assets are the artefacts of record. For aurora and zen both
orientations can still be re-cropped from source; for void only the landscape
can, and the 30 Studio compositions have no sources here at all.
