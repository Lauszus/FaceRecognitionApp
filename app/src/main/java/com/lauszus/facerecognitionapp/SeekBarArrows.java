/*******************************************************************************
 * Copyright (C) 2016 Kristian Sloth Lauszus. All rights reserved.
 *
 * This software may be distributed and modified under the terms of the GNU
 * General Public License version 2 (GPL2) as published by the Free Software
 * Foundation and appearing in the file GPL2.TXT included in the packaging of
 * this file. Please note that GPL2 Section 2[b] requires that all works based
 * on this software must also be made publicly available under the terms of
 * the GPL2 ("Copyleft").
 *
 * Contact information
 * -------------------
 *
 * Kristian Sloth Lauszus
 * Web      :  http://www.lauszus.com
 * e-mail   :  lauszus@gmail.com
 ******************************************************************************/

package com.lauszus.facerecognitionapp;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public class SeekBarArrows extends LinearLayout implements SeekBar.OnSeekBarChangeListener {
    public SeekBar mSeekBar;
    private TextView mSeekBarValue;
    private float multiplier;

    public SeekBarArrows(Context context, AttributeSet attrs) {
        super(context, attrs);

        inflate(context, R.layout.seek_bar_arrows, this); // Use custom layout

        TypedArray styledAttrs = getContext().obtainStyledAttributes(attrs, R.styleable.SeekBarArrows); // Read all attributes from xml

        String mSeekBarText = styledAttrs.getString(R.styleable.SeekBarArrows_text);
        float max = styledAttrs.getFloat(R.styleable.SeekBarArrows_max, 0);
        multiplier = styledAttrs.getFloat(R.styleable.SeekBarArrows_multiplier, 0);

        mSeekBar = (SeekBar) findViewById(R.id.seekBar);
        ((TextView) findViewById(R.id.text)).setText(mSeekBarText);
        mSeekBarValue = (TextView) findViewById(R.id.value);

        mSeekBar.setMax((int) (max / multiplier));
        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBar.setProgress(mSeekBar.getMax() / 2); // Call this after the OnSeekBarChangeListener is created

        // Use custom OnArrowListener class to handle button click, button long click and if the button is held down
        new OnArrowListener(findViewById(R.id.rightArrow), mSeekBar, true);
        new OnArrowListener(findViewById(R.id.leftArrow), mSeekBar, false);

        styledAttrs.recycle();
    }

    public float getProgress() {
        return mSeekBar.getProgress() * multiplier;
    }

    public void setProgress(float value) {
        mSeekBar.setProgress((int) (value / multiplier));
    }

    public String progressToString(int value) {
        final String format = multiplier == 0.00001f ? "%.5f" : multiplier == 0.0001f ? "%.4f" : multiplier == 0.001f ? "%.3f" : multiplier == 0.01f ? "%.2f" : multiplier == 0.1f ? "%.1f" : "%.0f"; // Set decimal places according to divider
        return String.format(Locale.US, format, (float) value * multiplier); // SeekBar can only handle integers, so format it to a float with two decimal places
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mSeekBarValue.setText(progressToString(progress));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    private class OnArrowListener implements View.OnClickListener, View.OnLongClickListener, View.OnTouchListener {
        private Handler handler = new Handler();
        private static final int repeatInterval = 300; // Repeat interval is 300 ms
        private SeekBar mSeekbar;
        private boolean positive;

        OnArrowListener(View v, SeekBar mSeekbar, boolean positive) {
            Button mButton = (Button) v;
            this.mSeekbar = mSeekbar;
            this.positive = positive;

            mButton.setOnClickListener(this);
            mButton.setOnLongClickListener(this);
            mButton.setOnTouchListener(this);
        }

        private int round10(int n) {
            return Math.round((float)n / 10.0f) * 10;
        }

        private void longClick() {
            mSeekbar.setProgress(round10(mSeekbar.getProgress() + (positive ? 10 : -10))); // Increase/decrease with 10 and round to nearest multiple of 10
        }

        private Runnable runnable = new Runnable() {
            @Override
            public void run() {
                longClick();
                handler.postDelayed(this, repeatInterval); // Repeat long click if button is held down
            }
        };

        @Override
        public void onClick(View v) {
            mSeekbar.setProgress(mSeekbar.getProgress() + (positive ? 1 : -1)); // Increase/decrease with 1
        }

        @Override
        public boolean onLongClick(View v) {
            longClick();
            handler.postDelayed(runnable, repeatInterval); // Repeat again in 300 ms
            return true;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(runnable); // Remove callback if button is released
            }
            return false;
        }
    }
}
