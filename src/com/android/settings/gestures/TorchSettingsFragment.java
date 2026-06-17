package com.android.settings.gestures;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.widget.MainSwitchPreference;
import android.database.ContentObserver;

/**
 * Torch settings fragment that contains a main switch and a timeout ListPreference.
 */
public class TorchSettingsFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "TorchSettingsFragment";
    private static final String KEY_MAIN_SWITCH = "torch_switch";
    private static final String KEY_TIMEOUT = "torch_long_press_power_timeout";
    private static final String KEY_GESTURE = "torch_long_press_power_gesture";
    private static final int DEFAULT_TIMEOUT = 0;

    private MainSwitchPreference mMainSwitch;
    private ListPreference mTimeoutPref;
    private ContentResolver mResolver;

    private final ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateUiFromSettings();
        }

        @Override
        public void onChange(boolean selfChange, @Nullable Uri uri) {
            updateUiFromSettings();
        }
    };

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_GESTURES; 
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        
        setPreferencesFromResource(R.xml.gesture_torch_settings, rootKey);
        
        Context ctx = getContext();
        mResolver = ctx.getContentResolver();

        mMainSwitch = findPreference(KEY_MAIN_SWITCH);
        mTimeoutPref = findPreference(KEY_TIMEOUT);

        if (mMainSwitch != null) {
            mMainSwitch.setOnPreferenceChangeListener((pref, newVal) -> {
                boolean enabled = (Boolean) newVal;
                boolean ok = Settings.Secure.putInt(mResolver, KEY_GESTURE, enabled ? 1 : 0);
                if (!ok) {
                    Slog.w(TAG, "Failed to write " + KEY_GESTURE + " = " + enabled);
                }
                updateUiFromSettings();
                return ok;
            });
        }

        if (mTimeoutPref != null) {
            mTimeoutPref.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mResolver.registerContentObserver(Settings.Secure.getUriFor(KEY_GESTURE),
                false, mSettingsObserver, UserHandle.USER_ALL);
        mResolver.registerContentObserver(Settings.Secure.getUriFor(KEY_TIMEOUT),
                false, mSettingsObserver, UserHandle.USER_ALL);
        updateUiFromSettings();
    }

    @Override
    public void onPause() {
        super.onPause();
        mResolver.unregisterContentObserver(mSettingsObserver);
    }

    private void updateUiFromSettings() {
        if (mMainSwitch != null) {
            int g = Settings.Secure.getInt(mResolver, KEY_GESTURE, 0);
            mMainSwitch.setChecked(g != 0);
        }

        if (mTimeoutPref != null) {
            int sec = Settings.Secure.getInt(mResolver, KEY_TIMEOUT, DEFAULT_TIMEOUT);
            String val = Integer.toString(sec);
            CharSequence[] entryValues = mTimeoutPref.getEntryValues();
            CharSequence[] entries = mTimeoutPref.getEntries();
            int idx = -1;

            if (entryValues != null) {
                for (int i = 0; i < entryValues.length; i++) {
                    if (val.equals(entryValues[i].toString())) {
                        idx = i;
                        break;
                    }
                }
            }

            if (idx >= 0) {
                mTimeoutPref.setValueIndex(idx);
                if (entries != null && idx < entries.length) {
                    mTimeoutPref.setSummary(entries[idx]);
                } else {
                    mTimeoutPref.setSummary(null);
                }
            } else {
                if (entries != null && entries.length > 0) {
                    mTimeoutPref.setValueIndex(0);
                    mTimeoutPref.setSummary(entries[0]);
                }
            }

            if (mMainSwitch != null) {
                mTimeoutPref.setEnabled(mMainSwitch.isChecked());
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mTimeoutPref) {
            String val = (String) newValue;
            int seconds;
            try {
                seconds = Integer.parseInt(val);
            } catch (NumberFormatException e) {
                Slog.w(TAG, "Invalid timeout value: " + val);
                return false;
            }
            boolean ok = Settings.Secure.putInt(mResolver, KEY_TIMEOUT, seconds);
            if (!ok) {
                Slog.w(TAG, "Failed to write " + KEY_TIMEOUT + " = " + seconds);
                updateUiFromSettings();
                return false;
            }
            updateUiFromSettings();
            return true;
        }
        return false;
    }
}
