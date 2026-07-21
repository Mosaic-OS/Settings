package com.android.settings.custom.preference;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.android.settings.widget.SeekBarPreference;

public class CustomSeekBarPreference extends SeekBarPreference {

    private String mOriginalTitle;
    private boolean mSuppressPersist;
    private TextView mTitleView;
    private boolean mTrackingTouch;
    // True while the setting is unset (stock behavior); the title then shows "Default".
    private boolean mUnset;

    public CustomSeekBarPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public CustomSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomSeekBarPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        mOriginalTitle = getTitle() != null ? getTitle().toString() : "";
        setPersistent(false);
        // Preview live while dragging; a secondary listener only refreshes the title suffix.
        setContinuousUpdates(true);
        setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // The base class has already synced/persisted the value before forwarding here.
                updateTitle();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mTrackingTouch = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mTrackingTouch = false;
                updateTitle();
            }
        });
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mTitleView = (TextView) holder.findViewById(android.R.id.title);
        updateTitle();
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        String key = getKey();
        if (key == null) {
            super.onSetInitialValue(restoreValue, defaultValue);
            return;
        }

        float secureValue = Settings.Secure.getFloat(
            getContext().getContentResolver(), key, Float.NaN);

        if (!Float.isNaN(secureValue)) {
            mUnset = false;
            showValueNoPersist(progressFromSecure(key, secureValue));
        } else if (defaultValue != null) {
            // Unset means stock behavior; show the default position without persisting it.
            showDefaultNoPersist((Integer) defaultValue);
        }

        updateTitle();
    }

    @Override
    public void setProgress(int progress) {
        super.setProgress(progress);
        updateTitle();
    }

    /** Shows the default position and marks the setting unset ("Default" in the title). */
    public void showDefaultNoPersist(int progress) {
        mUnset = true;
        showValueNoPersist(progress);
    }

    /** Shows a value without writing it, so the setting stays as it is. */
    public void showValueNoPersist(int progress) {
        mSuppressPersist = true;
        try {
            setProgress(progress);
        } finally {
            mSuppressPersist = false;
        }
        updateTitle();
    }

    @Override
    protected boolean persistInt(int value) {
        if (mSuppressPersist) return true;
        String key = getKey();
        if (key == null) return false;
        mUnset = false;

        try {
            Settings.Secure.putFloat(getContext().getContentResolver(),
                key, secureFromProgress(key, value));
            updateTitle();
            return true;
        } catch (Exception e) {
            android.util.Log.e("CustomSeekBar", "Error persisting value", e);
            return false;
        }
    }

    @Override
    protected int getPersistedInt(int defaultReturnValue) {
        String key = getKey();
        if (key == null) return defaultReturnValue;

        try {
            float secureValue = Settings.Secure.getFloat(
                getContext().getContentResolver(), key, Float.NaN);

            if (!Float.isNaN(secureValue)) {
                return progressFromSecure(key, secureValue);
            }

            return defaultReturnValue;
        } catch (Exception e) {
            android.util.Log.e("CustomSeekBar", "Error reading persisted value", e);
            return defaultReturnValue;
        }
    }

    // Transparency is stored as a 0..1 opacity alpha; the slider shows the inverse percentage.
    private static int progressFromSecure(String key, float secureValue) {
        if (key.contains("transparency")) {
            return 100 - Math.round(secureValue * 100);
        }
        return Math.round(secureValue);
    }

    private static float secureFromProgress(String key, int value) {
        if (key.contains("transparency")) {
            return (100 - value) / 100f;
        }
        return (float) value;
    }

    private void updateTitle() {
        String key = getKey();
        if (key == null) return;

        if (mUnset) {
            applyTitle(mOriginalTitle + " ("
                    + getContext().getString(com.android.settings.R.string.scrim_value_default)
                    + ")");
            return;
        }

        int value = getProgress();
        String valueText;

        if (key.contains("blur")) {
            valueText = value + " px";
        } else if (key.contains("transparency")) {
            valueText = value + "%";
        } else {
            valueText = String.valueOf(value);
        }

        applyTitle(mOriginalTitle + " (" + valueText + ")");
    }

    // setTitle() triggers notifyChanged(), and a list rebind mid-drag glitches the SeekBar;
    // write straight into the title view while dragging and commit on release.
    private void applyTitle(String title) {
        if (mTrackingTouch && mTitleView != null) {
            mTitleView.setText(title);
        } else {
            setTitle(title);
        }
    }
}
