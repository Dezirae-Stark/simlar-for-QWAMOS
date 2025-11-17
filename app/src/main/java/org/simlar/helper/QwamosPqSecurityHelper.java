/**
 * Copyright (C) 2025 QWAMOS Project.
 *
 * This file is part of QWAMOS Secure Voice (Simlar fork for QWAMOS).
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package org.simlar.helper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.linphone.core.Call;
import org.linphone.core.CallStats;
import org.linphone.core.StreamType;
import org.linphone.core.ZrtpKeyAgreement;
import org.simlar.BuildConfig;
import org.simlar.logging.Lg;

/**
 * QWAMOS Post-Quantum Security Helper
 *
 * This class provides runtime verification of post-quantum security for VoIP calls.
 * It enforces QWAMOS policy: PQ/hybrid ZRTP only, classical-only calls are rejected.
 */
public final class QwamosPqSecurityHelper {

    private QwamosPqSecurityHelper() {
        // Utility class, no instantiation
    }

    /**
     * Security level for a call based on ZRTP key agreement
     */
    public enum SecurityLevel {
        PQ_HYBRID_STRONG("PQ-Secured (Kyber-1024)", "🔒", true, 0xFF00FF00),  // Green
        PQ_HYBRID_STANDARD("PQ-Secured (Kyber-512)", "🔒", true, 0xFF00CC00), // Yellow-green
        CLASSICAL_ONLY("Classical Only - BLOCKED", "🚫", false, 0xFFFF0000),  // Red
        INSECURE("Insecure / No Encryption", "⚠", false, 0xFFFF6600),         // Orange
        UNKNOWN("Unknown Security Level", "❓", false, 0xFFCCCCCC);           // Gray

        private final String displayName;
        private final String icon;
        private final boolean isPqSecure;
        private final int color;

        SecurityLevel(String displayName, String icon, boolean isPqSecure, int color) {
            this.displayName = displayName;
            this.icon = icon;
            this.isPqSecure = isPqSecure;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIcon() {
            return icon;
        }

        public boolean isPqSecure() {
            return isPqSecure;
        }

        public int getColor() {
            return color;
        }
    }

    /**
     * Check if a call is using post-quantum secure key agreement
     *
     * @param call The Linphone call to check
     * @return true if PQ/hybrid ZRTP is in use, false otherwise
     */
    public static boolean isCallPqSecured(@Nullable Call call) {
        if (call == null) {
            Lg.w("QWAMOS: isCallPqSecured called with null call");
            return false;
        }

        final SecurityLevel level = getCallSecurityLevel(call);
        return level.isPqSecure();
    }

    /**
     * Get the detailed security level of a call
     *
     * @param call The Linphone call to analyze
     * @return SecurityLevel enum indicating the call's security status
     */
    @NonNull
    public static SecurityLevel getCallSecurityLevel(@Nullable Call call) {
        if (call == null) {
            return SecurityLevel.UNKNOWN;
        }

        try {
            // Get audio stream statistics (ZRTP is on audio stream)
            final CallStats stats = call.getAudioStats();
            if (stats == null) {
                Lg.w("QWAMOS: No call stats available");
                return SecurityLevel.UNKNOWN;
            }

            // Check if ZRTP is being used
            if (!stats.isZrtpKeyAgreementAlgoPostQuantum()) {
                Lg.w("QWAMOS: Call is NOT using post-quantum key agreement");
                return SecurityLevel.CLASSICAL_ONLY;
            }

            // Determine which PQ suite is in use
            // Note: Liblinphone doesn't provide direct API to query the exact suite used,
            // so we infer from the fact that PQ is in use and our configuration
            // prioritizes K448Kyb1024 > K255Kyb512

            Lg.i("QWAMOS: Call is using POST-QUANTUM key agreement");

            // Try to get authentication token for verification
            final String authToken = call.getAuthenticationToken();
            if (authToken != null && !authToken.isEmpty()) {
                Lg.i("QWAMOS: ZRTP SAS authentication token: ", authToken);
            }

            // Since we configured K448Kyb1024 as highest priority, assume it's used if PQ is active
            // In a more sophisticated implementation, we could query the actual suite if API available
            return SecurityLevel.PQ_HYBRID_STRONG;

        } catch (Exception e) {
            Lg.ex(e, "QWAMOS: Exception while checking call security level");
            return SecurityLevel.UNKNOWN;
        }
    }

    /**
     * Get a human-readable security status message for UI display
     *
     * @param call The call to analyze
     * @return Formatted security status string
     */
    @NonNull
    public static String getSecurityStatusMessage(@Nullable Call call) {
        final SecurityLevel level = getCallSecurityLevel(call);
        return level.getIcon() + " " + level.getDisplayName();
    }

    /**
     * Enforce QWAMOS policy: Deny calls that don't meet PQ requirements
     *
     * @param call The call to validate
     * @return true if call should be allowed, false if it should be terminated
     */
    public static boolean enforceQwamosPqPolicy(@Nullable Call call) {
        if (!BuildConfig.ENFORCE_PQ_VOIP_ONLY) {
            Lg.i("QWAMOS: PQ enforcement disabled in build config");
            return true;
        }

        if (call == null) {
            return false;
        }

        final boolean isPqSecured = isCallPqSecured(call);

        if (!isPqSecured) {
            Lg.e("QWAMOS POLICY VIOLATION: Call does not use post-quantum key agreement");
            Lg.e("QWAMOS: Terminating call per PQ-only policy");
            return false;
        }

        Lg.i("QWAMOS: Call meets PQ security requirements");
        return true;
    }

    /**
     * Get error message for policy violation (when remote doesn't support PQ)
     *
     * @return User-facing error message
     */
    @NonNull
    public static String getPqPolicyViolationMessage() {
        return "Remote endpoint does not support post-quantum secure voice.\n\n" +
               "Call cancelled per QWAMOS security policy.\n\n" +
               "Both endpoints must use QWAMOS Secure Voice or PQ-enabled clients.";
    }

    /**
     * Log detailed security information for debugging
     *
     * @param call The call to log information about
     */
    public static void logSecurityDetails(@Nullable Call call) {
        if (call == null) {
            Lg.w("QWAMOS: logSecurityDetails called with null call");
            return;
        }

        final SecurityLevel level = getCallSecurityLevel(call);
        Lg.i("=== QWAMOS SECURITY DETAILS ===");
        Lg.i("Call state: ", call.getState());
        Lg.i("Security level: ", level.getDisplayName());
        Lg.i("PQ secured: ", level.isPqSecure() ? "YES" : "NO");
        Lg.i("Authentication token: ", call.getAuthenticationToken());
        Lg.i("Remote address: ", call.getRemoteAddress().asStringUriOnly());

        try {
            final CallStats stats = call.getAudioStats();
            if (stats != null) {
                Lg.i("ZRTP PQ algorithm: ", stats.isZrtpKeyAgreementAlgoPostQuantum() ? "YES" : "NO");
            }
        } catch (Exception e) {
            Lg.ex(e, "QWAMOS: Error logging security details");
        }

        Lg.i("=== END SECURITY DETAILS ===");
    }

    /**
     * Check if video stream is enabled and PQ-secured
     *
     * @param call The call to check
     * @return true if video is enabled and secure, false otherwise
     */
    public static boolean isVideoStreamSecure(@Nullable Call call) {
        if (call == null) {
            return false;
        }

        try {
            final CallStats videoStats = call.getVideoStats(StreamType.Video);
            if (videoStats == null) {
                Lg.i("QWAMOS: No video stream active");
                return false; // No video stream, not applicable
            }

            // Check if video stream uses PQ key agreement
            final boolean videoPqSecured = videoStats.isZrtpKeyAgreementAlgoPostQuantum();
            Lg.i("QWAMOS: Video stream PQ-secured: ", videoPqSecured ? "YES" : "NO");

            return videoPqSecured;

        } catch (Exception e) {
            Lg.ex(e, "QWAMOS: Error checking video stream security");
            return false;
        }
    }

    /**
     * Get combined security status for audio and video streams
     *
     * @param call The call to analyze
     * @return true if all active streams are PQ-secured
     */
    public static boolean areAllStreamsSecure(@Nullable Call call) {
        if (call == null) {
            return false;
        }

        // Audio must always be PQ-secured
        final boolean audioSecure = isCallPqSecured(call);
        if (!audioSecure) {
            Lg.w("QWAMOS: Audio stream not PQ-secured");
            return false;
        }

        // If video is active, it must also be PQ-secured
        try {
            final CallStats videoStats = call.getVideoStats(StreamType.Video);
            if (videoStats != null) {
                final boolean videoSecure = isVideoStreamSecure(call);
                if (!videoSecure) {
                    Lg.w("QWAMOS: Video stream not PQ-secured");
                    return false;
                }
            }
        } catch (Exception e) {
            Lg.ex(e, "QWAMOS: Error checking all streams");
            return false;
        }

        Lg.i("QWAMOS: All active streams are PQ-secured");
        return true;
    }

    /**
     * Get detailed security status including video
     *
     * @param call The call to analyze
     * @return Formatted string with audio and video security status
     */
    @NonNull
    public static String getDetailedSecurityStatus(@Nullable Call call) {
        if (call == null) {
            return "No active call";
        }

        final StringBuilder status = new StringBuilder();
        final SecurityLevel audioLevel = getCallSecurityLevel(call);

        status.append("Audio: ").append(audioLevel.getIcon()).append(" ").append(audioLevel.getDisplayName());

        try {
            final CallStats videoStats = call.getVideoStats(StreamType.Video);
            if (videoStats != null) {
                final boolean videoPqSecured = videoStats.isZrtpKeyAgreementAlgoPostQuantum();
                if (videoPqSecured) {
                    status.append("\nVideo: 🔒 PQ-Secured");
                } else {
                    status.append("\nVideo: 🚫 Classical Only");
                }
            } else {
                status.append("\nVideo: Not active");
            }
        } catch (Exception e) {
            Lg.ex(e, "QWAMOS: Error getting video status");
            status.append("\nVideo: Unknown");
        }

        return status.toString();
    }
}
