package org.schabi.newpipe.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ThemeHelper;

public class AppearanceSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();


        final String accentColorKey = getString(R.string.accent_color_key);
        findPreference(accentColorKey).setOnPreferenceChangeListener((preference, newValue) -> {
            final String selectedAccent = newValue.toString();
            final String currentAccent = defaultPreferences.getString(accentColorKey,
                    getString(R.string.default_accent_color_value));
            if (!selectedAccent.equals(currentAccent)) {
                defaultPreferences.edit()
                        .putBoolean(Constants.KEY_THEME_CHANGE, true)
                        .putString(accentColorKey, selectedAccent)
                        .apply();
                ThemeHelper.updateLauncherIcon(requireContext());
                if (getActivity() != null) {
                    ActivityCompat.recreate(getActivity());
                }
            }
            return false;
        });

        final String themeKey = getString(R.string.theme_key);
        final String darkThemeKey = getString(R.string.dark_theme_key);
        final String blackThemeKey = getString(R.string.black_theme_key);
        final String storedThemeKey = defaultPreferences.getString(themeKey, darkThemeKey);
        // Migrate legacy Light and Automatic selections to Dark. The app now offers
        // only Dark and Black themes, regardless of the Android system appearance.
        final String startThemeKey = storedThemeKey.equals(blackThemeKey)
                ? blackThemeKey : darkThemeKey;
        if (!storedThemeKey.equals(startThemeKey)) {
            defaultPreferences.edit().putString(themeKey, startThemeKey).apply();
        }

        findPreference(themeKey).setOnPreferenceChangeListener((preference, newValue) -> {
            final String selectedTheme = newValue.toString().equals(blackThemeKey)
                    ? blackThemeKey : darkThemeKey;
            applyThemeChange(startThemeKey, themeKey, selectedTheme);
            return false;
        });
    }
    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (getString(R.string.caption_settings_key).equals(preference.getKey())) {
            try {
                startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
            } catch (final ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.general_error, Toast.LENGTH_SHORT).show();
            }
        }

        return super.onPreferenceTreeClick(preference);
    }

    private void applyThemeChange(final String beginningThemeKey,
                                  final String themeKey,
                                  final Object newValue) {
        defaultPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
        defaultPreferences.edit().putString(themeKey, newValue.toString()).apply();

        ThemeHelper.setDayNightMode(requireContext(), newValue.toString());

        if (!newValue.equals(beginningThemeKey) && getActivity() != null) {
            // if it's not the current theme
            ActivityCompat.recreate(getActivity());
        }
    }
}
