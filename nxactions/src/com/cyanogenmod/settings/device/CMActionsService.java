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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import java.util.List;
import java.util.LinkedList;

public class CMActionsService extends IntentService implements ScreenStateNotifier {
    private static final String TAG = "CMActions";

    private State mState;
    private SensorHelper mSensorHelper;
    private ScreenReceiver mScreenReceiver;
    private IrGestureManager mIrGestureManager;

    private CameraActivationAction mCameraActivationAction;
    private FlashlightActivationAction mFlashlightActivationAction;
    private DozePulseAction mDozePulseAction;

    private List<ScreenStateNotifier> mScreenStateNotifiers = new LinkedList<ScreenStateNotifier>();

    private IrSilencer mIrSilencer;
    private AlarmSilencer mAlarmSilencer;

    private Context mContext;

    public CMActionsService(Context context) {
        super("CMActionService");
        mContext = context;

        Log.d(TAG, "Starting");

        mState = new State(context);
        mSensorHelper = new SensorHelper(context);
        mScreenReceiver = new ScreenReceiver(context, this);
        mIrGestureManager = new IrGestureManager();

        mCameraActivationAction = new CameraActivationAction(context);
        mFlashlightActivationAction = new FlashlightActivationAction(context);
        mDozePulseAction = new DozePulseAction(context, mState);

        mScreenStateNotifiers.add(new CameraActivationSensor(mSensorHelper, mCameraActivationAction));
        mScreenStateNotifiers.add(new FlashlightActivationSensor(mSensorHelper, mFlashlightActivationAction));
        mScreenStateNotifiers.add(new FlatUpSensor(context, mSensorHelper, mState, mDozePulseAction));
        mScreenStateNotifiers.add(new IrGestureSensor(context, mSensorHelper, mDozePulseAction, mIrGestureManager));
        mScreenStateNotifiers.add(new StowSensor(context, mSensorHelper, mState, mDozePulseAction));

        mIrSilencer = new IrSilencer(context, mSensorHelper, mIrGestureManager);
        mAlarmSilencer = new AlarmSilencer(context, mSensorHelper, mIrGestureManager);

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager.isInteractive()) {
            screenTurnedOn();
        } else {
            screenTurnedOff();
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
    }

    @Override
    public void screenTurnedOn() {
        mState.setScreenIsOn(true);
        for (ScreenStateNotifier screenStateNotifier : mScreenStateNotifiers) {
            screenStateNotifier.screenTurnedOn();
        }
    }

    @Override
    public void screenTurnedOff() {
        mState.setScreenIsOn(false);
        for (ScreenStateNotifier screenStateNotifier : mScreenStateNotifiers) {
            screenStateNotifier.screenTurnedOff();
        }
    }

    private boolean isDozeEnabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
            Settings.Secure.DOZE_ENABLED, 1) != 0;
    }
}
