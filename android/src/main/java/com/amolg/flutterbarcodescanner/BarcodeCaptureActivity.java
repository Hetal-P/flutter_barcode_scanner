package com.amolg.flutterbarcodescanner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import com.amolg.flutterbarcodescanner.camera.CameraSource;
import com.amolg.flutterbarcodescanner.camera.CameraSourcePreview;
import com.amolg.flutterbarcodescanner.camera.GraphicOverlay;

import java.io.IOException;

public final class BarcodeCaptureActivity extends AppCompatActivity
        implements BarcodeGraphicTracker.BarcodeUpdateListener, View.OnClickListener {

    private static final int RC_HANDLE_GMS = 9001;
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    public static final String BarcodeObject = "Barcode";

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;

    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;

    private ImageView imgViewBarcodeCaptureUseFlash;
    private ImageView imgViewSwitchCamera;

    public static int SCAN_MODE = SCAN_MODE_ENUM.QR.ordinal();

    public enum SCAN_MODE_ENUM {
        QR,
        BARCODE,
        DEFAULT
    }

    enum USE_FLASH {
        OFF,
        ON
    }

    private int flashStatus = USE_FLASH.OFF.ordinal();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.barcode_capture);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (view, insets) -> {
                    Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    view.setPadding(systemInsets.left, systemInsets.top,
                            systemInsets.right, systemInsets.bottom);
                    return insets;
                });

        String buttonText;
        try {
            buttonText = getIntent().getStringExtra("cancelButtonText");
            if (buttonText == null) buttonText = "Cancel";
        } catch (Exception e) {
            buttonText = "Cancel";
        }

        Button cancelBtn = findViewById(R.id.btnBarcodeCaptureCancel);
        cancelBtn.setText(buttonText);
        cancelBtn.setOnClickListener(this);

        imgViewBarcodeCaptureUseFlash = findViewById(R.id.imgViewBarcodeCaptureUseFlash);
        imgViewBarcodeCaptureUseFlash.setOnClickListener(this);

        imgViewSwitchCamera = findViewById(R.id.imgViewSwitchCamera);
        imgViewSwitchCamera.setOnClickListener(this);

        // safe access from plugin config
        imgViewBarcodeCaptureUseFlash.setVisibility(
                FlutterBarcodeScannerPlugin.isShowFlashIcon ? View.VISIBLE : View.GONE
        );

        mPreview = findViewById(R.id.preview);
        mGraphicOverlay = findViewById(R.id.graphicOverlay);

        boolean autoFocus = true;
        boolean useFlash = false;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(autoFocus, useFlash, CameraSource.CAMERA_FACING_BACK);
        } else {
            requestCameraPermission();
        }

        gestureDetector = new GestureDetector(this, new CaptureGestureListener());
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                RC_HANDLE_CAMERA_PERM);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return scaleGestureDetector.onTouchEvent(e)
                || gestureDetector.onTouchEvent(e)
                || super.onTouchEvent(e);
    }

    private void createCameraSource(boolean autoFocus, boolean useFlash, int cameraFacing) {

        BarcodeDetector detector = new BarcodeDetector.Builder(getApplicationContext()).build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new BarcodeTrackerFactory(mGraphicOverlay, this)).build()
        );

        CameraSource.Builder builder = new CameraSource.Builder(getApplicationContext(), detector)
                .setFacing(cameraFacing)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(30.0f)
                .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            builder.setFocusMode(autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null);
        }

        if (mCameraSource != null) {
            mCameraSource.release();
        }

        mCameraSource = builder.build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPreview != null) mPreview.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPreview != null) mPreview.release();
    }

    private void startCameraSource() {
        int code = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(getApplicationContext());

        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg = GoogleApiAvailability.getInstance()
                    .getErrorDialog(this, code, RC_HANDLE_GMS);
            if (dlg != null) dlg.show();
        }

        try {
            if (mCameraSource != null) {
                mPreview.start(mCameraSource, mGraphicOverlay);
            }
        } catch (IOException e) {
            mCameraSource.release();
            mCameraSource = null;
        }
    }

    private boolean isContinuousMode() {
        return getIntent() != null &&
                getIntent().getBooleanExtra("isContinuousScan", false);
    }

    @Override
    public void onBarcodeDetected(Barcode barcode) {

        if (barcode == null) return;

        if (isContinuousMode()) {
            FlutterBarcodeScannerPlugin.sendContinuousScan(barcode.rawValue);
        } else {
            Intent data = new Intent();
            data.putExtra(BarcodeObject, barcode);
            setResult(CommonStatusCodes.SUCCESS, data);
            finish();
        }
    }

    @Override
    public void onClick(View v) {

        int id = v.getId();

        if (id == R.id.btnBarcodeCaptureCancel) {

            Intent data = new Intent();
            data.putExtra(BarcodeObject, "-1");
            setResult(CommonStatusCodes.SUCCESS, data);
            finish();

        } else if (id == R.id.imgViewBarcodeCaptureUseFlash) {

            flashStatus = (flashStatus == USE_FLASH.OFF.ordinal())
                    ? USE_FLASH.ON.ordinal()
                    : USE_FLASH.OFF.ordinal();

            turnOnOffFlashLight(flashStatus == USE_FLASH.ON.ordinal());

        } else if (id == R.id.imgViewSwitchCamera) {

            int currentFacing = mCameraSource.getCameraFacing();
            boolean autoFocus = true;
            boolean useFlash = flashStatus == USE_FLASH.ON.ordinal();

            createCameraSource(autoFocus, useFlash, getInverse(currentFacing));
            startCameraSource();
        }
    }

    private int getInverse(int facing) {
        return (facing == CameraSource.CAMERA_FACING_FRONT)
                ? CameraSource.CAMERA_FACING_BACK
                : CameraSource.CAMERA_FACING_FRONT;
    }

    private void turnOnOffFlashLight(boolean enable) {
        try {
            String mode = enable ?
                    Camera.Parameters.FLASH_MODE_TORCH :
                    Camera.Parameters.FLASH_MODE_OFF;

            mCameraSource.setFlashMode(mode);
        } catch (Exception e) {
            Toast.makeText(this, "Flash not available", Toast.LENGTH_SHORT).show();
        }
    }

    private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return onTouchEvent(e);
        }
    }

    private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {
        @Override public boolean onScale(ScaleGestureDetector detector) { return false; }
        @Override public boolean onScaleBegin(ScaleGestureDetector detector) { return true; }
        @Override public void onScaleEnd(ScaleGestureDetector detector) {
            mCameraSource.doZoom(detector.getScaleFactor());
        }
    }
}