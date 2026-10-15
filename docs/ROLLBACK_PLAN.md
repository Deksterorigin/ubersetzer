# DeutschLern: Safe Deployment & Rollback Plan

This document establishes the mandatory protocol for deploying updates to the physical test device and executing a rollback if any database migration or runtime issue occurs.

---

## 1. Golden Rules (Zero Data Loss)

1. **NEVER UNINSTALL THE APP**:
   Running `adb uninstall app.deutschlern` or `gradlew uninstall*` deletes `/data/data/app.deutschlern` completely, wiping the Room SQLite database, user vocabulary, study history, and encrypted API keys stored in DataStore.
2. **NEVER RUN `connectedAndroidTest` ON DEVICE WITH REAL DATA**:
   AGP instrumentation tasks install and uninstall test packages, destroying app data upon test completion.
3. **ALWAYS BACK UP BEFORE INSTALLING**:
   Before running `adb install -r`, take a full snapshot of the app's databases, WAL journals, and DataStore files.
4. **USE ONLY `adb install -r`**:
   The `-r` flag reinstalls the application while preserving all existing application data and permissions.

---

## 2. Pre-Deployment Step: Full Phone Data Backup

Run the automated backup script from the host terminal:
```powershell
py scripts/backup_phone_data.py
```

This creates a timestamped folder under `backups/phone_backup_YYYYMMDD_HHMMSS/` containing:
- `deutschlern_backup.tar` (bit-for-bit archive of `databases/`, `files/datastore/`, and `shared_prefs/`)
- `deutschlern_consolidated.db` (clean standalone SQLite database with WAL merged via `VACUUM INTO`)
- `backup_manifest.json` (verification report of table row counts, Room version, and key presence)

Verify in the output that:
- `Words stored` matches the current word count (e.g. 41).
- `Gemini key` and `Groq key` are reported as `Present`.
- `PRAGMA integrity_check` is `ok`.

---

## 3. Deployment Protocol (When Authorized)

Build the debug APK without installing:
```powershell
$env:PATH = ($env:PATH.Split(';') | Where-Object { $_ -notlike '*Tailscale*' }) -join ';'; .\gradlew.bat assembleDebug
```

Install the APK while preserving data:
```powershell
& "C:\Users\Sasha\android-sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
```

Launch the app:
```powershell
& "C:\Users\Sasha\android-sdk\platform-tools\adb.exe" shell monkey -p app.deutschlern -c android.intent.category.LAUNCHER 1
```

---

## 4. Post-Deployment Verification Checklist

Immediately upon launch, verify:
1. **Vocabulary Count**: The Vocabulary / History tab displays exactly the pre-install word count (41 words).
2. **Review Tab**: The Review tab opens and displays the expected flashcard review items.
3. **Dialogues**: Opening saved dialogues does not crash (`ClassCastException` fix).
4. **Logcat Monitoring**: Check for any SQLite or migration errors:
   ```powershell
   & "C:\Users\Sasha\android-sdk\platform-tools\adb.exe" logcat -d -s "AndroidRuntime" "Room" "SQLite"
   ```

---

## 5. Rollback Procedure (If Issues Occur)

If the new build crashes on launch, throws a `Room` / `SQLite` migration error, or fails verification:

### Step 5.1: Force-stop the failing app
```powershell
& "C:\Users\Sasha\android-sdk\platform-tools\adb.exe" shell am force-stop app.deutschlern
```

### Step 5.2: Restore the database and keys from backup
Using the automated restore script:
```powershell
py scripts/restore_phone_data.py
```

Or via direct ADB commands:
```powershell
# Ensure directories exist
& "C:\Users\Sasha\android-sdk\platform-tools\adb.exe" shell "run-as app.deutschlern mkdir -p /data/data/app.deutschlern/databases /data/data/app.deutschlern/files/datastore /data/data/app.deutschlern/shared_prefs"

# Stream backup tar into device app sandbox
Get-Content -Path "backups\phone_backup_20261015_225730\deutschlern_backup.tar" -Encoding Byte | & "C:\Users\Sasha\android-sdk\platform-tools\adb.exe" exec-in run-as app.deutschlern tar -xf - -C /data/data/app.deutschlern

# Fix permissions
& "C:\Users\Sasha\android-sdk\platform-tools\adb.exe" shell "run-as app.deutschlern chmod -R u+rwX /data/data/app.deutschlern/databases /data/data/app.deutschlern/files /data/data/app.deutschlern/shared_prefs"
```

### Step 5.3: Downgrade / Reinstall previous working build with `-r -d`
The `-d` flag permits version downgrade while `-r` preserves existing data:
```powershell
& "C:\Users\Sasha\android-sdk\platform-tools\adb.exe" install -r -d "<path_to_previous_working_apk>"
```

### Step 5.4: Relaunch and verify data
```powershell
& "C:\Users\Sasha\android-sdk\platform-tools\adb.exe" shell monkey -p app.deutschlern -c android.intent.category.LAUNCHER 1
```
Verify that all 41 words and API keys are intact.
