package com.android.settings.custom.preference;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.SeekBar;

import androidx.preference.PreferenceViewHolder;

import com.android.settings.widget.SeekBarPreference;

public class CustomSeekBarPreference extends SeekBarPreference {

    private String mOriginalTitle;

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
        // Persist live as the thumb is dragged so the change previews immediately, while
        // letting the base SeekBarPreference own the SeekBar (jank monitor, haptics,
        // callChangeListener and commit semantics). We register a *secondary* listener
        // that the base forwards to, only to keep the value suffix in the title up to
        // date; we do NOT replace the base's own SeekBar listener, which previously
        // dropped those behaviours and double-persisted the value.
        setContinuousUpdates(true);
        setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // The base class has already synced/persisted the value before forwarding here.
                updateTitle();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updateTitle();
            }
        });
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
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
            setProgress(progressFromSecure(key, secureValue));
        } else if (defaultValue != null) {
            int defValue = (Integer) defaultValue;
            setProgress(defValue);
            persistInt(defValue);
        }

        updateTitle();
    }

    @Override
    public void setProgress(int progress) {
        super.setProgress(progress);
        updateTitle();
    }

    @Override
    protected boolean persistInt(int value) {
        String key = getKey();
        if (key == null) return false;

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

    // Transparency keys are stored as an alpha fraction (0..1); the slider shows the
    // inverse percentage. Round (instead of truncating) so the value round-trips exactly
    // and does not creep upward by one on each read.
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

        int value = getProgress();
        String valueText;

        if (key.contains("blur")) {
            valueText = value + " px";
        } else if (key.contains("transparency")) {
            valueText = value + "%";
        } else {
            valueText = String.valueOf(value);
        }

        setTitle(mOriginalTitle + " (" + valueText + ")");
    }
}
