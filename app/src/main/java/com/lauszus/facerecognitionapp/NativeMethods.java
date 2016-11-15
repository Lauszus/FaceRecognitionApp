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

import android.os.AsyncTask;

import org.opencv.core.Mat;

class NativeMethods {
    static void TrainEigenfaces(Mat images) {
        TrainEigenfaces(images.getNativeObjAddr());
    }

    static void TrainFisherfaces(Mat images, Mat classes) {
        TrainFisherfaces(images.getNativeObjAddr(), classes.getNativeObjAddr());
    }

    static float[] MeasureDist(Mat image, boolean useEigenfaces) {
        return MeasureDist(image.getNativeObjAddr(), useEigenfaces);
    }

    /**
     * Native methods that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    private static native void TrainEigenfaces(long addrImages);

    private static native void TrainFisherfaces(long addrImages, long addrClasses);

    private static native float[] MeasureDist(long addrImage, boolean useEigenfaces);
}

class TrainEigenfacesTask extends AsyncTask<Mat, Void, Void> {

    @Override
    protected Void doInBackground(Mat... images) {
        NativeMethods.TrainEigenfaces(images[0]);
        return null;
    }
}

class TrainFisherfacesTask extends AsyncTask<Mat, Void, Void> {

    @Override
    protected Void doInBackground(Mat... mat) {
        NativeMethods.TrainFisherfaces(mat[0], mat[1]);
        return null;
    }
}
