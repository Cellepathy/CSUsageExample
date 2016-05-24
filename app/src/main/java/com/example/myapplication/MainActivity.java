/*******************************************************************************
 * Copyright (c) Cellepathy Ltd.
 *
 * http://www.cellepathy.com
 *
 * All rights reserved.
 ******************************************************************************/
package com.example.myapplication;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.cellepathy.cellematicsservice.common.ConnectionResult;
import com.cellepathy.cellematicsservice.tripdetection.TripClass;
import com.example.myapplication.TripDetectionService.TripDetectionServiceBinder;

import java.util.ArrayList;

/**
 * @author Daniel Waslicki
 */
public class MainActivity extends AppCompatActivity {

    private Button connectButton;
    private Button startButton;
    private Button wakeButton;
    private Button wakeServiceButton;
    private TextView connectionStatus;
    private TextView tripClassStatus;

    private TripDetectionServiceBinder tripDetectionService;

    private boolean isResumed;

    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            tripDetectionService = (TripDetectionServiceBinder) iBinder;

            if (isResumed) {
                initializeViews();
            }

            tripDetectionService.setListener(tripDetectionServiceListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            tripDetectionService = null;
        }

    };

    private final TripDetectionService.TripDetectionServiceListener tripDetectionServiceListener =
            new TripDetectionService.TripDetectionServiceListener() {

                @Override
                public boolean isVisible() {
                    return isResumed;
                }

                @Override
                public void onConnected() {

                    connectButton.setText(getString(R.string.disconnect_app));

                    connectionStatus.setText(getString(R.string.service_connected_and_setup));
                    startButton.setEnabled(true);
                    wakeButton.setEnabled(true);
                    wakeServiceButton.setEnabled(true);

                    if (tripDetectionService.isWakeUpWithBroadcastRegistered()) {
                        wakeButton.setText(getString(R.string.cancel_wake_me_up));
                    }

                    if (tripDetectionService.isWakeUpWithServiceRegistered()) {
                        wakeServiceButton.setText(getString(R.string.cancel_wake_up_my_service));
                    }

                }

                @Override
                public void onConnectionFailed(ConnectionResult reason) {

                    disconnected();

                    String message;

                    switch (reason.getErrorCode()) {
                        case ConnectionResult.NO_NETWORK_CONNECTION:
                            message = "There is no Internet connection. "
                                    + "Please connect your device to the Internet.";
                            break;
                        case ConnectionResult.LICENSE_CHECK_FAILED:
                            message = "License is not valid. Please contact us at www.cellepathy.com";
                            break;
                        case ConnectionResult.NETWORK_ERROR:
                            message = "Your device is experiencing some Internet connection issues."
                                    + " Retrying should resolve the problem.";
                            break;
                        case ConnectionResult.INITIALIZATION_ERROR:
                            message = "Cellematics Service is initializing and not available yet."
                                    + " Retrying should resolve the problem.";
                            break;
                        case ConnectionResult.SERVICE_MISSING:
                            message = "The Cellematics Service is not installed on your device.";
                            break;
                        case ConnectionResult.SERVICE_STOPPED:
                            message = "The Cellematics Service stopped responding."
                                    + " Reconnection should resolve the problem.";
                            break;
                        case ConnectionResult.LICENSE_KEY_ALREADY_ACTIVE:
                            message = "Used license key is already active" +
                                    " and there is an active connection associated with it. Please make" +
                                    " sure that all your connections are properly closed.";
                            break;
                        case ConnectionResult.UNKNOWN_ERROR:
                        default:
                            message = "Unknown error code.";
                            break;
                    }

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Cellematics Service's setup failed!")
                            .setMessage(message)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }

                @Override
                public void onMissingPermissions(final ArrayList<String> issues) {

                    StringBuilder builder = new StringBuilder();

                    for(String issue : issues) {
                        builder.append(issue);
                        builder.append("\n");
                    }

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Ups! Something is wrong ...")
                            .setMessage("Below issues doesn't allow us to run correctly:\n\n" + builder.toString())
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }

                @Override
                public void onTripClassChanged(final TripClass tripClass) {
                    String tripClassName;
                    switch (tripClass.getTripClass()) {
                        case TripClass.UNKNOWN:
                            tripClassName = "UNKNOWN";
                            break;
                        case TripClass.STATIONARY:
                            tripClassName = "STATIONARY";
                            break;
                        case TripClass.IN_TRANSIT:
                            tripClassName = "IN TRANSIT";
                            break;
                        default:
                            tripClassName = "Unknown class name";
                            break;
                    }
                    tripClassStatus.setText(tripClassName);
                }

            };

    private void disconnected() {
        connectButton.setText(getString(R.string.connect_app));
        startButton.setEnabled(false);
        wakeButton.setEnabled(false);
        wakeServiceButton.setEnabled(false);
        startButton.setText(getString(R.string.start_listening));
        connectionStatus.setText(getString(R.string.disconnected));

        resetFields();
    }

    private void initializeViews() {

        connectButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                if (!tripDetectionService.isConnected()) {

                    tripDetectionService.connect();

                } else {

                    tripDetectionService.disconnect();

                    disconnected();

                }
            }

        });

        startButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                if (!tripDetectionService.isListening()) {

                    tripDetectionService.startListening();
                    startButton.setText(getString(R.string.stop_listening));

                } else {

                    resetFields();
                    tripDetectionService.stopListening();
                    startButton.setText(getString(R.string.start_listening));

                }
            }

        });

        wakeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (tripDetectionService.isWakeUpWithBroadcastRegistered()) {
                    tripDetectionService.cancelWakeUpWithBroadcast();
                    wakeButton.setText(getString(R.string.wake_me_up));
                } else {
                    tripDetectionService.wakeUpOnInTransitEvent();
                    wakeButton.setText(getString(R.string.cancel_wake_me_up));
                }
            }
        });

        wakeServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (tripDetectionService.isWakeUpWithServiceRegistered()) {
                    tripDetectionService.cancelWakeUpWithService();
                    wakeServiceButton.setText(getString(R.string.wake_my_service));
                } else {
                    tripDetectionService.wakeUpServiceOnInTransitEvent();
                    wakeServiceButton.setText(getString(R.string.cancel_wake_up_my_service));
                }
            }
        });

        connectButton.setEnabled(true);

        if (tripDetectionService.isConnected()) {
            startButton.setEnabled(true);
            connectButton.setText(getString(R.string.disconnect_app));

            if (tripDetectionService.isListening()) {
                startButton.setText(getString(R.string.stop_listening));
            }

            if (tripDetectionService.isWakeUpWithBroadcastRegistered()) {
                wakeButton.setText(getString(R.string.cancel_wake_me_up));
            }

            if (tripDetectionService.isWakeUpWithServiceRegistered()) {
                wakeServiceButton.setText(getString(R.string.cancel_wake_up_my_service));
            }
        } else {
            startButton.setEnabled(false);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        connectButton = (Button) findViewById(R.id.connect_button);
        startButton = (Button) findViewById(R.id.start_button);
        wakeButton = (Button) findViewById(R.id.wake_button);
        wakeServiceButton = (Button) findViewById(R.id.wake_my_service_button);
        connectionStatus = (TextView) findViewById(R.id.connection_status);
        tripClassStatus = (TextView) findViewById(R.id.trip_class);

        bindService(
                new Intent(MainActivity.this, TripDetectionService.class),
                serviceConnection,
                BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {

        if (tripDetectionService != null) {
            tripDetectionService.setListener(null);
            unbindService(serviceConnection);
            tripDetectionService = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onPause() {

        isResumed = false;

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        isResumed = true;

        if (tripDetectionService != null) {
            initializeViews();
        }
    }

    private void resetFields() {
        tripClassStatus.setText("");
    }

}
