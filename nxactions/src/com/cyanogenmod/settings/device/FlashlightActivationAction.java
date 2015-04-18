/*
 * Copyright (c) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.settings.device;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.TorchManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;

public class FlashlightActivationAction implements SensorAction {
    private static final String TAG = "CMActions";

    private static final String GESTURE_FLASHLIGHT_KEY = "gesture_flashlight";

    private final TorchManager mTorchManager;
    private final Vibrator mVibrator;

    private Context mContext;

    private boolean mGestureFlashlightEnabled = true;

    public FlashlightActivationAction(Context context) {
        mContext = context;
        mTorchManager = (TorchManager) context.getSystemService(Context.TORCH_SERVICE);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        loadPreferences(sharedPrefs);
        sharedPrefs.registerOnSharedPreferenceChangeListener(mPrefListener);
    }

    @Override
    public void action() {
        if (mGestureFlashlightEnabled) {
            mVibrator.vibrate(250);
            mTorchManager.toggleTorch();
        }
    }

    private void loadPreferences(SharedPreferences sharedPreferences) {
        mGestureFlashlightEnabled = sharedPreferences.getBoolean(GESTURE_FLASHLIGHT_KEY, true);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (GESTURE_FLASHLIGHT_KEY.equals(key)) {
                mGestureFlashlightEnabled = sharedPreferences.getBoolean(GESTURE_FLASHLIGHT_KEY, true);
            } 
        }
    };
}
