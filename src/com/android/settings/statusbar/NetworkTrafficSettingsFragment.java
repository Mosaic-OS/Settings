/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.statusbar;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.SliderPreference;

/** Network traffic monitor settings; each preference key is its Settings.System key. */
@SearchIndexable(forTarget = SearchIndexable.MOBILE)
public class NetworkTrafficSettingsFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_RESET = "network_traffic_reset";

    /** Slider positions 0..10 map to these stored KB/s values; 0 = always show. */
    private static final int[] THRESHOLD_KBPS = {0, 1, 2, 5, 10, 20, 50, 100, 250, 500, 5120};

    private static final int DEF_STATE = 0;
    private static final int DEF_VIEW_LOCATION = 0;
    private static final int DEF_TYPE = 0;
    private static final int DEF_AUTOHIDE_THRESHOLD = 1;
    private static final int DEF_ARROW = 1;
    private static final int DEF_TEXT_ENABLED = 1;
    private static final int DEF_FONT_SIZE = 10;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.network_traffic_settings, rootKey);
        bindAll();

        final Preference reset = findPreference(KEY_RESET);
        if (reset != null) {
            reset.setOnPreferenceClickListener(preference -> {
                resetToDefaults();
                return true;
            });
        }
    }

    /** Reads every setting and pushes it into the visible preference state. */
    private void bindAll() {
        bindSwitch(Settings.System.NETWORK_TRAFFIC_STATE, DEF_STATE);
        bindList(Settings.System.NETWORK_TRAFFIC_VIEW_LOCATION, DEF_VIEW_LOCATION);
        bindList(Settings.System.NETWORK_TRAFFIC_TYPE, DEF_TYPE);
        bindThresholdSlider();
        bindSwitch(Settings.System.NETWORK_TRAFFIC_ARROW, DEF_ARROW);
        bindSwitch(Settings.System.NETWORK_TRAFFIC_TEXT_ENABLED, DEF_TEXT_ENABLED);
        bindSlider(Settings.System.NETWORK_TRAFFIC_FONT_SIZE, DEF_FONT_SIZE);
        updateFontSizeEnabled();
    }

    /** Font size only affects single-line modes, so grey it out for the "Both" type. */
    private void updateFontSizeEnabled() {
        final SliderPreference pref = findPreference(Settings.System.NETWORK_TRAFFIC_FONT_SIZE);
        if (pref == null) return;
        pref.setEnabled(getSystemInt(Settings.System.NETWORK_TRAFFIC_TYPE, DEF_TYPE) != 0);
    }

    /** Restores every setting except the master switch, which is left as the user set it. */
    private void resetToDefaults() {
        final ContentResolver resolver = getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_VIEW_LOCATION, DEF_VIEW_LOCATION,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_TYPE, DEF_TYPE, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, DEF_AUTOHIDE_THRESHOLD,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_ARROW, DEF_ARROW, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_TEXT_ENABLED, DEF_TEXT_ENABLED,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_FONT_SIZE, DEF_FONT_SIZE,
                UserHandle.USER_CURRENT);
        bindAll();
    }

    private int getSystemInt(String key, int def) {
        return Settings.System.getIntForUser(getContentResolver(), key, def,
                UserHandle.USER_CURRENT);
    }

    private void bindSwitch(String key, int def) {
        final TwoStatePreference pref = findPreference(key);
        if (pref == null) return;
        pref.setChecked(getSystemInt(key, def) == 1);
        pref.setOnPreferenceChangeListener(this);
    }

    private void bindList(String key, int def) {
        final ListPreference pref = findPreference(key);
        if (pref == null) return;
        pref.setValue(String.valueOf(getSystemInt(key, def)));
        pref.setOnPreferenceChangeListener(this);
    }

    private void bindSlider(String key, int def) {
        final SliderPreference pref = findPreference(key);
        if (pref == null) return;
        final int stored = getSystemInt(key, def);
        pref.setValue(stored);
        // setValue clamps to [min, max]; write the clamped value back so store and UI agree.
        final int value = pref.getValue();
        if (value != stored) {
            Settings.System.putIntForUser(getContentResolver(), key, value,
                    UserHandle.USER_CURRENT);
        }
        updateSliderSummary(pref, key, value);
        pref.setOnPreferenceChangeListener(this);
    }

    /** The threshold slider works in positions 0..10; the store holds THRESHOLD_KBPS[pos]. */
    private void bindThresholdSlider() {
        final String key = Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD;
        final SliderPreference pref = findPreference(key);
        if (pref == null) return;
        final int stored = getSystemInt(key, DEF_AUTOHIDE_THRESHOLD);
        final int pos = nearestThresholdPosition(stored);
        pref.setValue(pos);
        // Snap off-curve stored values to the nearest step.
        if (THRESHOLD_KBPS[pos] != stored) {
            Settings.System.putIntForUser(getContentResolver(), key, THRESHOLD_KBPS[pos],
                    UserHandle.USER_CURRENT);
        }
        updateSliderSummary(pref, key, THRESHOLD_KBPS[pos]);
        pref.setOnPreferenceChangeListener(this);
    }

    private static int nearestThresholdPosition(int kbps) {
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < THRESHOLD_KBPS.length; i++) {
            final long dist = Math.abs((long) THRESHOLD_KBPS[i] - kbps);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    /** The slider layout has no persistent value readout, so the summary carries it. */
    private void updateSliderSummary(SliderPreference pref, String key, int value) {
        final String summary;
        if (Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD.equals(key)) {
            if (value == 0) {
                summary = getString(R.string.network_traffic_autohide_threshold_summary_always);
            } else if (value >= 1024) {
                summary = getString(R.string.network_traffic_autohide_threshold_summary_mb,
                        value / 1024);
            } else {
                summary = getString(R.string.network_traffic_autohide_threshold_summary, value);
            }
        } else if (Settings.System.NETWORK_TRAFFIC_FONT_SIZE.equals(key)) {
            summary = getString(R.string.network_traffic_font_size_summary, value);
        } else {
            return;
        }
        pref.setSummary(summary);
        pref.setSliderStateDescription(summary);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        int value;
        if (newValue instanceof Boolean) {
            value = ((Boolean) newValue) ? 1 : 0;
        } else if (newValue instanceof Integer) {
            value = (Integer) newValue;
        } else if (newValue instanceof String) {
            try {
                value = Integer.parseInt((String) newValue);
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            return false;
        }
        if (Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD.equals(preference.getKey())) {
            // The slider reports a position on the threshold curve; store the KB/s value.
            value = THRESHOLD_KBPS[
                    Math.max(0, Math.min(THRESHOLD_KBPS.length - 1, value))];
        }
        if (!Settings.System.putIntForUser(getContentResolver(), preference.getKey(), value,
                UserHandle.USER_CURRENT)) {
            return false;
        }
        if (preference instanceof SliderPreference) {
            updateSliderSummary((SliderPreference) preference, preference.getKey(), value);
        }
        if (Settings.System.NETWORK_TRAFFIC_TYPE.equals(preference.getKey())) {
            updateFontSizeEnabled();
        }
        onSettingWritten(preference.getKey(), value);
        return true;
    }

    /** Arrows and text may not both be off — turning the last one off flips the other on. */
    private void onSettingWritten(String key, int value) {
        if (value != 0) return;
        if (Settings.System.NETWORK_TRAFFIC_ARROW.equals(key)) {
            forceEnablePeer(Settings.System.NETWORK_TRAFFIC_TEXT_ENABLED);
        } else if (Settings.System.NETWORK_TRAFFIC_TEXT_ENABLED.equals(key)) {
            forceEnablePeer(Settings.System.NETWORK_TRAFFIC_ARROW);
        }
    }

    private void forceEnablePeer(String peerKey) {
        if (getSystemInt(peerKey, 1) == 1) return;
        if (!Settings.System.putIntForUser(getContentResolver(), peerKey, 1,
                UserHandle.USER_CURRENT)) {
            return;
        }
        final TwoStatePreference peer = findPreference(peerKey);
        if (peer != null) peer.setChecked(true);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.network_traffic_settings);
}
