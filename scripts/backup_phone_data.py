#!/usr/bin/env python3
"""
backup_phone_data.py
Creates a full, bit-for-bit backup of DeutschLern app data from a connected Android device:
- SQLite databases (deutschlern.db, -wal, -shm)
- DataStore preferences (secure_keys.preferences_pb, app_settings.preferences_pb, json_mode_capabilities.preferences_pb)
- Shared preferences (com.google.mlkit.internal.xml)
- Standalone consolidated SQLite database (WAL merged via VACUUM INTO)

Output is stored in:
backups/phone_backup_YYYYMMDD_HHMMSS/
  ├── deutschlern_backup.tar
  ├── extracted/
  │     ├── databases/
  │     ├── files/datastore/
  │     └── shared_prefs/
  ├── deutschlern_consolidated.db
  └── backup_manifest.json
"""

import os
import sys
import shutil
import subprocess
import tarfile
import sqlite3
import json
from datetime import datetime

try:
    if sys.stdout.encoding.lower() != 'utf-8':
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass

def find_adb():
    adb = shutil.which("adb")
    if adb:
        return adb
    for env_var in ["ANDROID_HOME", "ANDROID_SDK_ROOT"]:
        base = os.environ.get(env_var)
        if base:
            candidate = os.path.join(base, "platform-tools", "adb.exe" if os.name == 'nt' else "adb")
            if os.path.exists(candidate):
                return candidate
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        candidate = os.path.join(local_app_data, "Android", "Sdk", "platform-tools", "adb.exe")
        if os.path.exists(candidate):
            return candidate
    local_props = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "local.properties")
    if os.path.exists(local_props):
        try:
            with open(local_props, "r", encoding="utf-8") as f:
                for line in f:
                    if line.startswith("sdk.dir="):
                        sdk_dir = line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\")
                        candidate = os.path.join(sdk_dir, "platform-tools", "adb.exe" if os.name == 'nt' else "adb")
                        if os.path.exists(candidate):
                            return candidate
        except Exception:
            pass
    raise RuntimeError("adb executable not found in PATH, %ANDROID_HOME%, or local.properties")

PACKAGE_NAME = "app.deutschlern"

def run_adb(adb, args, capture_output=True, check=True):
    cmd = [adb] + args
    res = subprocess.run(cmd, capture_output=capture_output)
    if check and res.returncode != 0:
        err = res.stderr.decode(errors='replace') if res.stderr else ""
        raise RuntimeError(f"ADB command failed ({' '.join(args)}): {err}")
    return res

def backup():
    adb = find_adb()
    print(f"[1/6] Found ADB at: {adb}")

    # Check device
    res = run_adb(adb, ["devices"])
    lines = [line.strip() for line in res.stdout.decode().splitlines() if line.strip() and not line.startswith("*")]
    devices = [line.split()[0] for line in lines[1:] if "device" in line]
    if not devices:
        raise RuntimeError("No connected Android device found!")
    device_id = devices[0]
    print(f"[2/6] Connected device: {device_id}")

    # Stop app to ensure quiet state
    print(f"[3/6] Stopping {PACKAGE_NAME} for clean snapshot...")
    run_adb(adb, ["shell", "am", "force-stop", PACKAGE_NAME], check=False)

    # Prepare backup directory
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    backup_dir = os.path.join(project_root, "backups", f"phone_backup_{timestamp}")
    os.makedirs(backup_dir, exist_ok=True)
    tar_path = os.path.join(backup_dir, "deutschlern_backup.tar")

    print(f"[4/6] Streaming app data tar archive from device to {tar_path}...")
    tar_cmd = [
        adb, "exec-out", "run-as", PACKAGE_NAME,
        "tar", "-cf", "-", "-C", f"/data/data/{PACKAGE_NAME}",
        "databases", "files/datastore", "shared_prefs"
    ]
    with open(tar_path, "wb") as f:
        res = subprocess.run(tar_cmd, stdout=f, stderr=subprocess.PIPE)
        if res.returncode != 0:
            err = res.stderr.decode(errors='replace') if res.stderr else ""
            raise RuntimeError(f"Failed to stream tar archive: {err}")

    tar_size = os.path.getsize(tar_path)
    print(f"      Downloaded {tar_size:,} bytes.")

    # Extract tar
    print(f"[5/6] Extracting files into {backup_dir}...")
    extracted_dir = os.path.join(backup_dir, "extracted")
    os.makedirs(extracted_dir, exist_ok=True)
    with tarfile.open(tar_path, "r") as tf:
        tf.extractall(path=extracted_dir)

    # Verify extracted contents
    db_path = os.path.join(extracted_dir, "databases", "deutschlern.db")
    wal_path = os.path.join(extracted_dir, "databases", "deutschlern.db-wal")
    secure_keys_path = os.path.join(extracted_dir, "files", "datastore", "secure_keys.preferences_pb")
    settings_path = os.path.join(extracted_dir, "files", "datastore", "app_settings.preferences_pb")

    if not os.path.exists(db_path):
        raise RuntimeError("deutschlern.db missing from extracted archive!")
    if not os.path.exists(secure_keys_path):
        raise RuntimeError("secure_keys.preferences_pb missing from extracted archive!")

    print("[6/6] Verifying SQLite integrity and creating consolidated standalone database...")
    con = sqlite3.connect(db_path)
    cur = con.cursor()
    integrity = cur.execute("PRAGMA integrity_check;").fetchone()[0]
    user_version = cur.execute("PRAGMA user_version;").fetchone()[0]
    word_count = cur.execute("SELECT count(*) FROM words;").fetchone()[0]

    # Tables list
    tables = [row[0] for row in cur.execute("SELECT name FROM sqlite_master WHERE type='table';").fetchall()]
    table_counts = {}
    for t in tables:
        if not t.startswith("sqlite_"):
            count = cur.execute(f"SELECT count(*) FROM {t};").fetchone()[0]
            table_counts[t] = count

    # Sample words
    sample_words = [row[0] for row in cur.execute("SELECT lemma FROM words ORDER BY id DESC LIMIT 10;").fetchall()]

    # Create standalone vacuumed DB (merges WAL pages into a single standalone file)
    consolidated_db_path = os.path.join(backup_dir, "deutschlern_consolidated.db")
    try:
        cur.execute(f"VACUUM INTO '{consolidated_db_path.replace(os.sep, '/')}';")
        print(f"      Consolidated database written to: {consolidated_db_path}")
    except Exception as e:
        print(f"      VACUUM INTO fallback: {e}")
        # Alternatively do checkpoint
        cur.execute("PRAGMA wal_checkpoint(TRUNCATE);")
        shutil.copyfile(db_path, consolidated_db_path)

    con.close()

    # Verify keys
    with open(secure_keys_path, "rb") as f:
        keys_bytes = f.read()
    has_gemini = b"enc_key_gemini" in keys_bytes
    has_groq = b"enc_key_groq" in keys_bytes

    manifest = {
        "timestamp": timestamp,
        "device_id": device_id,
        "package": PACKAGE_NAME,
        "tar_archive_size_bytes": tar_size,
        "sqlite_user_version": user_version,
        "sqlite_integrity": integrity,
        "words_count": word_count,
        "table_counts": table_counts,
        "recent_sample_words": sample_words,
        "secure_keys": {
            "has_gemini_key": has_gemini,
            "has_groq_key": has_groq,
            "size_bytes": len(keys_bytes)
        },
        "settings_size_bytes": os.path.getsize(settings_path) if os.path.exists(settings_path) else 0
    }

    manifest_path = os.path.join(backup_dir, "backup_manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)

    print("\n" + "="*60)
    print("BACKUP COMPLETED SUCCESSFULLY!")
    print("="*60)
    print(f"Target folder   : {backup_dir}")
    print(f"Tarball         : {tar_path} ({tar_size:,} bytes)")
    print(f"Consolidated DB : {consolidated_db_path}")
    print(f"Room version    : {user_version}")
    print(f"Words stored    : {word_count}")
    print(f"Sample words    : {', '.join(sample_words[:5])}...")
    print(f"Gemini key      : {'Present' if has_gemini else 'Missing'}")
    print(f"Groq key        : {'Present' if has_groq else 'Missing'}")
    print(f"Manifest        : {manifest_path}")
    print("="*60)

    return backup_dir

if __name__ == "__main__":
    backup()
