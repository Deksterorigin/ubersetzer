import sqlite3
import os
import shutil

def inspect_dialogs():
    for db_path in ['backup_db/deutschlern.db', 'backups/phone_backup_20261015_225730/deutschlern_consolidated.db']:
        print(f"=== {db_path} ===")
        if not os.path.exists(db_path):
            print("  Not found!")
            continue
        con = sqlite3.connect(db_path)
        cur = con.cursor()
        uv = cur.execute("PRAGMA user_version;").fetchone()[0]
        print(f"  user_version: {uv}")
        schema = cur.execute("SELECT sql FROM sqlite_master WHERE name='dialogs';").fetchone()
        if schema:
            print(f"  dialogs schema:\n{schema[0]}")
        else:
            print("  dialogs table not found!")
        
        # Check rows in dialogs if exists
        try:
            cnt = cur.execute("SELECT count(*) FROM dialogs;").fetchone()[0]
            print(f"  dialogs count: {cnt}")
        except Exception as e:
            print(f"  dialogs select error: {e}")
        con.close()

if __name__ == "__main__":
    inspect_dialogs()
