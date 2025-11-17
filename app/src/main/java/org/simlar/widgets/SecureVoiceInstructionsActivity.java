/*
 * Copyright (C) 2025 QWAMOS Project
 *
 * This file is part of Simlar for QWAMOS (https://github.com/Dezirae-Stark/simlar-for-QWAMOS).
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */

package org.simlar.widgets;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.simlar.R;
import org.simlar.logging.Lg;

/**
 * Shows instructions on how to use QWAMOS Secure Voice instead of carrier calls
 */
public final class SecureVoiceInstructionsActivity extends Activity {
    private static final String TAG = "SecureVoiceInstructions";

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_secure_voice_instructions);

        final String suggestedContact = getIntent().getStringExtra("suggested_contact");

        final TextView textViewInstructions = findViewById(R.id.textViewInstructions);
        final Button buttonOpenSecureVoice = findViewById(R.id.buttonOpenSecureVoice);
        final Button buttonClose = findViewById(R.id.buttonClose);

        final String instructions =
            "How to Make a Secure Call:\n\n" +
            "1. Open QWAMOS Secure Voice (this app)\n" +
            "2. Select the contact you want to call\n" +
            "3. Make sure they also have QWAMOS Secure Voice installed\n" +
            "4. Verify the call shows 🔒 PQ-Secured indicator\n\n" +
            "Your call will be protected with:\n" +
            "✓ Post-Quantum encryption (Kyber-1024)\n" +
            "✓ End-to-end encryption (ZRTP)\n" +
            "✓ Protection against quantum computers\n" +
            "✓ SAS verification for man-in-the-middle detection\n\n" +
            (suggestedContact != null && !suggestedContact.equals("Unknown") ?
                "Suggested contact: " + suggestedContact : "");

        textViewInstructions.setText(instructions);

        buttonOpenSecureVoice.setOnClickListener(v -> {
            openMainActivity();
            finish();
        });

        buttonClose.setOnClickListener(v -> finish());

        Lg.i(TAG, "Secure Voice instructions displayed");
    }

    private void openMainActivity() {
        final Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}
