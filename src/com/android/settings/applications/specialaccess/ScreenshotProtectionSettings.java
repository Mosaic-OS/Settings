/*
 * Copyright (C) 2026 The MosaicOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.applications.specialaccess;

import android.app.AlertDialog;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.app.settings.SettingsEnums;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.internal.util.ScreenshotProtectionUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.applications.AppUtils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.AppEntry;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.AppSwitchPreference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@SearchIndexable(forTarget = SearchIndexable.MOBILE)
public class ScreenshotProtectionSettings extends SettingsPreferenceFragment
        implements ApplicationsState.Callbacks {
    private static final String TAG = "ScreenshotProtection";
    private static final String SETTING = Settings.Secure.SCREENSHOT_PROTECTION_ALLOWED_PACKAGES;
    private static final String IDENTITIES = Settings.Secure.SCREENSHOT_PROTECTION_PACKAGE_IDENTITIES;
    private static final String BLOCKED = Settings.Secure.SCREENSHOT_PROTECTION_BLOCKED_PACKAGES;
    private static final String DETECTED = Settings.Secure.SCREENSHOT_PROTECTION_DETECTED_PACKAGES;
    private static final String PREFERENCES = "screenshot_protection";
    private static final String SKIP_CONFIRMATION = "skip_allow_confirmation";
    private static final int PACKAGE_FLAGS = PackageManager.MATCH_DIRECT_BOOT_AWARE
            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private static final int MENU_FILTER = Menu.FIRST + 92;
    private static final String QUERY = "screenshot_protection_query";

    private final int mUserId = UserHandle.myUserId();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateState();
        }
    };
    private final BroadcastReceiver mPolicyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateState();
        }
    };
    private final ApplicationsState.AppFilter mFilter = new ApplicationsState.AppFilter() {
        @Override
        public void init() {}

        @Override
        public boolean filterApp(AppEntry entry) {
            return UserHandle.getUserId(entry.info.uid) == mUserId
                    && (entry.info.flags & ApplicationInfo.FLAG_INSTALLED) != 0
                    && entry.hasLauncherEntry
                    && ScreenshotProtectionUtils.canAllow(entry.info.packageName);
        }
    };

    private ApplicationsState.Session mSession;
    private PreferenceCategory mAppCategory;
    private Preference mStatus;
    private ArrayList<AppEntry> mApps = new ArrayList<>();
    private Set<String> mAllowed = Collections.emptySet();
    private Set<String> mBlocked = Collections.emptySet();
    private Set<String> mDetected = Collections.emptySet();
    private String mQuery = "";
    private boolean mResumed;
    private boolean mObserved;
    private boolean mPolicyObserved;
    private boolean mAvailable;
    private boolean mAdminBlocked;
    private AlertDialog mDialog;
    private boolean mAuthenticating;
    private Runnable mAuthenticatedAction;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.screenshot_protection_settings, rootKey);
        mAppCategory = findPreference("screenshot_protection_apps");
        mStatus = findPreference("screenshot_protection_status");
        mQuery = savedInstanceState == null ? "" : savedInstanceState.getString(QUERY, "");
        final ApplicationsState state = ApplicationsState.getInstance(
                (Application) requireContext().getApplicationContext());
        mSession = state.newSession(this);
        mSession.setSessionFlags(ApplicationsState.FLAG_SESSION_REQUEST_LAUNCHER
                | ApplicationsState.FLAG_SESSION_REQUEST_ICONS);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SPECIAL_ACCESS;
    }

    @Override
    public void onResume() {
        super.onResume();
        mResumed = true;
        requireActivity().setTitle(R.string.screenshot_protection_title);
        try {
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(SETTING),
                    false, mObserver, mUserId);
            mObserved = true;
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(IDENTITIES),
                    false, mObserver, mUserId);
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(BLOCKED),
                    false, mObserver, mUserId);
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(DETECTED),
                    false, mObserver, mUserId);
            requireContext().registerReceiver(mPolicyReceiver,
                    new IntentFilter(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED),
                    Context.RECEIVER_NOT_EXPORTED);
            mPolicyObserved = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to observe screenshot protection controls");
        }
        mSession.onResume();
        updateState();
        rebuild();
        if (mAuthenticatedAction != null) {
            final Runnable action = mAuthenticatedAction;
            mAuthenticatedAction = null;
            action.run();
        }
    }

    @Override
    public void onPause() {
        mResumed = false;
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        if (mObserved) {
            getContentResolver().unregisterContentObserver(mObserver);
            mObserved = false;
        }
        if (mPolicyObserved) {
            requireContext().unregisterReceiver(mPolicyReceiver);
            mPolicyObserved = false;
        }
        mSession.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mSession.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(QUERY, mQuery);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        final SearchView search = new SearchView(requireContext());
        search.setQueryHint(getString(R.string.screenshot_protection_search));
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                mQuery = query;
                showApps();
                return true;
            }
        });
        final MenuItem item = menu.add(Menu.NONE, MENU_FILTER, Menu.NONE,
                R.string.screenshot_protection_search);
        item.setIcon(android.R.drawable.ic_menu_search);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        item.setActionView(search);
        if (!mQuery.isEmpty()) {
            item.expandActionView();
            search.setQuery(mQuery, false);
        }
    }

    private boolean isAdminBlocked() {
        final DevicePolicyManager manager = requireContext().getSystemService(DevicePolicyManager.class);
        if (manager == null) {
            throw new IllegalStateException("Device policy service is unavailable");
        }
        return manager.getScreenCaptureDisabled(null, mUserId);
    }

    private void updateState() {
        if (!mResumed) {
            return;
        }
        mAvailable = false;
        mAllowed = Collections.emptySet();
        mBlocked = Collections.emptySet();
        mDetected = Collections.emptySet();
        try {
            mAdminBlocked = isAdminBlocked();
            final Set<String> packages = ScreenshotProtectionUtils.parse(read(SETTING));
            final Map<String, String> identities = ScreenshotProtectionUtils.parseIdentities(
                    Settings.Secure.getStringForUser(getContentResolver(), IDENTITIES, mUserId));
            for (String packageName : new ArrayList<>(packages)) {
                try {
                    final PackageInfo info = requireContext().getPackageManager()
                            .getPackageInfoAsUser(packageName, PACKAGE_FLAGS, mUserId);
                    if (!ScreenshotProtectionUtils.canAllow(packageName) || info.applicationInfo == null
                            || !ScreenshotProtectionUtils.identity(info.applicationInfo.uid, info.firstInstallTime)
                                    .equals(identities.get(packageName))) {
                        packages.remove(packageName);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    packages.remove(packageName);
                }
            }
            mBlocked = ScreenshotProtectionUtils.parse(read(BLOCKED));
            mDetected = ScreenshotProtectionUtils.parse(read(DETECTED));
            mAllowed = packages;
            mAvailable = mObserved && mPolicyObserved;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read screenshot protection controls");
        }
        mStatus.setVisible(!mAvailable || mAdminBlocked);
        mStatus.setSummary(mAvailable ? R.string.screenshot_protection_admin_disabled
                : R.string.screenshot_protection_unavailable);
        showApps();
    }

    private void showApps() {
        if (!mResumed || mAppCategory == null) {
            return;
        }
        mAppCategory.removeAll();
        final String query = mQuery.trim().toLowerCase(Locale.getDefault());
        for (AppEntry entry : mApps) {
            final String packageName = entry.info.packageName;
            if (!entry.label.toLowerCase(Locale.getDefault()).contains(query)
                    && !packageName.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            final AppSwitchPreference preference = new AppSwitchPreference(requireContext());
            preference.setKey(packageName);
            preference.setPersistent(false);
            preference.setTitle(entry.label);
            preference.setIcon(AppUtils.getIcon(requireContext(), entry));
            final boolean detected = mDetected.contains(packageName);
            final boolean allowed = !mAdminBlocked && ScreenshotProtectionUtils.isEffectivelyAllowed(
                    detected, mAllowed.contains(packageName), mBlocked.contains(packageName));
            final boolean changed = !mAdminBlocked && allowed == detected;
            preference.setChecked(allowed);
            preference.setSummary(allowed
                    ? (changed ? R.string.screenshot_protection_allowed_changed
                            : R.string.screenshot_protection_allowed)
                    : (changed ? R.string.screenshot_protection_not_allowed_changed
                            : R.string.screenshot_protection_not_allowed));
            preference.setEnabled(mAvailable && !mAdminBlocked);
            preference.setOnPreferenceChangeListener((ignored, value) -> {
                if (Boolean.TRUE.equals(value)) {
                    confirmAllow(entry);
                } else if (Boolean.FALSE.equals(value)) {
                    authenticateThen(() -> save(entry, false, 0, false));
                }
                return false;
            });
            mAppCategory.addPreference(preference);
        }
        if (mAppCategory.getPreferenceCount() == 0) {
            final Preference empty = new Preference(requireContext());
            empty.setTitle(R.string.screenshot_protection_no_apps);
            empty.setSelectable(false);
            mAppCategory.addPreference(empty);
        }
    }

    private void authenticateThen(Runnable action) {
        if (!mResumed || mAuthenticating) {
            return;
        }
        final KeyguardManager keyguard = requireContext().getSystemService(KeyguardManager.class);
        final BiometricManager biometrics = requireContext().getSystemService(BiometricManager.class);
        if (keyguard == null || biometrics == null) {
            showSaveFailure();
            return;
        }
        if (!keyguard.isDeviceSecure()) {
            action.run();
            return;
        }
        if (biometrics.canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
            showSaveFailure();
            return;
        }
        final BiometricPrompt prompt = new BiometricPrompt.Builder(requireContext())
                .setTitle(getString(R.string.screenshot_protection_title))
                .setSubtitle(biometrics.getStrings(AUTHENTICATORS).getPromptMessage())
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build();
        mAuthenticating = true;
        prompt.authenticate(new CancellationSignal(), requireContext().getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        mAuthenticating = false;
                        if (mResumed) {
                            action.run();
                        } else {
                            mAuthenticatedAction = action;
                        }
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence message) {
                        mAuthenticating = false;
                    }
                });
    }

    private boolean skipConfirmation() {
        try {
            return requireContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getBoolean(SKIP_CONFIRMATION, false);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read screenshot protection confirmation preference");
            return false;
        }
    }

    private void confirmAllow(AppEntry entry) {
        if (!mAvailable || mAdminBlocked || !mResumed || mDialog != null || mAuthenticating) {
            return;
        }
        try {
            final PackageInfo info = requireContext().getPackageManager()
                    .getPackageInfoAsUser(entry.info.packageName, PACKAGE_FLAGS, mUserId);
            if (!ScreenshotProtectionUtils.parse(read(DETECTED)).contains(entry.info.packageName)
                    || skipConfirmation()) {
                authenticateThen(() -> save(entry, true, info.firstInstallTime, false));
                return;
            }
            final CheckBox dontShowAgain = new CheckBox(requireContext());
            dontShowAgain.setText(R.string.screenshot_protection_dont_show_again);
            dontShowAgain.setFilterTouchesWhenObscured(true);
            final LinearLayout container = new LinearLayout(requireContext());
            final int padding = Math.round(24 * getResources().getDisplayMetrics().density);
            container.setPadding(padding, 0, padding, 0);
            container.addView(dontShowAgain);
            mDialog = new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.screenshot_protection_title)
                    .setMessage(getString(R.string.screenshot_protection_confirm, entry.label))
                    .setView(container)
                    .setPositiveButton(R.string.screenshot_protection_allow, (dialog, which) -> {
                        final boolean dontShow = dontShowAgain.isChecked();
                        authenticateThen(() -> {
                            if (save(entry, true, info.firstInstallTime, true) && dontShow) {
                                try {
                                    requireContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                                            .edit().putBoolean(SKIP_CONFIRMATION, true).apply();
                                } catch (RuntimeException e) {
                                    Log.w(TAG, "Unable to save screenshot protection confirmation preference");
                                }
                            }
                        });
                    })
                    .setNegativeButton(R.string.screenshot_protection_cancel, null)
                    .create();
            mDialog.setOnDismissListener(dialog -> mDialog = null);
            mDialog.show();
            mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            showSaveFailure();
        }
    }

    private boolean save(AppEntry entry, boolean allow, long installTime, boolean confirmed) {
        if (!mResumed || !mAvailable) {
            return false;
        }
        String rollbackAllowed = null;
        String rollbackIdentities = null;
        String rollbackBlocked = null;
        boolean mutationStarted = false;
        boolean accepted = false;
        try {
            if (isAdminBlocked() || !mFilter.filterApp(entry)) {
                throw new IllegalStateException("Screenshot protection change is unavailable");
            }
            final String packageName = entry.info.packageName;
            final Set<String> packages = ScreenshotProtectionUtils.parse(read(SETTING));
            final Set<String> blocked = ScreenshotProtectionUtils.parse(read(BLOCKED));
            final Map<String, String> identities = ScreenshotProtectionUtils.parseIdentities(read(IDENTITIES));
            final boolean detected = ScreenshotProtectionUtils.parse(read(DETECTED)).contains(packageName);
            final PackageInfo current = requireContext().getPackageManager()
                    .getPackageInfoAsUser(packageName, PACKAGE_FLAGS, mUserId);
            if (current.applicationInfo == null || current.applicationInfo.uid != entry.info.uid
                    || (allow && current.firstInstallTime != installTime)) {
                throw new IllegalStateException("Application changed during confirmation");
            }
            if (allow && detected && !confirmed && !skipConfirmation()) {
                confirmAllow(entry);
                return false;
            }
            packages.removeIf(name -> !ScreenshotProtectionUtils.canAllow(name));
            blocked.removeIf(name -> !ScreenshotProtectionUtils.canAllow(name));
            packages.remove(packageName);
            identities.keySet().retainAll(packages);
            rollbackAllowed = ScreenshotProtectionUtils.serialize(packages);
            rollbackIdentities = ScreenshotProtectionUtils.serializeIdentities(identities);
            final Set<String> protectedPackages = new HashSet<>(blocked);
            protectedPackages.add(packageName);
            rollbackBlocked = ScreenshotProtectionUtils.serialize(protectedPackages);
            ScreenshotProtectionUtils.setOverride(packageName, allow, detected, packages, blocked);
            if (packages.contains(packageName)) {
                identities.put(packageName, ScreenshotProtectionUtils.identity(
                        current.applicationInfo.uid, current.firstInstallTime));
            }
            final String updatedIdentities = ScreenshotProtectionUtils.serializeIdentities(identities);
            final String updatedAllowed = ScreenshotProtectionUtils.serialize(packages);
            final String updatedBlocked = ScreenshotProtectionUtils.serialize(blocked);
            mutationStarted = true;
            writeVerified(BLOCKED, rollbackBlocked);
            writeVerified(IDENTITIES, updatedIdentities);
            writeVerified(SETTING, updatedAllowed);
            writeVerified(BLOCKED, updatedBlocked);
            accepted = updatedIdentities.equals(read(IDENTITIES))
                    && updatedAllowed.equals(read(SETTING)) && updatedBlocked.equals(read(BLOCKED));
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            Log.w(TAG, "Unable to save screenshot protection setting");
        }
        if (!accepted) {
            if (mutationStarted) {
                rollback(BLOCKED, rollbackBlocked);
                rollback(SETTING, rollbackAllowed);
                rollback(IDENTITIES, rollbackIdentities);
            }
            showSaveFailure();
        }
        updateState();
        return accepted;
    }

    private String read(String key) {
        return Settings.Secure.getStringForUser(getContentResolver(), key, mUserId);
    }

    private void writeVerified(String key, String value) {
        if (!Settings.Secure.putStringForUser(getContentResolver(), key, value, mUserId)
                || !value.equals(read(key))) {
            throw new IllegalStateException("Screenshot protection change was not saved");
        }
    }

    private void rollback(String key, String value) {
        try {
            writeVerified(key, value);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to verify screenshot protection rollback");
        }
    }

    private void showSaveFailure() {
        Log.w(TAG, "Screenshot protection change was not confirmed");
        if (isAdded()) {
            Toast.makeText(requireContext(), R.string.screenshot_protection_save_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void rebuild() {
        if (mResumed) {
            mSession.rebuild(mFilter, ApplicationsState.ALPHA_COMPARATOR);
        }
    }

    @Override
    public void onRebuildComplete(ArrayList<AppEntry> apps) {
        if (mResumed && apps != null) {
            mApps = apps;
            updateState();
        }
    }

    @Override
    public void onPackageListChanged() {
        rebuild();
    }

    @Override
    public void onLauncherInfoChanged() {
        rebuild();
    }

    @Override
    public void onLoadEntriesCompleted() {
        rebuild();
    }

    @Override
    public void onPackageIconChanged() {
        showApps();
    }

    @Override
    public void onRunningStateChanged(boolean running) {}

    @Override
    public void onPackageSizeChanged(String packageName) {}

    @Override
    public void onAllSizesComputed() {}

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.screenshot_protection_settings);
}
