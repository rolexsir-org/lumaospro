# LumaOS Design System v3.2 — source of truth

This directory holds the authored design bundle that the Kotlin token layer is
generated from. It is checked in on purpose: a design system that lives only in a
shared drive cannot be reviewed in a pull request, and the app's tokens would
drift from the design's tokens with nothing to catch it.

## Provenance

| | |
| --- | --- |
| Bundle | `design.zip` from the project owner's Google Drive (`LumaOS-Design-System`) |
| Uploaded | 2026-10-26 14:43 UTC |
| SHA-256 of the zip | `bc338391396d9c6784395ec33537681156e6032a28f8963aba52f1fc277b53a8` |
| Design-system version | `3.2` (token export timestamp `2026-10-26T14:20:38.398Z`) |
| Files copied here | `SPEC.md`, `tokens.json`, `tokens/{signature,atmosphere,monolith}.json` |
| Files **not** copied here | the studio build (`LumaOS-Design-System.html`, `index.html`, `app.js`, `style.css`) — ~700 KB of interactive design tooling, not product source |
| Studio copy kept in the repo | `design-src/v3.2/LumaOS-Design-System.html` (single-file build only, so the design is browsable at the tagged revision) |

Nothing else from the bundle was edited. Hex values, alpha values, radii, blur,
saturation, type sizes, spacing and easing curves below are exactly as authored.

## What the bundle contains

| File | Use |
| --- | --- |
| [`SPEC.md`](SPEC.md) | The written specification: principles, themes, tokens, iconography, screens, widgets, states, accessibility, Android notes, QC gate |
| [`tokens/signature.json`](tokens/signature.json) | Implementation tokens for **Signature** |
| [`tokens/atmosphere.json`](tokens/atmosphere.json) | Implementation tokens for **Atmosphere** |
| [`tokens/monolith.json`](tokens/monolith.json) | Implementation tokens for **Monolith** |
| [`tokens.json`](tokens.json) | Raw computed dump from the running studio (all three themes in one document) |

## How it is wired into the app

| Design concept | Kotlin |
| --- | --- |
| Token values (colour, shape, material, type, spacing, motion) | `app/src/main/java/com/lumaos/pro/launcher/ui/theme/LumaDesignTokens.kt` |
| Three identities, seeds, default backdrops | `ThemePresets.SIGNATURE_THEME / ATMOSPHERE_THEME / MONOLITH_THEME` |
| Authored colours → resolved palette | `ThemeEngine.authoredColors()` → `assemble()` |
| Radius roles (`r-xl`, `r-lg`, … , dock, tile) | `ResolvedTheme.radius(RadiusRole…)` |
| Blur and saturation as material values | `ResolvedTheme.blurDp`, `blurFactor`, `saturation`; consumed by `HomeActivity.applyDepth()` and `LumaComponents` |
| Label sizes per theme | `ResolvedTheme.appLabelSp` → `CellConfig.labelSizeSp` |
| Voices (`Luma Display / Text / Mono`) | `TypeStyle.PRECISION`, `AMBIENT`, `MONOGRAPH` |
| Motion characters | `MotionCharacter.SUBTLE / CINEMATIC / EXACT` and their profiles |

### Naming note: "Luma Sans" and "Luma Mono"

The specification names its faces **Luma Display**, **Luma Text** and **Luma Mono**.
No such font files exist in the bundle, and the app must not reference an
unbundled font. The mapping used — and documented in `TypeSystem.kt` — is:

* Luma Display / Luma Text → **Inter** (bundled, SIL OFL 1.1), the family the
  specification's letter-spacing and weights were drawn against;
* Luma Mono → the **platform monospace** face (Monolith labels and numerals).

If the publisher licenses actual Luma Display/Text/Mono files, dropping them into
`res/font/` and adding entries to `FontFamilyId` is the only change required; the
token layer is family-agnostic.

## How the tokens reach the app

| Token | Kotlin | Reaches |
| --- | --- | --- |
| colour roles | `LumaDesignTokens.<THEME>.colors` | `ThemeEngine.authoredColors` → `ThemeColors`; `ok`/`warn` continue on as `successSeed`/`warningSeed` |
| radii, tile | `TokenShape` | `ResolvedTheme.radius(RadiusRole)`; `LumaComponents.shapeScale` scales the older component tokens |
| blur, saturation | `TokenMaterial` | `ResolvedTheme.blurDp / blurFactor / saturation`; home + drawer blur, effects policy |
| type ladder | `LumaDesignTokens.TYPE_SCALE` | `ResolvedTheme.typeScale` (the clock's own size is `clockSp`) |
| spacing | `SPACING_DP`, `spacing(step)` | `LumaSpacing` (Compose `Dp`) and the View layout maths |
| motion | `LumaDesignTokens.Motion` | `LumaMotionProfiles` (`cinematic`, `exact`), `ResolvedTheme.motionProfile` |

Clock weight and tracking are **not** duplicated in the token object: they stay with
the type voice, which already owned them. Splitting them would let two sources of
truth drift.

## Two findings in the authored tokens

Both were found by measuring every authored pair rather than by eye. They are
reported here rather than silently "fixed", because the authored values are the
design's decision:

1. **Monolith `text-2` is 6.8:1 against `surface-2`**, slightly under the
   specification's own `text-2 ≥ 7:1` promise (§3.1). It still clears the engine's
   enforced floor (4.5:1) and is shipped as authored.
2. **Monolith `text-3` is 3.4:1 against `surface-1`**, under the specification's
   stated `text-3 ≥ 4.6:1` (§3.1). Signature measures 4.1:1 and Atmosphere 4.4:1
   for the same role, so the shortfall is not Monolith-only — the authored
   `text-3` greys are all under 4.6:1 on their own surfaces.

Both are one-line changes in `tokens/*.json` plus `LumaDesignTokens.kt` (the parity
test fails until both are updated together). Until then the app keeps the authored
values and the engine keeps its enforced floors.

## Release

This is the design system **shipped in LumaOS Pro 1.0.0** (`versionCode` 100).
Version format: `versionName` `major.minor.patch`; `versionCode` is
`major * 100 + minor * 10 + patch`.

## What this adoption does **not** claim

The bundle specifies a great deal more than a token layer: 26 screens, eight
widget anatomies, Quick Shelf states, Focus Spaces, a seven-section Studio, a
premium plan, and Glance notification surfaces. This repository now shares the
bundle's **tokens, themes, typography, geometry, material and motion**. Screens
that do not exist yet in the app are not implemented and are not claimed; see
`docs/DESIGN_SYSTEM.md` §14 and `docs/QA_CHECKLIST.md` for exactly what is and is
not verified.
