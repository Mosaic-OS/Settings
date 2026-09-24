/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.statusbar;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.internal.util.StatusBarClockPosition;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.Arrays;

/** Settings &gt; System &gt; Status bar — host screen for the status bar features. */
@SearchIndexable(forTarget = SearchIndexable.MOBILE)
public class StatusBarSettingsFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String CLOCK_POSITION_TAG = "StatusBarClockPosition";
    private static final String STATUS_BAR_QUICK_QS_PULLDOWN = "status_bar_quick_qs_pulldown";

    private static final int PULLDOWN_DIR_NONE = 0;
    private static final int PULLDOWN_DIR_RIGHT = 1;
    private static final int PULLDOWN_DIR_LEFT = 2;
    private static final int PULLDOWN_DIR_BOTH = 3;

    private ListPreference mQuickPulldown;
    private static final String CLOCK_SECONDS = "clock_seconds";
    private ListPreference mClockPosition;
    private TwoStatePreference mClockSeconds;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ContentObserver mClockObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateClockPreferences();
        }
    };
    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    updateClockPreferences();
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    updateClockPreferences();
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    updateClockPreferences();
                }
            };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.status_bar_settings, rootKey);

        mQuickPulldown = findPreference(STATUS_BAR_QUICK_QS_PULLDOWN);
        if (mQuickPulldown != null) {
            mQuickPulldown.setOnPreferenceChangeListener(this);
        }
        mClockPosition = findPreference(StatusBarClockPosition.SETTING);
        mClockSeconds = findPreference(CLOCK_SECONDS);
        updateClockPreferences();
        mClockPosition.setOnPreferenceChangeListener(this);
        mClockSeconds.setOnPreferenceChangeListener(this);
        Log.d(CLOCK_POSITION_TAG, "init key=" + mClockPosition.getKey()
                + " preference=" + mClockPosition.getClass().getName()
                + " entries=" + Arrays.toString(mClockPosition.getEntryValues())
                + " persistent=" + mClockPosition.isPersistent());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(getString(R.string.status_bar_settings));
        }
        updateQuickPulldown();
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(StatusBarClockPosition.SETTING), false, mClockObserver);
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(CLOCK_SECONDS), false, mClockObserver);
        final DisplayManager manager = getContext().getSystemService(DisplayManager.class);
        if (manager != null) manager.registerDisplayListener(mDisplayListener, mHandler);
        updateClockPreferences();
    }

    @Override
    public void onPause() {
        getContentResolver().unregisterContentObserver(mClockObserver);
        final DisplayManager manager = getContext().getSystemService(DisplayManager.class);
        if (manager != null) manager.unregisterDisplayListener(mDisplayListener);
        super.onPause();
    }

    private void updateClockPreferences() {
        final boolean centerAllowed = StatusBarClockPosition.isCenterAllowed(getContext());
        mClockPosition.setEntries(centerAllowed
                ? new CharSequence[] {getString(R.string.status_bar_custom_clock_left),
                        getString(R.string.status_bar_custom_clock_center),
                        getString(R.string.status_bar_custom_clock_right)}
                : new CharSequence[] {getString(R.string.status_bar_custom_clock_left),
                        getString(R.string.status_bar_custom_clock_right)});
        mClockPosition.setEntryValues(centerAllowed
                ? new String[] {StatusBarClockPosition.LEFT, StatusBarClockPosition.CENTER,
                        StatusBarClockPosition.RIGHT}
                : new String[] {StatusBarClockPosition.LEFT, StatusBarClockPosition.RIGHT});
        mClockPosition.setValue(StatusBarClockPosition.resolve(getContext(),
                Settings.Secure.getString(getContentResolver(), StatusBarClockPosition.SETTING)));
        mClockSeconds.setChecked(Settings.Secure.getInt(
                getContentResolver(), CLOCK_SECONDS, 0) != 0);
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
        if (preference == mClockPosition) {
            final String value = newValue instanceof String ? (String) newValue : null;
            final boolean validValue = StatusBarClockPosition.LEFT.equals(value)
                    || StatusBarClockPosition.CENTER.equals(value)
                    || StatusBarClockPosition.RIGHT.equals(value);
            final boolean allowed = validValue && (!StatusBarClockPosition.CENTER.equals(value)
                    || StatusBarClockPosition.isCenterAllowed(preference.getContext()));
            final ContentResolver resolver = preference.getContext().getContentResolver();
            boolean writeSucceeded = false;
            String readback = null;
            String writeError = "none";
            String readError = "none";
            if (allowed) {
                try {
                    writeSucceeded = Settings.Secure.putString(resolver,
                            StatusBarClockPosition.SETTING, value);
                } catch (RuntimeException e) {
                    writeError = e.getClass().getSimpleName();
                }
            }
            try {
                readback = Settings.Secure.getString(resolver, StatusBarClockPosition.SETTING);
            } catch (RuntimeException e) {
                readError = e.getClass().getSimpleName();
            }
            final boolean accepted = allowed && writeSucceeded && value.equals(readback);
            Log.d(CLOCK_POSITION_TAG, "selection chosen=" + (validValue ? value : "<invalid>")
                    + " entries=" + Arrays.toString(mClockPosition.getEntryValues())
                    + " user=" + resolver.getUserId() + " allowed=" + allowed
                    + " write=" + writeSucceeded + " readback=" + readback
                    + " accepted=" + accepted + " writeError=" + writeError
                    + " readError=" + readError);
            return accepted;
        }
        if (preference == mClockSeconds) {
            return newValue instanceof Boolean && Settings.Secure.putInt(
                    getContentResolver(), CLOCK_SECONDS, (Boolean) newValue ? 1 : 0);
        }
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
