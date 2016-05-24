/*******************************************************************************
 * Copyright (c) Cellepathy Ltd.
 *
 * http://www.cellepathy.com
 *
 * All rights reserved.
 ******************************************************************************/
package com.example.myapplication;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;

import com.cellepathy.cellematicsservice.CellematicsServiceClient;
import com.cellepathy.cellematicsservice.common.ConnectionResult;
import com.cellepathy.cellematicsservice.common.Malfunctions;
import com.cellepathy.cellematicsservice.tripdetection.TripClass;
import com.cellepathy.cellematicsservice.tripdetection.TripDetection;
import com.cellepathy.cellematicsservice.tripdetection.TripListener;

import java.util.ArrayList;

/**
 * @author Daniel Waslicki
 */
public class TripDetectionService extends Service {

    // TODO: insert your license key
    private static final String LICENSE_KEY = "ApplicationsKey";
    private static final String WAKE_UP_ACTION = "com.example.myapplication_WAKE_UP";

    private static final String[] MALFUNCTIONS = { "Turned off GPS", "Enabled mock locations", "Service version update required" };

    public static final String START_AND_INITIALIZE = "START_AND_INITIALIZE_ACTION";

    public interface TripDetectionServiceListener {

        boolean isVisible();

        void onConnected();

        void onConnectionFailed(ConnectionResult reason);

        void onMissingPermissions(ArrayList<String> issues);

        void onTripClassChanged(TripClass tripClass);

    }

    public static class TripDetectionServiceBinder extends Binder {

        private TripDetectionService tripDetectionService;
        private DeathRecipient deathRecipient;

        TripDetectionServiceBinder(TripDetectionService tripDetectionService) {
            this.tripDetectionService = tripDetectionService;
        }

        /**
         * Clears the reference to the outer class to minimize the leak.
         */
        private void detachFromService() {
            tripDetectionService = null;
            attachInterface(null, null);

            if (deathRecipient != null) {
                deathRecipient.binderDied();
            }
        }

        @Override
        public boolean isBinderAlive() {
            return tripDetectionService != null;
        }

        @Override
        public boolean pingBinder() {
            return isBinderAlive();
        }

        @Override
        public void linkToDeath(DeathRecipient recipient, int flags) {
            deathRecipient = recipient;
        }

        @Override
        public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
            if (!isBinderAlive()) {
                return false;
            }
            deathRecipient = null;
            return true;
        }

        public void cancelWakeUpWithBroadcast() {
            if (tripDetectionService != null) {
                tripDetectionService.cancelWakeUpWithBroadcast();
            }
        }

        public void cancelWakeUpWithService() {
            if (tripDetectionService != null) {
                tripDetectionService.cancelWakeUpWithService();
            }
        }

        public void connect() {
            if (tripDetectionService != null) {
                tripDetectionService.connect();
            }
        }

        public void disconnect() {
            if (tripDetectionService != null) {
                tripDetectionService.disconnect();
            }
        }

        public boolean isConnected() {
            return tripDetectionService != null && tripDetectionService.isConnected();
        }

        public boolean isListening() {
            return tripDetectionService != null && tripDetectionService.isListening();
        }

        public boolean isWakeUpWithServiceRegistered() {
            return tripDetectionService != null && tripDetectionService.isWakeUpWithServiceRegistered();
        }

        public boolean isWakeUpWithBroadcastRegistered() {
            return tripDetectionService != null && tripDetectionService.isWakeUpWithBroadcastRegistered();
        }

        public void setListener(TripDetectionServiceListener listener) {
            if (tripDetectionService != null) {
                tripDetectionService.setListener(listener);
            }
        }

        public void startListening() {
            if (tripDetectionService != null) {
                tripDetectionService.startListening();
            }
        }

        public void stopListening() {
            if (tripDetectionService != null) {
                tripDetectionService.stopListening();
            }
        }



        public void wakeUpOnInTransitEvent() {
            if (tripDetectionService != null) {
                tripDetectionService.wakeUpOnInTransitEvent();
            }
        }

        public void wakeUpServiceOnInTransitEvent() {
            if (tripDetectionService != null) {
                tripDetectionService.wakeUpServiceOnInTransitEvent();
            }
        }

    }

    private final CellematicsServiceClient.CellematicsServiceListener cellematicsServiceListener = new CellematicsServiceClient.CellematicsServiceListener() {

        @Override
        public void detectedMalfunctions(Malfunctions malfunctions) {
            ArrayList<String> issues = new ArrayList<>();

            if ((malfunctions.getMalfunctions() & Malfunctions.GPS_TURNED_OFF) != 0) {
                issues.add(MALFUNCTIONS[0]);
            }
            if ((malfunctions.getMalfunctions() & Malfunctions.ENABLED_MOCK_LOCATIONS) != 0) {
                issues.add(MALFUNCTIONS[1]);
            }
            if ((malfunctions.getMalfunctions() & Malfunctions.SERVICE_VERSION_UPDATE_REQUIRED) != 0) {
                issues.add(MALFUNCTIONS[2]);
            }

            notifyListenersOfMissingPermissions(issues);
        }

    };

    private final CellematicsServiceClient.ConnectionCallbacks connectionCallbacks = new CellematicsServiceClient.ConnectionCallbacks() {

        @Override
        public void onConnected() {
            isConnected = true;
            notifyListenersOfConnectionEstablished();

            if (wasWokenUp) {
                startListening();
                wakeUpServiceOnInTransitEvent();
                wasWokenUp = false;
            }
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            isConnected = false;
            isListening = false;
            tripClass = null;

            if ((listener != null && !listener.isVisible())
                    && (connectionResult.getErrorCode() == ConnectionResult.SERVICE_STOPPED
                    || connectionResult.getErrorCode() == ConnectionResult.INITIALIZATION_ERROR
                    || connectionResult.getErrorCode() == ConnectionResult.NO_NETWORK_CONNECTION
                    || connectionResult.getErrorCode() == ConnectionResult.NETWORK_ERROR)) {
                connect();
            } else {
                notifyListenersOfSetupFailure(connectionResult);
            }

        }

    };

    private TripDetectionServiceBinder binder;

    private TripClass tripClass;
    private boolean isConnected;
    private boolean isListening;
    private boolean wasWokenUp;

    private TripDetectionServiceListener listener;
    private final Object listenerLock = new Object();

    private CellematicsServiceClient cellematicsService;

    private TripListener tripListener;
    
    public TripDetectionService() {
    }

    private void cancelWakeUpWithBroadcast() {
        TripDetection.TripDetectionApi
                .cancelWakeUpWithBroadcastOnTripStart(
                        cellematicsService,
                        WAKE_UP_ACTION);
    }

    private void cancelWakeUpWithService() {
        TripDetection.TripDetectionApi
                .cancelWakeUpServiceOnTripStart(
                        cellematicsService,
                        getPackageName(),
                        TripDetectionService.class.getName(),
                        START_AND_INITIALIZE);
    }

    private void connect() {
        cellematicsService.connect();
    }

    private void disconnect() {
        isConnected = false;
        isListening = false;
        tripClass = null;

        cellematicsService.disconnect();
    }

    private boolean isConnected() {
        return isConnected;
    }

    private boolean isListening() {
        return isListening;
    }

    private boolean isWakeUpWithBroadcastRegistered() {
        return TripDetection.TripDetectionApi
                .isWakeUpWithBroadcastRegistered(cellematicsService, WAKE_UP_ACTION);
    }

    private boolean isWakeUpWithServiceRegistered() {
        return TripDetection.TripDetectionApi
                .isWakeUpServiceRegistered(
                        cellematicsService,
                        getPackageName(),
                        TripDetectionService.class.getName(),
                        START_AND_INITIALIZE);
    }

    private void notifyListenersOfConnectionEstablished() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                synchronized (listenerLock) {
                    if (listener != null) {
                        listener.onConnected();
                    }
                }
            }
        });
    }

    private void notifyListenersOfMissingPermissions(final ArrayList<String> issues) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                synchronized (listenerLock) {
                    if (listener != null) {
                        listener.onMissingPermissions(issues);
                    }
                }
            }
        });
    }

    private void notifyListenersOfSetupFailure(final ConnectionResult reason) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                synchronized (listenerLock) {
                    if (listener != null) {
                        listener.onConnectionFailed(reason);
                    }
                }
            }
        });
    }

    private void notifyListenersOfTripClassChanged(final TripClass tripClass) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                synchronized (listenerLock) {
                    if (listener != null) {
                        listener.onTripClassChanged(tripClass);
                    }
                }
            }
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        this.binder = new TripDetectionServiceBinder(this);

        this.cellematicsService = new CellematicsServiceClient.Builder(
                getApplicationContext())
                .addApi(TripDetection.API)
                .setLicenseKey(LICENSE_KEY)
                .addConnectionCallbacks(connectionCallbacks)
                .addEventListener(cellematicsServiceListener)
                .build();
    }

    @Override
    public void onDestroy() {

        binder.detachFromService();
        binder = null;

        cellematicsService.teardown();

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null
                && intent.getAction() != null) {

            if (intent.getAction().equals(START_AND_INITIALIZE)) {
                if (!isConnected) {
                    wasWokenUp = true;
                    connect();
                } else {
                    wakeUpServiceOnInTransitEvent();
                }
            }
        }

        return START_STICKY;
    }

    private void setListener(final TripDetectionServiceListener listener) {
        synchronized (listenerLock) {
            this.listener = listener;

            if (isConnected) {
                listener.onConnected();

                if (tripClass != null) {
                    listener.onTripClassChanged(tripClass);
                }

            }
        }
    }

    private void startListening() {
        TripDetection.TripDetectionApi.requestTripUpdates(
                cellematicsService,
                tripListener = new TripListener() {
                    @Override
                    public void tripClassChanged(long l, TripClass tripClass) {
                        TripDetectionService.this.tripClass = tripClass;
                        notifyListenersOfTripClassChanged(tripClass);
                    }
                }
        );
        isListening = true;
    }

    private void stopListening() {
        isListening = false;
        TripDetection.TripDetectionApi.removeTripUpdates(
                cellematicsService,
                tripListener);
    }

    private void wakeUpOnInTransitEvent() {
        TripDetection.TripDetectionApi.wakeUpWithBroadcastOnTripStart(cellematicsService, WAKE_UP_ACTION);
    }

    private void wakeUpServiceOnInTransitEvent() {
        TripDetection.TripDetectionApi.wakeUpServiceOnTripStart(
                cellematicsService,
                getPackageName(),
                TripDetectionService.class.getName(),
                START_AND_INITIALIZE);
    }

}
