package com.android.settings.statusbar;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.view.DisplayCutout;

import com.android.settings.SettingsPreferenceFragment;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;

import java.util.List;


public class NetworkTrafficSettingsFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "NetworkTrafficSettings";
    private static final String STATUS_BAR_CLOCK_STYLE = "status_bar_clock";

    private static final int POSITION_START = 0;
    private static final int POSITION_END = 2;

    private static final int UNITS_KILOBITS = 0;
    private static final int UNITS_MEGABITS = 1;
    private static final int UNITS_KILOBYTES = 2;
    private static final int UNITS_MEGABYTES = 3;
    private static final int UNITS_AUTOBYTES = 4;

    private static final int SHOW_UNITS_OFF = 0;
    private static final int SHOW_UNITS_ON = 1;
    private static final int SHOW_UNITS_COMPACT = 2;

    private ListPreference mNetTrafficMode;
    private ListPreference mNetTrafficPosition;
    private SwitchPreferenceCompat mNetTrafficAutohide;
    private ListPreference mNetTrafficUnits;
    private ListPreference mNetTrafficShowUnits;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.network_traffic_settings);
        getActivity().setTitle(R.string.network_traffic_settings_title);

        final ContentResolver resolver = getActivity().getContentResolver();

        mNetTrafficMode = (ListPreference) findPreference(Settings.Secure.NETWORK_TRAFFIC_MODE);
        if (mNetTrafficMode != null) {
            mNetTrafficMode.setOnPreferenceChangeListener(this);
            int mode = Settings.Secure.getInt(resolver,
                    Settings.Secure.NETWORK_TRAFFIC_MODE, 0);
            mNetTrafficMode.setValue(String.valueOf(mode));
        }

        // Position
        mNetTrafficPosition = (ListPreference) findPreference(Settings.Secure.NETWORK_TRAFFIC_POSITION);
        if (mNetTrafficPosition != null) {
            mNetTrafficPosition.setOnPreferenceChangeListener(this);

            // Adjust entries depending on RTL
            if (getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                mNetTrafficPosition.setEntries(R.array.network_traffic_position_entries_rtl);
                mNetTrafficPosition.setEntryValues(R.array.network_traffic_position_values);
            } else {
                mNetTrafficPosition.setEntries(R.array.network_traffic_position_entries);
                mNetTrafficPosition.setEntryValues(R.array.network_traffic_position_values);
            }

            int position = Settings.Secure.getInt(resolver,
                    Settings.Secure.NETWORK_TRAFFIC_POSITION, POSITION_END);
            mNetTrafficPosition.setValue(String.valueOf(position));
        }

        mNetTrafficAutohide = (SwitchPreferenceCompat) findPreference(Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE);
        if (mNetTrafficAutohide != null) {
            boolean checked = Settings.Secure.getInt(resolver,
                    Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE, 0) == 1;
            mNetTrafficAutohide.setChecked(checked);
            mNetTrafficAutohide.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean v = (Boolean) newValue;
                Settings.Secure.putInt(getActivity().getContentResolver(),
                        Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE, v ? 1 : 0);
                return true;
            });
        }

        // Units
        mNetTrafficUnits = (ListPreference) findPreference(Settings.Secure.NETWORK_TRAFFIC_UNITS);
        if (mNetTrafficUnits != null) {
            mNetTrafficUnits.setOnPreferenceChangeListener(this);
            int units = Settings.Secure.getInt(resolver,
                    Settings.Secure.NETWORK_TRAFFIC_UNITS, UNITS_KILOBYTES);
            mNetTrafficUnits.setValue(String.valueOf(units));
            // show units preference depends on units
            mNetTrafficShowUnits = (ListPreference) findPreference(Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS);
            if (mNetTrafficShowUnits != null) {
                mNetTrafficShowUnits.setOnPreferenceChangeListener(this);
                adjustShowUnitsState(units, resolver);
            }
        }

        updateEnabledStates(Settings.Secure.getInt(resolver, Settings.Secure.NETWORK_TRAFFIC_MODE, 0));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final ContentResolver resolver = getActivity().getContentResolver();

        // All of these list preferences carry integer entryValues; guard against a
        // malformed value rather than letting a NumberFormatException crash the UI.
        final int value;
        try {
            value = Integer.parseInt((String) newValue);
        } catch (NumberFormatException e) {
            return false;
        }

        if (preference == mNetTrafficMode) {
            int mode = value;

            int oldMode = Settings.Secure.getInt(resolver, Settings.Secure.NETWORK_TRAFFIC_MODE, 0);
            Settings.Secure.putInt(resolver, Settings.Secure.NETWORK_TRAFFIC_MODE, mode);
            if (oldMode == 0 && mode != 0) {
                Settings.Secure.putInt(resolver, Settings.Secure.NETWORK_TRAFFIC_POSITION, POSITION_END);
                if (mNetTrafficPosition != null) {
                    mNetTrafficPosition.setValue(String.valueOf(POSITION_END));
                }
            }

            updateEnabledStates(mode);
        } else if (preference == mNetTrafficPosition) {
            int position = value;
            Settings.Secure.putInt(resolver, Settings.Secure.NETWORK_TRAFFIC_POSITION, position);
        } else if (preference == mNetTrafficUnits) {
            int units = value;
            Settings.Secure.putInt(resolver, Settings.Secure.NETWORK_TRAFFIC_UNITS, units);
            adjustShowUnitsState(units, resolver);
        } else if (preference == mNetTrafficShowUnits) {
            int showUnits = value;
            Settings.Secure.putInt(resolver, Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, showUnits);
        }
        return true;
    }

    private void adjustShowUnitsState(int units, ContentResolver resolver) {
        int showUnits = Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, SHOW_UNITS_ON);
        if (units == UNITS_KILOBYTES || units == UNITS_MEGABYTES) {
            // off, on, compact
            if (mNetTrafficShowUnits != null) {
                mNetTrafficShowUnits.setEntries(R.array.network_traffic_show_units_entries);
                mNetTrafficShowUnits.setEntryValues(R.array.network_traffic_show_units_values);
            }
        } else {
            boolean putShowUnits = false;
            if (units == UNITS_AUTOBYTES) {
                if (showUnits == SHOW_UNITS_OFF) {
                    showUnits = SHOW_UNITS_COMPACT;
                    putShowUnits = true;
                }
                if (mNetTrafficShowUnits != null) {
                    mNetTrafficShowUnits.setEntries(R.array.network_traffic_show_units_entries_auto);
                    mNetTrafficShowUnits.setEntryValues(R.array.network_traffic_show_units_values_auto);
                }
            } else {
                if (showUnits == SHOW_UNITS_COMPACT) {
                    showUnits = SHOW_UNITS_ON;
                    putShowUnits = true;
                }
                if (mNetTrafficShowUnits != null) {
                    mNetTrafficShowUnits.setEntries(R.array.network_traffic_show_units_entries_bits);
                    mNetTrafficShowUnits.setEntryValues(R.array.network_traffic_show_units_values_bits);
                }
            }
            if (putShowUnits) {
                Settings.Secure.putInt(resolver,
                        Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, showUnits);
            }
        }
        if (mNetTrafficShowUnits != null) {
            mNetTrafficShowUnits.setValue(String.valueOf(showUnits));
        }
    }

    private void updateEnabledStates(int mode) {
        final boolean enabled = mode != 0;
        if (mNetTrafficPosition != null) mNetTrafficPosition.setEnabled(enabled);
        if (mNetTrafficAutohide != null) mNetTrafficAutohide.setEnabled(enabled);
        if (mNetTrafficUnits != null) mNetTrafficUnits.setEnabled(enabled);
        if (mNetTrafficShowUnits != null) mNetTrafficShowUnits.setEnabled(enabled);
    }

    private int getClockPosition() {
        return Settings.System.getInt(getActivity().getContentResolver(),
                STATUS_BAR_CLOCK_STYLE, 2);
    }
    
    @Override
    public int getMetricsCategory() {
        return 0;
    }
}
