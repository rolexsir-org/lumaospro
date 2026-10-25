# Design sources

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
