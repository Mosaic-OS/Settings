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

package com.android.settings.biometrics.fingerprint;

import android.content.Context;
import android.ext.settings.ExtSettings;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.preference.Preference;

import com.android.internal.widget.LockPatternUtils;

public class FingerprintSingleAttemptPreferenceController
        extends FingerprintSettingsPreferenceController {
    public static final String PREF_KEY = "fingerprint_single_attempt";

    private final LockPatternUtils mLockPatternUtils;
    private final UserManager mUserManager;

    public FingerprintSingleAttemptPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mLockPatternUtils = new LockPatternUtils(context);
        mUserManager = context.getSystemService(UserManager.class);
        setUserId(context.getUserId());
    }

    @Override
    public int getAvailabilityStatus() {
        if (mUserManager.isProfile(getUserId())) {
            return CONDITIONALLY_UNAVAILABLE;
        }
        if (!FingerprintSettings.isFingerprintHardwareDetected(mContext)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return mLockPatternUtils.isSecure(getUserId())
                ? AVAILABLE : DISABLED_DEPENDENT_SETTING;
    }

    @Override
    public boolean isChecked() {
        return ExtSettings.FINGERPRINT_SINGLE_ATTEMPT.get(mContext, getUserId());
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        if (getAvailabilityStatus() != AVAILABLE) {
            return false;
        }
        Context userContext = mContext.createContextAsUser(
                UserHandle.of(getUserId()), 0);
        return ExtSettings.FINGERPRINT_SINGLE_ATTEMPT.put(userContext, isChecked);
    }

    @Override
    public void updateState(Preference preference) {
        preference.setVisible(isAvailable());
        super.updateState(preference);
        preference.setEnabled(getAvailabilityStatus() == AVAILABLE);
    }
}
