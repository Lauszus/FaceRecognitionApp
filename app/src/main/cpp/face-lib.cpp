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

#include <jni.h>
#include <Eigen/Dense> // http://eigen.tuxfamily.org
#include <opencv2/core.hpp>
#include <opencv2/core/eigen.hpp>
#include <FaceRecognitionLib/Eigenfaces.h>
#include <FaceRecognitionLib/Fisherfaces.h>
#include <FaceRecognitionLib/Tools.h>
#include <android/log.h>

#ifdef NDEBUG
#define LOGD(...) ((void)0)
#define LOGI(...) ((void)0)
#define LOGE(...) ((void)0)
#define LOG_ASSERT(condition, ...) ((void)0)
#else
#define LOG_TAG "FaceRecognitionAppActivity/Native"
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOG_ASSERT(condition, ...) if (!(condition)) __android_log_assert(#condition, LOG_TAG, __VA_ARGS__)
#endif

Eigenfaces eigenfaces;
Fisherfaces fisherfaces;

using namespace std;
using namespace cv;
using namespace Eigen;

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL Java_com_lauszus_facerecognitionapp_NativeMethods_TrainFaces(JNIEnv, jobject, jlong addrImages, jlong addrClasses) {
    Mat *pImages = (Mat *) addrImages; // Each images is represented as a column vector
    Mat *pClasses = (Mat *) addrClasses; // Classes are represented as a vector

    LOG_ASSERT(pImages->type() == CV_8U, "Images must be an 8-bit matrix");
    MatrixXi images;
    cv2eigen(*pImages, images); // Copy from OpenCV Mat to Eigen matrix

    //Facebase *pFacebase;
    if (pClasses == NULL) { // If classes are NULL, then train Eigenfaces
        eigenfaces.train(images); // Train Eigenfaces
        LOGI("Eigenfacess numComponents: %d", eigenfaces.numComponents);
        //pFacebase = &eigenfaces;
    } else {
        LOG_ASSERT(pClasses->type() == CV_32S && pClasses->cols == 1, "Classes must be a signed 32-bit vector");
        VectorXi classes;
        cv2eigen(*pClasses, classes); // Copy from OpenCV Mat to Eigen vector
        LOG_ASSERT(classes.minCoeff() == 1, "Minimum value in the list must be 1");
        fisherfaces.train(images, classes); // Train Fisherfaces
        LOGI("Fisherfaces numComponents: %d", fisherfaces.numComponents);
        //pFacebase = &fisherfaces;
    }

    /*
    if (!pFacebase->V.hasNaN()) {
        for (int i = 0; i < pFacebase->numComponents; i++) { // Loop through eigenvectors
            for (int j = 0; j < 10; j++) // Print first 10 values
                LOGI("Eigenvector[%d]: %f", i, pFacebase->V(j, i));
        }
    } else
        LOGE("Eigenvectors are not valid!");
    */
}

JNIEXPORT void JNICALL Java_com_lauszus_facerecognitionapp_NativeMethods_MeasureDist(JNIEnv *env, jobject, jlong addrImage, jfloatArray minDist, jintArray minDistIndex, jfloatArray faceDist, jboolean useEigenfaces) {
    Facebase *pFacebase;
    if (useEigenfaces) {
        LOGI("Using Eigenfaces");
        pFacebase = &eigenfaces;
    } else {
        LOGI("Using Fisherfaces");
        pFacebase = &fisherfaces;
    }

    if (pFacebase->V.any()) { // Make sure that the eigenvector has been calculated
        Mat *pImage = (Mat *) addrImage; // Image is represented as a column vector

        VectorXi image;
        cv2eigen(*pImage, image); // Convert from OpenCV Mat to Eigen matrix

        LOGI("Project faces");
        VectorXf W = pFacebase->project(image); // Project onto subspace
        LOGI("Reconstructing faces");
        VectorXf face = pFacebase->reconstructFace(W);

        LOGI("Calculate normalized Euclidean distance");
        jfloat dist_face = pFacebase->euclideanDistFace(image, face);
        LOGI("Face distance: %f", dist_face);
        env->SetFloatArrayRegion(faceDist, 0, 1, &dist_face);

        VectorXf dist = pFacebase->euclideanDist(W);

        vector<size_t> sortedIdx = sortIndexes(dist);
        for (auto idx : sortedIdx)
            LOGI("dist[%zu]: %f", idx, dist(idx));

        int minIndex = (int) sortedIdx[0];
        env->SetFloatArrayRegion(minDist, 0, 1, &dist(minIndex));
        env->SetIntArrayRegion(minDistIndex, 0, 1, &minIndex);
    }
}
/*
static inline void convertYUVToRGBA(uint8_t y, uint8_t u, uint8_t v, uint8_t *buf) __attribute__((always_inline));

static void convertYUVImageToRGBA(const Mat *pYUV, Mat *pRGB) {
    const Size size = pRGB->size();
    const int width = size.width;
    const int height = size.height;
    const int n_pixels = width * height;
    const int rgba_channels = pRGB->channels();

    // See: https://android.googlesource.com/platform/frameworks/av/+/master/media/libstagefright/yuv/YUVImage.cpp,
    // https://wiki.videolan.org/YUV/#Semi-planar
    // and https://en.wikipedia.org/wiki/YUV#Y.E2.80.B2UV420p_.28and_Y.E2.80.B2V12_or_YV12.29_to_RGB888_conversion
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            // U and V channels are interleaved as VUVUVU.
            // So V data starts at the end of Y channel and
            // U data starts right after V's start.
            const int yIndex = x + y * width;
            const uint8_t y_val = pYUV->data[yIndex];

            // Since U and V channels are interleaved, offsets need to be doubled.
            const int uvOffset = (y >> 1) * (width >> 1) + (x >> 1);
            const int vIndex = n_pixels + 2*uvOffset;
            const int uIndex = vIndex + 1;
            const uint8_t v_val = pYUV->data[vIndex];
            const uint8_t u_val = pYUV->data[uIndex];
            convertYUVToRGBA(y_val, u_val, v_val, &pRGB->data[rgba_channels * yIndex]);
        }
    }
}

JNIEXPORT void JNICALL Java_com_lauszus_facerecognitionapp_NativeMethods_YUV2RGB(JNIEnv, jobject, jlong addrYuv, jlong addrRgba) {
    Mat *pYUV = (Mat *) addrYuv; // YUV 4:2:0 planar image, with 8 bit Y samples, followed by interleaved V/U plane with 8bit 2x2 sub-sampled chroma samples
    Mat *pRGB = (Mat *) addrRgba; // RGBA image

    convertYUVImageToRGBA(pYUV, pRGB);
}

JNIEXPORT void JNICALL Java_com_lauszus_facerecognitionapp_NativeMethods_HistEQ(JNIEnv, jobject, jlong addrYuv, jlong addrRgba) {
    Mat *pYUV = (Mat *) addrYuv; // YUV 4:2:0 planar image, with 8 bit Y samples, followed by interleaved V/U plane with 8bit 2x2 sub-sampled chroma samples
    Mat *pRGB = (Mat *) addrRgba; // RGBA image

    const Size size = pRGB->size();
    const int width = size.width;
    const int height = size.height;

    // 1. Step: Compute histogram of Y channel
    uint32_t histogram[256];
    memset(histogram, 0, sizeof(histogram));

    for (int y = 0; y < height; y++) {
        for (int x = width / 2; x < width; x++) { // Only look at half the image
            const int yIndex = x + y * width;
            const uint8_t y_val = pYUV->data[yIndex];
            histogram[y_val]++;
        }
    }

    // Step 2: Compute CDF of histogram
    uint32_t histogram_cdf[256];

    histogram_cdf[0] = histogram[0];
    for (int i = 1; i < 256; i++)
        histogram_cdf[i] = histogram_cdf[i - 1] + histogram[i]; // Calculate CDF

    for (int i = 0; i < 256; i++)
        histogram_cdf[i] /= histogram_cdf[255] / 255; // Normalize CDF

    // Step 3: Apply histogram equalization
    for (int y = 0; y < height; y++) {
        for (int x = width / 2; x < width; x++) { // Image is flipped after this function
            const int yIndex = x + y * width;
            const uint8_t y_val = pYUV->data[yIndex];
            pYUV->data[yIndex] = (uint8_t) histogram_cdf[y_val];
        }
    }

    // Step 4: Convert from YUV to RGB
    convertYUVImageToRGBA(pYUV, pRGB);
}

#define clamp(amt,low,high) ((amt)<(low)?(low):((amt)>(high)?(high):(amt)))

static inline void convertYUVToRGBA(uint8_t y, uint8_t u, uint8_t v, uint8_t *buf) {
    const int rTmp = y + (int)(1.370705f * (v - 128));
    const int gTmp = y - (int)(0.698001f * (v - 128)) - (int)(0.337633f * (u - 128));
    const int bTmp = y + (int)(1.732446f * (u - 128));

    buf[0] = (uint8_t) clamp(rTmp, 0, 255);
    buf[1] = (uint8_t) clamp(gTmp, 0, 255);
    buf[2] = (uint8_t) clamp(bTmp, 0, 255);
    buf[3] = 255; // Alpha channel
}
*/
#ifdef __cplusplus
}
#endif
