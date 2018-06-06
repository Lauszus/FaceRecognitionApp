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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

import com.wonderkiln.camerakit.CameraView;

public class CameraViewDoubleTap extends CameraView {
    public CameraViewDoubleTap(@NonNull Context context) {
        super(context);
    }
    public CameraViewDoubleTap(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }
    public CameraViewDoubleTap(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    interface OnToggleFacingListener {
        void onToggleFacing();
    }

    private OnToggleFacingListener mOnToggleFacingListener;

    public void setOnToggleFacingListener(OnToggleFacingListener l) {
        mOnToggleFacingListener = l;
    }

    @Override
    protected void onToggleFacing() { // This is called when the view is double tapped
        toggleFacing();
        mOnToggleFacingListener.onToggleFacing();
    }
}
