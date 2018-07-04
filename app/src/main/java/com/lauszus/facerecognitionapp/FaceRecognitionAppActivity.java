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

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.wonderkiln.camerakit.CameraKit;
import com.wonderkiln.camerakit.CameraKitEventCallback;
import com.wonderkiln.camerakit.CameraKitImage;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class FaceRecognitionAppActivity extends AppCompatActivity {
    private static final String TAG = FaceRecognitionAppActivity.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST_CODE = 0;
    private ArrayList<Mat> images;
    private ArrayList<String> imagesLabels;
    private String[] uniqueLabels;
    private CameraViewDoubleTap mCameraView;
    private Toast mToast;
    private boolean useEigenfaces;
    private SeekBarArrows mThresholdFace, mThresholdDistance, mMaximumImages;
    private float faceThreshold, distanceThreshold;
    private int maximumImages;
    private SharedPreferences prefs;
    private TinyDB tinydb;
    private Toolbar mToolbar;
    private NativeMethods.TrainFacesTask mTrainFacesTask;
    private WindowManager mWindowManager;

    private void showToast(String message, int duration) {
        if (duration != Toast.LENGTH_SHORT && duration != Toast.LENGTH_LONG)
            throw new IllegalArgumentException();
        if (mToast != null && mToast.getView().isShown())
            mToast.cancel(); // Close the toast if it is already open
        mToast = Toast.makeText(this, message, duration);
        mToast.show();
    }

    private void addLabel(String string) {
        String label = string.substring(0, 1).toUpperCase(Locale.US) + string.substring(1).trim().toLowerCase(Locale.US); // Make sure that the name is always uppercase and rest is lowercase
        imagesLabels.add(label); // Add label to list of labels
        Log.i(TAG, "Label: " + label);

        trainFaces(); // When we have finished setting the label, then retrain faces
    }

    /**
     * Train faces using stored images.
     * @return  Returns false if the task is already running.
     */
    private boolean trainFaces() {
        if (images.isEmpty())
            return true; // The array might be empty if the method is changed in the OnClickListener

        if (mTrainFacesTask != null && mTrainFacesTask.getStatus() != AsyncTask.Status.FINISHED) {
            Log.i(TAG, "mTrainFacesTask is still running");
            return false;
        }

        Mat imagesMatrix = new Mat((int) images.get(0).total(), images.size(), images.get(0).type());
        for (int i = 0; i < images.size(); i++)
            images.get(i).copyTo(imagesMatrix.col(i)); // Create matrix where each image is represented as a column vector

        Log.i(TAG, "Images height: " + imagesMatrix.height() + " Width: " + imagesMatrix.width() + " total: " + imagesMatrix.total());

        // Train the face recognition algorithms in an asynchronous task, so we do not skip any frames
        if (useEigenfaces) {
            Log.i(TAG, "Training Eigenfaces");
            showToast("Training " + getResources().getString(R.string.eigenfaces), Toast.LENGTH_SHORT);

            mTrainFacesTask = new NativeMethods.TrainFacesTask(imagesMatrix, trainFacesTaskCallback);
        } else {
            Log.i(TAG, "Training Fisherfaces");
            showToast("Training " + getResources().getString(R.string.fisherfaces), Toast.LENGTH_SHORT);

            Set<String> uniqueLabelsSet = new HashSet<>(imagesLabels); // Get all unique labels
            uniqueLabels = uniqueLabelsSet.toArray(new String[uniqueLabelsSet.size()]); // Convert to String array, so we can read the values from the indices

            int[] classesNumbers = new int[uniqueLabels.length];
            for (int i = 0; i < classesNumbers.length; i++)
                classesNumbers[i] = i + 1; // Create incrementing list for each unique label starting at 1

            int[] classes = new int[imagesLabels.size()];
            for (int i = 0; i < imagesLabels.size(); i++) {
                String label = imagesLabels.get(i);
                for (int j = 0; j < uniqueLabels.length; j++) {
                    if (label.equals(uniqueLabels[j])) {
                        classes[i] = classesNumbers[j]; // Insert corresponding number
                        break;
                    }
                }
            }

            /*for (int i = 0; i < imagesLabels.size(); i++)
                Log.i(TAG, "Classes: " + imagesLabels.get(i) + " = " + classes[i]);*/

            Mat vectorClasses = new Mat(classes.length, 1, CvType.CV_32S); // CV_32S == int
            vectorClasses.put(0, 0, classes); // Copy int array into a vector

            mTrainFacesTask = new NativeMethods.TrainFacesTask(imagesMatrix, vectorClasses, trainFacesTaskCallback);
        }
        mTrainFacesTask.execute();

        return true;
    }

    private NativeMethods.TrainFacesTask.Callback trainFacesTaskCallback = new NativeMethods.TrainFacesTask.Callback() {
        @Override
        public void onTrainFacesComplete(boolean result) {
            if (result)
                showToast("Training complete", Toast.LENGTH_SHORT);
            else
                showToast("Training failed", Toast.LENGTH_LONG);
        }
    };

    private void showLabelsDialog() {
        Set<String> uniqueLabelsSet = new HashSet<>(imagesLabels); // Get all unique labels
        if (!uniqueLabelsSet.isEmpty()) { // Make sure that there are any labels
            // Inspired by: http://stackoverflow.com/questions/15762905/how-can-i-display-a-list-view-in-an-android-alert-dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(FaceRecognitionAppActivity.this);
            builder.setTitle("Select label:");
            builder.setPositiveButton("New face", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    showEnterLabelDialog();
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    images.remove(images.size() - 1); // Remove last image
                }
            });
            builder.setCancelable(false); // Prevent the user from closing the dialog

            String[] uniqueLabels = uniqueLabelsSet.toArray(new String[uniqueLabelsSet.size()]); // Convert to String array for ArrayAdapter
            Arrays.sort(uniqueLabels); // Sort labels alphabetically
            final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(FaceRecognitionAppActivity.this, android.R.layout.simple_list_item_1, uniqueLabels) {
                @Override
                public @NonNull View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    TextView textView = (TextView) super.getView(position, convertView, parent);
                    if (getResources().getBoolean(R.bool.isTablet))
                        textView.setTextSize(20); // Make text slightly bigger on tablets compared to phones
                    else
                        textView.setTextSize(18); // Increase text size a little bit
                    return textView;
                }
            };
            ListView mListView = new ListView(FaceRecognitionAppActivity.this);
            mListView.setAdapter(arrayAdapter); // Set adapter, so the items actually show up
            builder.setView(mListView); // Set the ListView

            final AlertDialog dialog = builder.show(); // Show dialog and store in final variable, so it can be dismissed by the ListView

            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    dialog.dismiss();
                    String label = arrayAdapter.getItem(position);
                    if (label != null)
                        addLabel(label);
                }
            });
        } else
            showEnterLabelDialog(); // If there is no existing labels, then ask the user for a new label
    }

    private void showEnterLabelDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(FaceRecognitionAppActivity.this);
        builder.setTitle("Please enter your name:");

        final EditText input = new EditText(FaceRecognitionAppActivity.this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Submit", null); // Set up positive button, but do not provide a listener, so we can check the string before dismissing the dialog
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                images.remove(images.size() - 1); // Remove last image
            }
        });
        builder.setCancelable(false); // User has to input a name
        AlertDialog dialog = builder.create();

        // Source: http://stackoverflow.com/a/7636468/2175837
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                Button mButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                mButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String string = input.getText().toString().trim();
                        if (!string.isEmpty()) { // Make sure the input is valid
                            // If input is valid, dismiss the dialog and add the label to the array
                            dialog.dismiss();
                            addLabel(string);
                        }
                    }
                });
            }
        });

        // Show keyboard, so the user can start typing straight away
        Window mWindow = dialog.getWindow();
        if (mWindow != null)
            mWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        dialog.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        setContentView(R.layout.activity_face_recognition_app);
        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar); // Sets the Toolbar to act as the ActionBar for this Activity window

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        final RadioButton mRadioButtonEigenfaces = findViewById(R.id.eigenfaces);
        final RadioButton mRadioButtonFisherfaces = findViewById(R.id.fisherfaces);

        mRadioButtonEigenfaces.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useEigenfaces = true;
                if (!trainFaces()) {
                    useEigenfaces = false; // Set variable back
                    showToast("Still training...", Toast.LENGTH_SHORT);
                    mRadioButtonEigenfaces.setChecked(useEigenfaces);
                    mRadioButtonFisherfaces.setChecked(!useEigenfaces);
                }
            }
        });
        mRadioButtonFisherfaces.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useEigenfaces = false;
                if (!trainFaces()) {
                    useEigenfaces = true; // Set variable back
                    showToast("Still training...", Toast.LENGTH_SHORT);
                    mRadioButtonEigenfaces.setChecked(useEigenfaces);
                    mRadioButtonFisherfaces.setChecked(!useEigenfaces);
                }
            }
        });

        // Set radio button based on value stored in shared preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        useEigenfaces = prefs.getBoolean("useEigenfaces", false);
        mRadioButtonEigenfaces.setChecked(useEigenfaces);
        mRadioButtonFisherfaces.setChecked(!useEigenfaces);

        tinydb = new TinyDB(this); // Used to store ArrayLists in the shared preferences

        mThresholdFace = findViewById(R.id.threshold_face);
        mThresholdFace.setOnSeekBarArrowsChangeListener(new SeekBarArrows.OnSeekBarArrowsChangeListener() {
            @Override
            public void onProgressChanged(float progress) {
                Log.i(TAG, "Face threshold: " + mThresholdFace.progressToString(progress));
                faceThreshold = progress;
            }
        });
        faceThreshold = mThresholdFace.getProgress(); // Get initial value

        mThresholdDistance = findViewById(R.id.threshold_distance);
        mThresholdDistance.setOnSeekBarArrowsChangeListener(new SeekBarArrows.OnSeekBarArrowsChangeListener() {
            @Override
            public void onProgressChanged(float progress) {
                Log.i(TAG, "Distance threshold: " + mThresholdDistance.progressToString(progress));
                distanceThreshold = progress;
            }
        });
        distanceThreshold = mThresholdDistance.getProgress(); // Get initial value

        mMaximumImages = findViewById(R.id.maximum_images);
        mMaximumImages.setOnSeekBarArrowsChangeListener(new SeekBarArrows.OnSeekBarArrowsChangeListener() {
            @Override
            public void onProgressChanged(float progress) {
                Log.i(TAG, "Maximum number of images: " + mMaximumImages.progressToString(progress));
                maximumImages = (int)progress;
                if (images != null && images.size() > maximumImages) {
                    int nrRemoveImages = images.size() - maximumImages;
                    Log.i(TAG, "Removed " + nrRemoveImages + " images from the list");
                    images.subList(0, nrRemoveImages).clear(); // Remove oldest images
                    imagesLabels.subList(0, nrRemoveImages).clear(); // Remove oldest labels
                    trainFaces(); // Retrain faces
                }
            }
        });
        maximumImages = (int)mMaximumImages.getProgress(); // Get initial value

        findViewById(R.id.clear_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Cleared training set");
                images.clear(); // Clear both arrays, when new instance is created
                imagesLabels.clear();
                showToast("Training set cleared", Toast.LENGTH_SHORT);
            }
        });

        mCameraView = findViewById(R.id.camera);
        mCameraView.setOnToggleFacingListener(new CameraViewDoubleTap.OnToggleFacingListener() {
            @Override
            public void onToggleFacing() {
                // Show flip animation when the camera is flipped due to a double tap
                flipCameraAnimation();
            }
        });
        //mCameraView.setMethod(CameraKit.Constants.METHOD_SPEED);
        mCameraView.setFacing(prefs.getInt("facing", CameraKit.Constants.FACING_FRONT));
        mCameraView.setPinchToZoom(false);
        mCameraView.setPermissions(CameraKit.Constants.PERMISSIONS_PICTURE);

        findViewById(R.id.take_picture_button).setOnClickListener(new View.OnClickListener() {
            NativeMethods.MeasureDistTask mMeasureDistTask;

            @Override
            public void onClick(View v) {
                if (mMeasureDistTask != null && mMeasureDistTask.getStatus() != AsyncTask.Status.FINISHED) {
                    Log.i(TAG, "mMeasureDistTask is still running");
                    showToast("Still processing old image...", Toast.LENGTH_SHORT);
                    return;
                }
                if (mTrainFacesTask != null && mTrainFacesTask.getStatus() != AsyncTask.Status.FINISHED) {
                    Log.i(TAG, "mTrainFacesTask is still running");
                    showToast("Still training...", Toast.LENGTH_SHORT);
                    return;
                }

                mCameraView.captureImage(new CameraKitEventCallback<CameraKitImage>() {
                    @Override
                    public void callback(CameraKitImage cameraKitImage) {
                        Mat mRgba = new Mat();
                        Bitmap bmp = cameraKitImage.getBitmap();
                        Utils.bitmapToMat(bmp, mRgba);

                        // Flip image so it is always pointing upward
                        if (mCameraView.isFacingFront()) {
                            int orientation = getScreenOrientation();
                            switch (orientation) {
                                case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                                    Core.flip(mRgba, mRgba, -1); // Flip along both axis
                                    break;
                            }
                        }

                        Mat mGray = new Mat();
                        Imgproc.cvtColor(mRgba, mGray, Imgproc.COLOR_RGB2GRAY);

                        //SaveImage(mRgba);
                        //SaveImage(mGray);

                        Log.i(TAG, "Gray height: " + mGray.height() + " Width: " + mGray.width() + " total: " + mGray.total());
                        if (mGray.total() == 0)
                            return;
                        Size imageSize = new Size(200, 200.0f / ((float) mGray.width() / (float) mGray.height())); // Scale image in order to decrease computation time
                        Imgproc.resize(mGray, mGray, imageSize);
                        Log.i(TAG, "Small gray height: " + mGray.height() + " Width: " + mGray.width() + " total: " + mGray.total());

                        Mat image = mGray.reshape(0, (int) mGray.total()); // Create column vector
                        Log.i(TAG, "Vector height: " + image.height() + " Width: " + image.width() + " total: " + image.total());
                        images.add(image); // Add current image to the array

                        if (images.size() > maximumImages) {
                            images.remove(0); // Remove first image
                            imagesLabels.remove(0); // Remove first label
                            Log.i(TAG, "The number of images is limited to: " + images.size());
                        }

                        // Calculate normalized Euclidean distance
                        mMeasureDistTask = new NativeMethods.MeasureDistTask(useEigenfaces, measureDistTaskCallback);
                        mMeasureDistTask.execute(image);

                        showLabelsDialog();
                    }
                });
            }
        });
    }

    private NativeMethods.MeasureDistTask.Callback measureDistTaskCallback = new NativeMethods.MeasureDistTask.Callback() {
        @Override
        public void onMeasureDistComplete(Bundle bundle) {
            if (bundle == null) {
                showToast("Failed to measure distance", Toast.LENGTH_LONG);
                return;
            }

            float minDist = bundle.getFloat(NativeMethods.MeasureDistTask.MIN_DIST_FLOAT);
            if (minDist != -1) {
                int minIndex = bundle.getInt(NativeMethods.MeasureDistTask.MIN_DIST_INDEX_INT);
                float faceDist = bundle.getFloat(NativeMethods.MeasureDistTask.DIST_FACE_FLOAT);
                if (imagesLabels.size() > minIndex) { // Just to be sure
                    Log.i(TAG, "dist[" + minIndex + "]: " + minDist + ", face dist: " + faceDist + ", label: " + imagesLabels.get(minIndex));

                    String minDistString = String.format(Locale.US, "%.4f", minDist);
                    String faceDistString = String.format(Locale.US, "%.4f", faceDist);

                    if (faceDist < faceThreshold && minDist < distanceThreshold) // 1. Near face space and near a face class
                        showToast("Face detected: " + imagesLabels.get(minIndex) + ". Distance: " + minDistString, Toast.LENGTH_LONG);
                    else if (faceDist < faceThreshold) // 2. Near face space but not near a known face class
                        showToast("Unknown face. Face distance: " + faceDistString + ". Closest Distance: " + minDistString, Toast.LENGTH_LONG);
                    else if (minDist < distanceThreshold) // 3. Distant from face space and near a face class
                        showToast("False recognition. Face distance: " + faceDistString + ". Closest Distance: " + minDistString, Toast.LENGTH_LONG);
                    else // 4. Distant from face space and not near a known face class.
                        showToast("Image is not a face. Face distance: " + faceDistString + ". Closest Distance: " + minDistString, Toast.LENGTH_LONG);
                }
            } else {
                Log.w(TAG, "Array is null");
                if (useEigenfaces || uniqueLabels == null || uniqueLabels.length > 1)
                    showToast("Keep training...", Toast.LENGTH_SHORT);
                else
                    showToast("Fisherfaces needs two different faces", Toast.LENGTH_SHORT);
            }
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        // Read threshold values
        float progress = prefs.getFloat("faceThreshold", -1);
        if (progress != -1)
            mThresholdFace.setProgress(progress);
        progress = prefs.getFloat("distanceThreshold", -1);
        if (progress != -1)
            mThresholdDistance.setProgress(progress);
        mMaximumImages.setProgress(prefs.getInt("maximumImages", 25)); // Use 25 images by default
    }

    @Override
    public void onStop() {
        mCameraView.stop();

        // Store threshold values
        Editor editor = prefs.edit();
        editor.putFloat("faceThreshold", faceThreshold);
        editor.putFloat("distanceThreshold", distanceThreshold);
        editor.putInt("maximumImages", maximumImages);
        editor.putBoolean("useEigenfaces", useEigenfaces);
        editor.putInt("facing", mCameraView.getFacing());
        editor.apply();

        // Store ArrayLists containing the images and labels
        if (images != null && imagesLabels != null) {
            tinydb.putListMat("images", images);
            tinydb.putListString("imagesLabels", imagesLabels);
        }

        super.onStop();
    }

    @Override
    public void onPause() {
        mCameraView.stop();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    loadOpenCV();
                else {
                    showToast("Camera permission required", Toast.LENGTH_LONG);
                    finish();
                }
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Request permission if needed
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED/* || ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED*/)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA/*, Manifest.permission.WRITE_EXTERNAL_STORAGE*/}, PERMISSIONS_REQUEST_CODE);
        else
            loadOpenCV();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    NativeMethods.loadNativeLibraries(); // Load native libraries after(!) OpenCV initialization
                    Log.i(TAG, "OpenCV loaded successfully");
                    mCameraView.start();

                    // Read images and labels from shared preferences
                    images = tinydb.getListMat("images");
                    imagesLabels = tinydb.getListString("imagesLabels");

                    Log.i(TAG, "Number of images: " + images.size()  + ". Number of labels: " + imagesLabels.size());
                    if (!images.isEmpty()) {
                        trainFaces(); // Train images after they are loaded
                        Log.i(TAG, "Images height: " + images.get(0).height() + " Width: " + images.get(0).width() + " total: " + images.get(0).total());
                    }
                    Log.i(TAG, "Labels: " + imagesLabels);

                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    private void loadOpenCV() {
        if (!OpenCVLoader.initDebug(true)) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "unused"})
    public void SaveImage(Mat mat) {
        Mat mIntermediateMat = new Mat();

        if (mat.channels() == 1) // Grayscale image
            Imgproc.cvtColor(mat, mIntermediateMat, Imgproc.COLOR_GRAY2BGR);
        else
            Imgproc.cvtColor(mat, mIntermediateMat, Imgproc.COLOR_RGBA2BGR);

        File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), TAG); // Save pictures in Pictures directory
        path.mkdir(); // Create directory if needed
        String fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date()) + ".png";
        File file = new File(path, fileName);

        boolean bool = Imgcodecs.imwrite(file.toString(), mIntermediateMat);

        if (bool)
            Log.i(TAG, "SUCCESS writing image to external storage");
        else
            Log.e(TAG, "Failed writing image to external storage");
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START))
            drawer.closeDrawer(GravityCompat.START);
        else
            super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_face_recognition_app, menu);
        // Show rear camera icon if front camera is currently used and front camera icon if back camera is used
        MenuItem menuItem = menu.findItem(R.id.flip_camera);
        if (mCameraView.isFacingFront())
            menuItem.setIcon(R.drawable.ic_camera_front_white_24dp);
        else
            menuItem.setIcon(R.drawable.ic_camera_rear_white_24dp);
        return true;
    }

    private void flipCameraAnimation() {
        // Do flip camera animation
        View v = mToolbar.findViewById(R.id.flip_camera);
        ObjectAnimator animator = ObjectAnimator.ofFloat(v, "rotationY", v.getRotationY() + 180.0f);
        animator.setDuration(500);
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                supportInvalidateOptionsMenu(); // This will call onCreateOptionsMenu()
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        animator.start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.flip_camera:
                mCameraView.toggleFacing();
                flipCameraAnimation();
                return true;
        }
        return super.onOptionsItemSelected(item);
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
        // If the device's natural orientation is portrait:
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
}
