package com.android.settings.custom.preference;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;

import androidx.preference.SwitchPreferenceCompat;

public class CustomSwitchPreference extends SwitchPreferenceCompat {

    private boolean mSuppressPersist;

    public CustomSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public CustomSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomSwitchPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setPersistent(false);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        String key = getKey();
        if (key == null) {
            super.onSetInitialValue(restoreValue, defaultValue);
            return;
        }

        try {
            int value = Settings.Secure.getInt(getContext().getContentResolver(), key);
            setCheckedNoPersist(value == 1);
        } catch (Settings.SettingNotFoundException e) {
            // Unset means stock behavior; show the default state without persisting it.
            setCheckedNoPersist(defaultValue != null && (Boolean) defaultValue);
        }
    }

    /** Sets the checked state without writing it, so the setting stays as it is. */
    public void setCheckedNoPersist(boolean checked) {
        mSuppressPersist = true;
        try {
            setChecked(checked);
        } finally {
            mSuppressPersist = false;
        }
    }

    @Override
    protected boolean persistBoolean(boolean value) {
        if (mSuppressPersist) return true;
        String key = getKey();
        if (key == null) return false;

        try {
            Settings.Secure.putInt(getContext().getContentResolver(),
                key, value ? 1 : 0);
            return true;
        } catch (Exception e) {
            android.util.Log.e("CustomSwitch", "Error persisting value", e);
            return false;
        }
    }

    @Override
    protected boolean getPersistedBoolean(boolean defaultReturnValue) {
        String key = getKey();
        if (key == null) return defaultReturnValue;

        try {
            int value = Settings.Secure.getInt(getContext().getContentResolver(), key);
            return value == 1;
        } catch (Settings.SettingNotFoundException e) {
            return defaultReturnValue;
        }
    }
}
