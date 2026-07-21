package com.android.settings.gestures;

import android.content.Context;
import android.provider.Settings;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import com.android.settings.core.BasePreferenceController;

public class NavigationBarPillPreferenceController extends BasePreferenceController implements
        Preference.OnPreferenceChangeListener {

    private static final String SHOW_NAVIGATION_PILL = "show_navigation_pill";

    public NavigationBarPillPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void updateState(Preference preference) {
        int value = Settings.Secure.getInt(mContext.getContentResolver(),
                SHOW_NAVIGATION_PILL, 1);
        ((TwoStatePreference) preference).setChecked(value != 0);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean enabled = (Boolean) newValue;
        Settings.Secure.putInt(mContext.getContentResolver(),
                SHOW_NAVIGATION_PILL, enabled ? 1 : 0);
        return true;
    }
}
