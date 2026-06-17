package com.android.settings.statusbar;

import android.os.Bundle;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.R;

import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.search.Indexable;

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
            int val = Settings.Secure.getInt(getContext().getContentResolver(),
                    STATUS_BAR_QUICK_QS_PULLDOWN, PULLDOWN_DIR_NONE);
            mQuickPulldown.setValue(String.valueOf(val));
            updateQuickPulldownSummary(val);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(getString(R.string.status_bar_settings));
        }

        // Adjust entries for RTL layout direction
        boolean isRtl = getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;

        if (mQuickPulldown != null) {
            if (isRtl) {
                mQuickPulldown.setEntries(R.array.status_bar_quick_qs_pulldown_entries_rtl);
                mQuickPulldown.setEntryValues(R.array.status_bar_quick_qs_pulldown_values);
            } else {
                mQuickPulldown.setEntries(R.array.status_bar_quick_qs_pulldown_entries);
                mQuickPulldown.setEntryValues(R.array.status_bar_quick_qs_pulldown_values);
            }
            int val = Settings.Secure.getInt(getContext().getContentResolver(),
                    STATUS_BAR_QUICK_QS_PULLDOWN, PULLDOWN_DIR_NONE);
            mQuickPulldown.setValue(String.valueOf(val));
            updateQuickPulldownSummary(val);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mQuickPulldown) {
            try {
                int v = Integer.parseInt((String) newValue);
                Settings.Secure.putInt(getContext().getContentResolver(),
                        STATUS_BAR_QUICK_QS_PULLDOWN, v);
                updateQuickPulldownSummary(v);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private void updateQuickPulldownSummary(int value) {
        if (mQuickPulldown == null) return;

        String summary;
        switch (value) {
            case PULLDOWN_DIR_NONE:
                summary = getResources().getString(R.string.status_bar_quick_qs_pulldown_off);
                break;
            case PULLDOWN_DIR_BOTH:
                summary = getResources().getString(
                        R.string.status_bar_quick_qs_pulldown_summary,
                        getResources().getString(R.string.status_bar_quick_qs_pulldown_summary_both));
                break;

            case PULLDOWN_DIR_LEFT:
            case PULLDOWN_DIR_RIGHT:
                // When using RTL, left/right meanings swap in the displayed text:
                boolean isRtl = getResources().getConfiguration().getLayoutDirection()
                        == View.LAYOUT_DIRECTION_RTL;
                // Determine which summary string to use: left or right
                int labelRes = ((value == PULLDOWN_DIR_LEFT) ^ isRtl)
                        ? R.string.status_bar_quick_qs_pulldown_summary_left
                        : R.string.status_bar_quick_qs_pulldown_summary_right;
                summary = getResources().getString(R.string.status_bar_quick_qs_pulldown_summary,
                        getResources().getString(labelRes));
                break;

            default:
                summary = getResources().getString(R.string.status_bar_quick_qs_pulldown_off);
                break;
        }
        mQuickPulldown.setSummary(summary);
    }
    
    @Override
    public int getMetricsCategory() {
        return 0;
    }
    
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
        new BaseSearchIndexProvider(R.xml.status_bar_settings) {
            @Override
            protected boolean isPageSearchEnabled(Context context) {
                return true;
            }
        };
}
