package com.android.settings.fuelgauge;

import android.content.Context;
import android.database.ContentObserver;
import android.ext.power.BatteryBypassCharging;
import android.ext.power.BatteryChargeLimit;
import android.icu.text.NumberFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.SelectorWithWidgetPreference;

public class BatteryChargingOptimizationFragment extends DashboardFragment {
    private final SelectorWithWidgetPreference[] mOptions = new SelectorWithWidgetPreference[3];
    private final ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            refresh();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = requireContext();
        getActivity().setTitle(R.string.charging_optimization_title);
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        String[] titles = {
                getString(R.string.charging_optimization_summary_off),
                getString(R.string.charging_optimization_entry_summary_charge_limit,
                        NumberFormat.getPercentInstance().format(BatteryChargeLimit.CHARGE_LEVEL / 100f)),
                getString(R.string.bypass_charging_title)
        };
        for (int mode = 0; mode < mOptions.length; mode++) {
            final int requestedMode = mode;
            SelectorWithWidgetPreference pref = new SelectorWithWidgetPreference(context);
            pref.setKey("charging_mode_" + mode);
            pref.setTitle(titles[mode]);
            pref.setPersistent(false);
            pref.setOnClickListener(clicked -> {
                BatteryBypassCharging.requestMode(context, requestedMode);
                refresh();
            });
            mOptions[mode] = pref;
            screen.addPreference(pref);
        }
        FooterPreference footer = new FooterPreference(context);
        footer.setTitle(R.string.bypass_charging_footer);
        screen.addPreference(footer);
        FooterPreference limitFooter = new FooterPreference(context);
        limitFooter.setTitle(getString(R.string.charging_optimization_footer_message_charging_limit,
                NumberFormat.getPercentInstance().format(BatteryChargeLimit.CHARGE_LEVEL / 100f),
                NumberFormat.getPercentInstance().format(1f)));
        screen.addPreference(limitFooter);
        setPreferenceScreen(screen);
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        var resolver = requireContext().getContentResolver();
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.BATTERY_BYPASS_STATE),
                false, mObserver);
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.BATTERY_CHARGE_LIMIT),
                false, mObserver);
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.BATTERY_BYPASS_CHARGING),
                false, mObserver);
        refresh();
    }

    @Override
    public void onPause() {
        requireContext().getContentResolver().unregisterContentObserver(mObserver);
        super.onPause();
    }

    private void refresh() {
        if (mOptions[0] == null) {
            return;
        }
        Context context = requireContext();
        int selected = BatteryBypassCharging.getMode(context);
        boolean enabled = context.getUser().isSystem() && BatteryChargeLimit.isGoogleDevice();
        for (int mode = 0; mode < mOptions.length; mode++) {
            mOptions[mode].setChecked(mode == selected);
            mOptions[mode].setEnabled(enabled && (mode == BatteryBypassCharging.MODE_BYPASS
                    ? BatteryBypassCharging.isAvailable(context)
                    : BatteryBypassCharging.isPolicyAvailable(context)));
        }
        mOptions[BatteryBypassCharging.MODE_BYPASS].setSummary(getBypassSummary(context));
    }

    static CharSequence getBypassSummary(Context context) {
        CharSequence status = getBypassStatusSummary(context);
        if (!BatteryBypassCharging.isEnabled(context)) {
            return status;
        }
        String returnsTo = BatteryChargeLimit.isChargeLimitEnabled(context)
                ? context.getString(R.string.bypass_charging_returns_to_limit,
                        NumberFormat.getPercentInstance().format(BatteryChargeLimit.CHARGE_LEVEL / 100f))
                : context.getString(R.string.bypass_charging_returns_to_off);
        return status + "\n" + returnsTo;
    }

    private static CharSequence getBypassStatusSummary(Context context) {
        int held = BatteryBypassCharging.getHeldLevel(context);
        if (held > 0) {
            return context.getString(R.string.bypass_charging_held_level,
                    NumberFormat.getPercentInstance().format(held / 100f));
        }
        int message = switch (BatteryBypassCharging.getAvailability(context)) {
            case BatteryBypassCharging.AVAILABLE -> BatteryBypassCharging.isEnabled(context)
                    ? R.string.bypass_charging_waiting_for_power : R.string.bypass_charging_summary;
            case BatteryBypassCharging.SERVICE_MISSING -> R.string.bypass_charging_unavailable_service;
            case BatteryBypassCharging.HAL_ERROR -> R.string.bypass_charging_unavailable_hal;
            case BatteryBypassCharging.READBACK_ERROR -> R.string.bypass_charging_unavailable_readback;
            case BatteryBypassCharging.LEVEL_UNKNOWN -> R.string.bypass_charging_unavailable_level;
            case BatteryBypassCharging.RECOVERY_ERROR -> R.string.bypass_charging_unavailable_recovery;
            case BatteryBypassCharging.APPLYING -> R.string.bypass_charging_applying;
            default -> R.string.bypass_charging_checking;
        };
        return context.getString(message);
    }

    @Override
    public int getMetricsCategory() {
        return METRICS_CATEGORY_UNKNOWN;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return 0;
    }

    @Override
    protected String getLogTag() {
        return "BatteryChargingOptimization";
    }
}
