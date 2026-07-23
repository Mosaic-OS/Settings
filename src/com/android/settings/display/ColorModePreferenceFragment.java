/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.settings.display;

import static android.hardware.display.ColorDisplayManager.COLOR_MODE_AUTOMATIC;
import static android.hardware.display.ColorDisplayManager.COLOR_MODE_BOOSTED;
import static android.hardware.display.ColorDisplayManager.COLOR_MODE_NATURAL;
import static android.hardware.display.ColorDisplayManager.COLOR_MODE_SATURATED;
import static android.hardware.display.ColorDisplayManager.VENDOR_COLOR_MODE_RANGE_MAX;
import static android.hardware.display.ColorDisplayManager.VENDOR_COLOR_MODE_RANGE_MIN;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.hardware.display.ColorDisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.RadioButtonPickerFragment;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.search.SearchIndexableRaw;
import com.android.settingslib.widget.CandidateInfo;
import com.android.settingslib.widget.LayoutPreference;
import com.android.settingslib.widget.SliderPreference;

import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// LINT.IfChange
@SuppressWarnings("WeakerAccess")
@SearchIndexable
public class ColorModePreferenceFragment extends RadioButtonPickerFragment {

    private static final String KEY_COLOR_MODE_PREFIX = "color_mode_";

    private static final int COLOR_MODE_FALLBACK = COLOR_MODE_NATURAL;

    static final String PAGE_VIEWER_SELECTION_INDEX = "page_viewer_selection_index";

    private static final int DOT_INDICATOR_SIZE = 12;
    private static final int DOT_INDICATOR_LEFT_PADDING = 6;
    private static final int DOT_INDICATOR_RIGHT_PADDING = 6;

    private ContentObserver mContentObserver;
    private ColorDisplayManager mColorDisplayManager;
    private Resources mResources;

    private View mViewArrowPrevious;
    private View mViewArrowNext;
    private ViewPager mViewPager;

    private static final int WB_MIRED_DEFAULT = 154;
    private static final int WB_POS_MAX = 200;
    private static final int WB_POS_STEP = 5;
    private static final int WB_SNAP_POS = 5;
    // Piecewise anchors mapping slider positions to color temperatures, keeping stock at the
    // center and the preset detents evenly spaced per side.
    private static final int[] WB_ANCHOR_POS = {0, 35, 70, 100, 120, 140, 160, 180, 200};
    private static final int[] WB_ANCHOR_KELVIN =
            {15000, 8000, 7000, 6500, 6000, 5500, 4000, 3200, 2200};
    private static final int[] WB_STOP_POS = {35, 70, 100, 120, 140, 160, 180};
    private static final int[] WB_STOP_NAMES = {
            R.string.white_balance_stop_shade,
            R.string.white_balance_stop_cloudy,
            R.string.white_balance_stop_stock,
            R.string.white_balance_stop_flash,
            R.string.white_balance_stop_daylight,
            R.string.white_balance_stop_fluorescent,
            R.string.white_balance_stop_tungsten,
    };

    private ArrayList<View> mPageList;

    private ImageView[] mDotIndicators;
    private View[] mViewPagerImages;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mColorDisplayManager = context.getSystemService(ColorDisplayManager.class);
        mResources = context.getResources();
        setCategory(R.string.color_mode_modes_category);

        final ContentResolver cr = context.getContentResolver();
        mContentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                if (ColorDisplayManager.areAccessibilityTransformsEnabled(getContext())) {
                    // Color modes are not configurable when Accessibility transforms are enabled.
                    // Close this fragment in that case.
                    getActivity().finish();
                }
            }
        };
        cr.registerContentObserver(
                Secure.getUriFor(Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED),
                false /* notifyForDescendants */, mContentObserver, mUserId);
        cr.registerContentObserver(
                Secure.getUriFor(Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED),
                false /* notifyForDescendants */, mContentObserver, mUserId);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            final int selectedPosition = savedInstanceState.getInt(PAGE_VIEWER_SELECTION_INDEX);
            mViewPager.setCurrentItem(selectedPosition);
            updateIndicator(selectedPosition);
        }
    }

    @Override
    public void onDetach() {
        if (mContentObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mContentObserver);
            mContentObserver = null;
        }
        super.onDetach();
    }

    @Override
    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putInt(PAGE_VIEWER_SELECTION_INDEX, mViewPager.getCurrentItem());
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.color_mode_settings;
    }

    @Override
    protected void addPrefsAfterList(PreferenceScreen screen) {
        super.addPrefsAfterList(screen);
        final Context context = getPrefContext();

        final PreferenceCategory wbCategory = new PreferenceCategory(context);
        wbCategory.setKey("white_balance_category");
        wbCategory.setTitle(R.string.white_balance_category);
        screen.addPreference(wbCategory);

        final SliderPreference slider = new SliderPreference(context);
        slider.setKey("white_balance_slider");
        slider.setMin(0);
        slider.setMax(WB_POS_MAX);
        slider.setTextStart(R.string.white_balance_cool);
        slider.setTextEnd(R.string.white_balance_warm);
        slider.setUpdatesContinuously(true);
        slider.setPersistent(false);
        slider.setShowSliderValue(true);
        slider.setSliderIncrement(WB_POS_STEP);
        slider.setTickVisible(false);
        slider.setValue(positionForMired(Secure.getInt(context.getContentResolver(),
                Secure.DISPLAY_WHITE_BALANCE_TEMPERATURE, WB_MIRED_DEFAULT)));
        slider.setExtraChangeListener((materialSlider, sliderValue, fromUser) -> {
            if (fromUser && isStopPosition(Math.round(sliderValue))) {
                materialSlider.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        });
        slider.setExtraTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider materialSlider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider materialSlider) {
                // Settle onto the nearest detent once the finger lifts.
                final int position = Math.round(materialSlider.getValue());
                final int snapped = snapToStop(position);
                if (snapped != position) {
                    slider.setValue(snapped);
                    final int mired = Math.round(1000000f / positionToKelvin(snapped));
                    Secure.putInt(context.getContentResolver(),
                            Secure.DISPLAY_WHITE_BALANCE_TEMPERATURE, mired);
                    applyTemperature(mired);
                }
            }
        });
        slider.setLabelFormater(value -> {
            final int position = Math.round(value);
            for (int i = 0; i < WB_STOP_POS.length; i++) {
                if (position == WB_STOP_POS[i]) {
                    return getString(WB_STOP_NAMES[i]);
                }
            }
            return (Math.round(positionToKelvin(position) / 50f) * 50) + "K";
        });
        slider.setOnPreferenceChangeListener((pref, value) -> {
            final int mired = Math.round(1000000f / positionToKelvin((Integer) value));
            Secure.putInt(context.getContentResolver(),
                    Secure.DISPLAY_WHITE_BALANCE_TEMPERATURE, mired);
            applyTemperature(mired);
            return true;
        });
        wbCategory.addPreference(slider);

        if (mColorDisplayManager.isDisplayWhiteBalanceAvailable(context)
                && mColorDisplayManager.isDisplayWhiteBalanceEnabled()) {
            slider.setEnabled(false);
            slider.setSummary(getString(R.string.white_balance_unavailable_summary,
                    getString(R.string.display_white_balance_title)));
        }
    }

    private static boolean isStopPosition(int position) {
        for (int stop : WB_STOP_POS) {
            if (stop == position) {
                return true;
            }
        }
        return false;
    }

    private static int snapToStop(int position) {
        for (int stop : WB_STOP_POS) {
            if (Math.abs(position - stop) <= WB_SNAP_POS) {
                return stop;
            }
        }
        return position;
    }

    private static int positionToKelvin(int position) {
        final int clamped = Math.max(0, Math.min(WB_POS_MAX, position));
        for (int i = 1; i < WB_ANCHOR_POS.length; i++) {
            if (clamped <= WB_ANCHOR_POS[i]) {
                final int posSpan = WB_ANCHOR_POS[i] - WB_ANCHOR_POS[i - 1];
                final int kelvinSpan = WB_ANCHOR_KELVIN[i] - WB_ANCHOR_KELVIN[i - 1];
                return WB_ANCHOR_KELVIN[i - 1] + Math.round(
                        (float) (clamped - WB_ANCHOR_POS[i - 1]) * kelvinSpan / posSpan);
            }
        }
        return WB_ANCHOR_KELVIN[WB_ANCHOR_KELVIN.length - 1];
    }

    private static int positionForMired(int mired) {
        final int kelvin = 1000000 / Math.max(1, mired);
        final int clamped = Math.max(WB_ANCHOR_KELVIN[WB_ANCHOR_KELVIN.length - 1],
                Math.min(WB_ANCHOR_KELVIN[0], kelvin));
        for (int i = 1; i < WB_ANCHOR_KELVIN.length; i++) {
            if (clamped >= WB_ANCHOR_KELVIN[i]) {
                final int posSpan = WB_ANCHOR_POS[i] - WB_ANCHOR_POS[i - 1];
                final int kelvinSpan = WB_ANCHOR_KELVIN[i] - WB_ANCHOR_KELVIN[i - 1];
                final int position = WB_ANCHOR_POS[i - 1] + Math.round(
                        (float) (clamped - WB_ANCHOR_KELVIN[i - 1]) * posSpan / kelvinSpan);
                return Math.round((float) position / WB_POS_STEP) * WB_POS_STEP;
            }
        }
        return WB_POS_MAX;
    }

    private void applyTemperature(int mired) {
        final int[] rgb = kelvinToRgb(1000000 / mired);
        final int[] reference = kelvinToRgb(1000000 / WB_MIRED_DEFAULT);
        for (int i = 0; i < 3; i++) {
            final int value = Math.max(25,
                    Math.min(255, Math.round(255f * rgb[i] / reference[i])));
            mColorDisplayManager.setColorBalanceChannel(i, value);
        }
    }

    // Tanner Helland black-body approximation, valid for our 2500K-12000K range.
    private static int[] kelvinToRgb(int kelvin) {
        final float t = kelvin / 100f;
        final float red = t <= 66f ? 255f
                : 329.698727446f * (float) Math.pow(t - 60f, -0.1332047592f);
        final float green = t <= 66f
                ? 99.4708025861f * (float) Math.log(t) - 161.1195681661f
                : 288.1221695283f * (float) Math.pow(t - 60f, -0.0755148492f);
        final float blue = t >= 66f ? 255f
                : 138.5177312231f * (float) Math.log(t - 10f) - 305.0447927307f;
        return new int[] {
                Math.max(0, Math.min(255, Math.round(red))),
                Math.max(0, Math.min(255, Math.round(green))),
                Math.max(0, Math.min(255, Math.round(blue))),
        };
    }

    @VisibleForTesting
    void configureAndInstallPreview(LayoutPreference preview, PreferenceScreen screen) {
        preview.setSelectable(false);
        screen.addPreference(preview);
    }

    @VisibleForTesting
    public ArrayList<Integer> getViewPagerResource() {
        return new ArrayList<Integer>(
                Arrays.asList(
                        R.layout.color_mode_view1,
                        R.layout.color_mode_view2,
                        R.layout.color_mode_view3));
    }

    void addViewPager(LayoutPreference preview) {
        final ArrayList<Integer> tmpviewPagerList = getViewPagerResource();
        mViewPager = preview.findViewById(R.id.viewpager);

        boolean isRtl = getContext().getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;

        mViewPagerImages = new View[3];
        for (int idx = 0; idx < tmpviewPagerList.size(); idx++) {
            int index = isRtl ? tmpviewPagerList.size() - idx - 1 : idx;
            mViewPagerImages[index] =
                    getLayoutInflater().inflate(tmpviewPagerList.get(idx), null /* root */);
        }

        mPageList = new ArrayList<View>();
        mPageList.add(mViewPagerImages[0]);
        mPageList.add(mViewPagerImages[1]);
        mPageList.add(mViewPagerImages[2]);

        mViewPager.setAdapter(new ColorPagerAdapter(mPageList));

        int pageMarginPx;
        try {
            TypedArray resolvedAttribute = getContext().getTheme().obtainStyledAttributes(
                    new int[]{android.R.attr.listPreferredItemPaddingStart});
            pageMarginPx = resolvedAttribute.getDimensionPixelSize(0, 0);
            resolvedAttribute.recycle();
        } catch (NullPointerException | Resources.NotFoundException e) {
            pageMarginPx = (int) (16 * getResources().getDisplayMetrics().density);
            Log.w(getTag(), "addViewPager: Exception message: " + e.getMessage());
        }

        mViewPager.setPageMargin(pageMarginPx);
        mViewArrowPrevious = preview.findViewById(R.id.arrow_previous);
        mViewArrowPrevious.setOnClickListener(v -> {
            final int previousPos = mViewPager.getCurrentItem() - 1;
            mViewPager.setCurrentItem(previousPos, true);
        });

        mViewArrowNext = preview.findViewById(R.id.arrow_next);
        mViewArrowNext.setOnClickListener(v -> {
            final int nextPos = mViewPager.getCurrentItem() + 1;
            mViewPager.setCurrentItem(nextPos, true);
        });

        mViewPager.addOnPageChangeListener(createPageListener());

        final ViewGroup viewGroup = (ViewGroup) preview.findViewById(R.id.viewGroup);
        mDotIndicators = new ImageView[mPageList.size()];
        for (int i = 0; i < mPageList.size(); i++) {
            final ImageView imageView = new ImageView(getContext());
            final ViewGroup.MarginLayoutParams lp =
                    new ViewGroup.MarginLayoutParams(DOT_INDICATOR_SIZE, DOT_INDICATOR_SIZE);
            lp.setMargins(DOT_INDICATOR_LEFT_PADDING, 0, DOT_INDICATOR_RIGHT_PADDING, 0);
            imageView.setLayoutParams(lp);
            int dotIndex = isRtl ? mPageList.size() - 1 - i : i;
            mDotIndicators[dotIndex] = imageView;
            viewGroup.addView(imageView);
        }

        if (isRtl) {
            mViewPager.setCurrentItem(mPageList.size() - 1, true);
        }
        updateIndicator(mViewPager.getCurrentItem());
    }

    @Override
    protected void addStaticPreferences(PreferenceScreen screen) {
        final LayoutPreference preview = new LayoutPreference(screen.getContext(),
                R.layout.color_mode_preview);
        configureAndInstallPreview(preview, screen);

        addViewPager(preview);
    }

    @Override
    protected List<? extends CandidateInfo> getCandidates() {
        final Map<Integer, String> colorModesToSummaries =
                ColorModeUtils.getColorModeMapping(mResources);
        final List<ColorModeCandidateInfo> candidates = new ArrayList<>();
        for (int colorMode : ColorModeUtils.getAvailableColorModes(getContext())) {
            candidates.add(new ColorModeCandidateInfo(
                    colorModesToSummaries.get(colorMode),
                    getKeyForColorMode(colorMode),
                    true /* enabled */));
        }
        return candidates;
    }

    @Override
    protected String getDefaultKey() {
        final int colorMode = getColorMode();
        if (isValidColorMode(colorMode)) {
            return getKeyForColorMode(colorMode);
        }
        return getKeyForColorMode(COLOR_MODE_FALLBACK);
    }

    @Override
    protected boolean setDefaultKey(String key) {
        int colorMode = Integer.parseInt(key.substring(key.lastIndexOf("_") + 1));
        if (isValidColorMode(colorMode)) {
            setColorMode(colorMode);
        }
        return true;
    }

    @Override
    public @Nullable String getPreferenceScreenBindingKey(@NonNull Context context) {
        return ColorModeScreen.KEY;
    }

    /**
     * Wraps ColorDisplayManager#getColorMode for substitution in testing.
     */
    @VisibleForTesting
    public int getColorMode() {
        return mColorDisplayManager.getColorMode();
    }

    /**
     * Wraps ColorDisplayManager#setColorMode for substitution in testing.
     */
    @VisibleForTesting
    public void setColorMode(int colorMode) {
        mColorDisplayManager.setColorMode(colorMode);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.COLOR_MODE_SETTINGS;
    }

    @VisibleForTesting
    String getKeyForColorMode(int colorMode) {
        return KEY_COLOR_MODE_PREFIX + colorMode;
    }

    private boolean isValidColorMode(int colorMode) {
        return colorMode == COLOR_MODE_NATURAL
                || colorMode == COLOR_MODE_BOOSTED
                || colorMode == COLOR_MODE_SATURATED
                || colorMode == COLOR_MODE_AUTOMATIC
                || (colorMode >= VENDOR_COLOR_MODE_RANGE_MIN
                && colorMode <= VENDOR_COLOR_MODE_RANGE_MAX);
    }

    @VisibleForTesting
    static class ColorModeCandidateInfo extends CandidateInfo {
        private final CharSequence mLabel;
        private final String mKey;

        ColorModeCandidateInfo(CharSequence label, String key, boolean enabled) {
            super(enabled);
            mLabel = label;
            mKey = key;
        }

        @Override
        public CharSequence loadLabel() {
            return mLabel;
        }

        @Override
        public Drawable loadIcon() {
            return null;
        }

        @Override
        public String getKey() {
            return mKey;
        }
    }

    private ViewPager.OnPageChangeListener createPageListener() {
        return new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(
                    int position, float positionOffset, int positionOffsetPixels) {
                if (positionOffset != 0) {
                    for (int idx = 0; idx < mPageList.size(); idx++) {
                        mViewPagerImages[idx].setVisibility(View.VISIBLE);
                    }
                } else {
                    mViewPagerImages[position].setContentDescription(
                            getContext().getString(R.string.colors_viewpager_content_description));
                }
            }

            @Override
            public void onPageSelected(int position) {
                updateIndicator(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        };
    }

    private void updateIndicator(int position) {
        for (int i = 0; i < mPageList.size(); i++) {
            if (position == i) {
                mDotIndicators[i].setBackgroundResource(
                        R.drawable.ic_color_page_indicator_focused);

                mViewPagerImages[i].setVisibility(View.VISIBLE);
            } else {
                mDotIndicators[i].setBackgroundResource(
                        R.drawable.ic_color_page_indicator_unfocused);

                mViewPagerImages[i].setVisibility(View.INVISIBLE);
            }
        }

        if (position == 0) {
            mViewArrowPrevious.setVisibility(View.INVISIBLE);
            mViewArrowNext.setVisibility(View.VISIBLE);
        } else if (position == (mPageList.size() - 1)) {
            mViewArrowPrevious.setVisibility(View.VISIBLE);
            mViewArrowNext.setVisibility(View.INVISIBLE);
        } else {
            mViewArrowPrevious.setVisibility(View.VISIBLE);
            mViewArrowNext.setVisibility(View.VISIBLE);
        }
    }

    static class ColorPagerAdapter extends PagerAdapter {
        private final ArrayList<View> mPageViewList;

        ColorPagerAdapter(ArrayList<View> pageViewList) {
            mPageViewList = pageViewList;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            if (mPageViewList.get(position) != null) {
                container.removeView(mPageViewList.get(position));
            }
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            container.addView(mPageViewList.get(position));
            return mPageViewList.get(position);
        }

        @Override
        public int getCount() {
            return mPageViewList.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return object == view;
        }
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.color_mode_settings) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    final int[] availableColorModes =
                            ColorModeUtils.getAvailableColorModes(context);
                    return availableColorModes != null && availableColorModes.length > 0
                            && !ColorDisplayManager.areAccessibilityTransformsEnabled(context);
                }

                @Override
                public List<SearchIndexableRaw> getRawDataToIndex(
                        Context context, boolean enabled) {
                    if (!isPageSearchEnabled(context)) {
                        return Collections.emptyList();
                    }

                    final List<SearchIndexableRaw> rawData = new ArrayList<>();
                    final Resources res = context.getResources();
                    final String screenTitle = context.getString(R.string.color_mode_title);

                    final Map<Integer, String> colorModesToSummaries =
                            ColorModeUtils.getColorModeMapping(res);

                    for (int colorMode : ColorModeUtils.getAvailableColorModes(context)) {
                        String modeName = colorModesToSummaries.get(colorMode);
                        if (modeName != null) {
                            SearchIndexableRaw raw = new SearchIndexableRaw(context);
                            raw.key = KEY_COLOR_MODE_PREFIX + colorMode;
                            raw.title = modeName;
                            raw.screenTitle = screenTitle;
                            raw.className = ColorModePreferenceFragment.class.getName();
                            rawData.add(raw);
                        }
                    }
                    return rawData;
                }
            };
}
// LINT.ThenChange(ColorModeScreen.kt, ColorModeApiScreen.kt)
