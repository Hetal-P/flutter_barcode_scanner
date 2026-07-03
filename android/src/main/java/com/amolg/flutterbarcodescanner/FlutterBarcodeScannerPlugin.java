package com.amolg.flutterbarcodescanner;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

public class FlutterBarcodeScannerPlugin implements
        FlutterPlugin,
        MethodChannel.MethodCallHandler,
        EventChannel.StreamHandler,
        PluginRegistry.ActivityResultListener,
        ActivityAware {

    private static final String CHANNEL = "flutter_barcode_scanner";
    private static final String EVENT_CHANNEL = "flutter_barcode_scanner_receiver";
    private static final int RC_BARCODE_CAPTURE = 9001;

    // =========================
    // Plugin state
    // =========================
    private MethodChannel methodChannel;
    private EventChannel eventChannel;

    private Activity activity;
    private ActivityPluginBinding activityBinding;

    private MethodChannel.Result pendingResult;
    private EventChannel.EventSink eventSink;

    // =========================
    // Config (replaces old static vars safely)
    // =========================
    public static String lineColor = "#DC143C";
    public static boolean isShowFlashIcon = false;
    public static boolean isContinuousScan = false;

    // =========================
    // Flutter Plugin lifecycle
    // =========================

    private static FlutterBarcodeScannerPlugin instance;

    public FlutterBarcodeScannerPlugin() {
        instance = this;
    }

    static EventChannel.EventSink barcodeStream;
    public static void sendContinuousScan(String value) {
    try {
        if (barcodeStream != null) {
            barcodeStream.success(value);
        }
    } catch (Exception e) {
        Log.e("FlutterBarcodeScanner", "sendContinuousScan error: " + e.getMessage());
    }
}
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {

        methodChannel = new MethodChannel(binding.getBinaryMessenger(), CHANNEL);
        methodChannel.setMethodCallHandler(this);

        eventChannel = new EventChannel(binding.getBinaryMessenger(), EVENT_CHANNEL);
        eventChannel.setStreamHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        methodChannel.setMethodCallHandler(null);
        methodChannel = null;

        eventChannel.setStreamHandler(null);
        eventChannel = null;
    }

    // =========================
    // Method calls from Dart
    // =========================
    @Override
    public void onMethodCall(@NonNull MethodCall call,
                             @NonNull MethodChannel.Result result) {

        if (call.method.equals("scanBarcode")) {

            pendingResult = result;

            Map<String, Object> args = call.arguments();

            if (args != null) {

                lineColor = args.get("lineColor") != null
                        ? args.get("lineColor").toString()
                        : "#DC143C";

                isShowFlashIcon = Boolean.TRUE.equals(args.get("isShowFlashIcon"));

                isContinuousScan = Boolean.TRUE.equals(args.get("isContinuousScan"));
            }

            startScanner(
                    args != null ? (String) args.get("cancelButtonText") : null
            );

        } else {
            result.notImplemented();
        }
    }

    // =========================
    // Start scanner activity
    // =========================
    private void startScanner(String cancelText) {

        if (activity == null) {
            pendingResult.error("NO_ACTIVITY", "Activity not attached", null);
            return;
        }

        Intent intent = new Intent(activity, BarcodeCaptureActivity.class);
        intent.putExtra("cancelButtonText", cancelText);

        if (isContinuousScan) {
            activity.startActivity(intent);
        } else {
            activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
        }
    }

    // =========================
    // Activity result
    // =========================
    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == RC_BARCODE_CAPTURE) {

            if (resultCode == CommonStatusCodes.SUCCESS && data != null) {

                Barcode barcode = data.getParcelableExtra(
                        BarcodeCaptureActivity.BarcodeObject
                );

                if (barcode != null) {
                    pendingResult.success(barcode.rawValue);
                } else {
                    pendingResult.success("-1");
                }

            } else {
                pendingResult.success("-1");
            }

            pendingResult = null;
            return true;
        }

        return false;
    }

    // =========================
    // Event channel (continuous scan)
    // =========================
    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        eventSink = events;
    }

    @Override
    public void onCancel(Object arguments) {
        eventSink = null;
    }

    // Called from BarcodeCaptureActivity
    public static void sendBarcodeResult(Barcode barcode) {
        // kept for backward compatibility
    }

    public void sendContinuousResult(String value) {
        if (eventSink != null) {
            eventSink.success(value);
        }
    }

    // =========================
    // Activity binding (V2)
    // =========================
    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        activityBinding = binding;
        binding.addActivityResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(
            @NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {

        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(this);
            activityBinding = null;
        }

        activity = null;
    }
}