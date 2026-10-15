import os
import shutil
import sqlite3
import re
import subprocess

def extract_migration_sql():
    cmd = ["git", "show", "4011ac2:app/src/main/java/app/deutschlern/data/local/DatabaseMigrations.kt"]
    res = subprocess.run(cmd, capture_output=True, check=True)
    content = res.stdout.decode('utf-8', errors='replace')
    match = re.search(r'MIGRATION_7_8.*?db\.execSQL\(\s*"(.*?)"\s*\)', content, re.DOTALL)
    if not match:
        raise RuntimeError("Failed to extract MIGRATION_7_8 SQL!")
    return match.group(1)

def run_phone_migration_check():
    migration_sql = extract_migration_sql()
    print(f"Extracted Migration SQL: {migration_sql}")
    
    phone_db_src = os.path.abspath("backups/phone_backup_20261015_225730/deutschlern_consolidated.db")
    target_dir = os.path.abspath("backups/migration_verification")
    os.makedirs(target_dir, exist_ok=True)
    
    # Target copy
    copy_db = os.path.join(target_dir, "phone_backup_migrated.db")
    shutil.copyfile(phone_db_src, copy_db)
    
    con = sqlite3.connect(copy_db)
    cur = con.cursor()
    cur.execute("PRAGMA foreign_keys = ON;")
    
    # Because phone DB was already bumped to 8 by earlier dialogs build,
    # bring dialogs table schema back to version 7 in this copy to test the 7->8 migration properly
    cur.execute("PRAGMA foreign_keys = OFF;")
    cur.execute("""
        CREATE TABLE dialogs_v7 (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `title` TEXT NOT NULL,
            `startedAt` INTEGER NOT NULL,
            `durationMs` INTEGER NOT NULL,
            `localeTag` TEXT NOT NULL,
            `segmentCount` INTEGER NOT NULL,
            `summaryJson` TEXT,
            `summaryProviderModel` TEXT,
            `summaryUpdatedAt` INTEGER
        );
    """)
    cur.execute("""
        INSERT INTO dialogs_v7 (id, title, startedAt, durationMs, localeTag, segmentCount, summaryJson, summaryProviderModel, summaryUpdatedAt)
        SELECT id, title, startedAt, durationMs, localeTag, segmentCount, summaryJson, summaryProviderModel, summaryUpdatedAt FROM dialogs;
    """)
    cur.execute("DROP TABLE dialogs;")
    cur.execute("ALTER TABLE dialogs_v7 RENAME TO dialogs;")
    cur.execute("PRAGMA user_version = 7;")
    cur.execute("PRAGMA foreign_keys = ON;")
    con.commit()
    
    # Pre-migration metrics on phone copy
    uv_before = cur.execute("PRAGMA user_version;").fetchone()[0]
    integrity_before = cur.execute("PRAGMA integrity_check;").fetchall()
    fk_before = cur.execute("PRAGMA foreign_key_check;").fetchall()
    
    tables = [r[0] for r in cur.execute("SELECT name FROM sqlite_master WHERE type='table';").fetchall() if not r[0].startswith("sqlite_")]
    tables.sort()
    
    counts_before = {t: cur.execute(f"SELECT count(*) FROM {t};").fetchone()[0] for t in tables}
    
    # Run Migration 7 -> 8
    cur.execute(migration_sql)
    cur.execute("PRAGMA user_version = 8;")
    con.commit()
    
    uv_after = cur.execute("PRAGMA user_version;").fetchone()[0]
    integrity_after = cur.execute("PRAGMA integrity_check;").fetchall()
    fk_after = cur.execute("PRAGMA foreign_key_check;").fetchall()
    counts_after = {t: cur.execute(f"SELECT count(*) FROM {t};").fetchone()[0] for t in tables}
    
    con.close()
    
    # Also test on a copy with pre-existing dialogs
    pop_copy_db = os.path.join(target_dir, "phone_backup_populated_migrated.db")
    shutil.copyfile(copy_db, pop_copy_db)
    con2 = sqlite3.connect(pop_copy_db)
    cur2 = con2.cursor()
    # Re-revert to v7 and insert 3 dialogs
    cur2.execute("PRAGMA foreign_keys = OFF;")
    cur2.execute("""
        CREATE TABLE dialogs_v7 (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `title` TEXT NOT NULL,
            `startedAt` INTEGER NOT NULL,
            `durationMs` INTEGER NOT NULL,
            `localeTag` TEXT NOT NULL,
            `segmentCount` INTEGER NOT NULL,
            `summaryJson` TEXT,
            `summaryProviderModel` TEXT,
            `summaryUpdatedAt` INTEGER
        );
    """)
    cur2.execute("DROP TABLE dialogs;")
    cur2.execute("ALTER TABLE dialogs_v7 RENAME TO dialogs;")
    cur2.execute("""
        INSERT INTO dialogs (title, startedAt, durationMs, localeTag, segmentCount, summaryJson, summaryProviderModel, summaryUpdatedAt)
        VALUES 
        ('Café Gespräch', 1700000000000, 120000, 'de-DE', 5, '{"summary":"Test 1"}', 'gemini', 1700000120000),
        ('Am Bahnhof', 1700000500000, 180000, 'de-DE', 8, '{"summary":"Test 2"}', 'gemini', 1700000680000),
        ('Beim Arzt', 1700001000000, 90000, 'de-DE', 4, null, null, null);
    """)
    cur2.execute("PRAGMA user_version = 7;")
    cur2.execute("PRAGMA foreign_keys = ON;")
    con2.commit()
    
    # Run migration
    cur2.execute(migration_sql)
    cur2.execute("PRAGMA user_version = 8;")
    con2.commit()
    
    status_distribution = cur2.execute("SELECT status, count(*) FROM dialogs GROUP BY status;").fetchall()
    integrity_pop = cur2.execute("PRAGMA integrity_check;").fetchall()
    fk_pop = cur2.execute("PRAGMA foreign_key_check;").fetchall()
    con2.close()
    
    print("\n" + "="*70)
    print("MIGRATION CHECK REPORT (7 -> 8) ON PHONE BACKUP DATA")
    print("="*70)
    print(f"Target DB Path (phone copy) : {copy_db}")
    print(f"user_version before         : {uv_before}")
    print(f"user_version after          : {uv_after}")
    print(f"PRAGMA integrity_check      : {integrity_after[0][0]}")
    print(f"PRAGMA foreign_key_check    : {'PASSED (0 violations)' if len(fk_after) == 0 else fk_after}")
    print("\nTable row counts (before -> after):")
    for t in tables:
        print(f"  {t:<25}: {counts_before[t]:>4} -> {counts_after[t]:>4} (diff: {counts_after[t] - counts_before[t]})")
        
    print("\nPopulated dialogs.status distribution:")
    for status, count in status_distribution:
        print(f"  status='{status}': {count}")
    print(f"Populated integrity_check   : {integrity_pop[0][0]}")
    print(f"Populated foreign_key_check : {'PASSED (0 violations)' if len(fk_pop) == 0 else fk_pop}")
    print("="*70)

if __name__ == "__main__":
    run_phone_migration_check()
