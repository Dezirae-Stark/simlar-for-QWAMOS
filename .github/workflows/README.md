# GitHub Actions CI/CD for QWAMOS Secure Voice

This directory contains GitHub Actions workflows for automated building, testing, and releasing of QWAMOS Secure Voice.

## 📋 Workflows

### 1. **android-build.yml** - Main Build Workflow

**Triggers:**
- Push to `master` or `feature/qwamos-pq-secure-voice`
- Pull requests to these branches
- Manual trigger via workflow_dispatch

**Jobs:**
- **build-debug**: Builds debug APK
  - Output: `simlar-qwamos-debug-apk` artifact
  - Retention: 30 days

- **build-release**: Builds unsigned release APK
  - Output: `simlar-qwamos-release-unsigned-apk` artifact
  - Retention: 90 days
  - Only runs on push/manual trigger

- **lint-and-test**: Runs Android lint and unit tests
  - Uploads lint reports and test results
  - Continues on error (non-blocking)

- **security-scan**: QWAMOS security feature verification
  - Checks for PQ ZRTP configuration
  - Verifies classical suite removal
  - Validates security policy enforcement

**Environment:**
- Java 17 (Temurin)
- Android API 35
- Build Tools 34.0.0

### 2. **pr-checks.yml** - Pull Request Validation

**Triggers:**
- Pull requests to `master` or `feature/qwamos-pq-secure-voice`

**Jobs:**
- **validate-pr**: Basic validation
  - Commit message check
  - Large file detection (>5MB)
  - Sensitive data scanning

- **build-check**: Compilation verification
  - Gradle wrapper validation
  - Java compilation check (fast)

- **security-features-check**: QWAMOS-specific checks
  - PQ ZRTP configuration validation
  - Classical suite removal check
  - Package name verification
  - Build flags validation
  - **Fails if critical issues found**

### 3. **release.yml** - Release Creation

**Triggers:**
- Git tags matching `v*.*.*` (e.g., `v1.0.0`)
- Manual trigger with version input

**Jobs:**
- **create-release**: Build and publish release
  - Builds release APK
  - Generates checksums (SHA256, MD5)
  - Creates release notes
  - Uploads APK and checksums to GitHub Releases
  - Marks as pre-release (unsigned)

**Output:**
- `simlar-qwamos-{VERSION}-unsigned.apk`
- `checksums-{VERSION}.txt`
- Automated release notes

## 🚀 Usage

### Running Builds Automatically

Builds run automatically on every push and pull request. No action needed!

### Manual Build Trigger

1. Go to **Actions** tab in GitHub
2. Select **Android CI/CD** workflow
3. Click **Run workflow**
4. Select branch and click **Run workflow**

### Creating a Release

**Method 1: Git Tag**
```bash
git tag -a v1.0.0 -m "QWAMOS Secure Voice v1.0.0"
git push origin v1.0.0
```

**Method 2: Manual Workflow**
1. Go to **Actions** → **Create Release**
2. Click **Run workflow**
3. Enter version (e.g., `v1.0.0`)
4. Click **Run workflow**

### Downloading Build Artifacts

1. Go to **Actions** tab
2. Click on a completed workflow run
3. Scroll to **Artifacts** section
4. Download desired artifact:
   - `simlar-qwamos-debug-apk`
   - `simlar-qwamos-release-unsigned-apk`
   - `lint-results`
   - `test-results`

## 🔧 Build Configuration

### Environment Variables

Set in workflow files:
```yaml
env:
  JAVA_VERSION: '17'
  ANDROID_API_LEVEL: '35'
  ANDROID_BUILD_TOOLS_VERSION: '34.0.0'
```

### Caching

Gradle packages are cached to speed up builds:
- `~/.gradle/caches`
- `~/.gradle/wrapper`

Cache key: Based on Gradle files hash

### Build Optimization

- `--no-daemon`: Reduces memory usage
- `--stacktrace`: Better error reporting
- Gradle wrapper validated on every build

## 📦 Artifacts

### Debug APK
- **Name:** `simlar-qwamos-debug-apk`
- **Path:** `app/build/outputs/apk/alwaysOnline/debug/app-alwaysOnline-debug.apk`
- **Retention:** 30 days
- **Use:** Development and testing

### Release APK (Unsigned)
- **Name:** `simlar-qwamos-release-unsigned-apk`
- **Path:** `app/build/outputs/apk/alwaysOnline/release/app-alwaysOnline-release-unsigned.apk`
- **Retention:** 90 days
- **Use:** Production (must be signed)

### Lint Results
- **Name:** `lint-results`
- **Path:** `app/build/reports/lint-results-*.html`
- **Retention:** 30 days

### Test Results
- **Name:** `test-results`
- **Path:** `app/build/reports/tests/`
- **Retention:** 30 days

## 🔐 Security Checks

All workflows include QWAMOS-specific security validation:

✅ **PQ ZRTP Configuration**
- Verifies Kyber-1024 and Kyber-512 suites
- Checks suite priority order

✅ **Classical Suite Removal**
- Ensures X255, X448, DH2K, DH3K are removed
- No classical-only fallback allowed

✅ **Policy Enforcement**
- Validates QwamosPqSecurityHelper presence
- Checks enforceQwamosPqPolicy() usage

✅ **Build Configuration**
- Package name: `org.qwamos.securevoice`
- Build flags: `QWAMOS_PQ_ONLY=true`

## ⚠️ Important Notes

### APK Signing

**Release APKs are unsigned!** You must sign them before distribution:

```bash
# Generate keystore (one-time)
keytool -genkey -v -keystore qwamos-release.keystore \
  -alias qwamos -keyalg RSA -keysize 4096 -validity 10000

# Sign APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore qwamos-release.keystore \
  app-alwaysOnline-release-unsigned.apk qwamos

# Verify
jarsigner -verify -verbose app-alwaysOnline-release-unsigned.apk

# Zipalign (optional)
zipalign -v 4 app-alwaysOnline-release-unsigned.apk simlar-qwamos.apk
```

### PQ Liblinphone Dependency

Standard builds use regular Liblinphone **without PQ support**. For full functionality:

1. Build Liblinphone with `-DENABLE_PQCRYPTO=ON`
2. Place AAR in `app/libs/linphone-sdk/5.4.24/`
3. Rebuild

See main README for detailed instructions.

### GitHub Secrets

No secrets currently required. Future enhancements may need:
- `KEYSTORE_FILE`: Base64-encoded keystore
- `KEYSTORE_PASSWORD`: Keystore password
- `KEY_ALIAS`: Signing key alias
- `KEY_PASSWORD`: Key password

## 📊 Build Status

Check build status at:
`https://github.com/Dezirae-Stark/simlar-for-QWAMOS/actions`

## 🐛 Troubleshooting

### Build Fails: "SDK location not found"

Should not occur in GitHub Actions (Android SDK auto-configured).
If it does, check workflow file for `ANDROID_HOME` environment variable.

### Build Fails: "Permission denied on gradlew"

Fixed by `chmod +x gradlew` step in workflows.

### Lint/Test Failures Don't Fail Build

This is intentional (`continue-on-error: true`). Check uploaded reports.

### Release APK Can't Be Installed

APK is unsigned. Follow signing instructions above.

## 📝 Customization

### Changing Build Variants

Edit workflow files:
```yaml
# Debug
run: ./gradlew assembleDebug

# Release
run: ./gradlew assembleAlwaysOnlineRelease -Pno-google-services
```

### Adding More Checks

Add steps to `security-scan` job or create new jobs.

### Modifying Artifact Retention

```yaml
- uses: actions/upload-artifact@v4
  with:
    retention-days: 90  # Change this value
```

## 🔗 Resources

- [GitHub Actions Docs](https://docs.github.com/en/actions)
- [Android Build Tools](https://developer.android.com/studio/build)
- [QWAMOS Documentation](https://github.com/Dezirae-Stark/QWAMOS)
