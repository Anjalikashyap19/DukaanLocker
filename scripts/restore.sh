#!/bin/bash
# ──────────────────────────────────────────────────────────────────────────────
# DukaanLocker — Restore Script
# Restores PostgreSQL database + MinIO documents from a backup.
#
# Usage:
#   ./scripts/restore.sh <backup-date>
#   ./scripts/restore.sh 2026-09-21_0200
#   ./scripts/restore.sh latest
#
# This will:
#   1. Drop and recreate the dukaanlocker database
#   2. Import the SQL dump
#   3. Restore MinIO documents from the backup
#
# WARNING: This is DESTRUCTIVE — it overwrites the current database and documents.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ─── Resolve project root ────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ─── Load .env ───────────────────────────────────────────────────────────────
if [ -f "$PROJECT_ROOT/.env" ]; then
    set -a; source "$PROJECT_ROOT/.env"; set +a
else
    echo "ERROR: .env not found at $PROJECT_ROOT/.env" >&2
    exit 1
fi

# ─── Configuration ───────────────────────────────────────────────────────────
BACKUP_ROOT="${BACKUP_ROOT:-/opt/dukaanlocker/backups}"
BACKUP_DATE="${1:-}"

if [ -z "$BACKUP_DATE" ]; then
    echo "Usage: $0 <backup-date>"
    echo "       $0 latest"
    echo ""
    echo "Available backups:"
    echo "  PostgreSQL dumps:"
    ls -1 "$BACKUP_ROOT/postgres/"*.sql.gz 2>/dev/null | sed 's|.*/||' || echo "    (none)"
    echo "  MinIO document backups:"
    ls -1d "$BACKUP_ROOT/minio/documents_"* 2>/dev/null | sed 's|.*/||' || echo "    (none)"
    exit 1
fi

# ─── Resolve backup files ───────────────────────────────────────────────────
if [ "$BACKUP_DATE" = "latest" ]; then
    PG_DUMP=$(ls -t "$BACKUP_ROOT/postgres/"*.sql.gz 2>/dev/null | head -1)
    MINIO_BACKUP=$(ls -td "$BACKUP_ROOT/minio/documents_"* 2>/dev/null | head -1)
    if [ -z "$PG_DUMP" ] || [ -z "$MINIO_BACKUP" ]; then
        echo "ERROR: No backups found in $BACKUP_ROOT" >&2
        exit 1
    fi
    BACKUP_DATE="$(basename "$PG_DUMP" | sed 's/dukaanlocker_//; s/\.sql\.gz//')"
    echo "Latest backup found: $BACKUP_DATE"
else
    PG_DUMP="$BACKUP_ROOT/postgres/dukaanlocker_${BACKUP_DATE}.sql.gz"
    MINIO_BACKUP="$BACKUP_ROOT/minio/documents_${BACKUP_DATE}"
fi

# ─── Validate backup exists ─────────────────────────────────────────────────
echo ""
echo "========================================"
echo "  DukaanLocker Restore — $BACKUP_DATE"
echo "========================================"
echo ""

if [ ! -f "$PG_DUMP" ]; then
    echo "ERROR: PostgreSQL dump not found: $PG_DUMP" >&2
    exit 1
fi

if [ ! -d "$MINIO_BACKUP" ]; then
    echo "WARNING: MinIO backup directory not found: $MINIO_BACKUP"
    echo "  Proceeding with PostgreSQL restore only."
    MINIO_BACKUP=""
fi

# ─── Confirmation prompt ────────────────────────────────────────────────────
echo "WARNING: This will OVERWRITE the current database and documents!"
echo "  Database: dukaanlocker"
echo "  PostgreSQL dump: $(basename "$PG_DUMP")"
if [ -n "$MINIO_BACKUP" ]; then
    MINIO_COUNT=$(find "$MINIO_BACKUP" -type f | wc -l)
    echo "  MinIO files: $MINIO_COUNT files"
fi
echo ""
read -p "Are you sure? (yes/no): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
    echo "Restore cancelled."
    exit 0
fi

# ─── Step 1: Restore PostgreSQL ──────────────────────────────────────────────
echo ""
echo "[1/2] Restoring PostgreSQL database..."

# Drop and recreate the database
docker exec dukaanlocker-postgres psql -U dukaanlocker -d postgres -c \
    "DROP DATABASE IF EXISTS dukaanlocker;" >/dev/null 2>&1
docker exec dukaanlocker-postgres psql -U dukaanlocker -d postgres -c \
    "CREATE DATABASE dukaanlocker OWNER dukaanlocker;" >/dev/null 2>&1

# Import the dump
if gunzip -c "$PG_DUMP" | docker exec -i dukaanlocker-postgres psql -U dukaanlocker -d dukaanlocker >/dev/null 2>&1; then
    echo "  ✓ PostgreSQL database restored from $(basename "$PG_DUMP")"
else
    echo "  ✗ PostgreSQL restore FAILED" >&2
    exit 1
fi

# ─── Step 2: Restore MinIO Documents ────────────────────────────────────────
if [ -n "$MINIO_BACKUP" ]; then
    echo ""
    echo "[2/2] Restoring MinIO documents..."

    # Copy from host backup dir to container's /backups (volume mount)
    # The backup dir is already accessible via the volume mount at /backups
    # We just need to mirror it into the bucket
    if docker exec dukaanlocker-minio mc alias set local http://localhost:9000 minioadmin minioadmin >/dev/null 2>&1 \
       && docker exec dukaanlocker-minio mc mirror --overwrite "/backups/$(basename "$MINIO_BACKUP")/" "local/dukaanlocker-documents/" >/dev/null 2>&1; then
        echo "  ✓ MinIO documents restored ($MINIO_COUNT files)"
    else
        echo "  ✗ MinIO restore FAILED" >&2
        exit 1
    fi
else
    echo ""
    echo "[2/2] Skipping MinIO restore (no backup found)"
fi

# ─── Summary ─────────────────────────────────────────────────────────────────
echo ""
echo "========================================"
echo "  Restore Complete"
echo "  Database: dukaanlocker (restored)"
if [ -n "$MINIO_BACKUP" ]; then
    echo "  MinIO: $MINIO_COUNT files (restored)"
fi
echo ""
echo "  You may need to restart the Spring Boot app:"
echo "    docker restart dukaanlocker-app"
echo "========================================"
