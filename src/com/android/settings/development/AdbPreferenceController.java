/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.development;


import android.content.Context;
import android.debug.AdbManager;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.development.AbstractEnableAdbPreferenceController;

public class AdbPreferenceController extends AbstractEnableAdbPreferenceController implements
        PreferenceControllerMixin {

    @Nullable private final DevelopmentSettingsDashboardFragment mFragment;

    public AdbPreferenceController(Context context,
        @Nullable DevelopmentSettingsDashboardFragment fragment) {
        super(context);
        mFragment = fragment;
    }

    public void onAdbDialogConfirmed() {
        writeAdbSetting(true);
    }

    public void onAdbDialogDismissed() {
        updateState(mPreference);
    }

    /** True once the user permanently disabled ADB; only a factory reset clears it. */
    static boolean isAdbPermanentlyLocked(Context context) {
        AdbManager adbManager = context.getSystemService(AdbManager.class);
        return adbManager != null && adbManager.isAdbPermanentlyLocked();
    }

    static void applyPermanentLock(Context context, @Nullable Preference preference) {
        if (preference == null || !isAdbPermanentlyLocked(context)) {
            return;
        }
        if (preference instanceof TwoStatePreference) {
            ((TwoStatePreference) preference).setChecked(false);
        }
        preference.setEnabled(false);
        preference.setSummary(R.string.adb_permanently_disabled_summary);
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        applyPermanentLock(mContext, preference);
    }

    @Override
    protected void onDeveloperOptionsSwitchEnabled() {
        super.onDeveloperOptionsSwitchEnabled();
        applyPermanentLock(mContext, mPreference);
    }

    @Override
    public void showConfirmationDialog(@Nullable Preference preference) {
        EnableAdbWarningDialog.show(mFragment);
    }

    @Override
    public void dismissConfirmationDialog() {
        // intentional no-op
    }

    @Override
    public boolean isConfirmationDialogShowing() {
        // intentional no-op
        return false;
    }

    @Override
    protected void onDeveloperOptionsSwitchDisabled() {
        super.onDeveloperOptionsSwitchDisabled();
        writeAdbSetting(false);
        mPreference.setChecked(false);
    }
}
