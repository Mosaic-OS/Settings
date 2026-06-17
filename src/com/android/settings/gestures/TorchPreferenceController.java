package com.android.settings.gestures;

import android.content.Context;
import android.provider.Settings;

import com.android.settings.core.TogglePreferenceController;

/**
 * Parent controller for Torch long-press feature.
 * Controls the main switch and navigates into its detailed settings.
 */
public class TorchPreferenceController extends TogglePreferenceController {
    private static final String KEY = "gesture_torch";

    public TorchPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean isChecked() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TORCH_LONG_PRESS_POWER_GESTURE, 0) == 1;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.TORCH_LONG_PRESS_POWER_GESTURE,
                isChecked ? 1 : 0);
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return 0;
    }
}
