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
import android.preference.PreferenceManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import static com.cyanogenmod.settings.device.IrGestureManager.*;

public class IrGestureSensor implements ActionableSensor, SensorEventListener {
    private static final String TAG = "CMActions-IRGestureSensor";

    private static final int IR_GESTURES_FOR_SCREEN_OFF = (1 << IR_GESTURE_APPROACH);
    private static final String GESTURE_IR_KEY = "gesture_ir";

    private SensorHelper mSensorHelper;
    private SensorAction mSensorAction;
    private IrGestureVote mIrGestureVote;
    private Sensor mSensor;

    private Context mContext;

    private boolean mGestureIrEnabled = true;

    public IrGestureSensor(Context context, SensorHelper sensorHelper, SensorAction action,
                                IrGestureManager irGestureManager) {
        mContext = context;
        mSensorHelper = sensorHelper;
        mSensorAction = action;
        mIrGestureVote = new IrGestureVote(irGestureManager);

        mSensor = sensorHelper.getIrGestureSensor();
        mIrGestureVote.voteForState(false, 0);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        loadPreferences(sharedPrefs);
        sharedPrefs.registerOnSharedPreferenceChangeListener(mPrefListener);
    }

    @Override
    public void setScreenOn() {
        Log.d(TAG, "Disabling");
        mSensorHelper.unregisterListener(this);
        mIrGestureVote.voteForState(false, 0);
    }

    @Override
    public void setScreenOff() {
        if (mGestureIrEnabled) {
            Log.d(TAG, "Enabling");
            mSensorHelper.registerListener(mSensor, this);
            mIrGestureVote.voteForState(true, IR_GESTURES_FOR_SCREEN_OFF);
        } else {
            Log.d(TAG, "Disabling");
            mSensorHelper.unregisterListener(this);
            mIrGestureVote.voteForState(false, 0);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int gesture = (int) event.values[1];

        if ((gesture == IR_GESTURE_APPROACH) && mGestureIrEnabled) {
            Log.d(TAG, "event: [" + event.values.length + "]: " + event.values[0] + ", " +
                event.values[1] + ", " + event.values[2]);
            mSensorAction.action();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor mSensor, int accuracy) {
    }

    private void loadPreferences(SharedPreferences sharedPreferences) {
        mGestureIrEnabled = sharedPreferences.getBoolean(GESTURE_IR_KEY, true);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (GESTURE_IR_KEY.equals(key)) {
                mGestureIrEnabled = sharedPreferences.getBoolean(GESTURE_IR_KEY, true);
            } 
        }
    };
}
