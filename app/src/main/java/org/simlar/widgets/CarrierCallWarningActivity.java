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
 */

package org.simlar.widgets;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import org.simlar.R;
import org.simlar.helper.PreferencesHelper;
import org.simlar.logging.Lg;

/**
 * QWAMOS Carrier Call Warning Dialog
 *
 * Displayed when an incoming carrier call is detected, warning the user that:
 * 1. Carrier calls are NOT encrypted
 * 2. They are vulnerable to interception and surveillance
 * 3. They violate QWAMOS security policy
 *
 * Provides options:
 * - Accept insecure call anyway (explicit acknowledgment of risk)
 * - Reject and use QWAMOS Secure Voice instead (recommended)
 * - Remember choice (don't ask again)
 */
public final class CarrierCallWarningActivity extends Activity {
    private static final String TAG = "CarrierCallWarningActivity";

    private String mCallerNumber;
    private TextView mTextViewMessage;
    private TextView mTextViewCallerInfo;
    private CheckBox mCheckBoxRemember;
    private Button mButtonAcceptInsecure;
    private Button mButtonRejectSecure;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show activity over lockscreen and wake screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        setContentView(R.layout.activity_carrier_call_warning);

        // Get caller number from intent
        mCallerNumber = getIntent().getStringExtra("caller_number");
        if (mCallerNumber == null) {
            mCallerNumber = "Unknown";
        }

        initializeViews();
        displayWarning();

        Lg.w(TAG, "Carrier call warning dialog displayed");
    }

    private void initializeViews() {
        mTextViewMessage = findViewById(R.id.textViewWarningMessage);
        mTextViewCallerInfo = findViewById(R.id.textViewCallerInfo);
        mCheckBoxRemember = findViewById(R.id.checkBoxRememberChoice);
        mButtonAcceptInsecure = findViewById(R.id.buttonAcceptInsecure);
        mButtonRejectSecure = findViewById(R.id.buttonRejectSecure);

        mButtonAcceptInsecure.setOnClickListener(v -> onAcceptInsecureCall());
        mButtonRejectSecure.setOnClickListener(v -> onRejectAndUseSecureVoice());
    }

    private void displayWarning() {
        final String message =
            "SECURITY WARNING\n\n" +
            "You are receiving an INSECURE carrier (cellular) call.\n\n" +
            "⚠️ This call is NOT encrypted\n" +
            "⚠️ Vulnerable to interception and surveillance\n" +
            "⚠️ Metadata exposed to carriers and governments\n" +
            "⚠️ Violates QWAMOS security policy\n\n" +
            "RECOMMENDED: Reject this call and use QWAMOS Secure Voice with " +
            "post-quantum encryption instead.";

        mTextViewMessage.setText(message);

        final String callerInfo = String.format("Incoming from: %s", formatCallerNumber(mCallerNumber));
        mTextViewCallerInfo.setText(callerInfo);
    }

    private String formatCallerNumber(final String number) {
        if (number == null || number.isEmpty() || number.equals("Unknown")) {
            return "Unknown Number";
        }
        return number;
    }

    private void onAcceptInsecureCall() {
        Lg.w(TAG, "User chose to ACCEPT INSECURE carrier call (security policy violation)");

        if (mCheckBoxRemember.isChecked()) {
            PreferencesHelper.setCarrierCallAction(this, PreferencesHelper.CARRIER_CALL_ACTION_ALLOW);
            Lg.w(TAG, "User disabled carrier call warnings - security risk!");
        }

        // Don't reject the call, let it ring normally
        finish();
    }

    private void onRejectAndUseSecureVoice() {
        Lg.i(TAG, "User chose to REJECT carrier call and use secure VoIP (correct choice)");

        if (mCheckBoxRemember.isChecked()) {
            PreferencesHelper.setCarrierCallAction(this, PreferencesHelper.CARRIER_CALL_ACTION_AUTO_REJECT);
            Lg.i(TAG, "User enabled auto-reject for carrier calls - good security practice");
        }

        // Reject the carrier call
        rejectCall();

        // Show how to use QWAMOS Secure Voice
        showSecureVoiceInstructions();

        finish();
    }

    private void rejectCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                final TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
                if (telecomManager != null &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
                        == PackageManager.PERMISSION_GRANTED) {

                    telecomManager.endCall();
                    Lg.i(TAG, "Successfully rejected carrier call");
                }
            } catch (final Exception e) {
                Lg.ex(e, "Failed to reject call");
            }
        }
    }

    private void showSecureVoiceInstructions() {
        final Intent intent = new Intent(this, SecureVoiceInstructionsActivity.class);
        intent.putExtra("suggested_contact", mCallerNumber);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        // Require explicit choice - don't allow dismissing with back button
        Lg.i(TAG, "Back button pressed - requiring explicit choice");
    }
}
