package com.android.settings.display;

import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.custom.preference.CustomSeekBarPreference;
import com.android.settings.custom.preference.CustomSwitchPreference;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public class ScrimCustomizationSettings extends DashboardFragment {

    private static final String TAG = "ScrimCustomization";
    private static final String KEY_RESET = "reset_scrim_settings";

    // Default values
    private static final int DEFAULT_BACKGROUND_BLUR = 76;
    private static final int DEFAULT_BACKGROUND_TRANSPARENCY = 20;
    private static final int DEFAULT_NOTIFICATION_TRANSPARENCY = 50;
    private static final boolean DEFAULT_BACKGROUND_ACCENT = true;
    private static final boolean DEFAULT_NOTIFICATION_ACCENT = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Preference resetPref = findPreference(KEY_RESET);
        if (resetPref != null) {
            resetPref.setOnPreferenceClickListener(preference -> {
                resetToDefaults();
                return true;
            });
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.scrim_customization_settings;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.DISPLAY;
    }

    private void resetToDefaults() {
        // Delete the overrides so SystemUI returns to its exact stock appearance.
        final ContentResolver resolver = getContext().getContentResolver();
        for (String key : new String[] {
                "background_blur_radius", "background_transparency",
                "notification_transparency", "background_accent", "notification_accent"}) {
            Settings.Secure.putString(resolver, key, null);
        }
        CustomSeekBarPreference bgBlur = findPreference("background_blur_radius");
        if (bgBlur != null) bgBlur.showDefaultNoPersist(DEFAULT_BACKGROUND_BLUR);

        CustomSeekBarPreference bgTransparency = findPreference("background_transparency");
        if (bgTransparency != null) bgTransparency.showDefaultNoPersist(DEFAULT_BACKGROUND_TRANSPARENCY);

        CustomSeekBarPreference notifTransparency = findPreference("notification_transparency");
        if (notifTransparency != null) {
            notifTransparency.showDefaultNoPersist(DEFAULT_NOTIFICATION_TRANSPARENCY);
        }

        CustomSwitchPreference bgAccent = findPreference("background_accent");
        if (bgAccent != null) bgAccent.setCheckedNoPersist(DEFAULT_BACKGROUND_ACCENT);

        CustomSwitchPreference notifAccent = findPreference("notification_accent");
        if (notifAccent != null) notifAccent.setCheckedNoPersist(DEFAULT_NOTIFICATION_ACCENT);
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.scrim_customization_settings);
}
