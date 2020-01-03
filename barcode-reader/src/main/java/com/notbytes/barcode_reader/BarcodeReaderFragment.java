package com.notbytes.barcode_reader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.TypedArray;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.notbytes.barcode_reader.camera.CameraSource;
import com.notbytes.barcode_reader.camera.CameraSourcePreview;
import com.notbytes.barcode_reader.camera.GraphicOverlay;
import com.wang.avi.AVLoadingIndicatorView;

import java.io.IOException;
import java.util.List;

public class BarcodeReaderFragment extends Fragment implements View.OnTouchListener, BarcodeGraphicTracker.BarcodeGraphicTrackerListener {
    protected static final String TAG = BarcodeReaderFragment.class.getSimpleName();
    protected static final String KEY_AUTO_FOCUS = "key_auto_focus";
    protected static final String KEY_USE_FLASH = "key_use_flash";
    private static final String KEY_SCAN_OVERLAY_VISIBILITY = "key_scan_overlay_visibility";
    // intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    // constants used to pass extra data in the intent
    protected boolean autoFocus = false;
    protected boolean useFlash = false;
    private String beepSoundFile;
    public static final String BarcodeObject = "Barcode";
    private boolean isPaused = false;

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;

    // helper objects for detecting taps and pinches.
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private BarcodeReaderListener mListener;
    private SharedPreferences permissionStatus;
    private static final int PERMISSION_CALLBACK_CONSTANT = 101;
    private static final int REQUEST_PERMISSION_SETTING = 102;
    private boolean sentToSettings = false;
    private ScannerOverlay mScanOverlay;
    private int scanOverlayVisibility;
    private AVLoadingIndicatorView loader;
    private String deviceSSID;

    public BarcodeReaderFragment() {
        // Required empty public constructor
    }

    public static BarcodeReaderFragment newInstance(boolean autoFocus, boolean useFlash) {
        Bundle args = new Bundle();
        args.putBoolean(KEY_AUTO_FOCUS, autoFocus);
        args.putBoolean(KEY_USE_FLASH, useFlash);
        BarcodeReaderFragment fragment = new BarcodeReaderFragment();
        fragment.setArguments(args);
        return fragment;
    }


    public static BarcodeReaderFragment newInstance(boolean autoFocus, boolean useFlash, int scanOverlayVisibleStatus) {

        Bundle args = new Bundle();
        args.putBoolean(KEY_AUTO_FOCUS, autoFocus);
        args.putBoolean(KEY_USE_FLASH, useFlash);
        args.putInt(KEY_SCAN_OVERLAY_VISIBILITY, scanOverlayVisibleStatus);
        BarcodeReaderFragment fragment = new BarcodeReaderFragment();
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Use this listener if you want more callbacks
     *
     * @param barcodeReaderListener
     */
    public void setListener(BarcodeReaderListener barcodeReaderListener) {
        mListener = barcodeReaderListener;
    }

    public void setBeepSoundFile(String fileName) {
        beepSoundFile = fileName;
    }

    public void pauseScanning() {
        isPaused = true;
    }

    public void resumeScanning() {
        isPaused = false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = this.getArguments();
        if (arguments != null) {
            this.autoFocus = arguments.getBoolean(KEY_AUTO_FOCUS, false);
            this.useFlash = arguments.getBoolean(KEY_USE_FLASH, false);
            this.scanOverlayVisibility = arguments.getInt(KEY_SCAN_OVERLAY_VISIBILITY, View.VISIBLE);
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_barcode_reader, container, false);
        permissionStatus = getActivity().getSharedPreferences("permissionStatus", getActivity().MODE_PRIVATE);
        mPreview = view.findViewById(R.id.preview);
        mGraphicOverlay = view.findViewById(R.id.graphicOverlay);
        mScanOverlay = view.findViewById(R.id.scan_overlay);
        mScanOverlay.setVisibility(scanOverlayVisibility);
        loader = view.findViewById(R.id.loader);
        gestureDetector = new GestureDetector(getActivity(), new CaptureGestureListener());
        scaleGestureDetector = new ScaleGestureDetector(getActivity(), new ScaleListener());
        view.setOnTouchListener(this);
        return view;
    }


    @Override
    public void onInflate(Context context, AttributeSet attrs, Bundle savedInstanceState) {
        super.onInflate(context, attrs, savedInstanceState);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BarcodeReaderFragment);
        autoFocus = a.getBoolean(R.styleable.BarcodeReaderFragment_auto_focus, true);
        useFlash = a.getBoolean(R.styleable.BarcodeReaderFragment_use_flash, false);
        a.recycle();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof BarcodeReaderListener) {
            mListener = (BarcodeReaderListener) context;
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        permissionStatus = getActivity().getSharedPreferences("permissionStatus", getActivity().MODE_PRIVATE);
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.CAMERA)) {
                //Show Information about why you need the permission
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(getString(R.string.grant_permission));
                builder.setMessage(getString(R.string.permission_camera));
                builder.setPositiveButton(R.string.grant, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_CALLBACK_CONSTANT);
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        mListener.onCameraPermissionDenied();
                    }
                });
                builder.show();
            } else if (permissionStatus.getBoolean(Manifest.permission.CAMERA, false)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(getString(R.string.grant_permission));
                builder.setMessage(getString(R.string.permission_camera));
                builder.setPositiveButton(R.string.grant, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        sentToSettings = true;
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
                        intent.setData(uri);
                        startActivityForResult(intent, REQUEST_PERMISSION_SETTING);
                    }
                });
                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        mListener.onCameraPermissionDenied();
                    }
                });
                builder.show();
            } else {
                //just request the permission
                requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_CALLBACK_CONSTANT);
            }


            SharedPreferences.Editor editor = permissionStatus.edit();
            editor.putBoolean(Manifest.permission.CAMERA, true);
            editor.apply();
        } else {
            //You already have the permission, just go ahead.
            proceedAfterPermission();
        }
    }

    private void proceedAfterPermission() {
        createCameraSource(autoFocus, useFlash);
    }


    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     * <p>
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @SuppressLint("InlinedApi")
    private void createCameraSource(final boolean autoFocus, final boolean useFlash) {
        Log.e(TAG, "createCameraSource:");
        Context context = getActivity();

        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
        final BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay, this);
        barcodeDetector.setProcessor(
                new MultiProcessor.Builder<>(barcodeFactory).build());

        if (!barcodeDetector.isOperational()) {
            // Note: The first time that an app using the barcode or face API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any barcodes
            // and/or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = getActivity().registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(getActivity(), R.string.low_storage_error, Toast.LENGTH_LONG).show();
                Log.w(TAG, getString(R.string.low_storage_error));
            }
        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.

        CameraSource.Builder builder = new CameraSource.Builder(getActivity(), barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(1.0f);

        // make sure that auto focus is an available option
        builder = builder.setFocusMode(
                autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null);

        mCameraSource = builder
                .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .build();
    }

    /**
     * Trigger flash torch on and off, perhaps using a compound button.
     */
    public void setUseFlash(boolean use) {
        useFlash = use;
        mCameraSource.setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
    }

    /**
     * Trigger auto focus mode, perhaps using a compound button.
     */
    public void setAutoFocus(boolean continuous) {
        autoFocus = continuous;
        mCameraSource.setFocusMode(autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : Camera.Parameters.FOCUS_MODE_AUTO);
    }

    /**
     * Returns true if device supports flash.
     */
    public boolean deviceSupportsFlash() {
        if (getActivity().getPackageManager() == null)
            return false;
        return getActivity().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA_FLASH);
    }

    /**
     * Restarts the camera.
     */
    @Override
    public void onResume() {
        super.onResume();
        startCameraSource();
        if (sentToSettings) {
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                //Got Permission
                proceedAfterPermission();
            } else {
                mListener.onCameraPermissionDenied();
            }
        }
    }

    /**
     * Stops the camera.
     */
    @Override
    public void onPause() {
        super.onPause();
        if (mPreview != null) {
            mPreview.stop();
        }
        if (loader != null) {
            loader.hide();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPreview != null) {
            mPreview.release();
        }
        if (loader != null) {
            loader.hide();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CALLBACK_CONSTANT) {
            //check if all permissions are granted
            boolean allgranted = false;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    allgranted = true;
                } else {
                    allgranted = false;
                    break;
                }
            }

            if (allgranted) {
                proceedAfterPermission();
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.CAMERA)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(getString(R.string.grant_permission));
                builder.setMessage(getString(R.string.permission_camera));
                builder.setPositiveButton(R.string.grant, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_CALLBACK_CONSTANT);
                    }
                });
                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        mListener.onCameraPermissionDenied();
                    }
                });
                builder.show();
            } else {
                mListener.onCameraPermissionDenied();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PERMISSION_SETTING) {
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                //Got Permission
                proceedAfterPermission();
            }
        }
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() throws SecurityException {
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getActivity());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(getActivity(), code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    /**
     * onTap returns the tapped barcode result to the calling Activity.
     *
     * @param rawX - the raw position of the tap
     * @param rawY - the raw position of the tap.
     * @return true if the activity is ending.
     */
    private boolean onTap(float rawX, float rawY) {
        // Find tap point in preview frame coordinates.
        int[] location = new int[2];
        mGraphicOverlay.getLocationOnScreen(location);
        float x = (rawX - location[0]) / mGraphicOverlay.getWidthScaleFactor();
        float y = (rawY - location[1]) / mGraphicOverlay.getHeightScaleFactor();

        // Find the barcode whose center is closest to the tapped point.
        Barcode best = null;
        float bestDistance = Float.MAX_VALUE;
        for (BarcodeGraphic graphic : mGraphicOverlay.getGraphics()) {
            Barcode barcode = graphic.getBarcode();
            if (barcode.getBoundingBox().contains((int) x, (int) y)) {
                // Exact hit, no need to keep looking.
                best = barcode;
                break;
            }
            float dx = x - barcode.getBoundingBox().centerX();
            float dy = y - barcode.getBoundingBox().centerY();
            float distance = (dx * dx) + (dy * dy);  // actually squared distance
            if (distance < bestDistance) {
                best = barcode;
                bestDistance = distance;
            }
        }

        if (best != null) {
            Intent data = new Intent();
            data.putExtra(BarcodeObject, best);

            // TODO - pass the scanned value
            getActivity().setResult(CommonStatusCodes.SUCCESS, data);
            return true;
        }
        return false;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        boolean b = scaleGestureDetector.onTouchEvent(motionEvent);

        boolean c = gestureDetector.onTouchEvent(motionEvent);

        return b || c || view.onTouchEvent(motionEvent);
    }

    @Override
    public void onScanned(final Barcode barcode) {
        if (mListener != null && !isPaused) {
            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pauseScanning();
                    if (mPreview != null) {
                        mPreview.stop();
                    }
                    mListener.onScanned(barcode);
                }
            });
        }
    }

    @Override
    public void onScannedMultiple(final List<Barcode> barcodes) {
        if (mListener != null && !isPaused) {
            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mListener.onScannedMultiple(barcodes);
                }
            });

        }
    }

    @Override
    public void onBitmapScanned(final SparseArray<Barcode> sparseArray) {
        if (mListener != null) {
            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mListener.onBitmapScanned(sparseArray);
                }
            });

        }
    }

    @Override
    public void onScanError(final String errorMessage) {
        if (mListener != null) {
            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mListener.onScanError(errorMessage);
                }
            });

        }
    }

    private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return onTap(e.getRawX(), e.getRawY()) || super.onSingleTapConfirmed(e);
        }
    }

    private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {

        /**
         * Responds to scaling events for a gesture in progress.
         * Reported by pointer motion.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should consider this event
         * as handled. If an event was not handled, the detector
         * will continue to accumulate movement until an event is
         * handled. This can be useful if an application, for example,
         * only wants to update scaling factors if the change is
         * greater than 0.01.
         */
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return false;
        }

        /**
         * Responds to the beginning of a scaling gesture. Reported by
         * new pointers going down.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should continue recognizing
         * this gesture. For example, if a gesture is beginning
         * with a focal point outside of a region where it makes
         * sense, onScaleBegin() may return false to ignore the
         * rest of the gesture.
         */
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        /**
         * Responds to the end of a scale gesture. Reported by existing
         * pointers going up.
         * <p/>
         * Once a scale has ended, {@link ScaleGestureDetector#getFocusX()}
         * and {@link ScaleGestureDetector#getFocusY()} will return focal point
         * of the pointers remaining on the screen.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         */
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mCameraSource.doZoom(detector.getScaleFactor());
        }
    }

    public void playBeep() {
        MediaPlayer m = new MediaPlayer();
        try {
            if (m.isPlaying()) {
                m.stop();
                m.release();
                m = new MediaPlayer();
            }

            AssetFileDescriptor descriptor = getActivity().getAssets().openFd(beepSoundFile != null ? beepSoundFile : "beep.mp3");
            m.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            descriptor.close();

            m.prepare();
            m.setVolume(1f, 1f);
            m.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showLoading() {
        loader.setVisibility(View.VISIBLE);
        loader.show();
        // or avi.smoothToShow();
    }

    void hideLoading() {
        loader.hide();
//        loader.setVisibility(View.GONE);
        // or avi.smoothToHide();
    }

    public interface BarcodeReaderListener {
        void onScanned(Barcode barcode);

        void onScannedMultiple(List<Barcode> barcodes);

        void onBitmapScanned(SparseArray<Barcode> sparseArray);

        void onScanError(String errorMessage);

        void onCameraPermissionDenied();
    }
}
