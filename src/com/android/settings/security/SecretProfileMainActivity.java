package com.android.settings.security;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.settings.R;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.google.android.setupdesign.GlifRecyclerLayout;
import com.google.android.setupdesign.items.IItem;
import com.google.android.setupdesign.items.Item;
import com.google.android.setupdesign.items.ItemGroup;
import com.google.android.setupdesign.items.RecyclerItemAdapter;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class SecretProfileMainActivity extends SecretProfileActivity implements RecyclerItemAdapter.OnItemSelectedListener {
    private static final String TAG = SecretProfileMainActivity.class.getSimpleName();
    private static final String KEY_USER_CREDENTIAL = "user_credential";
    private static final String KEY_HAS_ASKED_FOR_USER_CREDENTIALS = "asked_for_user_credentials";

    private GlifRecyclerLayout layout;
    private LockscreenCredential userCredential;
    private boolean askedForUserCredentials;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        var layout = new GlifRecyclerLayout(this);
        this.layout = layout;
        layout.setIcon(getDrawable(R.drawable.ic_person));
        layout.setHeaderText(R.string.secret_profile_title);
        adjustDescriptionStyle(layout);
        layout.setDescriptionText(R.string.secret_profile_description);

        setContentView(layout);

        if (savedInstanceState != null) {
            userCredential = savedInstanceState.getParcelable(KEY_USER_CREDENTIAL, LockscreenCredential.class);
            askedForUserCredentials = requireNonNull(savedInstanceState.getBoolean2(KEY_HAS_ASKED_FOR_USER_CREDENTIALS));
        }
    }

    @Override
    protected boolean hasUserCredential() {
        return userCredential != null && !userCredential.isNone();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_USER_CREDENTIAL, userCredential);
        outState.putBoolean(KEY_HAS_ASKED_FOR_USER_CREDENTIALS, askedForUserCredentials);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (userCredential == null) {
            if (!getLockPatternUtils().isSecure(getUserId())) {
                userCredential = LockscreenCredential.createNone();
            } else if (!askedForUserCredentials) {
                var b = new ChooseLockSettingsHelper.Builder(this);
                b.setRequestCode(REQ_CODE_OBTAIN_USER_CREDENTIALS);
                b.setReturnCredentials(true);
                b.setForegroundOnly(true);
                b.show();
                askedForUserCredentials = true;
            }
        }

        updateActionList();
    }

    private void updateActionList() {
        if (userCredential == null) {
            return;
        }

        var g = new ItemGroup();
        // Always show "Add" - opening this activity always creates a fresh secret profile,
        // replacing any existing one.
        g.addChild(createItem(R.string.secret_profile_pwd_action_add, R.drawable.ic_add_24dp));

        var adapter = new RecyclerItemAdapter(g);
        adapter.setOnItemSelectedListener(this);
        layout.setAdapter(adapter);
    }

    private Item createItem(@StringRes int title, @DrawableRes int icon) {
        var i = new Item();
        i.setId(title);
        i.setTitle(getText(title));
        i.setIcon(getDrawable(icon));
        return i;
    }

    @Override
    public void onItemSelected(IItem iitem) {
        Item item = (Item) iitem;
        int id = item.getId();

        if (id == R.string.secret_profile_pwd_action_add) {
            var i = new Intent(this, SecretProfileSetupActivity.class);
            i.putExtra(SecretProfileSetupActivity.EXTRA_TITLE_TEXT, id);
            i.putExtra(SecretProfileSetupActivity.EXTRA_USER_CREDENTIAL, userCredential);
            allowNextOnStop = true;
            startActivityForResult(i, REQ_CODE_SETUP);
        }
    }

    static final int REQ_CODE_OBTAIN_USER_CREDENTIALS = 1;
    static final int REQ_CODE_SETUP = 2;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_CODE_SETUP) {
            if (resultCode == RESULT_CANCELED) {
                finish();
            }
            return;
        }

        if (requestCode != REQ_CODE_OBTAIN_USER_CREDENTIALS) {
            throw new IllegalStateException(Integer.toString(resultCode));
        }

        if (resultCode != RESULT_OK) {
            finish();
            return;
        }

        if (data == null) {
            throw new IllegalStateException("data == null");
        }

        var credential = data.getParcelableExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, LockscreenCredential.class);
        if (credential == null) {
            throw new IllegalStateException("no returned credential");
        }
        userCredential = credential;
    }
}
