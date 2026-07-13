package com.android.settings.security;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.PasswordValidationError;
import com.android.settings.R;
import com.android.settings.password.ChooseLockPassword;
import com.android.settings.password.ChooseLockPassword.ChooseLockPasswordFragment.PasswordValidationErrorConverter;
import com.android.settingslib.utils.ThreadUtils;
import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupdesign.GlifLayout;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class SecretProfileSetupActivity extends SecretProfileActivity
        implements TextView.OnEditorActionListener {
    static final String TAG = SecretProfileSetupActivity.class.getSimpleName();
    static final String EXTRA_USER_CREDENTIAL = "user_credential";
    static final String EXTRA_TITLE_TEXT = "title";
    private boolean isUpdate;

    enum SecretProfileCredentialType {
        PIN(LockscreenCredential::createPin,
                R.id.pin_input, R.id.pin_input_confirmation,
                R.string.lockpassword_confirm_pins_dont_match),
        PASSWORD(LockscreenCredential::createPassword,
                R.id.password_input, R.id.password_input_confirmation,
                R.string.lockpassword_confirm_passwords_dont_match),
        ;

        final Function<String, LockscreenCredential> credentialCreator;
        final @IdRes int mainInputId;
        final @IdRes int confirmationInputId;
        final @StringRes int confirmationMismatchText;

        SecretProfileCredentialType(Function<String, LockscreenCredential> credentialCreator,
                             int mainInputId, int confirmationInputId,
                             int confirmationMismatchText) {
            this.credentialCreator = credentialCreator;
            this.mainInputId = mainInputId;
            this.confirmationInputId = confirmationInputId;
            this.confirmationMismatchText = confirmationMismatchText;
        }

        LockscreenCredential createCredential(SecretProfileSetupActivity activity) {
            EditText ed = activity.requireViewById(mainInputId);
            return credentialCreator.apply(ed.getText().toString());
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.secret_password_setup);

        GlifLayout layout = requireViewById(R.id.glif_layout);
        int headerText = R.string.secret_profile_pwd_action_add;
        isUpdate = false;
        layout.setHeaderText(headerText);
        adjustDescriptionStyle(layout);

        FooterBarMixin footerBar = layout.getMixin(FooterBarMixin.class);
        {
            var b = new FooterButton.Builder(this);
            b.setText(isUpdate ? R.string.duress_pwd_update_button : R.string.duress_pwd_add_button);
            b.setButtonType(FooterButton.ButtonType.DONE);
            b.setListener(v -> save());
            b.setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Primary);
            footerBar.setPrimaryButton(b.build());
        }
        {
            var b = new FooterButton.Builder(this);
            b.setText(R.string.duress_pwd_cancel_button);
            b.setButtonType(FooterButton.ButtonType.CANCEL);
            b.setListener(v -> finish());
            b.setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Secondary);
            footerBar.setSecondaryButton(b.build());
        }
        for (var ct : SecretProfileCredentialType.values()) {
            for (int id : new int[] { ct.mainInputId, ct.confirmationInputId }) {
                EditText ed = requireViewById(id);
                ed.setTag(ct);
                ed.setOnEditorActionListener(this);
            }
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if ((actionId != EditorInfo.IME_ACTION_NEXT && actionId != EditorInfo.IME_ACTION_DONE)) {
            return false;
        }

        EditText ed = (EditText) v;
        SecretProfileCredentialType ct = (SecretProfileCredentialType) ed.getTag();
        int id = v.getId();

        if (id == ct.mainInputId) {
            return !checkCredentialInputErrors(ct);
        }
        if (id == ct.confirmationInputId) {
            return !checkCredentialConfirmationError(ct);
        }
        return false;
    }

    private void save() {
        boolean credentialCheckRes = true;
        for (SecretProfileCredentialType ct : SecretProfileCredentialType.values()) {
            credentialCheckRes &= checkCredentialInputErrors(ct);
            credentialCheckRes &= checkCredentialConfirmationError(ct);
        }
        if (!credentialCheckRes) {
            return;
        }

        var warning = new AlertDialog.Builder(this);
        warning.setTitle(R.string.duress_pwd_save_warning_title);
        warning.setMessage(R.string.secret_profile_pwd_save_info_text);
        warning.setNegativeButton(R.string.duress_pwd_cancel_button, null);
        warning.setPositiveButton(R.string.duress_pwd_proceed_button, (d, w) -> doSave());
        warning.show();
    }

    private void doSave() {
        var userCredential = getIntent().getParcelableExtra(EXTRA_USER_CREDENTIAL, LockscreenCredential.class);
        Objects.requireNonNull(userCredential, EXTRA_USER_CREDENTIAL);
        LockscreenCredential pin = SecretProfileCredentialType.PIN.createCredential(this);
        LockscreenCredential password = SecretProfileCredentialType.PASSWORD.createCredential(this);
        LockPatternUtils lockPatternUtils = getLockPatternUtils();

        ThreadUtils.postOnBackgroundThread(() -> {
            Exception error = null;
            try {
                int existingUserId = lockPatternUtils.getSecretProfileUserId();
                if (existingUserId != android.os.UserHandle.USER_NULL) {
                    android.os.UserManager um =
                            (android.os.UserManager) getSystemService(USER_SERVICE);
                    um.removeUser(existingUserId);
                }
                if (lockPatternUtils.secretCredentialsExist()) {
                    lockPatternUtils.deleteSecretCredentials(userCredential);
                }
                lockPatternUtils.setSecretCredentials(userCredential, pin, password);
            } catch (Exception e) {
                error = e;
            } finally {
                pin.zeroize();
                password.zeroize();
                userCredential.zeroize();
            }
            final Exception fError = error;
            ThreadUtils.postOnMainThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (fError != null) {
                    var d = new AlertDialog.Builder(this);
                    d.setMessage(getString(R.string.secret_profile_pwd_save_error, fError.toString()));
                    d.setNeutralButton(R.string.duress_pwd_error_dialog_dismiss, null);
                    d.show();
                    return;
                }
                Toast.makeText(this, R.string.secret_profile_pwd_toast_added,
                        Toast.LENGTH_LONG).show();
                finish();
            });
        });
    }

    private boolean checkCredentialInputErrors(SecretProfileCredentialType ct) {
        EditText ed = requireViewById(ct.mainInputId);
        String text = ed.getText().toString();
        LockscreenCredential c = ct.credentialCreator.apply(text);
        try {
            List<PasswordValidationError> errors = LockPatternUtils.validateSecretCredential(c);
            String error = null;
            boolean res = true;
            if (!errors.isEmpty()) {
                res = false;
                var pvec = new PasswordValidationErrorConverter(this, c.isPassword(), ChooseLockPassword.ChooseLockPasswordFragment.ProfileType.None, errors);
                error = String.join("\n", pvec.convertErrorCodeToMessages());
            }
            ed.setError(error);
            return res;
        } finally {
            c.zeroize();
        }
    }

    private boolean checkCredentialConfirmationError(SecretProfileCredentialType ct) {
        EditText main = requireViewById(ct.mainInputId);
        EditText confirmation = requireViewById(ct.confirmationInputId);

        boolean res = main.getText().toString().equals(confirmation.getText().toString());
        confirmation.setError(res ? null : getText(ct.confirmationMismatchText));
        return res;
    }
}
