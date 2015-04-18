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

public class FlatUpSensor implements ActionableSensor, SensorEventListener {
    private static final String TAG = "CMActions-FlatUpSensor";

    private static final String PICK_UP_KEY = "pick_up";

    private SensorHelper mSensorHelper;
    private State mState;
    private SensorAction mSensorAction;

    private Sensor mSensor;

    private Context mContext;

    private boolean mPickUpEnabled = true;

    public FlatUpSensor(Context context, SensorHelper sensorHelper, State state, SensorAction action) {
        mContext = context;
        mSensorHelper = sensorHelper;
        mState = state;
        mSensorAction = action;

        mSensor = sensorHelper.getFlatUpSensor();

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        loadPreferences(sharedPrefs);
        sharedPrefs.registerOnSharedPreferenceChangeListener(mPrefListener);
    }

    @Override
    public void setScreenOn() {
        Log.d(TAG, "Disabling");
        mSensorHelper.unregisterListener(this);
    }

    @Override
    public void setScreenOff() {
        if (mPickUpEnabled) {
            Log.d(TAG, "Enabling");
            mSensorHelper.registerListener(mSensor, this);
        } else {
            Log.d(TAG, "Disabling");
            mSensorHelper.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean thisFlatUp = (event.values[0] != 0);
        boolean lastFlatUp = mState.setIsFlatUp(thisFlatUp);
        boolean isStowed = mState.getIsStowed();

        Log.d(TAG, "event: " + thisFlatUp + " lastFlatUp=" + lastFlatUp + " isStowed=" + isStowed);

        // Only pulse when picked up:
        if (lastFlatUp && !thisFlatUp && !isStowed && mPickUpEnabled) {
            mSensorAction.action();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor mSensor, int accuracy) {
    }

    private void loadPreferences(SharedPreferences sharedPreferences) {
        mPickUpEnabled = sharedPreferences.getBoolean(PICK_UP_KEY, true);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (PICK_UP_KEY.equals(key)) {
                mPickUpEnabled = sharedPreferences.getBoolean(PICK_UP_KEY, true);
            } 
        }
    };
}
