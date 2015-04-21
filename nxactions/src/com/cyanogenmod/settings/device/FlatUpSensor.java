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

public class FlatUpSensor implements ScreenStateNotifier {
    private static final String TAG = "CMActions-FlatUpSensor";

    private static final String PICK_UP_KEY = "pick_up";

    private SensorHelper mSensorHelper;
    private SensorAction mSensorAction;

    private final Sensor mFlatUpSensor;
    private final Sensor mStowSensor;

    private boolean mIsStowed;
    private boolean mLastFlatUp;

    private Context mContext;

    private boolean mPickUpEnabled = true;

    public FlatUpSensor(Context context, SensorHelper sensorHelper, SensorAction action) {
        mContext = context;
        mSensorHelper = sensorHelper;
        mSensorAction = action;

        mFlatUpSensor = sensorHelper.getFlatUpSensor();
        mStowSensor = sensorHelper.getStowSensor();

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        loadPreferences(sharedPrefs);
        sharedPrefs.registerOnSharedPreferenceChangeListener(mPrefListener);
    }

    @Override
    public void screenTurnedOn() {
        Log.d(TAG, "Disabling");
            mSensorHelper.unregisterListener(mFlatUpListener);
            mSensorHelper.unregisterListener(mStowListener);
    }

    @Override
    public void screenTurnedOff() {
        if (mPickUpEnabled) {
            Log.d(TAG, "Enabling");
            mSensorHelper.registerListener(mFlatUpSensor, mFlatUpListener);
            mSensorHelper.registerListener(mStowSensor, mStowListener);
        } else {
            Log.d(TAG, "Disabling");
            mSensorHelper.unregisterListener(mFlatUpListener);
            mSensorHelper.unregisterListener(mStowListener);
        }
    }

    private SensorEventListener mFlatUpListener = new SensorEventListener() {
        @Override
        public synchronized void onSensorChanged(SensorEvent event) {
            boolean thisFlatUp = (event.values[0] != 0);

            Log.d(TAG, "event: " + thisFlatUp + " mLastFlatUp=" + mLastFlatUp + " mIsStowed=" +
                mIsStowed);

            if (mLastFlatUp && ! thisFlatUp && !mIsStowed && mPickUpEnabled) {
                mSensorAction.action();
            }
            mLastFlatUp = thisFlatUp;
        }

        @Override
        public void onAccuracyChanged(Sensor mSensor, int accuracy) {
        }
    };

    private SensorEventListener mStowListener = new SensorEventListener() {
        @Override
        public synchronized void onSensorChanged(SensorEvent event) {
            mIsStowed = (event.values[0] != 0);
        }

        @Override
        public void onAccuracyChanged(Sensor mSensor, int accuracy) {
        }
    };

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
