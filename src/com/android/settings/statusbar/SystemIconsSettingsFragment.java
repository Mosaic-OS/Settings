package com.android.settings.statusbar;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.Settings;
import android.telecom.TelecomManager;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.Utils;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.search.SearchIndexableRaw;
import com.android.settingslib.widget.AppSwitchPreference;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SearchIndexable(forTarget = SearchIndexable.MOBILE)
public class SystemIconsSettingsFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String ICON_HIDE_LIST = "icon_blacklist";
    private static final String HIDE_LOW_PRIORITY =
            "mosaic_status_bar_hide_low_priority_icons";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String CONNECTIVITY = "status_bar_connectivity";
    private static final String SOUND = "status_bar_sound";
    private static final String SYSTEM = "status_bar_system";
    private static final String[] CATEGORIES = {CONNECTIVITY, SOUND, SYSTEM};

    private static final IconSpec[] ICONS = {
            new IconSpec("alarm_clock", "status_bar_alarm", R.string.status_bar_custom_icon_alarm,
                    R.drawable.ic_zen_mode_sound_alarms, SOUND),
            new IconSpec("rotate", "status_bar_settings_auto_rotation",
                    R.string.accelerometer_title, R.drawable.ic_screen_rotation_24dp,
                    SYSTEM),
            new IconSpec("headset", "headset", R.string.status_bar_custom_icon_headset,
                    com.android.settingslib.R.drawable.ic_headphone, SOUND),
            new IconSpec("data_saver", "data_saver", R.string.status_bar_custom_icon_data_saver,
                    R.drawable.ic_data_saver, SYSTEM),
            new IconSpec("ime", "accessibility_status_bar_input_method_indicator",
                    R.string.status_bar_custom_icon_ime, R.drawable.ic_settings_keyboards, SYSTEM),
            new IconSpec("tty", null, R.string.status_bar_custom_icon_tty,
                    R.drawable.ic_status_bar_tty, SYSTEM),
            new IconSpec("managed_profile", "status_bar_work", R.string.status_bar_custom_icon_work,
                    com.android.internal.R.drawable.ic_corp_statusbar_icon, SYSTEM),
            new IconSpec("connected_display", "connected_display_icon_desc",
                    R.string.status_bar_custom_icon_display,
                    com.android.settingslib.R.drawable.ic_external_display, CONNECTIVITY),
            new IconSpec("vpn", "legacy_vpn_name", R.string.status_bar_custom_icon_vpn,
                    R.drawable.ic_vpn_key, CONNECTIVITY),
            new IconSpec("bluetooth", "quick_settings_bluetooth_label",
                    R.string.status_bar_custom_icon_bluetooth,
                    com.android.internal.R.drawable.ic_qs_bluetooth,
                    CONNECTIVITY),
            new IconSpec("mute", "volume_ringer_status_silent", R.string.status_bar_custom_icon_mute,
                    R.drawable.ic_notifications_off_24dp, SOUND),
            new IconSpec("volume", "volume_ringer_status_vibrate",
                    R.string.status_bar_custom_icon_vibrate, R.drawable.ic_volume_ringer_vibrate,
                    SOUND),
            new IconSpec("zen", "quick_settings_dnd_label", R.string.status_bar_custom_icon_zen,
                    com.android.settingslib.R.drawable.ic_do_not_disturb_on_24dp, SOUND),
            new IconSpec("ethernet", "status_bar_ethernet", R.string.status_bar_custom_icon_ethernet,
                    R.drawable.ic_settings_ethernet, CONNECTIVITY),
            new IconSpec("hotspot", "quick_settings_hotspot_label",
                    R.string.status_bar_custom_icon_hotspot, R.drawable.ic_hotspot, CONNECTIVITY),
            new IconSpec("satellite", "accessibility_status_bar_satellite_symbol",
                    R.string.status_bar_custom_icon_satellite, R.drawable.ic_android_satellite_24px,
                    CONNECTIVITY),
            new IconSpec("mobile", "quick_settings_cellular_detail_title",
                    R.string.status_bar_custom_icon_mobile, R.drawable.ic_network_cell, CONNECTIVITY),
            new IconSpec("wifi", "quick_settings_wifi_label", R.string.status_bar_custom_icon_wifi,
                    R.drawable.ic_wifi_signal_4, CONNECTIVITY),
            new IconSpec("airplane", "status_bar_airplane", R.string.status_bar_custom_icon_airplane,
                    R.drawable.ic_airplanemode_active, CONNECTIVITY),
            new IconSpec("clock", null, R.string.status_bar_custom_clock,
                    R.drawable.ic_settings_date_time, SYSTEM),
            new IconSpec("battery", "battery", R.string.status_bar_custom_icon_battery,
                    R.drawable.ic_battery_full, SYSTEM),
    };

    private Resources mSystemUiResources;
    private AppSwitchPreference mShowLowPriority;
    private final Map<String, AppSwitchPreference> mSwitches = new LinkedHashMap<>();
    private final ContentObserver mObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateSwitches();
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.status_bar_system_icons, rootKey);
        mSystemUiResources = getSystemUiResources(getContext());
        mShowLowPriority = findPreference(HIDE_LOW_PRIORITY);
        if (mSystemUiResources != null) {
            final int title = mSystemUiResources.getIdentifier(
                    "tuner_low_priority", "string", SYSTEM_UI);
            if (title != 0) mShowLowPriority.setTitle(mSystemUiResources.getString(title));
        }
        mShowLowPriority.setOnPreferenceChangeListener(this);
        mSwitches.clear();
        for (AvailableIcon icon : getAvailableIcons(getContext())) {
            final PreferenceCategory category = findPreference(icon.spec.category);
            final AppSwitchPreference preference = new AppSwitchPreference(getPrefContext());
            preference.setKey(icon.spec.slot);
            preference.setTitle(icon.title);
            preference.setIcon(getPrefContext().getDrawable(icon.spec.drawable).mutate());
            preference.getIcon().setTintList(Utils.getColorAttr(
                    getPrefContext(), android.R.attr.colorControlNormal));
            preference.setOrder(category.getPreferenceCount());
            preference.setPersistent(false);
            preference.setOnPreferenceChangeListener(this);
            category.addPreference(preference);
            mSwitches.put(icon.spec.slot, preference);
        }
        for (String key : CATEGORIES) {
            final PreferenceCategory category = findPreference(key);
            category.setVisible(category.getPreferenceCount() != 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(ICON_HIDE_LIST), false, mObserver);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.STATUS_BAR_SHOW_VIBRATE_ICON), false, mObserver);
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(HIDE_LOW_PRIORITY), false, mObserver);
        updateSwitches();
    }

    @Override
    public void onPause() {
        getContentResolver().unregisterContentObserver(mObserver);
        super.onPause();
    }

    private Set<String> readHiddenIcons() {
        final Set<String> hidden = new LinkedHashSet<>();
        final String stored = Settings.Secure.getString(getContentResolver(), ICON_HIDE_LIST);
        if (stored != null) {
            hidden.addAll(Arrays.asList(stored.split(",")));
        } else if (mSystemUiResources != null) {
            // Materialize all effective defaults before the first explicit hide-list write
            addConfiguredIcons(hidden, "config_statusBarIconsToExclude");
            addConfiguredIcons(hidden, "config_collapsed_statusbar_icon_blocklist");
            final String volume = getString(com.android.internal.R.string.status_bar_volume);
            if (Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.STATUS_BAR_SHOW_VIBRATE_ICON, 0) != 0) {
                hidden.remove(volume);
            } else {
                hidden.add(volume);
            }
        }
        hidden.remove("");
        return hidden;
    }

    private void addConfiguredIcons(Set<String> hidden, String name) {
        final int id = mSystemUiResources.getIdentifier(name, "array", SYSTEM_UI);
        if (id != 0) hidden.addAll(Arrays.asList(mSystemUiResources.getStringArray(id)));
    }

    private void updateSwitches() {
        mShowLowPriority.setChecked(Settings.Secure.getInt(
                getContentResolver(), HIDE_LOW_PRIORITY, 0) == 0);
        final Set<String> hidden = readHiddenIcons();
        mSwitches.forEach((slot, preference) -> preference.setChecked(!hidden.contains(slot)));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!(newValue instanceof Boolean)) return false;
        if (preference == mShowLowPriority) {
            return Settings.Secure.putInt(getContentResolver(), HIDE_LOW_PRIORITY,
                    (Boolean) newValue ? 0 : 1);
        }
        if (!mSwitches.containsKey(preference.getKey()) || mSystemUiResources == null) {
            return false;
        }
        final Set<String> hidden = readHiddenIcons();
        if ((Boolean) newValue) {
            hidden.remove(preference.getKey());
        } else {
            hidden.add(preference.getKey());
        }
        return Settings.Secure.putString(getContentResolver(), ICON_HIDE_LIST,
                String.join(",", hidden));
    }

    private static Resources getSystemUiResources(Context context) {
        try {
            return context.createPackageContext(SYSTEM_UI, 0)
                    .createConfigurationContext(context.getResources().getConfiguration())
                    .getResources();
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static List<AvailableIcon> getAvailableIcons(Context context) {
        final List<AvailableIcon> icons = new ArrayList<>();
        final Resources resources = getSystemUiResources(context);
        if (resources == null) return icons;
        final Set<String> slots = new LinkedHashSet<>(Arrays.asList(
                resources.getStringArray(com.android.internal.R.array.config_statusBarIcons)));
        slots.add("clock");
        for (IconSpec spec : ICONS) {
            if (!slots.contains(spec.slot)) continue;
            try {
                if (isAvailable(context, spec.slot)) {
                    icons.add(new AvailableIcon(spec, spec.getTitle(context, resources)));
                }
            } catch (RuntimeException e) {
                // A failed capability query must leave the control unavailable
                continue;
            }
        }
        final Collator collator = Collator.getInstance(
                context.getResources().getConfiguration().getLocales().get(0));
        icons.sort((left, right) -> {
            final int order = collator.compare(left.title, right.title);
            return order != 0 ? order : left.spec.slot.compareTo(right.spec.slot);
        });
        return icons;
    }

    private static boolean isAvailable(Context context, String slot) {
        final PackageManager pm = context.getPackageManager();
        final boolean mobile = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        final boolean wifi = pm.hasSystemFeature(PackageManager.FEATURE_WIFI);
        switch (slot) {
            case "rotate":
                final boolean composeIcons = com.android.systemui.Flags.statusBarSystemStatusIconsInCompose()
                        && com.android.settingslib.flags.Flags.newStatusBarIcons()
                        && com.android.systemui.Flags.sceneContainer()
                        && com.android.systemui.Flags.statusIconsInComposeRefresh();
                return !composeIcons
                        && pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER);
            case "headset": return pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
            case "data_saver": return mobile;
            case "tty":
                final TelecomManager telecom = context.getSystemService(TelecomManager.class);
                return mobile && telecom != null && telecom.isTtySupported();
            case "managed_profile": return pm.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS);
            case "connected_display":
                return pm.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS);
            case "bluetooth": return pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
            case "volume":
                final Vibrator vibrator = context.getSystemService(Vibrator.class);
                return vibrator != null && vibrator.hasVibrator();
            case "ethernet": return pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET)
                    || pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST);
            case "hotspot":
                final WifiManager wifiManager = context.getSystemService(WifiManager.class);
                return wifi && wifiManager != null && wifiManager.isPortableHotspotSupported();
            case "satellite":
                return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_SATELLITE);
            case "mobile": return mobile;
            case "wifi": return wifi;
            case "airplane": return mobile || wifi;
            default: return true;
        }
    }

    private static final class IconSpec {
        final String slot;
        final String systemUiTitle;
        final int fallbackTitle;
        final int drawable;
        final String category;

        IconSpec(String slot, String systemUiTitle, int fallbackTitle, int drawable, String category) {
            this.slot = slot;
            this.systemUiTitle = systemUiTitle;
            this.fallbackTitle = fallbackTitle;
            this.drawable = drawable;
            this.category = category;
        }

        String getTitle(Context context, Resources resources) {
            if (systemUiTitle != null) {
                final int id = resources.getIdentifier(systemUiTitle, "string", SYSTEM_UI);
                if (id != 0) return resources.getString(id);
            }
            return context.getString(fallbackTitle);
        }
    }

    private static final class AvailableIcon {
        final IconSpec spec;
        final String title;

        AvailableIcon(IconSpec spec, String title) {
            this.spec = spec;
            this.title = title;
        }
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.status_bar_system_icons) {
                @Override
                public List<SearchIndexableRaw> getRawDataToIndex(Context context, boolean enabled) {
                    final List<SearchIndexableRaw> data = new ArrayList<>();
                    getAvailableIcons(context).forEach(icon -> {
                        final SearchIndexableRaw item = new SearchIndexableRaw(context);
                        item.key = icon.spec.slot;
                        item.title = icon.title;
                        item.screenTitle = context.getString(R.string.status_bar_custom_system_icons);
                        data.add(item);
                    });
                    return data;
                }
            };
}
