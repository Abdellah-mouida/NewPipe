# Aurora redesign audit

## Scope and repository facts

- Repository: `Abdellah-mouida/NewPipe`, GPL-3.0, Android app module plus `shared` and `desktopApp` Gradle modules.
- Active work branch: `copilot-redesig`, based on `dev`.
- Android UI is implemented with Java/Kotlin, XML layouts, Fragments, AndroidX, and Material Components. Compose artifacts exist in the version catalog for other targets but the Android app uses Views/XML and ViewBinding.
- `app/build.gradle.kts` enables `viewBinding = true`; compile/min/target SDK are supplied via `NEWPIPE_VERSION_SDK_*` constants in build logic and are not directly declared in that file. Preserve these values and the existing Gradle configuration.
- Version catalog: AGP 9.5.0, Kotlin 2.4.21, Material Components 1.11.0, AppCompat 1.8.0, Preference 1.2.1, RecyclerView 1.4.0, ConstraintLayout 2.2.2, ExoPlayer 2.19.1, Coil 3.6.3. The app also uses AndroidX Fragment 1.9.1, Media 1.7.1, Groupie 2.10.1, Room 3.0.3. Image loading is Coil; player is ExoPlayer 2.19.1.
- No dependency changes are planned. Full SDK/constants discovery and local build availability remain to be verified.

## Existing theme engine

- Theme resources are primarily in `app/src/main/res/values/styles.xml`, with opening/splash night override in `values-night/styles.xml`; theme-specific colors live in `values/colors.xml` and attributes in `values/attrs.xml`.
- `ThemeHelper` resolves per-service Light, Dark and Black theme styles, supports Auto/device night behavior, and resolves dialog/settings themes. `MainActivity.onCreate` calls `ThemeHelper.setDayNightMode` and `ThemeHelper.setTheme` before `super.onCreate`; it recreates on `Constants.KEY_THEME_CHANGE` after preferences signal a theme change.
- Appearance preferences: `app/src/main/res/xml/appearance_settings.xml`. Stored key and values are string resources (`theme_key`, `default_theme_value`, light/dark/black/auto-device keys and values); exact strings are defined in the values resource set. Existing `black` value is the AMOLED compatibility path. Night preference remains separate.
- Existing special styles include OpeningTheme, light/dark/black app themes, service-specific theme variants, light/dark dialogs and min-width dialogs, settings themes, and translucent router themes. Manifest activities include MainActivity, PlayQueueActivity, SettingsActivity, AboutActivity, ErrorActivity, ReCaptchaActivity, DownloadActivity, RouterActivity and FilePickerActivityHelper, as well as no-display panic/exit activities. Activity-specific theme setup beyond inspected MainActivity still requires inventory.
- Risk: app-wide recreation and AppCompat day/night changes may affect fragment/player view lifecycle. Keep playback/service logic unchanged and do not claim uninterrupted playback without runtime testing.

## Navigation and player structure

- `activity_main.xml` uses `FocusAwareDrawerLayout` containing `FocusAwareCoordinator`, `fragment_holder`, included toolbar, `fragment_player_holder` using `CustomBottomSheetBehavior`, and included drawer layout.
- MainActivity builds drawer groups dynamically: subscriptions, feed, bookmarks, downloads, history; service-specific kiosk entries; settings, donation and about; service switcher is a drawer-header action. The service selection list is generated from NewPipe services and includes PeerTube instance selection.
- Main page content and main-tab customization are owned by existing MainFragment/settings behavior; kiosk and tab destinations are opened through `NavigationHelper`.
- Existing video detail is hosted in `fragment_player_holder` as a bottom sheet. MainActivity handles collapsed/hidden/expanded states and delegates back to player/detail fragments. Do not replace this mechanism until fully mapped and regression tested.
- Initial navigation inventory includes MainActivity, MainFragment, SearchFragment, VideoDetailFragment, CommentRepliesFragment, channel/playlist/kiosk/subscription/feed/bookmark/statistic fragments, settings fragments, about, download, error, reCAPTCHA, router, queue and file-picker surfaces. This inventory is not yet exhaustive; see ledger for outstanding audit.

## Initial layout / component inventory

- Shell: `activity_main.xml`, `toolbar_layout.xml`, `drawer_layout.xml`, `drawer_header.xml`, `fragment_main.xml`.
- Content: `fragment_search.xml`, `fragment_subscription.xml`, `fragment_feed.xml`, `fragment_kiosk.xml`, `fragment_channel.xml`, `fragment_channel_tab.xml`, `fragment_channel_videos.xml`, `fragment_playlist.xml`, `fragment_bookmarks.xml`, `fragment_video_detail.xml`, `fragment_description.xml`, `fragment_comments.xml`, `fragment_related_items.xml`, `fragment_empty.xml`.
- Reusable stream/channel/playlist/comment components: `list_stream_card_item.xml`, `list_stream_grid_item.xml`, `list_stream_item.xml`, `list_stream_mini_item.xml`, stream-playlist variants, `list_channel_card_item.xml`, `list_channel_grid_item.xml`, `list_channel_item.xml`, `list_channel_mini_item.xml`, playlist card/grid/list/mini variants, `list_comment_item.xml`, metadata layouts, chips, feed carousel/group layouts, and queue item layouts.
- Secondary surfaces include download missions and dialogs, import/select-channel/select-kiosk/select-playlist layouts, error panels, settings layouts and category headers, about/license resources, player and popup overlays, notification custom layouts, and extensive preference XML. A complete class-to-layout/dialog/menu/notification/widget map remains in progress; the tracker records categories not yet inventoried.

## Theme/color risks identified so far

- `colors.xml` contains separate legacy light/dark/black palettes and many direct-purpose colors, including red YouTube accents, grayscale card backgrounds, download colors, queue scrims, and duration/live badges.
- Theme attrs (`actionColor`, `separator_color`, `contrast_background_color`, `windowBackground`, `colorPrimary`, toolbar tint, card background/contrast, border) currently resolve directly to legacy palette colors.
- `styles.xml`, `values-night/styles.xml`, service themes, drawable XML, menu/icon tints, and Java/Kotlin custom paints need a repository-wide hardcoded-color pass; this first audit pass only inspected core theme and shell resources. Do not assume all literal colors have been found.
- Custom view risks include `FocusAwareDrawerLayout`, `FocusAwareCoordinator`, player gesture/bottom-sheet behavior and player overlays. Icon resources may embed colors; inspect before retinting.

## Navigation mapping (proposal; existing drawer remains until reachability is proven)

| Existing destination | Aurora location proposal | Current status |
| --- | --- | --- |
| Main page / customizable tabs / kiosks | Home; preserve configurable tabs and kiosk access | Not migrated |
| Search | Home search pill and top-level search action | Not migrated |
| Subscriptions | Subscriptions destination | Not migrated |
| Feed / channel groups | Subscriptions feed and group controls | Not migrated |
| Bookmarks / local playlists | Library | Not migrated |
| History / watch statistics | Library | Not migrated |
| Downloads | Downloads destination | Not migrated |
| Service switcher and PeerTube instances | More / service chip; preserve secondary drawer entry | Not migrated |
| Settings, backup/restore, About, donation/help | More surface; retain drawer until every action is reachable | Not migrated |
| Player queue, video detail, channel/playlist pages, error and utility activities | Existing navigation / contextual surfaces | Not migrated |

## Decisions and constraints

- Retain Views/XML, existing Fragment navigation, IDs, player service, database, extractor and existing dependencies.
- Do not remove the drawer before proving every current and dynamic service/kiosk destination is reachable. No runtime network assets or analytics are introduced.
- Current branch has not been built or run in this remote GitHub editing environment. The local Gradle/SDK/emulator toolchain is not available through the repository API; report runtime/build verification as not run unless a build environment becomes available.
- Audit is ongoing. Items not inspected are deliberately marked in progress rather than certified complete.
