/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.statusbar;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

/** Settings &gt; System &gt; Status bar — host screen for the status bar features. */
@SearchIndexable(forTarget = SearchIndexable.MOBILE)
public class StatusBarSettingsFragment extends SettingsPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.status_bar_settings, rootKey);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(getString(R.string.status_bar_settings));
        }
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
