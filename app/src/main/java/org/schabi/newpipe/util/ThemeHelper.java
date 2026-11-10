/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * ThemeHelper.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.info_list.ItemViewMode;

public final class ThemeHelper {
    private ThemeHelper() {
    }

    /**
     * Apply the selected theme (on NewPipe settings) in the context
     * with the default style (see {@link #setTheme(Context, int)}).
     *
     * ThemeHelper.setDayNightMode should be called before
     * the applying theme for the first time in session
     *
     * @param context context that the theme will be applied
     */
    public static void setTheme(final Context context) {
        setTheme(context, -1);
    }

    /**
     * Apply the selected theme (on NewPipe settings) in the context,
     * themed according with the styles defined for the service .
     *
     * ThemeHelper.setDayNightMode should be called before
     * the applying theme for the first time in session
     *
     * @param context   context that the theme will be applied
     * @param serviceId the theme will be styled to the service with this id,
     *                  pass -1 to get the default style
     */
    public static void setTheme(final Context context, final int serviceId) {
        context.setTheme(getThemeForService(context, serviceId));
        context.getTheme().applyStyle(getAccentColorStyle(context), true);
        updateLauncherIcon(context);
    }

    /**
     * Applies the appearance theme plus the user's selected accent to the settings activity.
     * Call this before the activity's super.onCreate().
     */
    public static void setSettingsTheme(final Context context) {
        context.setTheme(getSettingsThemeStyle(context));
        context.getTheme().applyStyle(getAccentColorStyle(context), true);
        updateLauncherIcon(context);
    }

    @StyleRes
    private static int getAccentColorStyle(final Context context) {
        final String key = context.getString(R.string.accent_color_key);
        final String accent = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(key, context.getString(R.string.default_accent_color_value));
        return switch (accent) {
            case "violet" -> R.style.AccentColorViolet;
            case "red" -> R.style.AccentColorRed;
            case "green" -> R.style.AccentColorGreen;
            case "blue" -> R.style.AccentColorBlue;
            default -> R.style.AccentColorBlue;
        };
    }

    /**
     * Switch the launcher alias so its background follows the selected app accent.
     * Semantic colors such as success and error remain independent of this preference.
     */
    public static void updateLauncherIcon(final Context context) {
        final String key = context.getString(R.string.accent_color_key);
        final String accent = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(key, context.getString(R.string.default_accent_color_value));
        final String selectedAlias = switch (accent) {
            case "violet" -> "LauncherViolet";
            case "red" -> "LauncherRed";
            case "green" -> "LauncherGreen";
            case "blue" -> "LauncherBlue";
            default -> "LauncherBlue";
        };

        final PackageManager packageManager = context.getPackageManager();
        final String[] aliases = {
                "LauncherBlue", "LauncherViolet", "LauncherRed", "LauncherGreen"
        };
        for (final String alias : aliases) {
            final ComponentName component = new ComponentName(
                    context.getPackageName(), context.getPackageName() + "." + alias);
            final int desiredState = alias.equals(selectedAlias)
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            if (packageManager.getComponentEnabledSetting(component) != desiredState) {
                packageManager.setComponentEnabledSetting(component, desiredState,
                        PackageManager.DONT_KILL_APP);
            }
        }
    }

    /**
     * Return true if the selected theme (on NewPipe settings) is the Light theme.
     *
     * @param context context to get the preference
     * @return whether the light theme is selected
     */
    public static boolean isLightThemeSelected(final Context context) {
        // Kept for callers that choose dialog styles. The app is dark-only now.
        return false;
    }

    /**
     * Return a dialog theme styled according to the (default) selected theme.
     *
     * @param context context to get the selected theme
     * @return the dialog style (the default one)
     */
    @StyleRes
    public static int getDialogTheme(final Context context) {
        return switch (getSelectedAccentValue(context)) {
            case "violet" -> R.style.DarkDialogTheme_Violet;
            case "red" -> R.style.DarkDialogTheme_Red;
            case "green" -> R.style.DarkDialogTheme_Green;
            default -> R.style.DarkDialogTheme_Blue;
        };
    }

    /**
     * Return a min-width dialog theme styled according to the (default) selected theme.
     *
     * @param context context to get the selected theme
     * @return the dialog style (the default one)
     */
    @StyleRes
    public static int getMinWidthDialogTheme(final Context context) {
        return switch (getSelectedAccentValue(context)) {
            case "violet" -> R.style.DarkDialogMinWidthTheme_Violet;
            case "red" -> R.style.DarkDialogMinWidthTheme_Red;
            case "green" -> R.style.DarkDialogMinWidthTheme_Green;
            default -> R.style.DarkDialogMinWidthTheme_Blue;
        };
    }

    /**
     * Return the selected theme styled according to the serviceId.
     *
     * @param context   context to get the selected theme
     * @param serviceId return a theme styled to this service,
     *                  -1 to get the default
     * @return the selected style (styled)
     */
    @StyleRes
    public static int getThemeForService(final Context context, final int serviceId) {
        final String blackThemeKey = context.getString(R.string.black_theme_key);
        final String selectedThemeKey = getSelectedThemeKey(context);

        final int baseTheme = selectedThemeKey.equals(blackThemeKey)
                ? R.style.BlackTheme : R.style.DarkTheme;

        if (serviceId <= -1) {
            return baseTheme;
        }

        final StreamingService service;
        try {
            service = NewPipe.getService(serviceId);
        } catch (final ExtractionException ignored) {
            return baseTheme;
        }

        String themeName = "DarkTheme"; // default
        if (baseTheme == R.style.LightTheme) {
            themeName = "LightTheme";
        } else if (baseTheme == R.style.BlackTheme) {
            themeName = "BlackTheme";
        }

        themeName += "." + service.getServiceInfo().getName();
        final int resourceId = getThemeOrDefault(themeName, baseTheme);

        if (resourceId > 0) {
            return resourceId;
        }
        return baseTheme;
    }

    @StyleRes
    public static int getSettingsThemeStyle(final Context context) {
        final String blackThemeKey = context.getString(R.string.black_theme_key);
        return getSelectedThemeKey(context).equals(blackThemeKey)
                ? R.style.BlackSettingsTheme : R.style.DarkSettingsTheme;
    }

    /**
     * Get a color from an attr styled according to the context's theme.
     *
     * @param context   Android app context
     * @param attrColor attribute reference of the resource
     * @return the color
     */
    public static int resolveColorFromAttr(final Context context, @AttrRes final int attrColor) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attrColor, value, true);

        if (value.resourceId != 0) {
            return ContextCompat.getColor(context, value.resourceId);
        }

        return value.data;
    }

    /**
     * Resolves a {@link Drawable} by it's id.
     *
     * @param context   Context
     * @param attrResId Resource id
     * @return the {@link Drawable}
     */
    public static Drawable resolveDrawable(@NonNull final Context context,
                                           @AttrRes final int attrResId) {
        final TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrResId, typedValue, true);
        return AppCompatResources.getDrawable(context, typedValue.resourceId);
    }

    /**
     * Gets a runtime dimen from the {@code android} package. Should be used for dimens for which
     * normal accessing with {@code R.dimen.} is not available.
     *
     * @param context context
     * @param name    dimen resource name (e.g. navigation_bar_height)
     * @return the obtained dimension, in pixels, or 0 if the resource could not be resolved
     */
    public static int getAndroidDimenPx(@NonNull final Context context, final String name) {
        final int resId = context.getResources().getIdentifier(name, "dimen", "android");
        if (resId <= 0) {
            return 0;
        }
        return context.getResources().getDimensionPixelSize(resId);
    }

    private static String getSelectedAccentValue(final Context context) {
        final String key = context.getString(R.string.accent_color_key);
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(key, context.getString(R.string.default_accent_color_value));
    }

    private static String getSelectedThemeKey(final Context context) {
        final String themeKey = context.getString(R.string.theme_key);
        final String darkThemeKey = context.getString(R.string.dark_theme_key);
        final String blackThemeKey = context.getString(R.string.black_theme_key);
        final String storedThemeKey = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(themeKey, darkThemeKey);
        // Old installs may still have Light or Automatic saved in preferences.
        return storedThemeKey.equals(blackThemeKey) ? blackThemeKey : darkThemeKey;
    }

    private static String getSelectedNightThemeKey(final Context context) {
        final String nightThemeKey = context.getString(R.string.night_theme_key);
        final String defaultNightTheme = context.getResources()
                .getString(R.string.default_night_theme_value);
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(nightThemeKey, defaultNightTheme);
    }

    /**
     * Sets the title to the activity, if the activity is an {@link AppCompatActivity} and has an
     * action bar.
     *
     * @param activity the activity to set the title of
     * @param title    the title to set to the activity
     */
    public static void setTitleToAppCompatActivity(@Nullable final Activity activity,
                                                   final CharSequence title) {
        if (activity instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
            }
        }
    }

    /**
     * Get the device theme
     * <p>
     * It will return true if the device 's theme is dark, false otherwise.
     * <p>
     * From https://developer.android.com/guide/topics/ui/look-and-feel/darktheme#java
     *
     * @param context the context to use
     * @return true:dark theme, false:light or unknown
     */
    public static boolean isDeviceDarkThemeEnabled(final Context context) {
        final int deviceTheme = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        switch (deviceTheme) {
            case Configuration.UI_MODE_NIGHT_YES:
                return true;
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
            case Configuration.UI_MODE_NIGHT_NO:
            default:
                return false;
        }
    }

    public static void setDayNightMode(final Context context) {
        setDayNightMode(context, ThemeHelper.getSelectedThemeKey(context));
    }

    public static void setDayNightMode(final Context context, final String selectedThemeKey) {
        // Do not follow system light mode: this fork now supports Dark and Black only.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    /**
     * Returns whether the grid layout or the list layout should be used. If the user set "auto"
     * mode in settings, decides based on screen orientation (landscape) and size.
     *
     * @param context the context to use
     * @return true:use grid layout, false:use list layout
     */
    public static boolean shouldUseGridLayout(final Context context) {
        final ItemViewMode mode = getItemViewMode(context);
        return mode == ItemViewMode.GRID;
    }

    /**
     * Calculates the number of grid channel info items that can fit horizontally on the screen.
     *
     * @param context the context to use
     * @return the span count of grid channel info items
     */
    public static int getGridSpanCountChannels(final Context context) {
        return getGridSpanCount(context,
                context.getResources().getDimensionPixelSize(R.dimen.channel_item_grid_min_width));
    }

    /**
     * Returns item view mode.
     * @param context to read preference and parse string
     * @return Returns one of ItemViewMode
     */
    public static ItemViewMode getItemViewMode(final Context context) {
        final String listMode = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.list_view_mode_key),
                        context.getString(R.string.list_view_mode_value));
        final ItemViewMode result;
        if (listMode.equals(context.getString(R.string.list_view_mode_list_key))) {
            result = ItemViewMode.LIST;
        } else if (listMode.equals(context.getString(R.string.list_view_mode_grid_key))) {
            result = ItemViewMode.GRID;
        } else if (listMode.equals(context.getString(R.string.list_view_mode_card_key))) {
            result = ItemViewMode.CARD;
        } else {
            // Auto mode - evaluate whether to use Grid based on screen real estate.
            final Configuration configuration = context.getResources().getConfiguration();
            final boolean useGrid = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    && configuration.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE);
            if (useGrid) {
                result = ItemViewMode.GRID;
            } else {
                // Use the large, full-width video cards as the phone-first default.
                result = ItemViewMode.CARD;
            }
        }
        return result;
    }

    /**
     * Calculates the number of grid stream info items that can fit horizontally on the screen. The
     * width of a grid stream info item is obtained from the thumbnail width plus the right and left
     * paddings.
     *
     * @param context the context to use
     * @return the span count of grid stream info items
     */
    public static int getGridSpanCountStreams(final Context context) {
        final Resources res = context.getResources();
        return getGridSpanCount(context,
                res.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width)
                        + res.getDimensionPixelSize(R.dimen.video_item_search_padding) * 2);
    }

    /**
     * Calculates the number of grid items that can fit horizontally on the screen based on the
     * minimum width.
     *
     * @param context the context to use
     * @param minWidth the minimum width of items in the grid
     * @return the span count of grid list items
     */
    public static int getGridSpanCount(final Context context, final int minWidth) {
        return Math.max(1, context.getResources().getDisplayMetrics().widthPixels / minWidth);
    }

    @StyleRes
    private static int getThemeOrDefault(final String name, @StyleRes final int baseTheme) {
        return switch (name) {
            case "LightTheme.YouTube" -> R.style.LightTheme_YouTube;
            case "DarkTheme.YouTube" -> R.style.DarkTheme_YouTube;
            case "BlackTheme.YouTube" -> R.style.BlackTheme_YouTube;
            case "LightTheme.SoundCloud" -> R.style.LightTheme_SoundCloud;
            case "DarkTheme.SoundCloud" -> R.style.DarkTheme_SoundCloud;
            case "BlackTheme.SoundCloud" -> R.style.BlackTheme_SoundCloud;
            case "LightTheme.PeerTube" -> R.style.LightTheme_PeerTube;
            case "DarkTheme.PeerTube" -> R.style.DarkTheme_PeerTube;
            case "BlackTheme.PeerTube" -> R.style.BlackTheme_PeerTube;
            case "LightTheme.media.ccc.de" -> R.style.LightTheme_media_ccc_de;
            case "DarkTheme.media.ccc.de" -> R.style.DarkTheme_media_ccc_de;
            case "BlackTheme.media.ccc.de" -> R.style.BlackTheme_media_ccc_de;
            case "LightTheme.Bandcamp" -> R.style.LightTheme_Bandcamp;
            case "DarkTheme.Bandcamp" -> R.style.DarkTheme_Bandcamp;
            case "BlackTheme.Bandcamp" -> R.style.BlackTheme_Bandcamp;
            default -> baseTheme;
        };
    }
}
