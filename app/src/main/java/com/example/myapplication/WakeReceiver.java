/*******************************************************************************
 * Copyright (c) Cellepathy Ltd.
 *
 * http://www.cellepathy.com
 *
 * All rights reserved.
 ******************************************************************************/
package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * @author Daniel Waslicki
 */
public class WakeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent newIntent = new Intent(context, TripDetectionService.class);
        newIntent.setAction(TripDetectionService.START_AND_INITIALIZE);
        context.startService(newIntent);
    }

}
