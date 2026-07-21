package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceScreen;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.internal.view.RotationPolicy;


public class RotationSettingsFragment extends SettingsPreferenceFragment {

    private static final String KEY_AUTO_ROTATE_SWITCH = "auto_rotate_switch";
    private static final String KEY_ROTATE_90          = "rotation_mode_90";
    private static final String KEY_ROTATE_180         = "rotation_mode_180";
    private static final String KEY_ROTATE_270         = "rotation_mode_270";

    private MainSwitchPreference mAutoRotateSwitch;
    private CheckBoxPreference m90, m180, m270;
    private RotationAnglePreferenceController mC90, mC180, mC270;

    private final RotationPolicy.RotationPolicyListener mListener =
        new RotationPolicy.RotationPolicyListener() {
            @Override
            public void onChange() {
                if (mAutoRotateSwitch != null) {
                    mAutoRotateSwitch.setChecked(isAutoRotateOn());
                }
                refreshAngleCheckboxes();
            }
        };

    // The policy listener does not fire on angle-bitmask edits, so observe the setting too.
    private final ContentObserver mAnglesObserver =
        new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                refreshAngleCheckboxes();
            }
        };

    private void refreshAngleCheckboxes() {
        if (mC90 != null)   mC90.updateState(m90);
        if (mC180 != null)  mC180.updateState(m180);
        if (mC270 != null)  mC270.updateState(m270);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState,
                                    String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        setPreferencesFromResource(R.xml.rotation_settings, rootKey);

        Context ctx = getContext();
        PreferenceScreen screen = getPreferenceScreen();
        if (ctx == null || screen == null) {
            return;
        }

        // Also reachable via the exported AUTO_ROTATE_SETTINGS activity, so gate on
        // rotation support here.
        if (!RotationPolicy.isRotationSupported(ctx)) {
            screen.removeAll();
            return;
        }

        mAutoRotateSwitch = screen.findPreference(KEY_AUTO_ROTATE_SWITCH);
        if (mAutoRotateSwitch != null) {
            mAutoRotateSwitch.setOnPreferenceChangeListener((pref, newVal) -> {
                boolean on = (Boolean) newVal;
                RotationPolicy.setRotationLock(ctx, !on, "RotationSettingsFragment");
                return true;
            });
        }

        m90  = screen.findPreference(KEY_ROTATE_90);
        m180 = screen.findPreference(KEY_ROTATE_180);
        m270 = screen.findPreference(KEY_ROTATE_270);

        mC90   = new RotationAnglePreferenceController(ctx, KEY_ROTATE_90,
                RotationAnglePreferenceController.BIT_90);
        mC180  = new RotationAnglePreferenceController(ctx, KEY_ROTATE_180,
                RotationAnglePreferenceController.BIT_180);
        mC270  = new RotationAnglePreferenceController(ctx, KEY_ROTATE_270,
                RotationAnglePreferenceController.BIT_270);

        mC90.displayPreference(screen);
        mC180.displayPreference(screen);
        mC270.displayPreference(screen);
    }

    @Override
    public void onStart() {
        super.onStart();
        RotationPolicy.registerRotationPolicyListener(getContext(), mListener);
        if (getContext() != null) {
            getContext().getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION_ANGLES),
                false, mAnglesObserver);
        }
        mListener.onChange();
    }

    @Override
    public void onStop() {
        super.onStop();
        RotationPolicy.unregisterRotationPolicyListener(getContext(), mListener);
        if (getContext() != null) {
            getContext().getContentResolver().unregisterContentObserver(mAnglesObserver);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(getString(R.string.accelerometer_title));
        }
    }

    private boolean isAutoRotateOn() {
        int v = android.provider.Settings.System.getInt(
            getContext().getContentResolver(),
            android.provider.Settings.System.ACCELEROMETER_ROTATION, 0);
        return v == 1 && !RotationPolicy.isRotationLocked(getContext());
    }
}
