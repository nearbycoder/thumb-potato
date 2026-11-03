# Thumb Potato — Design System

Thumb Potato is a premium "play later" companion for gamers: a backlog, a release tracker and a session planner for Switch, PlayStation, Xbox, PC, Mac and mobile. The look is **cinematic, confident and quiet**: box art is the hero and the chrome recedes. Reference points: the Apple TV app, Letterboxd, Backloggd, the Steam Deck library. No cartoon mascots in the UI, no emoji in labels, no tilted "sticker" cards, no hard offset shadows, no pixel/novelty fonts, no RGB-gamer neon overload.

It is the sibling of [Couch Potato](https://github.com/nearbycoder/couch-potato) and shares its structure; the accent, status colors, moods and icon are what make it its own. The same system is used by the Apple app (SwiftUI), the Android app (Jetpack Compose / Material 3) and the website.

## Color

Dark is the primary appearance; light is a first-class alternative. Both must look finished.

| Token | Dark | Light |
|---|---|---|
| `bg` | `#0B0C10` | `#F4F5F7` |
| `surface` | `#14151B` | `#FFFFFF` |
| `surfaceElevated` | `#1C1D25` | `#FFFFFF` + soft shadow |
| `hairline` | `rgba(255,255,255,0.08)` | `rgba(0,0,0,0.08)` |
| `textPrimary` | `#F4F5F7` | `#111114` |
| `textSecondary` | `rgba(244,245,247,0.62)` | `rgba(17,17,20,0.62)` |
| `textTertiary` | `rgba(244,245,247,0.40)` | `rgba(17,17,20,0.40)` |
| `accent` ("Volt") | `#B8F23D` | `#4F8A00` |
| `onAccent` | `#122000` | `#FFFFFF` |
| `success` ("Trophy") | `#FFC54D` | `#B7800F` |
| `danger` | `#F2545B` | `#D63E45` |
| `info` | `#5B8CFF` | `#2F6FE0` |

Backlog statuses: Want to Play `info`, Playing `accent`, Beaten `success` (trophy gold), Shelved `textTertiary`.

Platform brand colors are used only as a small tile, dot or monogram background — never full-bleed chips:

| Platform | Color | Monogram |
|---|---|---|
| Nintendo Switch 2 | `#E60012` | `NS2` |
| Nintendo Switch | `#E60012` | `NS` |
| PlayStation 5 | `#0070D1` | `PS5` |
| PlayStation 4 | `#003791` | `PS4` |
| Xbox Series X\|S | `#107C10` | `XS` |
| Xbox One | `#0E6A0E` | `XB1` |
| PC | `#2A475E` | `PC` |
| Mac | `#A2AAAD` | `MAC` |
| iOS & Android | `#5E5CE6` | `MOB` |

Stores (Where to Play) use monograms on their own colors: Steam `#1B2838`, Epic Games Store `#2F2F2F`, GOG `#86328A`, PlayStation Store `#0070D1`, Xbox Store `#107C10`, Nintendo eShop `#E60012`, App Store `#0A84FF`, Google Play `#01875F`, Humble `#CC2929`, Fanatical `#FF9800`, Green Man Gaming `#00C25E`, Ubisoft Store `#0070FF`.

Mood duotones (gradient top-left → bottom-right):

| Mood | From | To | SF Symbol / Material icon |
|---|---|---|---|
| Cozy | `#E09A62` | `#5A2E1A` | `cup.and.saucer` / `local_cafe` |
| Adrenaline | `#E2473F` | `#3B0B0B` | `bolt` / `bolt` |
| Epic | `#5B7FE8` | `#121C4A` | `crown` / `castle` |
| Couch Co-op | `#F0C94A` | `#5C4300` | `person.2` / `group` |
| Spooky | `#8A5CF5` | `#22104D` | `moon.stars` / `dark_mode` |
| Brainy | `#3DBB9E` | `#0B3A33` | `puzzlepiece` / `extension` |

## Typography

System fonts only: SF Pro on Apple, the Material 3 default on Android, Inter on the web.

| Role | Size / weight |
|---|---|
| Large title | 34 / bold, tight tracking |
| Title 1 | 28 / bold |
| Title 2 | 22 / semibold |
| Section header | 20 / semibold, trailing "See All" 15 / medium in `accent` |
| Headline | 17 / semibold |
| Body | 17 / regular |
| Callout | 15 / regular |
| Footnote | 13 / regular, `textSecondary` |
| Caption | 12 / medium, `textSecondary` |

## Shape and depth

- **Box art is portrait 3:4** (game cases), not the 2:3 movie poster. Frame every cover to 3:4 with `fill` cropping so grids stay even; Steam's 600×900 capsules crop fine.
- Cover corner radius: 8 (thumb), 10 (row), 14 (featured). Cards 16. Sheets 24. Buttons: capsule.
- Every artwork gets a 1 px `hairline` border.
- Shadows are soft and low: dark `0 8px 24px rgba(0,0,0,0.45)`, light `0 6px 20px rgba(0,0,0,0.10)`.
- No rotation, no tilt, no hard offset shadows.
- Backdrops (Steam hero art or screenshots; otherwise a heavily blurred cover) fade into `bg` with a vertical gradient (clear → bg over the bottom 55 %).

## Components

- **Hero carousel**: edge-to-edge backdrop (16:9 on wide layouts, 4:5 on phones), gradient fade, bottom-aligned Title 1, metadata line `2025 · Action, RPG · Metacritic 92`, platform monogram row, primary button (accent capsule, "Add to Backlog" / "In Backlog") and secondary button (translucent capsule, "Details"). When a game only has a cover, wide layouts put the text in its own column beside the cover; never draw text over box art. Page indicator dots.
- **Cover row**: section header + horizontally scrolling covers. Cover width 120 (phone), 150 (tablet/desktop). Caption: title 15 semibold one line, subtitle 13 `textSecondary` (year · first genre) and up to three platform dots.
- **Cover grid**: adaptive, 100–110 min width on phones, 150–170 on larger screens, 12–16 gutter, equal columns.
- **Platform tile**: 36 pt rounded-square monogram on brand color; name beneath in Caption.
- **Store row** (Where to Play): monogram tile, store name, price (strike-through regular price + sale price in `accent` when discounted), chevron. Rows grouped "Your platforms" then "Other stores".
- **Status badge**: pill, Caption, colored dot + label.
- **Rating**: 5 stars in `accent`, 24 pt interactive, 14 pt display. Critic score shown as a small rounded square `92` colored by band (≥ 75 `accent`, 50–74 `#F0C94A`, < 50 `danger`).
- **Buttons**: primary = accent fill, `onAccent` text; secondary = `white 12 %` (dark) / `black 6 %` (light) fill; tertiary = text in accent. Height 44 on touch, 32–36 on desktop. Use native Liquid Glass / Material tonal styles where the platform offers them.
- **Mood tiles**: 2 columns on phones, 3–4 on wide layouts, 16:10 (max height ~130 pt on wide layouts), duotone gradient, 24 pt outlined symbol top-left, title bottom-left in white Title 2.
- **Shuffle**: a stack of covers that cycles quickly and decelerates to a stop. The winner expands into a card with a blurred backdrop, title, metadata and actions: "Play now" (marks Playing), "Plan a session", "Shuffle again". Filters: pool (Backlog / Discover), mood, session length (Quick break · An hour · Long session), platform. Scale the deck to the available height.
- **Planner**: week strip with 7 columns; the selected day is an accent circle; days with plans get a 4 pt dot. Agenda cards: cover thumb, title, time range, platform dot, calendar/reminder indicators, done checkbox. A "Release radar" section lists upcoming release dates of followed games.
- **Filter bar** (platform, mood, genre and "See All" pages): Sort (Popular, Newest, Top rated, A–Z), Released (Any, Upcoming, This year, Last 5 years, 2020s, 2010s, 2000s, Before 2000), Genre, Critic score (Any, 70+, 80+, 90+). Capsule menus in a horizontal row.
- **Empty states**: 40 pt symbol in `textTertiary`, Headline, Footnote, one primary button. Centered, max width 320.
- **Settings**: native grouped lists.

## Motion

200–350 ms ease-out for transitions; springs for interactive elements; hover scale 1.02 on desktop; respect reduce-motion everywhere.

## Voice

Concise and warm, no emoji. Feature names: **Tonight**, **Browse**, **Backlog**, **Planner**, **Shuffle**, **Moods**, **Release Radar**, **Where to Play**. "Spud" exists only as the app-icon mark and in the About screen.

## App icon

Deep charcoal gradient (`#1E2027` → `#0B0C10`), a minimal Volt (`#B8F23D`) game-controller silhouette whose left stick is a small potato-shaped thumb pad. Source: `design/AppIcon.svg`.
