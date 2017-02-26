/*
 * Copyright 2017 TomeOkin
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.decay.zxing.sample;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.decay.zxing.BarcodeCallback;
import com.decay.zxing.BarcodeResult;
import com.decay.zxing.CaptureManager;
import com.decay.zxing.DecoratedBarcodeView;
import com.google.zxing.ResultPoint;

import java.util.List;

public class ZxingActivity extends AppCompatActivity implements DecoratedBarcodeView.TorchListener {
    DecoratedBarcodeView barcodeScannerView;
    CaptureManager capture;

    private Handler handler;
    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(final BarcodeResult result) {
            //barcodeScannerView.pause();

            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ZxingActivity.this, "扫描结果：" + result.getText(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {}
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zxing);

        handler = new Handler(Looper.getMainLooper());
        barcodeScannerView = (DecoratedBarcodeView) findViewById(R.id.zxing_scanner);
        barcodeScannerView.setTorchListener(this);

        // if the device does not have flashlight in its camera,
        // then remove the switch flashlight button...
        if (!hasFlash()) {
            //switchFlashlightButton.setVisibility(View.GONE);
        }

        capture = new CaptureManager(this, barcodeScannerView);
        capture.initializeFromIntent(getIntent(), savedInstanceState);
        capture.decodeSingle(callback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        capture.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        capture.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        capture.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        capture.onSaveInstanceState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
        @NonNull int[] grantResults) {
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Check if the device's camera has a Flashlight.
     *
     * @return true if there is Flashlight, otherwise false.
     */
    private boolean hasFlash() {
        return getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    @Override
    public void onTorchOn() {

    }

    @Override
    public void onTorchOff() {

    }
}
