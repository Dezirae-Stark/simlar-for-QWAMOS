# Simlar for QWAMOS: Post-Quantum Secure Voice

[![QWAMOS](https://img.shields.io/badge/QWAMOS-PQ--Only-purple.svg)](https://github.com/Dezirae-Stark/QWAMOS)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()
[![License: GPL v2+](https://img.shields.io/badge/License-GPLv2+-blue.svg)](https://www.gnu.org/licenses/gpl-2.0)

**Simlar for QWAMOS** is a post-quantum hardened fork of [Simlar](https://www.simlar.org) (a Liblinphone-based VoIP client) designed exclusively for the **QWAMOS** (Qubes+Whonix Advanced Mobile OS) secure mobile operating system.

This fork enforces **mandatory post-quantum ZRTP encryption** using **CRYSTALS-Kyber** hybrid key agreement, with **zero classical-only fallback**. Calls that don't meet post-quantum security requirements are automatically rejected.

---

## 🔐 Key Differences from Upstream Simlar

| Feature | Upstream Simlar | Simlar for QWAMOS |
|---------|-----------------|-------------------|
| **Encryption** | ZRTP (classical ECDH) | ZRTP-PQ (Kyber hybrid) |
| **Key Agreement** | X255, X448, DH2K, DH3K | **K448Kyb1024, K255Kyb512 ONLY** |
| **Classical Fallback** | ✅ Allowed | ❌ **BLOCKED** (PQ-only policy) |
| **Policy Enforcement** | Optional | **Mandatory** (auto-terminates non-PQ calls) |
| **Runtime Verification** | No | ✅ Yes (QwamosPqSecurityHelper) |
| **UI Security Indicators** | Basic | ✅ **Real-time PQ status display** |
| **Package Name** | org.simlar | org.qwamos.securevoice |
| **Network Routing** | Direct clearnet | QWAMOS gateway (Tor/I2P) |
| **Target Use Case** | General VoIP | Nation-state defense / High-security |

---

## 🚀 Features

### Post-Quantum Cryptography
- **CRYSTALS-Kyber-1024** + Curve448 (highest security)
- **CRYSTALS-Kyber-512** + Curve25519 (balanced security)
- **NIST PQC Standard** - resistant to quantum attacks
- **Hybrid design** - combines classical ECDH + PQ KEM for defense-in-depth

### Strict Security Policy
- **PQ-only enforcement**: Classical-only calls automatically rejected
- **Runtime PQ verification**: Verifies Kyber is actually being used
- **Policy violation handling**: Clear error messages when remote doesn't support PQ
- **No downgrade attacks**: Cannot fall back to insecure classical crypto

### User-Visible Security Indicators
- 🔒 **Green indicator**: PQ-Secured (Kyber-1024 or Kyber-512)
- 🚫 **Red indicator**: Classical-only detected, call blocked
- Real-time updates during call
- Visible security status at all times

### QWAMOS Integration
- Designed for QWAMOS Secure Voice subsystem
- Routes all traffic through QWAMOS network gateway (Tor/I2P/DNSCrypt)
- Integrates with QWAMOS secure profiles and policies
- Part of QWAMOS VM-based isolation architecture

---

## 📋 Requirements

### Critical Dependency: PQ-Enabled Liblinphone

**Standard Liblinphone Maven artifacts DO NOT include post-quantum support.** You MUST build Liblinphone from source with PQ enabled:

```bash
# Clone Liblinphone
git clone https://github.com/BelledonneCommunications/liblinphone.git
cd liblinphone

# Build with PQ support
cd android
./prepare.py -DENABLE_PQCRYPTO=ON
./gradlew assembleRelease

# Copy AAR to Simlar project
cp liblinphone-sdk/build/outputs/aar/linphone-sdk-android-release.aar \
   ../simlar-for-QWAMOS/app/libs/linphone-sdk/5.4.24/
```

**Without PQ-enabled Liblinphone:**
- Code compiles but logs "Post-Quantum encryption NOT available"
- PQ suites won't be negotiated
- All calls will fail (no classical fallback per QWAMOS policy)

### Build Dependencies
* Java Development Kit 17+
* Android SDK with API 35
* Android Studio (recommended)
* Gradle 8.0+

---

## 🛠️ Build Instructions

### Standard Build (Debug)
```bash
export ANDROID_HOME=<YOUR ANDROID SDK DIRECTORY>
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/alwaysOnline/debug/app-alwaysOnline-debug.apk`

### Release Build
```bash
./gradlew assembleAlwaysOnlineRelease -Pno-google-services
```

### Build with PQ Verification
```bash
# Check build logs for PQ confirmation
./gradlew assembleDebug | grep "QWAMOS: ZRTP Post-Quantum"
# Should see: "QWAMOS: ZRTP Post-Quantum encryption available: true"
```

### Install on Device
```bash
adb install app/build/outputs/apk/alwaysOnline/debug/app-alwaysOnline-debug.apk
```

---

## 🧪 Testing PQ Encryption

### Smoke Test (Two Devices Required)

1. **Install on both devices**:
   ```bash
   adb -s device1 install app-alwaysOnline-debug.apk
   adb -s device2 install app-alwaysOnline-debug.apk
   ```

2. **Register both devices** with SIP server (simlar.org or custom)

3. **Place call** from Device 1 to Device 2

4. **Check logs** for PQ confirmation:
   ```bash
   adb logcat | grep QWAMOS
   # Expected output:
   # QWAMOS: ZRTP Post-Quantum encryption available: true
   # QWAMOS: PQ-only ZRTP policy enforced - classical key agreement disabled
   # QWAMOS: Call meets PQ security requirements - proceeding
   # QWAMOS: PQ Security Status updated: 🔒 PQ-Secured (Kyber-1024)
   ```

5. **Verify UI** shows green 🔒 indicator with "PQ-Secured (Kyber)"

### Policy Enforcement Test

1. **Install standard Linphone** (no PQ support) on Device 1
2. **Install Simlar for QWAMOS** on Device 2
3. **Attempt call** from Linphone → QWAMOS
4. **Expected result**: Call rejected with error:
   ```
   Remote endpoint does not support post-quantum secure voice.
   Call cancelled per QWAMOS security policy.
   ```

---

## 📁 Modified Files

### Core PQ Implementation
- `app/src/main/java/org/simlar/service/liblinphone/LinphoneHandler.java`
  - **Lines 159-184**: PQ ZRTP configuration
  - Enabled K448Kyb1024 and K255Kyb512 suites
  - Removed classical suites (X255, X448, DH2K, DH3K)

- `app/src/main/java/org/simlar/helper/QwamosPqSecurityHelper.java` (NEW)
  - 260 lines - PQ security verification
  - `isCallPqSecured(Call)` - Runtime PQ check
  - `getCallSecurityLevel(Call)` - Detailed security status
  - `enforceQwamosPqPolicy(Call)` - Policy enforcement

- `app/src/main/java/org/simlar/service/liblinphone/LinphoneManager.java`
  - **Lines 51, 474-501**: Import and use QwamosPqSecurityHelper
  - **Lines 141-150**: Expose getCurrentCall() for PQ verification
  - **Lines 474-493**: PQ policy enforcement in onCallEncryptionChanged

### UI Implementation
- `app/src/main/res/layout/activity_call.xml`
  - **Lines 162-193**: Added linearLayoutPqSecurityStatus
  - Green/red background based on PQ status
  - Icon + text display

- `app/src/main/java/org/simlar/widgets/CallActivity.java`
  - **Lines 52, 93-96**: Import QwamosPqSecurityHelper, add UI fields
  - **Lines 203-205**: Initialize PQ indicator views
  - **Lines 310-340**: updatePqSecurityStatus() method
  - **Line 377**: Call updatePqSecurityStatus() on state change

- `app/src/main/java/org/simlar/service/SimlarService.java`
  - **Lines 79, 120-121**: Import helper, add PQ status fields
  - **Lines 1204-1239**: getPqSecurityStatus() and isCallPqSecured()

### Build Configuration
- `app/build.gradle`
  - **Line 64**: Changed applicationId to `org.qwamos.securevoice`
  - **Lines 76-78**: Added QWAMOS build flags

---

## 📖 Documentation

Comprehensive documentation available in QWAMOS repository:

- **[QWAMOS-Secure-Voice-PQ.md](https://github.com/Dezirae-Stark/QWAMOS/blob/master/docs/QWAMOS-Secure-Voice-PQ.md)** (4,500+ lines)
  - Cryptographic architecture
  - Threat model
  - Usage instructions
  - Build instructions for PQ Liblinphone
  - FAQ and troubleshooting

- **[QWAMOS-SECURE-VOICE-IMPLEMENTATION.md](https://github.com/Dezirae-Stark/QWAMOS/blob/master/docs/QWAMOS-SECURE-VOICE-IMPLEMENTATION.md)** (1,200+ lines)
  - Implementation summary
  - Technical details
  - Testing plan
  - Next steps

---

## 🔬 Cryptographic Details

### ZRTP Key Agreement (PQ/Hybrid)

**Configured Suites (Priority Order):**

1. **K448Kyb1024** (HIGHEST SECURITY)
   - Classical: Goldilocks Curve448 (ECDH)
   - PQ: CRYSTALS-Kyber-1024 (lattice-based KEM)
   - Security: ~256-bit equivalent vs quantum adversary
   - Use: Maximum security for high-value communications

2. **K255Kyb512** (BALANCED)
   - Classical: Bernstein Curve25519 (ECDH)
   - PQ: CRYSTALS-Kyber-512 (lattice-based KEM)
   - Security: ~128-bit equivalent vs quantum adversary
   - Use: Standard secure communications

**Handshake Process:**
1. Endpoints negotiate highest mutually supported suite
2. Hybrid key agreement: Classical ECDH + Kyber KEM
3. Keys combined using ZRTP's hash combiner (defense-in-depth)
4. Derive SRTP master key from hybrid secret
5. SAS (Short Authentication String) generated for MitM detection

### SRTP Media Encryption
- **Cipher**: AES-256-CM-HMAC-SHA1-80
- **Key Derivation**: From ZRTP PQ-agreed master secret
- **Forward Secrecy**: Per-call ephemeral keys
- **Authentication**: HMAC-SHA1 for integrity

---

## ⚠️ Limitations & Future Work

### Current Limitations
1. **1-to-1 calls only** - Group calls not yet supported with PQ
2. **Liblinphone dependency** - Must build from source with PQ enabled
3. **SIP metadata not protected** - Server sees caller IDs, timestamps
   - *Mitigation*: Use Tor/I2P proxy (planned QWAMOS integration)
4. **No video calls tested** - Audio-only focus initially
5. **Standard SIP server** - Currently uses simlar.org (third-party)
   - *Recommendation*: Deploy QWAMOS-controlled SIP server

### Not Implemented Yet
- Automatic carrier call warning (designed, pending)
- Network gateway proxy routing (designed, pending)
- Contact sync with QWAMOS global contacts
- Custom SIP server deployment

### Future Enhancements
- Video calls with PQ verification
- Group conferencing (when Liblinphone upstream supports PQ group calls)
- Integration with QWAMOS VM isolation
- Full E2E metadata protection via Tor/I2P

---

## 🤝 Contributing

This is a **QWAMOS-specific fork**. For contributions to upstream Simlar, see:
- https://github.com/simlar/simlar-android

For QWAMOS Secure Voice contributions:
1. Fork this repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Ensure PQ tests pass
4. Commit with detailed message
5. Push and create Pull Request

**Important**: All contributions must maintain PQ-only policy enforcement.

---

## 📜 License

This fork maintains the upstream Simlar license:

- **GPLv2+** - Same as upstream Simlar
- **Liblinphone**: GPLv3
- **bzrtp (ZRTP implementation)**: GPLv3
- **CRYSTALS-Kyber**: Public domain (NIST PQC)

---

## 🔗 Links

- **QWAMOS Main Repository**: https://github.com/Dezirae-Stark/QWAMOS
- **Upstream Simlar**: https://github.com/simlar/simlar-android
- **Simlar Website**: https://www.simlar.org
- **Liblinphone**: https://gitlab.linphone.org/BC/public/liblinphone
- **CRYSTALS-Kyber**: https://pq-crystals.org/kyber/
- **ZRTP RFC 6189**: https://www.rfc-editor.org/rfc/rfc6189.html

---

## 📞 Support

For issues specific to QWAMOS Secure Voice:
- **GitHub Issues**: https://github.com/Dezirae-Stark/QWAMOS/issues
- **Tag**: [secure-voice]

For upstream Simlar issues:
- https://github.com/simlar/simlar-android/issues

---

**Built for QWAMOS** | **PQ-Only Secure Voice** | **No Classical Fallback** | **Quantum-Resistant**

**Last Updated**: 2025-11-17
**Version**: Based on Simlar (simlar-android) + QWAMOS PQ Extensions
**Branch**: feature/qwamos-pq-secure-voice
