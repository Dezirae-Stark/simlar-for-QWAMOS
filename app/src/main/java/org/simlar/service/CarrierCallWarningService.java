/*
 * Copyright (C) 2025 QWAMOS Project
 *
 * This file is part of Simlar for QWAMOS (https://github.com/Dezirae-Stark/simlar-for-QWAMOS).
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * Contributors:
 *   - QWAMOS Secure Voice Team
 */

package org.simlar.service;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import org.simlar.BuildConfig;
import org.simlar.helper.PreferencesHelper;
import org.simlar.logging.Lg;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * QWAMOS Carrier Call Warning Service
 *
 * Monitors incoming carrier (cellular) calls and alerts the user that these calls
 * are NOT encrypted and do not meet QWAMOS security standards. Provides options to:
 * 1. Accept the insecure call anyway (with explicit warning)
 * 2. Reject the call and suggest using QWAMOS Secure Voice instead
 *
 * This service addresses the threat model where adversaries may attempt to force
 * users onto insecure carrier networks instead of using PQ-encrypted VoIP.
 *
 * Security Considerations:
 * - Carrier calls are vulnerable to IMSI catchers, SS7 attacks, lawful intercept
 * - No end-to-end encryption available on carrier networks
 * - Metadata (caller ID, duration, location) exposed to carriers and governments
 * - QWAMOS policy recommends rejecting all carrier calls for high-security users
 */
public final class CarrierCallWarningService extends Service {
    private static final String TAG = "CarrierCallWarning";

    // Service control
    private TelephonyManager mTelephonyManager;
    private Object mPhoneStateCallback; // PhoneStateListener (API < 31) or TelephonyCallback (API >= 31)
    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    // Warning state
    private String mIncomingNumber;
    private boolean mWarningShown = false;

    public static void start(final Context context) {
        if (!BuildConfig.QWAMOS_PQ_ONLY) {
            Lg.i(TAG, "Carrier call warning disabled - not QWAMOS build");
            return;
        }

        if (!PreferencesHelper.getCarrierCallWarningEnabled(context)) {
            Lg.i(TAG, "Carrier call warning disabled by user preference");
            return;
        }

        final Intent intent = new Intent(context, CarrierCallWarningService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        Lg.i(TAG, "Carrier call warning service started");
    }

    public static void stop(final Context context) {
        final Intent intent = new Intent(context, CarrierCallWarningService.class);
        context.stopService(intent);
        Lg.i(TAG, "Carrier call warning service stopped");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (mTelephonyManager == null) {
            Lg.e(TAG, "Failed to get TelephonyManager - cannot monitor calls");
            stopSelf();
            return;
        }

        // Check permissions
        if (!hasRequiredPermissions()) {
            Lg.e(TAG, "Missing required permissions for call monitoring");
            stopSelf();
            return;
        }

        // Register phone state listener
        registerPhoneStateListener();

        Lg.i(TAG, "CarrierCallWarningService created and listening");
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        return START_STICKY; // Restart if killed by system
    }

    @Override
    public void onDestroy() {
        unregisterPhoneStateListener();
        Lg.i(TAG, "CarrierCallWarningService destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) {
        return null; // Not a bound service
    }

    private boolean hasRequiredPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void registerPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerModernPhoneStateCallback();
        } else {
            registerLegacyPhoneStateListener();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void registerModernPhoneStateCallback() {
        final TelephonyCallback.CallStateListener callback = new TelephonyCallback.CallStateListener() {
            @Override
            public void onCallStateChanged(final int state) {
                handleCallStateChanged(state);
            }
        };

        mPhoneStateCallback = callback;

        try {
            mTelephonyManager.registerTelephonyCallback(mExecutor, (TelephonyCallback) mPhoneStateCallback);
            Lg.i(TAG, "Registered modern TelephonyCallback (API 31+)");
        } catch (final SecurityException e) {
            Lg.ex(e, "Failed to register TelephonyCallback - permission denied");
        }
    }

    @SuppressWarnings("deprecation")
    private void registerLegacyPhoneStateListener() {
        final PhoneStateListener listener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(final int state, final String phoneNumber) {
                mIncomingNumber = phoneNumber;
                handleCallStateChanged(state);
            }
        };

        mPhoneStateCallback = listener;

        try {
            mTelephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
            Lg.i(TAG, "Registered legacy PhoneStateListener (API < 31)");
        } catch (final SecurityException e) {
            Lg.ex(e, "Failed to register PhoneStateListener - permission denied");
        }
    }

    private void unregisterPhoneStateListener() {
        if (mPhoneStateCallback == null) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mTelephonyManager.unregisterTelephonyCallback((TelephonyCallback) mPhoneStateCallback);
            } else {
                @SuppressWarnings("deprecation")
                final PhoneStateListener listener = (PhoneStateListener) mPhoneStateCallback;
                mTelephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE);
            }
            Lg.i(TAG, "Unregistered phone state listener");
        } catch (final Exception e) {
            Lg.ex(e, "Error unregistering phone state listener");
        }

        mPhoneStateCallback = null;
    }

    private void handleCallStateChanged(final int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                onIncomingCarrierCall();
                break;

            case TelephonyManager.CALL_STATE_IDLE:
                onCallEnded();
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK:
                // Call answered or outgoing call started
                Lg.i(TAG, "Call answered or outgoing call active");
                break;

            default:
                Lg.w(TAG, "Unknown call state: ", state);
                break;
        }
    }

    /**
     * Called when an incoming carrier call is detected.
     * Shows security warning to user if enabled.
     */
    private void onIncomingCarrierCall() {
        if (mWarningShown) {
            Lg.i(TAG, "Warning already shown for this call");
            return;
        }

        final String callerNumber = getIncomingNumber();
        Lg.w(TAG, "SECURITY ALERT: Incoming INSECURE carrier call from: ", maskPhoneNumber(callerNumber));

        // Check user preference for automatic action
        final String action = PreferencesHelper.getCarrierCallAction(this);

        switch (action) {
            case PreferencesHelper.CARRIER_CALL_ACTION_WARN:
                showWarningDialog(callerNumber);
                break;

            case PreferencesHelper.CARRIER_CALL_ACTION_AUTO_REJECT:
                Lg.w(TAG, "Auto-rejecting carrier call per QWAMOS security policy");
                rejectCall();
                showRejectionNotification(callerNumber);
                break;

            case PreferencesHelper.CARRIER_CALL_ACTION_ALLOW:
                Lg.w(TAG, "Allowing carrier call per user preference (WARNING: INSECURE)");
                // Do nothing, let call ring normally
                break;

            default:
                // Default to showing warning
                showWarningDialog(callerNumber);
                break;
        }

        mWarningShown = true;
    }

    private void onCallEnded() {
        Lg.i(TAG, "Call ended, resetting warning state");
        mWarningShown = false;
        mIncomingNumber = null;
    }

    private String getIncomingNumber() {
        if (mIncomingNumber != null) {
            return mIncomingNumber;
        }
        return "Unknown";
    }

    private String maskPhoneNumber(final String number) {
        if (number == null || number.length() < 4) {
            return "****";
        }
        // Show last 4 digits only for logging
        return "****" + number.substring(number.length() - 4);
    }

    private void showWarningDialog(final String callerNumber) {
        // Launch warning dialog activity
        final Intent intent = new Intent(this, CarrierCallWarningActivity.class);
        intent.putExtra("caller_number", callerNumber);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);

        Lg.w(TAG, "Launched carrier call warning dialog");
    }

    private void rejectCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            rejectCallModern();
        } else {
            Lg.w(TAG, "Call rejection requires API 28+ or device-specific implementation");
            // On older Android versions, we can't programmatically reject calls
            // Show notification instead
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void rejectCallModern() {
        try {
            final TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager == null) {
                Lg.e(TAG, "TelecomManager not available");
                return;
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
                    == PackageManager.PERMISSION_GRANTED) {

                final boolean rejected = telecomManager.endCall();
                if (rejected) {
                    Lg.i(TAG, "Successfully rejected insecure carrier call");
                } else {
                    Lg.w(TAG, "Failed to reject call - no active call found");
                }
            } else {
                Lg.e(TAG, "Missing ANSWER_PHONE_CALLS permission to reject call");
            }
        } catch (final SecurityException e) {
            Lg.ex(e, "Security exception when trying to reject call");
        } catch (final Exception e) {
            Lg.ex(e, "Error rejecting call");
        }
    }

    private void showRejectionNotification(final String callerNumber) {
        // Use SimlarNotificationChannel to show notification
        final String message = String.format(
            "QWAMOS Security Policy: Rejected insecure carrier call from %s. " +
            "Use QWAMOS Secure Voice for encrypted calls.",
            maskPhoneNumber(callerNumber)
        );

        // Note: Full implementation would use NotificationManager
        Lg.w(TAG, message);
    }
}
