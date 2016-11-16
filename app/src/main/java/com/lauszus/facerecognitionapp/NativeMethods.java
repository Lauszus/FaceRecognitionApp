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

// All computations is done in an asynchronous task, so we do not skip any frames
class NativeMethods {
    static class TrainEigenfacesTask extends AsyncTask<Mat, Void, Void> {
        @Override
        protected Void doInBackground(Mat... images) {
            TrainEigenfaces(images[0].getNativeObjAddr());
            return null;
        }
    }

    static class TrainFisherfacesTask extends AsyncTask<Mat, Void, Void> {
        @Override
        protected Void doInBackground(Mat... mat) {
            TrainFisherfaces(mat[0].getNativeObjAddr(), mat[1].getNativeObjAddr());
            return null;
        }
    }

    static class MeasureDistTask extends AsyncTask<Mat, Void, float[]> {
        private final Callback mCallback;
        private final boolean useEigenfaces;

        interface Callback {
            void onMeasureDistComplete(float[] dist);
        }

        MeasureDistTask(boolean useEigenfaces, Callback callback) {
            this.useEigenfaces = useEigenfaces;
            mCallback = callback;
        }

        @Override
        protected float[] doInBackground(Mat... mat) {
            return MeasureDist(mat[0].getNativeObjAddr(), useEigenfaces);
        }

        @Override
        protected void onPostExecute(float[] dist) {
            mCallback.onMeasureDistComplete(dist);
        }
    }

    /**
     * Native methods that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    private static native void TrainEigenfaces(long addrImages);

    private static native void TrainFisherfaces(long addrImages, long addrClasses);

    private static native float[] MeasureDist(long addrImage, boolean useEigenfaces);
}
