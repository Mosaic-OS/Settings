package com.android.settings.display;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import com.android.internal.view.RotationPolicy;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;


public class RotationAnglePreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, Preference.OnPreferenceChangeListener {
    private static final String TAG = "RotationAnglePC";

    // Bits of ACCELEROMETER_ROTATION_ANGLES; bit 0 (portrait) is always allowed.
    public static final int BIT_0   = 1 << 0;
    public static final int BIT_90  = 1 << 1;
    public static final int BIT_180 = 1 << 2;
    public static final int BIT_270 = 1 << 3;

    // Must match the defaultValue entries in rotation_settings.xml plus always-allowed 0deg.
    public static final int DEFAULT_MASK = BIT_0 | BIT_90 | BIT_270;

    private final String mPreferenceKey;
    private final int    mBitValue;
    private TwoStatePreference mPreference;
    public RotationAnglePreferenceController(@NonNull Context context,
                                             @NonNull String key,
                                             int bit) {
        super(context);
        mPreferenceKey = key;
        mBitValue      = bit;
    }
    @Override
    public String getPreferenceKey() {
        return mPreferenceKey;
    }
    @Override
    public boolean isAvailable() {
        return RotationPolicy.isRotationSupported(mContext);
    }
    @Override
    public void updateState(@NonNull Preference preference) {
        mPreference = (TwoStatePreference) preference;
        int mask = getCurrentMask();
        boolean isChecked = (mask & mBitValue) != 0;
        mPreference.setChecked(isChecked);
    }
    private int getCurrentMask() {
        int mask = 0;
        try {
            mask = Settings.System.getInt(
                    mContext.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION_ANGLES);
        } catch (Settings.SettingNotFoundException e) {
            mask = DEFAULT_MASK;
            Log.d(TAG, "getCurrentMask: not found, defaultMask=" + mask);
        }
        return mask;
    }
    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        boolean isChecked = (Boolean) newValue;
        saveState(isChecked);
        return true;
    }
    public void saveState(boolean isChecked) {
        ContentResolver resolver = mContext.getContentResolver();
        int mask = getCurrentMask();
        if (isChecked) {
            mask |= mBitValue;
        } else {
            mask &= ~mBitValue;
        }
        Settings.System.putInt(
                resolver,
                Settings.System.ACCELEROMETER_ROTATION_ANGLES,
                mask);
        if (mPreference != null) {
            mPreference.setChecked(isChecked);
        }
    }
    @Override
    public void displayPreference(androidx.preference.PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = (TwoStatePreference) screen.findPreference(mPreferenceKey);
        if (mPreference != null) {
            mPreference.setOnPreferenceChangeListener(this);
            updateState(mPreference);
        }
    }
}
