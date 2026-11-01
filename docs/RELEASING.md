# Releasing HerdrOid

Releases are built by `.github/workflows/release.yml` when a tag like `v1.2.3`
is pushed. The workflow runs the unit tests and lint, builds a signed and
minified APK, verifies the signature, and attaches the APK plus a SHA-256 file
to the GitHub Release.

The workflow fails instead of publishing an unsigned APK. You need to add the
signing secrets once.

## 1. Create the release keystore (once)

Use a dedicated keystore for HerdrOid. Do not reuse a debug key and do not
commit the file.

```sh
keytool -genkeypair -v \
  -keystore herdroid-release.jks \
  -alias herdroid \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype JKS
```

Back up `herdroid-release.jks` and its passwords somewhere safe, offline. Keep
the same keystore for every release: Android will not install an update signed
with a different key. If you lose it, users must uninstall and reinstall the
app, losing app data.

## 2. Add the GitHub Actions secrets

Base64-encode the keystore.

Linux:

```sh
base64 -w0 herdroid-release.jks > herdroid-release.jks.b64
```

macOS:

```sh
base64 -i herdroid-release.jks -o herdroid-release.jks.b64
```

Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("herdroid-release.jks")) |
  Set-Content herdroid-release.jks.b64
```

Add these repository secrets under **Settings → Secrets and variables →
Actions**:

| Secret | Value |
| --- | --- |
| `HERDROID_KEYSTORE_BASE64` | contents of `herdroid-release.jks.b64` |
| `HERDROID_KEYSTORE_PASSWORD` | keystore password |
| `HERDROID_KEY_ALIAS` | `herdroid` (or the alias you chose) |
| `HERDROID_KEY_PASSWORD` | key password |

With the GitHub CLI:

```sh
gh secret set HERDROID_KEYSTORE_BASE64 < herdroid-release.jks.b64
gh secret set HERDROID_KEYSTORE_PASSWORD
gh secret set HERDROID_KEY_ALIAS
gh secret set HERDROID_KEY_PASSWORD
```

Delete the local `.b64` file after uploading it if you do not need it. Never
commit the keystore or the password to the repository.

## 3. Cut a release

Use a `vMAJOR.MINOR` or `vMAJOR.MINOR.PATCH` tag. For the first release,
`v0.1` and `v0.1.0` both work:

```sh
git tag v0.1
git push origin v0.1
```

The workflow derives the APK's `versionName` from the tag and computes
`versionCode` as:

```text
major * 1,000,000 + minor * 1,000 + patch
```

For example, `v1.2.3` becomes versionName `1.2.3` and versionCode `1002003`;
`v0.1` becomes versionName `0.1` and versionCode `1000`. Pick one tag style
and stay with it: `v0.1` and `v0.1.0` produce the same versionCode, so do not
publish both for the same version.
Keep each part below 1000 and only move forward.

The release contains:

- `HerdrOid-<version>.apk`
- `HerdrOid-<version>.apk.sha256`

Users can verify the download on Linux/macOS with:

```sh
sha256sum -c HerdrOid-0.1.apk.sha256
```

## 4. Build a signed release locally

Set the same four values and run Gradle:

```sh
export HERDROID_KEYSTORE=/absolute/path/to/herdroid-release.jks
export HERDROID_KEYSTORE_PASSWORD='…'
export HERDROID_KEY_ALIAS=herdroid
export HERDROID_KEY_PASSWORD='…'

./gradlew :app:assembleRelease \
  -Pherdroid.versionName=0.1 \
  -Pherdroid.versionCode=1000
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Verify it:

```sh
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

Without signing values, `assembleRelease` still works but produces
`app-release-unsigned.apk`, which Android will not install. The CI workflow
always requires the secrets.

## Signing keys are not interchangeable

Debug builds are signed with the local Android debug key. A release APK signed
with a different key cannot update a debug install; uninstall the debug build
first. Do not ship the debug key.

## Future options

- Play Store or F-Droid packaging is not configured yet. Both need their own
  release process and, for Play, an app bundle.
- If you add Play Store releases, keep the GitHub Releases as a sideload
  channel, or document that users must pick one channel and stay on it.
