import os
import shutil
import sqlite3
import re

def extract_migration_sql():
    import subprocess
    cmd = ["git", "show", "4011ac2:app/src/main/java/app/deutschlern/data/local/DatabaseMigrations.kt"]
    res = subprocess.run(cmd, capture_output=True, check=True)
    content = res.stdout.decode('utf-8', errors='replace')
    
    # Extract db.execSQL inside MIGRATION_7_8
    match = re.search(r'MIGRATION_7_8.*?db\.execSQL\(\s*"(.*?)"\s*\)', content, re.DOTALL)
    if not match:
        raise RuntimeError("Failed to extract MIGRATION_7_8 SQL from DatabaseMigrations.kt!")
    sql = match.group(1)
    print(f"Extracted MIGRATION_7_8 SQL from DatabaseMigrations.kt:\n  -> {sql}")
    return sql

def run_check():
    migration_sql = extract_migration_sql()
    
    source_v7_db = os.path.abspath("backup_db/deutschlern.db")
    target_dir = os.path.abspath("backups/migration_verification")
    os.makedirs(target_dir, exist_ok=True)
    
    copy_db = os.path.join(target_dir, "deutschlern_v7_migrated.db")
    shutil.copyfile(source_v7_db, copy_db)
    
    con = sqlite3.connect(copy_db)
    cur = con.cursor()
    
    # Enable foreign keys
    cur.execute("PRAGMA foreign_keys = ON;")
    
    # Pre-migration metrics
    uv_before = cur.execute("PRAGMA user_version;").fetchone()[0]
    integrity_before = cur.execute("PRAGMA integrity_check;").fetchall()
    fk_before = cur.execute("PRAGMA foreign_key_check;").fetchall()
    
    tables = [r[0] for r in cur.execute("SELECT name FROM sqlite_master WHERE type='table';").fetchall() if not r[0].startswith("sqlite_")]
    tables.sort()
    
    counts_before = {}
    for t in tables:
        counts_before[t] = cur.execute(f"SELECT count(*) FROM {t};").fetchone()[0]
        
    # To test dialogs.status distribution realistically, let's also insert 3 sample dialogs in a second copy
    # But first, let's test copy_db as the exact mirror of the backup
    print("\n--- Running migration on exact replica of backup_db/deutschlern.db ---")
    cur.execute(migration_sql)
    cur.execute("PRAGMA user_version = 8;")
    con.commit()
    
    uv_after = cur.execute("PRAGMA user_version;").fetchone()[0]
    integrity_after = cur.execute("PRAGMA integrity_check;").fetchall()
    fk_after = cur.execute("PRAGMA foreign_key_check;").fetchall()
    
    counts_after = {}
    for t in tables:
        counts_after[t] = cur.execute(f"SELECT count(*) FROM {t};").fetchone()[0]
        
    con.close()
    
    # Now run on a populated copy to check dialogs.status distribution
    populated_copy_db = os.path.join(target_dir, "deutschlern_v7_populated_migrated.db")
    shutil.copyfile(source_v7_db, populated_copy_db)
    con2 = sqlite3.connect(populated_copy_db)
    cur2 = con2.cursor()
    cur2.execute("PRAGMA foreign_keys = ON;")
    
    # Insert 3 sample dialogs prior to migration
    cur2.execute("""
        INSERT INTO dialogs (title, startedAt, durationMs, localeTag, segmentCount, summaryJson, summaryProviderModel, summaryUpdatedAt)
        VALUES 
        ('Café Gespräch', 1700000000000, 120000, 'de-DE', 5, '{"summary":"Test 1"}', 'gemini', 1700000120000),
        ('Am Bahnhof', 1700000500000, 180000, 'de-DE', 8, '{"summary":"Test 2"}', 'gemini', 1700000680000),
        ('Beim Arzt', 1700001000000, 90000, 'de-DE', 4, null, null, null);
    """)
    con2.commit()
    
    counts_populated_before = {}
    for t in tables:
        counts_populated_before[t] = cur2.execute(f"SELECT count(*) FROM {t};").fetchone()[0]
        
    # Execute migration on populated copy
    cur2.execute(migration_sql)
    cur2.execute("PRAGMA user_version = 8;")
    con2.commit()
    
    integrity_pop_after = cur2.execute("PRAGMA integrity_check;").fetchall()
    fk_pop_after = cur2.execute("PRAGMA foreign_key_check;").fetchall()
    
    counts_populated_after = {}
    for t in tables:
        counts_populated_after[t] = cur2.execute(f"SELECT count(*) FROM {t};").fetchone()[0]
        
    status_distribution = cur2.execute("SELECT status, count(*) FROM dialogs GROUP BY status;").fetchall()
    con2.close()
    
    print("\n" + "="*70)
    print("MIGRATION CHECK REPORT (7 -> 8) ON LOCAL DB COPY")
    print("="*70)
    print(f"Target DB Path (as-is copy)     : {copy_db}")
    print(f"Target DB Path (populated copy) : {populated_copy_db}")
    print(f"user_version before             : {uv_before}")
    print(f"user_version after              : {uv_after}")
    print(f"PRAGMA integrity_check          : {integrity_after[0][0]}")
    print(f"PRAGMA foreign_key_check        : {'PASSED (0 violations)' if len(fk_after) == 0 else fk_after}")
    print("\nTable row counts (before -> after):")
    for t in tables:
        print(f"  {t:<25}: {counts_before[t]:>4} -> {counts_after[t]:>4} (diff: {counts_after[t] - counts_before[t]})")
        
    print("\nPopulated copy dialogs.status distribution:")
    print(f"  Total dialogs before: {counts_populated_before['dialogs']}")
    print(f"  Total dialogs after : {counts_populated_after['dialogs']}")
    for status, count in status_distribution:
        print(f"  status='{status}': {count}")
    print("="*70)

if __name__ == "__main__":
    run_check()
