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

import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.MediaStore;
import android.util.Log;

public class FlashlightActivationSensor implements ScreenStateNotifier, SensorEventListener {
    private static final String TAG = "CMActions-FlashlightSensor";

    private SensorHelper mSensorHelper;
    private SensorAction mSensorAction;

    private Sensor mChopChopSensor;

    private Context mContext;

    public FlashlightActivationSensor(SensorHelper sensorHelper, SensorAction sensorAction) {
        mSensorHelper = sensorHelper;
        mSensorAction = sensorAction;
        mChopChopSensor = sensorHelper.getChopChopSensor();

        Log.d(TAG, "Enabling");
        mSensorHelper.registerListener(mChopChopSensor, this);
    }

    @Override
    public void screenTurnedOn() {
    }

    @Override
    public void screenTurnedOff() {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d(TAG, "activate flashlight");
        mSensorAction.action();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
