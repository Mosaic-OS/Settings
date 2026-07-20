/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.statusbar;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

/** Settings &gt; System &gt; Status bar — host screen for the status bar features. */
@SearchIndexable(forTarget = SearchIndexable.MOBILE)
public class StatusBarSettingsFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String STATUS_BAR_QUICK_QS_PULLDOWN = "status_bar_quick_qs_pulldown";

    private static final int PULLDOWN_DIR_NONE = 0;
    private static final int PULLDOWN_DIR_RIGHT = 1;
    private static final int PULLDOWN_DIR_LEFT = 2;
    private static final int PULLDOWN_DIR_BOTH = 3;

    private ListPreference mQuickPulldown;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.status_bar_settings, rootKey);

        mQuickPulldown = findPreference(STATUS_BAR_QUICK_QS_PULLDOWN);
        if (mQuickPulldown != null) {
            mQuickPulldown.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(getString(R.string.status_bar_settings));
        }
        updateQuickPulldown();
    }

    private void updateQuickPulldown() {
        if (mQuickPulldown == null) return;
        // RTL swaps the left/right entry labels.
        mQuickPulldown.setEntries(isRtl()
                ? R.array.status_bar_quick_qs_pulldown_entries_rtl
                : R.array.status_bar_quick_qs_pulldown_entries);
        mQuickPulldown.setEntryValues(R.array.status_bar_quick_qs_pulldown_values);
        final int val = Settings.Secure.getInt(getContext().getContentResolver(),
                STATUS_BAR_QUICK_QS_PULLDOWN, PULLDOWN_DIR_NONE);
        mQuickPulldown.setValue(String.valueOf(val));
        updateQuickPulldownSummary(val);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mQuickPulldown) {
            final int val;
            try {
                val = Integer.parseInt((String) newValue);
            } catch (NumberFormatException e) {
                return false;
            }
            Settings.Secure.putInt(getContext().getContentResolver(),
                    STATUS_BAR_QUICK_QS_PULLDOWN, val);
            updateQuickPulldownSummary(val);
            return true;
        }
        return false;
    }

    private void updateQuickPulldownSummary(int value) {
        if (mQuickPulldown == null) return;
        final String summary;
        switch (value) {
            case PULLDOWN_DIR_BOTH:
                summary = getString(R.string.status_bar_quick_qs_pulldown_summary,
                        getString(R.string.status_bar_quick_qs_pulldown_summary_both));
                break;
            case PULLDOWN_DIR_LEFT:
            case PULLDOWN_DIR_RIGHT:
                // RTL swaps the left/right meaning in the displayed text.
                summary = getString(R.string.status_bar_quick_qs_pulldown_summary,
                        getString((value == PULLDOWN_DIR_LEFT) ^ isRtl()
                                ? R.string.status_bar_quick_qs_pulldown_summary_left
                                : R.string.status_bar_quick_qs_pulldown_summary_right));
                break;
            default:
                summary = getString(R.string.status_bar_quick_qs_pulldown_off);
                break;
        }
        mQuickPulldown.setSummary(summary);
    }

    private boolean isRtl() {
        return getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.status_bar_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return true;
                }
            };
}
