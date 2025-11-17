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

package org.simlar.helper;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.simlar.BuildConfig;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * QWAMOS Network Configuration Helper
 *
 * Manages network gateway settings for routing VoIP traffic through
 * Tor/I2P/DNSCrypt proxies as part of QWAMOS security architecture.
 *
 * Security Benefits:
 * - Hides caller IP address from SIP server
 * - Protects metadata (call setup times, registration)
 * - Prevents traffic analysis
 * - Adds network-layer anonymity to end-to-end encryption
 *
 * Gateway Types Supported:
 * 1. Tor SOCKS5 proxy (default: 127.0.0.1:9050)
 * 2. I2P HTTP/SOCKS proxy (default: 127.0.0.1:4444)
 * 3. Custom SOCKS5/HTTP proxy
 * 4. Direct connection (fallback, not recommended for QWAMOS)
 */
public final class QwamosNetworkConfig {
    private static final String TAG = "QwamosNetworkConfig";

    // QWAMOS Gateway Configuration File
    private static final String QWAMOS_CONFIG_DIR = "/data/data/org.qwamos.system/files/config";
    private static final String GATEWAY_CONFIG_FILE = QWAMOS_CONFIG_DIR + "/network_gateway.conf";

    // Default Tor/I2P Proxy Settings
    private static final String DEFAULT_TOR_HOST = "127.0.0.1";
    private static final int DEFAULT_TOR_PORT = 9050; // Tor SOCKS5
    private static final String DEFAULT_I2P_HOST = "127.0.0.1";
    private static final int DEFAULT_I2P_PORT = 4444; // I2P HTTP proxy

    // Preferences keys
    private static final String PREFS_FILE = "qwamos_network";
    private static final String PREF_PROXY_ENABLED = "proxy_enabled";
    private static final String PREF_PROXY_TYPE = "proxy_type";
    private static final String PREF_PROXY_HOST = "proxy_host";
    private static final String PREF_PROXY_PORT = "proxy_port";
    private static final String PREF_USE_SYSTEM_GATEWAY = "use_system_gateway";

    public enum ProxyType {
        NONE("none"),
        TOR_SOCKS5("tor"),
        I2P_HTTP("i2p"),
        CUSTOM_SOCKS5("socks5"),
        CUSTOM_HTTP("http");

        private final String value;

        ProxyType(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @NonNull
        public static ProxyType fromString(@Nullable final String str) {
            if (str == null) return NONE;
            for (final ProxyType type : values()) {
                if (type.value.equalsIgnoreCase(str)) {
                    return type;
                }
            }
            return NONE;
        }
    }

    public static final class ProxyConfig {
        public final boolean enabled;
        public final ProxyType type;
        public final String host;
        public final int port;

        public ProxyConfig(final boolean enabled, final ProxyType type, final String host, final int port) {
            this.enabled = enabled;
            this.type = type;
            this.host = host;
            this.port = port;
        }

        public boolean isValid() {
            if (!enabled) return true; // No proxy is valid
            if (type == ProxyType.NONE) return true;
            if (Util.isNullOrEmpty(host)) return false;
            if (port <= 0 || port > 65535) return false;
            return true;
        }

        @NonNull
        @Override
        public String toString() {
            if (!enabled || type == ProxyType.NONE) {
                return "ProxyConfig{DISABLED}";
            }
            return String.format("ProxyConfig{type=%s, host=%s, port=%d}", type.getValue(), host, port);
        }
    }

    private QwamosNetworkConfig() {
        throw new AssertionError("This class was not meant to be instantiated");
    }

    /**
     * Get current network proxy configuration
     */
    @NonNull
    public static ProxyConfig getProxyConfig(final Context context) {
        // Check if QWAMOS build and proxy should be enabled by default
        if (!BuildConfig.QWAMOS_PQ_ONLY) {
            Lg.i(TAG, "Not a QWAMOS build - proxy disabled");
            return new ProxyConfig(false, ProxyType.NONE, "", 0);
        }

        final SharedPreferences prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);

        // Check if user explicitly disabled proxy
        final boolean enabled = prefs.getBoolean(PREF_PROXY_ENABLED, true); // Default: enabled for QWAMOS
        if (!enabled) {
            Lg.w(TAG, "Proxy explicitly disabled by user - SECURITY WARNING");
            return new ProxyConfig(false, ProxyType.NONE, "", 0);
        }

        // Try to read from QWAMOS system gateway config first
        if (prefs.getBoolean(PREF_USE_SYSTEM_GATEWAY, true)) {
            final ProxyConfig systemConfig = readSystemGatewayConfig();
            if (systemConfig != null && systemConfig.isValid()) {
                Lg.i(TAG, "Using QWAMOS system gateway config: ", systemConfig);
                return systemConfig;
            }
        }

        // Fall back to user preferences or defaults
        final String typeStr = prefs.getString(PREF_PROXY_TYPE, ProxyType.TOR_SOCKS5.getValue());
        final ProxyType type = ProxyType.fromString(typeStr);

        String host;
        int port;

        switch (type) {
            case TOR_SOCKS5:
                host = prefs.getString(PREF_PROXY_HOST, DEFAULT_TOR_HOST);
                port = prefs.getInt(PREF_PROXY_PORT, DEFAULT_TOR_PORT);
                break;

            case I2P_HTTP:
                host = prefs.getString(PREF_PROXY_HOST, DEFAULT_I2P_HOST);
                port = prefs.getInt(PREF_PROXY_PORT, DEFAULT_I2P_PORT);
                break;

            case CUSTOM_SOCKS5:
            case CUSTOM_HTTP:
                host = prefs.getString(PREF_PROXY_HOST, "");
                port = prefs.getInt(PREF_PROXY_PORT, 0);
                break;

            case NONE:
            default:
                Lg.w(TAG, "Proxy type NONE selected - traffic not anonymized");
                return new ProxyConfig(false, ProxyType.NONE, "", 0);
        }

        final ProxyConfig config = new ProxyConfig(true, type, host, port);
        Lg.i(TAG, "Using proxy config: ", config);
        return config;
    }

    /**
     * Read proxy configuration from QWAMOS system gateway config file
     */
    @Nullable
    private static ProxyConfig readSystemGatewayConfig() {
        final File configFile = new File(GATEWAY_CONFIG_FILE);
        if (!configFile.exists()) {
            Lg.i(TAG, "QWAMOS gateway config file not found: ", GATEWAY_CONFIG_FILE);
            return null;
        }

        try (final BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            boolean enabled = true;
            ProxyType type = ProxyType.TOR_SOCKS5;
            String host = DEFAULT_TOR_HOST;
            int port = DEFAULT_TOR_PORT;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip comments and empty lines
                }

                final String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }

                final String key = parts[0].trim();
                final String value = parts[1].trim();

                switch (key) {
                    case "proxy_enabled":
                        enabled = Boolean.parseBoolean(value);
                        break;
                    case "proxy_type":
                        type = ProxyType.fromString(value);
                        break;
                    case "proxy_host":
                        host = value;
                        break;
                    case "proxy_port":
                        try {
                            port = Integer.parseInt(value);
                        } catch (final NumberFormatException e) {
                            Lg.ex(e, "Invalid port number in config: ", value);
                        }
                        break;
                }
            }

            return new ProxyConfig(enabled, type, host, port);

        } catch (final IOException e) {
            Lg.ex(e, "Error reading QWAMOS gateway config");
            return null;
        }
    }

    /**
     * Save proxy configuration to preferences
     */
    public static void saveProxyConfig(final Context context, final ProxyConfig config) {
        final SharedPreferences prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean(PREF_PROXY_ENABLED, config.enabled);
        editor.putString(PREF_PROXY_TYPE, config.type.getValue());
        editor.putString(PREF_PROXY_HOST, config.host);
        editor.putInt(PREF_PROXY_PORT, config.port);
        editor.apply();

        Lg.i(TAG, "Proxy config saved: ", config);
    }

    /**
     * Enable/disable system gateway config reading
     */
    public static void setUseSystemGateway(final Context context, final boolean use) {
        final SharedPreferences prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_USE_SYSTEM_GATEWAY, use).apply();
        Lg.i(TAG, "Use system gateway: ", use);
    }

    /**
     * Check if Tor is likely running on the system
     */
    public static boolean isTorAvailable() {
        // Check if Tor SOCKS port is listening
        // This is a simple heuristic - full implementation would attempt connection
        final File torPidFile = new File("/var/run/tor/tor.pid");
        return torPidFile.exists();
    }

    /**
     * Check if I2P is likely running on the system
     */
    public static boolean isI2pAvailable() {
        // Check if I2P router.config exists
        final File i2pConfigFile = new File("/data/data/net.i2p.android/files/router.config");
        return i2pConfigFile.exists();
    }
}
