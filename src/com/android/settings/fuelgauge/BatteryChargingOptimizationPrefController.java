package com.android.settings.fuelgauge;

import android.content.Context;
import android.database.ContentObserver;
import android.ext.power.BatteryBypassCharging;
import android.ext.power.BatteryChargeLimit;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import android.icu.text.NumberFormat;

import com.android.settings.R;
import com.android.settings.ext.BoolSettingFragmentPrefController;
import com.android.settings.ext.ExtSettingControllerHelper;

public class BatteryChargingOptimizationPrefController extends BoolSettingFragmentPrefController {
    private static final String TAG = "BatteryChargeLimitPrefController";

    public BatteryChargingOptimizationPrefController(Context ctx, String key) {
        super(ctx, key, BatteryChargeLimit.getSetting());
    }

    private final ContentObserver mBypassObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    if (preference != null) {
                        updateState(preference);
                    }
                }
            };

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        super.onResume(owner);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.BATTERY_BYPASS_STATE),
                false, mBypassObserver);
        if (preference != null) {
            updateState(preference);
        }
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        mContext.getContentResolver().unregisterContentObserver(mBypassObserver);
        super.onPause(owner);
    }

    @Override
    public CharSequence getSummary() {
        if (BatteryBypassCharging.isEnabled(mContext)) {
            return BatteryChargingOptimizationFragment.getBypassSummary(mContext);
        }
        return super.getSummary();
    }

    @Override
    protected CharSequence getSummaryOn() {
        return mContext.getString(R.string.charging_optimization_entry_summary_charge_limit,
                        NumberFormat.getPercentInstance().format(BatteryChargeLimit.CHARGE_LEVEL / 100f));
    }

    @Override
    protected CharSequence getSummaryOff() {
        return mContext.getString(R.string.charging_optimization_summary_off);
    }

    static int getAvailabilityStatus(Context ctx) {
        if (!BatteryChargeLimit.isGoogleDevice()) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return ExtSettingControllerHelper.getGlobalSettingAvailability(ctx);
    }

    @Override
    public int getAvailabilityStatus() {
        return getAvailabilityStatus(mContext);
    }
}
