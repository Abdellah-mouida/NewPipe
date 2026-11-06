# Aurora redesign — implementation report

Date: 2026-11-06  
Repository: Abdellah-mouida/NewPipe  
Working branch: redesign/premium-ui  
Base: dev  
Current status: **Partial implementation — not ready to merge as a completed redesign**

## What is implemented

### Audit and continuity

- Added `docs/redesign/00-AUDIT.md` with the repository architecture, build facts, theme system, navigation/player structure, destination inventory, migration map, reusable components and risk list.
- Added `docs/redesign/PROGRESS.md` as a screen-level ledger. It deliberately leaves unimplemented surfaces marked Not started or In progress.
- Kept the current Android app architecture: the primary media app remains Java/Kotlin + Views/XML; the newer `:shared` Kotlin Multiplatform Compose module is retained and its theme palette is aligned. No navigation, player or extractor rewrite was made.

### Aurora visual foundation

- Added the Aurora light, dark and AMOLED color palettes in `app/src/main/res/values/np_colors.xml`.
- Added 24 semantic theme attributes and mapped them in the existing Light, Dark and Black theme styles. Existing saved theme preference values/keys and Auto behavior were not changed.
- Repointed legacy Light/Dark/Black colors and the service-specific primary/accent color values to the Aurora palette. Existing service-specific style inheritance remains.
- Added reusable spacing, radius, motion and typography resources: `np_dimens.xml`, `np_motion.xml`, `np_type.xml`.
- Added standard and emphasized-decelerate interpolators and a shared press-scale state animator.
- Reworked the Compose palette and expanded the black scheme to use a deeper AMOLED surface stack.

### Existing Android surfaces refined

- Main activity window background and toolbar use the theme background instead of a service-colored toolbar.
- Main-page tab surface, icons, ripple and selected indicator now use theme attributes rather than hardcoded white.
- Drawer item icon/text colors use the theme's foreground token.
- Video, channel and playlist card/grid/list/mini layout variants received the first Aurora styling pass: rounded thumbnail clipping, rounded duration overlays, consistent text appearances and subtle press-scale feedback.
- Existing IDs, item binding structure and click targets were retained in the edited layouts.

## Current token snapshot

| Theme | Background | Surface 1 | Surface 2 | Surface 3 | Outline | Accent | Accent container |
|---|---|---|---|---|---|---|---|
| Dark | #0E1014 | #151821 | #1B1F2A | #232837 | #2C3242 | #8B7BFF | #2A2459 |
| AMOLED | #000000 | #08090C | #0F1115 | #161922 | #1E222C | #9A8CFF | #1D1847 |
| Light | #F7F8FB | #FFFFFF | #F0F2F7 | #E6E9F1 | #DDE1EB | #5B3DF5 | #E6E1FF |

Gradient accents:
- Dark: #8B7BFF → #5B8CFF → #22D3EE
- AMOLED: #9A8CFF → #62A0FF → #2BE0F5
- Light: #5B3DF5 → #3B82F6 → #06B6D4

Shared semantic states: success #3DD68C, warning #FFB454, error #FF6B7A, live #FF4D5E.

## Existing destination map

This phase does **not** replace the current navigation. Existing entries remain accessible through the unchanged NavigationHelper and MainActivity drawer flow.

| Existing destination | Planned Aurora placement | Implementation status |
|---|---|---|
| Home / main tabs / kiosks | Home | Current tabs restyled; shell redesign incomplete |
| Subscriptions | Primary destination | Not yet moved to bottom navigation |
| Feed | Subscriptions / More | Existing route retained; no new shell mapping implemented |
| Bookmarks / local playlists | Library | Existing route retained; no new shell mapping implemented |
| History | Library / More | Existing route retained; no new shell mapping implemented |
| Downloads | Primary destination | Existing route retained; visual redesign pending |
| Services / PeerTube instance selection | More / service switcher | Existing drawer service switching retained |
| Settings and sub-screens | More / Settings | Pending |
| Donation / About / licenses / help | More / About | Pending |
| Search | Prominent Home / toolbar search | Existing search flow retained; screen redesign pending |
| Video detail / player modes | Existing detail sheet and player hosts | No playback or player UI changes in this phase |

Floating bottom navigation, More sheet and rail are deferred until fragment-stack visibility, mini-player stacking, content insets, RTL, tablet and TV behavior can be safely integrated.

## Assets and dependencies

- New assets in this phase are self-authored XML resources (color palettes, shapes, typography styles, interpolators and animator).
- No external font, icon pack, network-hosted asset or third-party runtime asset was added.
- No Gradle dependency, application ID, package name, signing configuration, SDK level, extractor, database schema, networking or playback implementation was changed.
- APK size delta: **not measured**; no APK was built.

## Verification

| Check | Result | Evidence / limitation |
|---|---|---|
| Branch and change inventory | PASS | GitHub reports branch `redesign/premium-ui` is 4 commits ahead of `dev`, with 38 changed files |
| New token and layout references | Source-inspected | New color references resolve against committed palette and legacy color resources; this is not an AAPT compile |
| `./gradlew assembleDebug` | NOT RUN | No repository checkout, Gradle runner or Android SDK is exposed in this connected GitHub session |
| Release/minified R8 build | NOT RUN | Same environment limitation |
| Android lint / Checkstyle / ktlint | NOT RUN | Same environment limitation |
| Unit tests / instrumentation | NOT RUN | No test runner or emulator available |
| Install / launch / playback regression | NOT RUN | No Android device or emulator available |
| Theme screenshot matrix | NOT RUN | No emulator screenshot capability |
| RTL / font scaling / tablet / TV / accessibility | NOT RUN | Requires device/emulator validation |
| Low-end scrolling and animation performance | NOT RUN | Requires runtime profiling |

No CI statuses were returned for the current feature head. Source inspection must not be mistaken for a successful build or runtime verification.

## Remaining work

1. Complete the main shell and implement floating bottom navigation / large-screen rail only after resolving mini-player stacking and shared bottom insets.
2. Redesign Search, Home feed, subscriptions/feed groups, channel, playlist, library, history and downloads states and controls.
3. Redesign video detail, full player, popup player, queue and notifications without touching playback behavior.
4. Restyle all settings screens, backup/restore, import/export, About/licenses, pickers, error/reCAPTCHA and special dialogs/menus.
5. Implement consistent page/content transitions, system reduced-motion and Battery Saver behavior, and first-load-only list entrance motion.
6. Finish dark/AMOLED/light contrast, RTL, font-scale, landscape, tablet, TV, accessibility and performance passes.
7. Build debug and release, run project lint/static analysis/tests, install and capture the requested theme/screen matrix; update the ledger with actual results.

**Merge recommendation:** Do not merge this branch as the finished redesign yet. It is a source-level foundation and first component pass, with the remaining scope explicitly tracked in `PROGRESS.md`.
