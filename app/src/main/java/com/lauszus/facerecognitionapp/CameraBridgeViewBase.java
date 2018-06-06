// Based on: http://stackoverflow.com/questions/14816166/rotate-camera-preview-to-portrait-android-opencv-camera/38210157#38210157

package com.lauszus.facerecognitionapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import org.opencv.R;
import org.opencv.android.FpsMeter;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;

import java.io.File;
import java.util.List;

/**
 * This is a basic class, implementing the interaction with Camera and OpenCV library.
 * The main responsibility of it - is to control when camera can be enabled, process the frame,
 * call external listener to make any adjustments to the frame and then draw the resulting
 * frame to the screen.
 * The clients shall implement CvCameraViewListener.
 */
@SuppressWarnings({"unused", "UnnecessaryInterfaceModifier"})
public abstract class CameraBridgeViewBase extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "CameraBridge";
    private static final int MAX_UNSPECIFIED = -1;
    private static final int STOPPED = 0;
    private static final int STARTED = 1;

    private int mState = STOPPED;
    private Bitmap mCacheBitmap;
    private CvCameraViewListener2 mListener;
    private boolean mSurfaceExist;
    private final Object mSyncObject = new Object();

    protected int mFrameWidth;
    protected int mFrameHeight;
    protected int mMaxHeight;
    protected int mMaxWidth;
    protected float mScale = 0;
    protected int mPreviewFormat = RGBA;
    protected int mCameraIndex;
    protected boolean mEnabled;
    protected FpsMeter mFpsMeter = null;

    public static final int CAMERA_ID_ANY   = -1;
    public static final int CAMERA_ID_BACK  = 99;
    public static final int CAMERA_ID_FRONT = 98;
    public static final int RGBA = 1;
    public static final int GRAY = 2;

    private WindowManager mWindowManager;

    public CameraBridgeViewBase(Context context, int cameraId) {
        super(context);
        mCameraIndex = cameraId;
        getHolder().addCallback(this);
        mMaxWidth = MAX_UNSPECIFIED;
        mMaxHeight = MAX_UNSPECIFIED;
    }

    public CameraBridgeViewBase(Context context, AttributeSet attrs) {
        super(context, attrs);

        int count = attrs.getAttributeCount();
        Log.d(TAG, "Attr count: " + count);

        TypedArray styledAttrs = getContext().obtainStyledAttributes(attrs, R.styleable.CameraBridgeViewBase);
        if (styledAttrs.getBoolean(R.styleable.CameraBridgeViewBase_show_fps, false))
            enableFpsMeter();

        mCameraIndex = styledAttrs.getInt(R.styleable.CameraBridgeViewBase_camera_id, CAMERA_ID_ANY);

        getHolder().addCallback(this);
        mMaxWidth = MAX_UNSPECIFIED;
        mMaxHeight = MAX_UNSPECIFIED;
        styledAttrs.recycle();

        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void flipCamera() {
        disableView();
        if (this.mCameraIndex == CAMERA_ID_FRONT)
            setCameraIndex(CAMERA_ID_BACK);
        else
            setCameraIndex(CAMERA_ID_FRONT);
        enableView();
    }

    /**
     * Sets the camera index
     * @param cameraIndex new camera index
     */
    public void setCameraIndex(int cameraIndex) {
        this.mCameraIndex = cameraIndex;
    }

    public interface CvCameraViewListener {
        /**
         * This method is invoked when camera preview has started. After this method is invoked
         * the frames will start to be delivered to client via the onCameraFrame() callback.
         * @param width -  the width of the frames that will be delivered
         * @param height - the height of the frames that will be delivered
         */
        public void onCameraViewStarted(int width, int height);

        /**
         * This method is invoked when camera preview has been stopped for some reason.
         * No frames will be delivered via onCameraFrame() callback after this method is called.
         */
        public void onCameraViewStopped();

        /**
         * This method is invoked when delivery of the frame needs to be done.
         * The returned values - is a modified frame which needs to be displayed on the screen.
         * TODO: pass the parameters specifying the format of the frame (BPP, YUV or RGB and etc)
         */
        public Mat onCameraFrame(Mat inputFrame);
    }

    public interface CvCameraViewListener2 {
        /**
         * This method is invoked when camera preview has started. After this method is invoked
         * the frames will start to be delivered to client via the onCameraFrame() callback.
         * @param width -  the width of the frames that will be delivered
         * @param height - the height of the frames that will be delivered
         */
        public void onCameraViewStarted(int width, int height);

        /**
         * This method is invoked when camera preview has been stopped for some reason.
         * No frames will be delivered via onCameraFrame() callback after this method is called.
         */
        public void onCameraViewStopped();

        /**
         * This method is invoked when delivery of the frame needs to be done.
         * The returned values - is a modified frame which needs to be displayed on the screen.
         * TODO: pass the parameters specifying the format of the frame (BPP, YUV or RGB and etc)
         */
        public Mat onCameraFrame(CvCameraViewFrame inputFrame);
    }

    @SuppressWarnings("WeakerAccess")
    protected class CvCameraViewListenerAdapter implements CvCameraViewListener2  {
        public CvCameraViewListenerAdapter(CvCameraViewListener oldStypeListener) {
            mOldStyleListener = oldStypeListener;
        }

        public void onCameraViewStarted(int width, int height) {
            mOldStyleListener.onCameraViewStarted(width, height);
        }

        public void onCameraViewStopped() {
            mOldStyleListener.onCameraViewStopped();
        }

        public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
            Mat result = null;
            switch (mPreviewFormat) {
                case RGBA:
                    result = mOldStyleListener.onCameraFrame(inputFrame.rgba());
                    break;
                case GRAY:
                    result = mOldStyleListener.onCameraFrame(inputFrame.gray());
                    break;
                default:
                    Log.e(TAG, "Invalid frame format! Only RGBA and Gray Scale are supported!");
            }

            return result;
        }

        public void setFrameFormat(int format) {
            mPreviewFormat = format;
        }

        private int mPreviewFormat = RGBA;
        private CvCameraViewListener mOldStyleListener;
    }

    /**
     * This class interface is abstract representation of single frame from camera for onCameraFrame callback
     * Attention: Do not use objects, that represents this interface out of onCameraFrame callback!
     */
    public interface CvCameraViewFrame {

        /**
         * This method returns RGBA Mat with frame
         */
        public Mat rgba();

        /**
         * This method returns single channel gray scale Mat with frame
         */
        public Mat gray();
    }

    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
        Log.d(TAG, "call surfaceChanged event");
        synchronized(mSyncObject) {
            if (!mSurfaceExist) {
                mSurfaceExist = true;
                checkCurrentState();
            } else {
                /* Surface changed. We need to stop camera and restart with new parameters */
                /* Pretend that old surface has been destroyed */
                mSurfaceExist = false;
                checkCurrentState();
                /* Now use new surface. Say we have it now */
                mSurfaceExist = true;
                checkCurrentState();
            }
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        /* Do nothing. Wait until surfaceChanged delivered */
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        synchronized(mSyncObject) {
            mSurfaceExist = false;
            checkCurrentState();
        }
    }

    /**
     * This method is provided for clients, so they can enable the camera connection.
     * The actual onCameraViewStarted callback will be delivered only after both this method is called and surface is available
     */
    public void enableView() {
        synchronized(mSyncObject) {
            mEnabled = true;
            checkCurrentState();
        }
    }

    /**
     * This method is provided for clients, so they can disable camera connection and stop
     * the delivery of frames even though the surface view itself is not destroyed and still stays on the scren
     */
    public void disableView() {
        synchronized(mSyncObject) {
            mEnabled = false;
            checkCurrentState();
        }
    }

    /**
     * This method enables label with fps value on the screen
     */
    public void enableFpsMeter() {
        if (mFpsMeter == null) {
            mFpsMeter = new FpsMeter();
            mFpsMeter.setResolution(mFrameWidth, mFrameHeight);
        }
    }

    public void disableFpsMeter() {
        mFpsMeter = null;
    }

    /**
     *
     * @param listener - set the CvCameraViewListener2
     */
    public void setCvCameraViewListener(CvCameraViewListener2 listener) {
        mListener = listener;
    }

    public void setCvCameraViewListener(CvCameraViewListener listener) {
        CvCameraViewListenerAdapter adapter = new CvCameraViewListenerAdapter(listener);
        adapter.setFrameFormat(mPreviewFormat);
        mListener = adapter;
    }

    /**
     * This method sets the maximum size that camera frame is allowed to be. When selecting
     * size - the biggest size which less or equal the size set will be selected.
     * As an example - we set setMaxFrameSize(200,200) and we have 176x152 and 320x240 sizes. The
     * preview frame will be selected with 176x152 size.
     * This method is useful when need to restrict the size of preview frame for some reason (for example for video recording)
     * @param maxWidth - the maximum width allowed for camera frame.
     * @param maxHeight - the maximum height allowed for camera frame
     */
    public void setMaxFrameSize(int maxWidth, int maxHeight) {
        mMaxWidth = maxWidth;
        mMaxHeight = maxHeight;
    }

    public void SetCaptureFormat(int format)
    {
        mPreviewFormat = format;
        if (mListener instanceof CvCameraViewListenerAdapter) {
            CvCameraViewListenerAdapter adapter = (CvCameraViewListenerAdapter) mListener;
            adapter.setFrameFormat(mPreviewFormat);
        }
    }

    /**
     * Called when mSyncObject lock is held
     */
    private void checkCurrentState() {
        Log.d(TAG, "call checkCurrentState");
        int targetState;

        if (mEnabled && mSurfaceExist && getVisibility() == VISIBLE) {
            targetState = STARTED;
        } else {
            targetState = STOPPED;
        }

        if (targetState != mState) {
            /* The state change detected. Need to exit the current state and enter target state */
            processExitState(mState);
            mState = targetState;
            processEnterState(mState);
        }
    }

    private void processEnterState(int state) {
        Log.d(TAG, "call processEnterState: " + state);
        switch(state) {
            case STARTED:
                onEnterStartedState();
                if (mListener != null) {
                    mListener.onCameraViewStarted(mFrameWidth, mFrameHeight);
                }
                break;
            case STOPPED:
                onEnterStoppedState();
                if (mListener != null) {
                    mListener.onCameraViewStopped();
                }
                break;
        }
    }

    private void processExitState(int state) {
        Log.d(TAG, "call processExitState: " + state);
        switch(state) {
            case STARTED:
                onExitStartedState();
                break;
            case STOPPED:
                onExitStoppedState();
                break;
        }
    }

    private void onEnterStoppedState() {
        /* nothing to do */
    }

    private void onExitStoppedState() {
        /* nothing to do */
    }

    // NOTE: The order of bitmap constructor and camera connection is important for android 4.1.x
    // Bitmap must be constructed before surface
    private void onEnterStartedState() {
        Log.d(TAG, "call onEnterStartedState");
        /* Connect camera */
        if (!connectCamera(getWidth(), getHeight())) {
            AlertDialog ad = new AlertDialog.Builder(getContext()).create();
            ad.setCancelable(false); // This blocks the 'BACK' button
            ad.setMessage("It seems that you device does not support camera (or it is locked). Application will be closed.");
            ad.setButton(DialogInterface.BUTTON_NEUTRAL,  "OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    ((Activity) getContext()).finish();
                }
            });
            ad.show();

        }
    }

    private void onExitStartedState() {
        disconnectCamera();
        if (mCacheBitmap != null) {
            mCacheBitmap.recycle();
        }
    }

    private float getRatio(int widthSource, int heightSource, int widthTarget, int heightTarget) {
        if (widthTarget <= heightTarget) {
            return (float) heightTarget / (float) heightSource;
        } else {
            return (float) widthTarget / (float) widthSource;
        }
    }

    private static int rating = -1;

    /**
     * Used to check if the app is running on an emulator or not.
     * See: https://github.com/gingo/android-emulator-detector
     * @return True if the app is running on an emulator.
     */
    public boolean isEmulator() {
        if (BuildConfig.DEBUG) {
            int newRating = 0;
            if (rating < 0) {
                if (Build.PRODUCT.contains("sdk") ||
                        Build.PRODUCT.contains("Andy") ||
                        Build.PRODUCT.contains("ttVM_Hdragon") ||
                        Build.PRODUCT.contains("google_sdk") ||
                        Build.PRODUCT.contains("Droid4X") ||
                        Build.PRODUCT.contains("nox") ||
                        Build.PRODUCT.contains("sdk_x86") ||
                        Build.PRODUCT.contains("sdk_google") ||
                        Build.PRODUCT.contains("vbox86p")) {
                    Log.d(TAG, "Build.PRODUCT: " + Build.PRODUCT);
                    newRating++;
                }

                if (Build.MANUFACTURER.equals("unknown") ||
                        Build.MANUFACTURER.equals("Genymotion") ||
                        Build.MANUFACTURER.contains("Andy") ||
                        Build.MANUFACTURER.contains("MIT") ||
                        Build.MANUFACTURER.contains("nox") ||
                        Build.MANUFACTURER.contains("TiantianVM")){
                    newRating++;
                    Log.d(TAG, "Build.MANUFACTURER: " + Build.MANUFACTURER);
                }

                if (Build.BRAND.equals("generic") ||
                        Build.BRAND.equals("generic_x86") ||
                        Build.BRAND.equals("TTVM") ||
                        Build.BRAND.equals("google") ||
                        Build.BRAND.contains("Andy")) {
                    newRating++;
                    Log.d(TAG, "Build.BRAND: " + Build.BRAND);
                }

                if (Build.DEVICE.contains("generic") ||
                        Build.DEVICE.contains("generic_x86") ||
                        Build.DEVICE.contains("Andy") ||
                        Build.DEVICE.contains("ttVM_Hdragon") ||
                        Build.DEVICE.contains("Droid4X") ||
                        Build.DEVICE.contains("nox") ||
                        Build.DEVICE.contains("generic_x86_64") ||
                        Build.DEVICE.contains("vbox86p")) {
                    newRating++;
                    Log.d(TAG, "Build.DEVICE: " + Build.DEVICE);
                }

                if (Build.MODEL.equals("sdk") ||
                        Build.MODEL.equals("google_sdk") ||
                        Build.MODEL.contains("Droid4X") ||
                        Build.MODEL.contains("TiantianVM") ||
                        Build.MODEL.contains("Andy") ||
                        Build.MODEL.equals("Android SDK built for x86_64") ||
                        Build.MODEL.equals("Android SDK built for x86")) {
                    newRating++;
                    Log.d(TAG, "Build.MODEL: " + Build.MODEL);
                }

                if (Build.HARDWARE.equals("goldfish") ||
                        Build.HARDWARE.equals("vbox86") ||
                        Build.HARDWARE.contains("nox") ||
                        Build.HARDWARE.contains("ttVM_x86")) {
                    Log.d(TAG, "Build.HARDWARE: " + Build.HARDWARE);
                    newRating++;
                }

                if (Build.FINGERPRINT.contains("generic/sdk/generic") ||
                        Build.FINGERPRINT.contains("generic_x86/sdk_x86/generic_x86") ||
                        Build.FINGERPRINT.contains("Andy") ||
                        Build.FINGERPRINT.contains("ttVM_Hdragon") ||
                        Build.FINGERPRINT.contains("generic_x86_64") ||
                        Build.FINGERPRINT.contains("generic/google_sdk/generic") ||
                        Build.FINGERPRINT.contains("vbox86p") ||
                        Build.FINGERPRINT.contains("generic/vbox86p/vbox86p")) {
                    Log.d(TAG, "Build.FINGERPRINT: " + Build.FINGERPRINT);
                    newRating++;
                }

                try {
                    String opengl = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
                    if (opengl != null){
                        if( opengl.contains("Bluestacks") ||
                                opengl.contains("Translator")
                                )
                            newRating += 10;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    File sharedFolder = new File(Environment
                            .getExternalStorageDirectory().toString()
                            + File.separatorChar
                            + "windows"
                            + File.separatorChar
                            + "BstSharedFolder");

                    if (sharedFolder.exists()) {
                        newRating += 10;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                rating = newRating;
            }
            return rating > 3;
        } else
            return false; // Always return false if it is a release build
    }

    /**
     * Determine current orientation of the device.
     * Source: http://stackoverflow.com/a/10383164/2175837
     * @return Returns the current orientation of the device.
     */
    public int getScreenOrientation() {
        int rotation = mWindowManager.getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) && height > width || (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) && width > height) {
            switch(rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    Log.e(TAG, "Unknown screen orientation. Defaulting to portrait");
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
        } else { // If the device's natural orientation is landscape or if the device is square:
            switch(rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    Log.e(TAG, "Unknown screen orientation. Defaulting to landscape");
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
            }
        }

        return orientation;
    }

    /**
     * This method shall be called by the subclasses when they have valid
     * object and want it to be delivered to external client (via callback) and
     * then displayed on the screen.
     * @param frame - the current frame to be delivered
     */
    protected void deliverAndDrawFrame(CvCameraViewFrame frame) {
        Mat modified;

        if (mListener != null) {
            modified = mListener.onCameraFrame(frame);
        } else {
            modified = frame.rgba();
        }

        boolean bmpValid = true;
        if (modified != null) {
            try {
                Utils.matToBitmap(modified, mCacheBitmap);
            } catch(Exception e) {
                Log.e(TAG, "Mat type: " + modified);
                Log.e(TAG, "Bitmap type: " + mCacheBitmap.getWidth() + "*" + mCacheBitmap.getHeight());
                Log.e(TAG, "Utils.matToBitmap() throws an exception: " + e.getMessage());
                bmpValid = false;
            }
        }

        if (bmpValid && mCacheBitmap != null) {
            Canvas canvas = getHolder().lockCanvas();
            if (canvas != null) {
                canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
                int degrees = 0;

                if (!isEmulator()) { // Rotation is always reported as portrait on the emulator for some reason
                    int orientation = getScreenOrientation();
                    switch (orientation) {
                        case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                            degrees = -90;
                            break;
                        case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                            break;
                        case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                            degrees = 90;
                            break;
                        case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                            degrees = 180;
                            break;
                    }
                }

                Matrix matrix = new Matrix();
                matrix.postRotate(degrees);
                Bitmap outputBitmap = Bitmap.createBitmap(mCacheBitmap, 0, 0, mCacheBitmap.getWidth(), mCacheBitmap.getHeight(), matrix, true);

                if (outputBitmap.getWidth() <= canvas.getWidth()) {
                    mScale = getRatio(outputBitmap.getWidth(), outputBitmap.getHeight(), canvas.getWidth(), canvas.getHeight());
                } else {
                    mScale = getRatio(canvas.getWidth(), canvas.getHeight(), outputBitmap.getWidth(), outputBitmap.getHeight());
                }

                if (mScale != 0) {
                    canvas.scale(mScale, mScale, 0, 0);
                }

                if (BuildConfig.DEBUG)
                    Log.v(TAG, "mStretch value: " + mScale);

                canvas.drawBitmap(outputBitmap, 0, 0, null);

                if (mFpsMeter != null) {
                    mFpsMeter.measure();
                    mFpsMeter.draw(canvas, 20, 30);
                }
                getHolder().unlockCanvasAndPost(canvas);
            }
        }
    }

    /**
     * This method is invoked shall perform concrete operation to initialize the camera.
     * CONTRACT: as a result of this method variables mFrameWidth and mFrameHeight MUST be
     * initialized with the size of the Camera frames that will be delivered to external processor.
     * @param width - the width of this SurfaceView
     * @param height - the height of this SurfaceView
     */
    protected abstract boolean connectCamera(int width, int height);

    /**
     * Disconnects and release the particular camera object being connected to this surface view.
     * Called when syncObject lock is held
     */
    protected abstract void disconnectCamera();

    // NOTE: On Android 4.1.x the function must be called before SurfaceTexture constructor!
    protected void AllocateCache()
    {
        mCacheBitmap = Bitmap.createBitmap(mFrameWidth, mFrameHeight, Bitmap.Config.ARGB_8888);
    }

    public interface ListItemAccessor {
        public int getWidth(Object obj);
        public int getHeight(Object obj);
    }

    /**
     * This helper method can be called by subclasses to select camera preview size.
     * It goes over the list of the supported preview sizes and selects the maximum one which
     * fits both values set via setMaxFrameSize() and surface frame allocated for this view
     * @param supportedSizes - list of android.hardware.Camera.Size
     * @param accessor - accessor used to get the width and height
     * @param surfaceWidth - width of the surface
     * @param surfaceHeight - height of the surface
     * @return optimal frame size
     */
    protected Size calculateCameraFrameSize(List<?> supportedSizes, ListItemAccessor accessor, int surfaceWidth, int surfaceHeight) {
        int calcWidth = 0;
        int calcHeight = 0;

        int maxAllowedWidth = (mMaxWidth != MAX_UNSPECIFIED && mMaxWidth < surfaceWidth)? mMaxWidth : surfaceWidth;
        int maxAllowedHeight = (mMaxHeight != MAX_UNSPECIFIED && mMaxHeight < surfaceHeight)? mMaxHeight : surfaceHeight;

        for (Object size : supportedSizes) {
            int width = accessor.getWidth(size);
            int height = accessor.getHeight(size);

            if (width <= maxAllowedWidth && height <= maxAllowedHeight) {
                if (width >= calcWidth && height >= calcHeight) {
                    calcWidth = width;
                    calcHeight = height;
                }
            }
        }

        return new Size(calcWidth, calcHeight);
    }
}
