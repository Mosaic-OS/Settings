package com.android.settings.display;

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import com.android.internal.view.RotationPolicy;

public class RotationSettingsPreferenceController extends TogglePreferenceController
        implements LifecycleObserver, OnStart, OnStop {

    private PrimarySwitchPreference mPreference;
    private final RotationPolicy.RotationPolicyListener mListener =
            new RotationPolicy.RotationPolicyListener() {
        @Override
        public void onChange() {
            if (mPreference != null) {
                mPreference.setChecked(isChecked());
            }
        }
    };

    public RotationSettingsPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return RotationPolicy.isRotationSupported(mContext)
                ? AVAILABLE
                : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(@NonNull PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onStart() {
        RotationPolicy.registerRotationPolicyListener(mContext, mListener);
    }

    @Override
    public void onStop() {
        RotationPolicy.unregisterRotationPolicyListener(mContext, mListener);
    }

    @Override
    public boolean isChecked() {
        int v = Settings.System.getInt(
                mContext.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0);
        return v == 1 && !RotationPolicy.isRotationLocked(mContext);
    }

    @Override
    public boolean setChecked(boolean on) {
        Settings.System.putInt(
                mContext.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION,
                on ? 1 : 0);
        if (on) {
            RotationPolicy.setRotationLock(mContext, false, "RotationSettingsPreferenceController");
        } else {
            RotationPolicy.setRotationLockAtAngle(mContext, true,
                    RotationPolicy.getNaturalRotation(), "RotationSettingsPreferenceController");
        }
        return true;
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getString(R.string.rotation_settings_summary);
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_display;
    }
}
