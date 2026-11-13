# Flovar Attendance (Android, Kotlin + Jetpack Compose)

Employee app for erp.flovarservices.com (Frappe/ERPNext + HRMS v16). Records geotagged
clock-in/out as **Employee Checkin** (latitude, longitude, device_id) so HRMS auto-attendance works unchanged.

## 1. Server side (do this first)
1. Frappe Cloud > Site > Site Config: set `server_script_enabled` = 1.
2. In the Desk create two **Server Scripts** (type *API*, Allow Guest off) with the code in
   `server/flovar_checkin.py` (method `flovar_checkin`) and `server/flovar_today.py` (method `flovar_today`).
3. For each employee: link the **User** on the Employee record (`user_id`).
4. Geofence: create **Shift Location** records (lat/long + check-in radius, e.g. one per BTS/solar/office site),
   attach to the Shift Type, set Employee > Default Shift. Employees with no shift location can clock from
   anywhere (still geotagged) - tighten as you prefer.
5. Give the Employee role read access to nothing extra: the scripts run server-side and derive the employee
   from the session, so users cannot clock for someone else.

## 2. Build
Open the folder in Android Studio (Koala+), sync, Run. Base URL is in `Config.kt`.
Release: Build > Generate Signed Bundle/APK. Distribute via Play Internal testing or Managed Play.

## 3. Security built in
- HTTPS only (cleartext disabled); no password stored - only the session cookie, in Keystore-backed EncryptedSharedPreferences
- Biometric / device-PIN unlock; app re-locks every time it leaves the foreground
- FLAG_SECURE (no screenshots), backups disabled, R8 shrinking in release
- Mock-location + accuracy checks on device AND server; server timestamps and derives the employee itself
- Duplicate-punch guard and geofence enforced server-side (client can't be trusted)

## 4. Verify / harden next
- CSRF: the app reads the token from `/app` after login. If your v16 site embeds it differently, adjust the regex in `Api.fetchCsrf()`.
  Alternative: per-user API key/secret with `Authorization: token key:secret`.
- Enable 2FA on the ERP login (Frappe System Settings) - the app's login call will then need the OTP step (not included).
- Add Play Integrity API attestation (rooted/tampered devices) and certificate pinning with a backup pin once you settle on a stable cert chain.
- Shift Type's `shift_location` field name is assumed from HRMS; the scripts fail open to "no geofence" if it is absent.

## 5. Getting the APK
**GitHub (no installs):** push this folder to a private repo > Actions > *Build APK* > run > download the artifact
`flovar-attendance-apk` (signed `app-release.apk`). Copy to phones and install (allow "install unknown apps").
**Android Studio:** open folder, Build > Build APK(s), or run `./gradlew assembleRelease`.
The APK is signed with the bundled internal key (`app/flovar-internal.jks`, password in app/build.gradle.kts):
fine for in-house sideloading; use your own key (KEYSTORE_PASSWORD / KEY_PASSWORD env vars) before Play Store.
Keep the repo private.
