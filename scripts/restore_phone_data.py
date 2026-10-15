#!/usr/bin/env python3
"""
restore_phone_data.py
Restores DeutschLern app data (database, keys, settings) from a backup directory to a connected Android device.

Usage:
    py scripts/restore_phone_data.py [PATH_TO_BACKUP_DIR]

If PATH_TO_BACKUP_DIR is omitted, the most recent backup in backups/phone_backup_* is used.
"""

import os
import sys
import glob
import shutil
import subprocess

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
    raise RuntimeError("adb executable not found in PATH or %ANDROID_HOME%")

PACKAGE_NAME = "app.deutschlern"

def run_adb(adb, args, check=True):
    cmd = [adb] + args
    res = subprocess.run(cmd, capture_output=True)
    if check and res.returncode != 0:
        err = res.stderr.decode(errors='replace') if res.stderr else ""
        raise RuntimeError(f"ADB command failed ({' '.join(args)}): {err}")
    return res

def restore(backup_dir=None):
    adb = find_adb()
    project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    if not backup_dir:
        backups_pattern = os.path.join(project_root, "backups", "phone_backup_*")
        candidates = sorted(glob.glob(backups_pattern))
        if not candidates:
            raise RuntimeError(f"No backups found in {backups_pattern}")
        backup_dir = candidates[-1]

    backup_dir = os.path.abspath(backup_dir)
    tar_path = os.path.join(backup_dir, "deutschlern_backup.tar")
    if not os.path.exists(tar_path):
        raise RuntimeError(f"Tar file not found: {tar_path}")

    print(f"[1/5] Using backup: {backup_dir}")
    print(f"      Tar archive : {tar_path} ({os.path.getsize(tar_path):,} bytes)")

    # Check device
    res = run_adb(adb, ["devices"])
    lines = [line.strip() for line in res.stdout.decode().splitlines() if line.strip() and not line.startswith("*")]
    devices = [line.split()[0] for line in lines[1:] if "device" in line]
    if not devices:
        raise RuntimeError("No connected Android device found!")
    device_id = devices[0]
    print(f"[2/5] Connected device: {device_id}")

    # Verify package installed
    pkg_res = run_adb(adb, ["shell", "pm", "list", "packages", PACKAGE_NAME], check=False)
    if PACKAGE_NAME not in pkg_res.stdout.decode():
        raise RuntimeError(f"Package {PACKAGE_NAME} is not installed on device {device_id}!")

    # Stop app
    print(f"[3/5] Force-stopping {PACKAGE_NAME}...")
    run_adb(adb, ["shell", "am", "force-stop", PACKAGE_NAME], check=False)

    # Ensure target dirs exist and remove stale DB/WAL/SHM files
    print(f"[4/5] Preparing app storage and removing existing DB/WAL/SHM files...")
    run_adb(adb, ["shell", "run-as", PACKAGE_NAME, "mkdir", "-p", f"/data/data/{PACKAGE_NAME}/databases", f"/data/data/{PACKAGE_NAME}/files/datastore", f"/data/data/{PACKAGE_NAME}/shared_prefs"])
    run_adb(adb, ["shell", "run-as", PACKAGE_NAME, "rm", "-f",
                  f"/data/data/{PACKAGE_NAME}/databases/deutschlern.db",
                  f"/data/data/{PACKAGE_NAME}/databases/deutschlern.db-wal",
                  f"/data/data/{PACKAGE_NAME}/databases/deutschlern.db-shm"])

    # Stream tar archive into device
    print(f"[5/5] Extracting tar archive into /data/data/{PACKAGE_NAME}...")
    tar_cmd = [
        adb, "exec-in", "run-as", PACKAGE_NAME,
        "tar", "-xf", "-", "-C", f"/data/data/{PACKAGE_NAME}"
    ]
    with open(tar_path, "rb") as f:
        res = subprocess.run(tar_cmd, stdin=f, capture_output=True)
        if res.returncode != 0:
            err = res.stderr.decode(errors='replace') if res.stderr else ""
            raise RuntimeError(f"Failed to restore tar archive: {err}")

    # Set permissions
    run_adb(adb, ["shell", "run-as", PACKAGE_NAME, "chmod", "-R", "u+rwX", f"/data/data/{PACKAGE_NAME}/databases", f"/data/data/{PACKAGE_NAME}/files", f"/data/data/{PACKAGE_NAME}/shared_prefs"], check=False)

    print("\n" + "="*60)
    print("RESTORE COMPLETED SUCCESSFULLY!")
    print("="*60)
    print(f"Data restored from: {backup_dir}")
    print(f"To launch the app: adb shell monkey -p {PACKAGE_NAME} -c android.intent.category.LAUNCHER 1")
    print("="*60)

if __name__ == "__main__":
    bdir = sys.argv[1] if len(sys.argv) > 1 else None
    restore(bdir)
