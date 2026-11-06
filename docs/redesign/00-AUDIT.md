# Aurora redesign — initial audit

Date: 2026-11-06  
Repository: Abdellah-mouida/NewPipe  
Base branch: dev  
Working branch: redesign/premium-ui

## Scope and architectural decision

The supplied redesign brief describes a Views/XML Android app. The repository confirms that the primary Android media application is still the legacy Java/Kotlin + Android Views/XML app in :app, and its main shell is org.schabi.newpipe.MainActivity. The repository also contains a newer Kotlin Multiplatform Compose module in :shared (Compose Material 3, Navigation 3, Koin) and a desktop app. The redesign will therefore preserve the actual app architecture: restyle the existing Android Views/XML surfaces and existing theme helpers; improve shared Compose UI where it is already used; do not migrate the main app to Compose or replace its navigation/player architecture.

No dependency additions are planned.

## Build facts

| Item | Repository value |
|---|---|
| Gradle wrapper | 9.8.2 |
| Android Gradle Plugin | 9.5.0 |
| Kotlin | 2.4.21 |
| Java toolchain | 21 |
| Modules | :app, :shared, :desktopApp, plus :buildSrc |
| compileSdk | 37 |
| minSdk | 23 |
| targetSdk | 35 |
| Android Material Components | 1.11.0 |
| AppCompat | 1.8.0 |
| AndroidX Preference | 1.2.1 |
| RecyclerView | 1.4.0 |
| ConstraintLayout | 2.2.2 |
| Coil | 3.6.3 (Compose/network integrations are already declared) |
| Player | ExoPlayer 2.19.1 |
| View binding | Enabled in :app |
| Shared UI | Compose Multiplatform 1.12.1 / Material 3 1.11.0-alpha07 |
| Shared navigation | Navigation 3 1.1.2 |

SDK constants are in buildSrc/src/main/kotlin/ProjectConfig.kt. Android build configuration and dependencies are in app/build.gradle.kts; multiplatform configuration is in shared/build.gradle.kts; dependency versions are centralized in gradle/libs.versions.toml.

## Theme system

### Android Views application

- Theme resources live primarily in app/src/main/res/values/styles.xml, with service-specific child themes in styles_services.xml.
- Legacy theme colors and aliases live in colors.xml, service brand colors in colors_services.xml, and night-mode overrides in values-night/colors.xml.
- ThemeHelper applies Light, Dark, Black, and Auto/device modes. It reads the existing preference keys and values from resources and SharedPreferences; those keys and values must remain unchanged.
- Service-specific theme styles currently override primary/accent colors for YouTube, SoundCloud, PeerTube, media.ccc.de, and Bandcamp.
- MainActivity.onCreate applies AppCompat day/night mode and the selected service theme before super.onCreate. onResume consumes the existing theme-change flag and recreates the activity.
- Special surfaces include player/queue, SettingsActivity, AboutActivity, ErrorActivity, ReCaptchaActivity, file picker, and RouterActivity themes. They must be audited separately; global colors alone do not prove every one is themed.

### Compose UI in :shared

- AppTheme and currentColorScheme are in shared/src/commonMain/kotlin/net/newpipe/app/theme/Theme.kt.
- Palette definitions are in theme/Color.kt; spacing tokens are in theme/Dimens.kt.
- The Compose theme already observes the persisted theme and night-theme preferences. Black mode currently copies the dark scheme and changes only its surface, so a complete AMOLED scheme is a concrete follow-up.
- Existing Compose settings screens include appearance, settings home, video/audio, player, downloads, history, and debug; About and license screens also exist. These are not the primary Android home/feed shell.

## Navigation and player architecture

- Android shell: app/src/main/res/layout/activity_main.xml, MainActivity.java, toolbar_layout.xml, drawer_layout.xml, and fragment_main.xml.
- Main navigation currently uses a Material NavigationView drawer. Drawer destinations include Subscriptions, Feed, Bookmarks, Downloads, History, service-provided kiosks, Settings, Donation, and About. Service switching is managed in the drawer header.
- Main content tabs use ScrollableTabLayout and a ViewPager inside MainFragment; tabs remain customizable through existing settings.
- Fragment navigation is centralized in NavigationHelper; MainActivity routes drawer selection to existing helper methods. Preserve these paths and every old destination.
- The in-app video detail / mini-player is hosted in fragment_player_holder using the existing custom BottomSheetBehavior. The playback service, queue, media session, popup, and background playback logic must not be modified by visual work.
- A floating replacement navigation bar is not yet wired. It must not be introduced until all destinations, fragment visibility, bottom insets, mini-player stacking, and tablet/TV behavior can be handled without hiding content or breaking Back.

## Destination and component inventory (static tree inspection)

The Android app contains 113 layout XML files and 58 Activity/Fragment source classes. Key destination groups and known source/layout anchors:

| Destination group | Source anchors | Layout/resource anchors |
|---|---|---|
| Main shell / Home / customizable tabs / kiosks | MainActivity, MainFragment, DefaultKioskFragment, KioskFragment | activity_main.xml, fragment_main.xml, toolbar_layout.xml, drawer_layout.xml |
| Search and suggestions | SearchFragment | fragment_search.xml, item_search_suggestion.xml, toolbar_search_layout.xml |
| Subscriptions and feed | SubscriptionFragment, FeedFragment | fragment_subscription.xml, fragment_feed.xml, feed_group_*.xml |
| Channel and channel tabs | ChannelFragment, ChannelTabFragment, ChannelAboutFragment | fragment_channel.xml, fragment_channel_tab.xml, fragment_channel_videos.xml, list_channel_*.xml |
| Video detail, description, related, comments | VideoDetailFragment, DescriptionFragment, RelatedItemsFragment, CommentsFragment, CommentRepliesFragment | fragment_video_detail.xml, fragment_description.xml, fragment_related_items.xml, fragment_comments.xml, comment_replies_header.xml |
| Remote/local playlists and bookmarks | PlaylistFragment, BookmarkFragment, LocalPlaylistFragment | fragment_playlist.xml, fragment_bookmarks.xml, list_playlist_*.xml, list_stream_playlist_*.xml |
| History | StatisticsPlaylistFragment | local list/playlist item layouts |
| Downloads and download manager | DownloadActivity and download manager services | activity_downloader.xml, mission_item*.xml, missions.xml, download_*.xml |
| Settings and preference sub-screens | SettingsActivity, MainSettingsFragment, AppearanceSettingsFragment, ContentSettingsFragment, VideoAudioSettingsFragment, PlayerSettingsFragment, DownloadSettingsFragment, HistorySettingsFragment, NotificationSettingsFragment, UpdateSettingsFragment, BackupRestoreSettingsFragment, ExoPlayerSettingsFragment, DebugSettingsFragment | res/xml/*settings*.xml, appearance_settings.xml, preference styles |
| Import/export, backup/restore, selectors | SubscriptionsImportFragment, SelectChannelFragment, SelectFeedGroupFragment, SelectKioskFragment, SelectPlaylistFragment, PeertubeInstanceListFragment | fragment_import.xml, fragment_instance_list.xml, dialog_feed_group_*.xml, selector/list layouts |
| About, licenses, software components | AboutActivity plus Compose About/License screens | About/Compose resources and software component row layouts |
| Error, crash, reCAPTCHA, routing, file picker | ErrorActivity, ReCaptchaActivity, RouterActivity, ExitActivity, FilePickerActivityHelper | activity_error.xml, activity_recaptcha.xml, error_panel.xml, file-picker/router styles |
| Player, queue, popup/background controls | PlayerService, PlayQueueActivity, player fragments/views | activity_player_queue_control.xml, player controls/drawables and fragment_player_holder |
| Notifications and widgets | notification worker / player and download notification code | notification layouts, media/session styles, widget resources |

This is a source-tree inventory, not a claim that every individual dialog/menu/widget has already been exhaustively behavior-tested. The progress ledger tracks the work by visible screen group and explicitly leaves unverified surfaces incomplete.

## Reusable components identified

- Android: BaseFragment, BaseListFragment, BaseListInfoFragment, BaseLocalListFragment, NavigationHelper, ThemeHelper, existing NewPipe custom views, RecyclerView adapters/holders, Material Components, ConstraintLayout, Coil and existing shape/selector/progress drawables.
- Compose: shared TopAppBar, PreferenceRow, ListPreference, SwitchPreference, AppTheme, theme preview provider, Navigation 3 and lifecycle-aware view models.
- Existing component infrastructure should be styled/reused before new wrappers or custom controls are added.

## Initial visual/theme risk list

1. Service-specific child styles currently replace primary/accent colors, which can undermine a unified product accent unless repointed deliberately.
2. Legacy layouts include direct colors and theme bypasses (e.g. duration badges, tab icons/tints, selected backgrounds, and older drawables); each must be audited as it is touched.
3. values-night/colors.xml separately changes default icon/contrast colors; aliases must preserve readable night behavior.
4. The player sheet shares the main CoordinatorLayout. Any floating bar must be coordinated with the sheet's peek/expanded states and must not cover its controls.
5. Main-page tab styling currently hardcodes white tab icon/ripple/indicator tints, which will fail on a light or subdued surface.
6. Activity-specific themes (dialogs, file picker, router, player and Settings) do not all inherit the same style path.
7. The Compose module has its own color scheme and persisted preference observer; Android Views and Compose must remain visually consistent without changing preference keys.
8. RecyclerView item animations, image clipping, and overlay elevation need to stay lightweight on API 23 and low-end devices.

## Initial migration map

| Existing entry | Aurora location / treatment |
|---|---|
| Home / main-page tabs / kiosks | Home surface with preserved customizable tabs and kiosk selection |
| Subscriptions | Primary navigation destination; retain existing fragment flow |
| Feed | Subscriptions/More entry; keep direct route reachable |
| Bookmarks / local playlists | Library destination |
| History | Library / More entry; keep direct route reachable |
| Downloads | Primary navigation destination; preserve DownloadActivity |
| Services / PeerTube instance chooser | More / service switcher |
| Settings and every settings sub-screen | More / Settings; preserve existing PreferenceFragments and Compose screens |
| Donation, About, licenses, help | More / About and secondary pages |
| Search | Prominent toolbar/search entry; preserve SearchFragment |
| Video detail and player modes | Existing detail sheet, full player, popup, background and queue; visual-only changes |

## Audit limitations

GitHub source inspection is available, but this session does not provide a checked-out working tree, Android SDK/emulator, or a Gradle execution environment. Build, lint, screenshot, instrumentation, device behavior, accessibility runtime, and playback regression checks are therefore not yet run. Do not treat source inspection as build/runtime verification.
