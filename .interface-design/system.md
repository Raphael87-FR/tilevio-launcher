# Tilevio Design System

## Direction

**Personality:** Artwork-first, quiet, and cinematic. The TV viewer should recognize and open an App with minimal reading and no unnecessary navigation.

**Foundation:** The configurable Wallpaper is the canvas. App artwork supplies the color; Tilevio adds no brand palette or decorative background to the home screen.

**Depth:** Flat at rest. No Tile shadows, permanent frames, or card surfaces. A white outline communicates remote focus.

**Signature:** Android TV App banners fill borderless 16:9 Tiles directly, with the original App icon as an undistorted fallback.

## Tokens

### Spacing

Base: 4dp

| Token | Value | Use |
| --- | --- | --- |
| `space-micro` | 4dp | Small optical adjustments |
| `space-inline` | 8dp | Compact inline gaps |
| `space-control` | 12dp | Control-internal spacing |
| `space-component` | 16dp | Component padding |
| `space-header` | 24dp | Header outer padding |
| `space-grid-gutter` | 24dp | Horizontal and vertical Tile gaps |
| `space-grid-margin` | 32dp | Minimum grid outer margin |

### Color

| Token | Value | Use |
| --- | --- | --- |
| `wallpaper-default` | `#171717` | Existing default Wallpaper; user-configurable |
| `foreground-on-wallpaper` | `#FFFFFF` | Clock and essential home-screen text |
| `tile-surface` | Transparent | No added color behind App artwork |
| `tile-border-resting` | Transparent | No permanent Tile frame |
| `tile-border-focused` | `#FFFFFF` | Remote focus indication |
| `tile-border-focus-contrast` | `#171717` | Inner contrast edge on light App artwork |
| `settings-surface-resting` | Transparent | Gear without a permanent container |
| `control-surface` | `#292929` | Settings option and action container |
| `control-border-resting` | `#666666` | Settings control at rest (1dp) |
| `control-border-focused` | `#FFFFFF` | Settings control focused or selected (3dp) |

App banners and icons retain their original colors. Do not assign per-App brand colors or place artwork on invented card backgrounds.

### Shape and depth

| Token | Value |
| --- | --- |
| `tile-corner-radius` | 12dp |
| `tile-focus-outline` | 4dp white + 2dp inner contrast edge |
| `tile-shadow` | None |
| `tile-resting-frame` | None |
| `control-corner-radius` | 8dp |

### Typography

Use the existing Android system sans-serif family; add no font dependency.

| Token | Value | Use |
| --- | --- | --- |
| `clock-style` | 20sp, medium weight, tabular numerals | Top-right clock |
| `home-app-label` | Hidden visually; accessible App name retained | Tiles |
| `home-title` | None | No "App" heading |

The clock follows the device's 12-hour or 24-hour preference and displays no seconds.

## Layout

- Use exactly five grid columns on TV screens.
- Derive Tile width from the available width; every Tile has a 16:9 layout box.
- Center the grid in the home-screen body rather than attaching it directly below the header.
- Preserve regular 24dp gutters and at least 32dp outer margins.
- Keep the final row incomplete when necessary; never stretch Tiles to fill it.
- Add vertically scrollable rows for larger catalogs without shrinking Tiles.
- Place only the clock and Settings action at the top right. The clock is not focusable.

## Component Patterns

### App Tile

- Prefer the App's Android TV banner.
- Fit artwork without stretching or cropping. Native 16:9 banners fill the Tile.
- If no banner exists, center the original icon with transparent space around it.
- Clip artwork to the Tile's rounded shape, without internal artwork padding.
- Add no background, shadow, visible resting border, or visible App label.
- Keep the App name as the accessibility description.
- Show a 4dp white outline with a 2dp anthracite inner contrast edge on focus and preserve remote activation.

### Header clock

- Show the current local time in the system 12-hour or 24-hour format.
- Update only while the home screen is visible, aligned to minute changes.
- Use no resident service or background polling.

### Settings action

- Show the gear without a permanent card or background.
- Preserve its accessible name and remote activation.
- Provide a clear focus treatment when selected.

### Wallpaper

- Preserve the existing solid-color and imported-image choices.
- Do not bundle or impose the landscape from the visual reference.
- Keep essential text and focus indicators legible over the selected Wallpaper.

## Motion and Resource Use

- Add no decorative Tile animations or animated Wallpaper.
- Preserve lazy grid rendering and bound decoded artwork dimensions.
- Add no image-loading library, resident service, or unrelated background work.

## Decisions

- 2026-10-27: Adopted the five-column, banner-led home screen and restrained clock/Settings header from the validated design discussion. Full product and testing contract: GitHub issue #14.
