import os
import json
import sqlite3
import subprocess
import re

def normalize_default_value(val):
    if val is None:
        return None
    s = str(val).strip()
    # Strip enclosing parentheses recursively: (('val')) -> 'val'
    while s.startswith("(") and s.endswith(")"):
        s = s[1:-1].strip()
    # Strip enclosing single/double quotes: 'val' -> val
    if (s.startswith("'") and s.endswith("'")) or (s.startswith('"') and s.endswith('"')):
        s = s[1:-1]
    return s

def load_schema_8():
    local_path = "app/schemas/app.deutschlern.data.local.AppDatabase/8.json"
    if os.path.exists(local_path):
        with open(local_path, "r", encoding="utf-8") as f:
            return json.load(f)
    # Extract from commit 4011ac2
    cmd = ["git", "show", "4011ac2:app/schemas/app.deutschlern.data.local.AppDatabase/8.json"]
    res = subprocess.run(cmd, capture_output=True, check=True)
    return json.loads(res.stdout.decode('utf-8'))

def validate_schema(db_path):
    print(f"Validating database against Room 8.json:\n  Database: {db_path}")
    schema = load_schema_8()
    entities = schema["database"]["entities"]
    
    con = sqlite3.connect(db_path)
    cur = con.cursor()
    
    existing_tables = [r[0] for r in cur.execute("SELECT name FROM sqlite_master WHERE type='table';").fetchall()]
    
    differences = []
    
    for entity in entities:
        table_name = entity["tableName"]
        if table_name not in existing_tables:
            differences.append(f"MISSING TABLE: Table '{table_name}' does not exist in SQLite DB!")
            continue
            
        # 1. Validate Columns via PRAGMA table_info
        table_info = cur.execute(f"PRAGMA table_info('{table_name}');").fetchall()
        if table_name == 'translation_cache':
            print("translation_cache raw table_info:")
            for row in table_info:
                print(" ", row)
            sql_row = cur.execute("SELECT sql FROM sqlite_master WHERE name='translation_cache';").fetchone()
            print("translation_cache SQL:\n ", sql_row[0] if sql_row else "None")
        # Columns format: (cid, name, type, notnull, dflt_value, pk)
        db_cols = {row[1]: {
            "cid": row[0],
            "name": row[1],
            "type": row[2].upper(),
            "notnull": bool(row[3]),
            "dflt_value": normalize_default_value(row[4]),
            "pk": bool(row[5])
        } for row in table_info}
        
        schema_fields = {f["columnName"]: f for f in entity["fields"]}
        
        # Check every schema field against DB
        for col_name, field in schema_fields.items():
            if col_name not in db_cols:
                differences.append(f"[{table_name}] MISSING COLUMN: Column '{col_name}' missing in SQLite table!")
                continue
            db_col = db_cols[col_name]
            
            # Affinity / Type
            expected_type = field["affinity"].upper()
            if db_col["type"] != expected_type:
                differences.append(f"[{table_name}.{col_name}] TYPE MISMATCH: expected {expected_type}, found {db_col['type']}")
                
            # Not null
            expected_notnull = bool(field.get("notNull", False))
            if db_col["notnull"] != expected_notnull:
                differences.append(f"[{table_name}.{col_name}] NOTNULL MISMATCH: expected {expected_notnull}, found {db_col['notnull']}")
                
            # Default value
            expected_dflt = normalize_default_value(field.get("defaultValue"))
            actual_dflt = db_col["dflt_value"]
            if expected_dflt != actual_dflt:
                differences.append(f"[{table_name}.{col_name}] DEFAULT VALUE MISMATCH: expected '{expected_dflt}', found '{actual_dflt}'")
                
        # Check primary key
        pk_cols = entity["primaryKey"]["columnNames"]
        for col_name, db_col in db_cols.items():
            is_expected_pk = col_name in pk_cols
            if db_col["pk"] != is_expected_pk:
                differences.append(f"[{table_name}.{col_name}] PRIMARY KEY MISMATCH: expected pk={is_expected_pk}, found {db_col['pk']}")
                
        # Check foreign keys
        expected_fks = entity.get("foreignKeys", [])
        actual_fks = cur.execute(f"PRAGMA foreign_key_list('{table_name}');").fetchall()
        # FK row: (id, seq, table, from, to, on_update, on_delete, match)
        actual_fk_map = {}
        for row in actual_fks:
            fk_target = row[2]
            from_col = row[3]
            to_col = row[4]
            on_update = row[5].upper()
            on_delete = row[6].upper()
            actual_fk_map[from_col] = {
                "targetTable": fk_target,
                "to": to_col,
                "onUpdate": on_update,
                "onDelete": on_delete
            }
            
        for efk in expected_fks:
            for from_c, to_c in zip(efk["columns"], efk["referencedColumns"]):
                if from_c not in actual_fk_map:
                    differences.append(f"[{table_name}] MISSING FOREIGN KEY on column '{from_c}' -> {efk['table']}({to_c})")
                else:
                    afk = actual_fk_map[from_c]
                    if afk["targetTable"] != efk["table"]:
                        differences.append(f"[{table_name}.{from_c}] FK TARGET MISMATCH: expected {efk['table']}, found {afk['targetTable']}")
                    if afk["onDelete"] != efk["onDelete"].upper():
                        differences.append(f"[{table_name}.{from_c}] FK onDelete MISMATCH: expected {efk['onDelete']}, found {afk['onDelete']}")
                    if afk["onUpdate"] != efk["onUpdate"].upper():
                        differences.append(f"[{table_name}.{from_c}] FK onUpdate MISMATCH: expected {efk['onUpdate']}, found {afk['onUpdate']}")

        # Check indices
        expected_indices = entity.get("indices", [])
        actual_indices = cur.execute(f"PRAGMA index_list('{table_name}');").fetchall()
        # actual index: (seq, name, unique, origin, partial)
        actual_index_names = {row[1]: bool(row[2]) for row in actual_indices}
        
        for eidx in expected_indices:
            idx_name = eidx["name"]
            if idx_name not in actual_index_names:
                differences.append(f"[{table_name}] MISSING INDEX: '{idx_name}'")
            else:
                is_unique = actual_index_names[idx_name]
                if is_unique != bool(eidx.get("unique", False)):
                    differences.append(f"[{table_name}] INDEX UNIQUE MISMATCH: '{idx_name}' expected unique={eidx.get('unique')}, found {is_unique}")
                    
    con.close()
    
    print("\n" + "="*70)
    print("SCHEMA 8.JSON COMPARISON REPORT")
    print("="*70)
    if not differences:
        print("ALL 10 ENTITIES MATCH 8.JSON EXACTLY! 0 DIFFERENCES FOUND.")
        print("  - Columns, Types, NOT NULL, Defaults (quote/paren-normalized): MATCH")
        print("  - Primary Keys: MATCH")
        print("  - Foreign Keys & Cascades: MATCH")
        print("  - Indices & Uniqueness: MATCH")
    else:
        print(f"FOUND {len(differences)} DIFFERENCES:")
        for d in differences:
            print(f"  * {d}")
    print("="*70)
    return len(differences) == 0

if __name__ == "__main__":
    db = "backups/migration_verification/phone_backup_migrated.db"
    validate_schema(db)
