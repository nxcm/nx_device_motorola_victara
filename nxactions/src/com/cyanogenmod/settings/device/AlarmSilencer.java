/*
 * Copyright (c) 2015 Nexus Experience Project
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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.preference.PreferenceManager;
import android.util.Log;

import static com.cyanogenmod.settings.device.IrGestureManager.*;

public class AlarmSilencer extends Activity implements SensorEventListener {
    private static final String TAG = "CMActions-AlarmSilencer";

    private static final int IR_GESTURES_FOR_ALARM = (1 << IR_GESTURE_SWIPE);
    private static final String ALARM_SILENCE_KEY = "gesture_ir_silence";

    public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";
    public static final String ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE";
    public static final String ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS";
    public static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";

    private SensorHelper mSensorHelper;
    private Sensor mSensor;
    private IrGestureVote mIrGestureVote;

    private Context mContext;

    private boolean mAlarmSilenceEnabled = true;

    public AlarmSilencer(Context context, SensorHelper sensorHelper, IrGestureManager irGestureManager) {
        mContext = context;
        mSensorHelper = sensorHelper;
        mSensor = sensorHelper.getIrGestureSensor();
        mIrGestureVote = new IrGestureVote(irGestureManager);
        mIrGestureVote.voteForSensors(0);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        loadPreferences(sharedPrefs);
        sharedPrefs.registerOnSharedPreferenceChangeListener(mPrefListener);

        IntentFilter AlarmStateFilter = new IntentFilter(ALARM_ALERT_ACTION);
        AlarmStateFilter.addAction(ALARM_DISMISS_ACTION);
        AlarmStateFilter.addAction(ALARM_SNOOZE_ACTION);
        AlarmStateFilter.addAction(ALARM_DONE_ACTION);
        mContext.registerReceiver(mAlarmStateReceiver, AlarmStateFilter);
    }

    public void onAlarmStateOn() {
        if (mAlarmSilenceEnabled) {
            Log.d(TAG, "Alarm started");
            mSensorHelper.registerListener(mSensor, this);
            mIrGestureVote.voteForSensors(IR_GESTURES_FOR_ALARM);
        } else {
            Log.d(TAG, "Alarm stopped");
            mSensorHelper.unregisterListener(this);
            mIrGestureVote.voteForSensors(0);
        }
    }

    public void onAlarmStateOff() {
        Log.d(TAG, "Alarm stopped");
        mSensorHelper.unregisterListener(this);
        mIrGestureVote.voteForSensors(0);
    }

    @Override
    public synchronized void onSensorChanged(SensorEvent event) {
        int gesture = (int) event.values[1];

        if (gesture == IR_GESTURE_SWIPE && mAlarmSilenceEnabled) {
            Log.d(TAG, "Sending alarm.snooze intent");
            mContext.sendBroadcast(new Intent(ALARM_SNOOZE_ACTION));
            Log.d(TAG, "Alarm stopped");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor mSensor, int accuracy) {
    }

    private BroadcastReceiver mAlarmStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ALARM_DISMISS_ACTION)) {
                onAlarmStateOff();
            } else if (action.equals(ALARM_ALERT_ACTION)) {
                onAlarmStateOn();
            } else {
                onAlarmStateOff();
            }
        }
    };

    private void loadPreferences(SharedPreferences sharedPreferences) {
        mAlarmSilenceEnabled = sharedPreferences.getBoolean(ALARM_SILENCE_KEY, true);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (ALARM_SILENCE_KEY.equals(key)) {
                mAlarmSilenceEnabled = sharedPreferences.getBoolean(ALARM_SILENCE_KEY, true);
            } 
        }
    };
}
