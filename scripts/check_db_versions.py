import sqlite3
import os

def check_db(path):
    print("Checking:", path)
    if not os.path.exists(path):
        print("  File not found!")
        return
    con = sqlite3.connect(path)
    cur = con.cursor()
    uv = cur.execute("PRAGMA user_version;").fetchone()[0]
    print("  user_version:", uv)
    cur.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = [r[0] for r in cur.fetchall()]
    print("  tables count:", len(tables))
    for t in tables:
        if not t.startswith("sqlite_"):
            cnt = cur.execute(f"SELECT count(*) FROM {t}").fetchone()[0]
            print(f"    {t}: {cnt}")
    con.close()

if __name__ == "__main__":
    check_db("backup_db/deutschlern.db")
    print("-" * 40)
    check_db("backups/phone_backup_20261015_225730/deutschlern_consolidated.db")
