#!/bin/bash
# ──────────────────────────────────────────────────────────────────────────────
# DukaanLocker — Daily Backup Script
# Backs up PostgreSQL database + MinIO documents to local disk and external S3.
#
# Usage:
#   ./scripts/backup.sh              # full backup (local + external upload)
#   ./scripts/backup.sh --local-only # skip external S3 upload (for dev/testing)
#
# Crontab entry (runs daily at 2:00 AM IST):
#   0 2 * * * /opt/dukaanlocker/scripts/backup.sh >> /var/log/dukaanlocker-backup.log 2>&1
#
# Environment variables (from .env):
#   BACKUP_S3_ENDPOINT    — external S3/MinIO endpoint URL
#   BACKUP_S3_ACCESS_KEY  — external S3 access key
#   BACKUP_S3_SECRET_KEY  — external S3 secret key
#   BACKUP_S3_BUCKET      — external S3 bucket name
#   BACKUP_S3_REGION      — external S3 region (default: us-east-1)
#   BACKUP_RETENTION_DAYS — local backup retention (default: 7)
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
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
DATE="$(date +%Y-%m-%d_%H%M)"
LOCAL_ONLY=false

if [[ "${1:-}" == "--local-only" ]]; then
    LOCAL_ONLY=true
    echo "[backup] Running in --local-only mode (skipping external S3 upload)"
fi

# ─── Create backup directories ───────────────────────────────────────────────
POSTGRES_DIR="$BACKUP_ROOT/postgres"
MINIO_DIR="$BACKUP_ROOT/minio/documents_${DATE}"
mkdir -p "$POSTGRES_DIR" "$MINIO_DIR"

echo "========================================"
echo "  DukaanLocker Backup — $DATE"
echo "========================================"

# ─── Step 1: PostgreSQL Backup ──────────────────────────────────────────────
echo ""
echo "[1/4] Backing up PostgreSQL database..."

PG_DUMP_FILE="$POSTGRES_DIR/dukaanlocker_${DATE}.sql.gz"

if docker exec dukaanlocker-postgres pg_dump -U dukaanlocker -d dukaanlocker --no-owner --no-acl \
    | gzip > "$PG_DUMP_FILE"; then
    PG_SIZE=$(du -h "$PG_DUMP_FILE" | cut -f1)
    echo "  ✓ PostgreSQL dump saved: $PG_DUMP_FILE ($PG_SIZE)"
else
    echo "  ✗ PostgreSQL dump FAILED" >&2
    exit 1
fi

# ─── Step 2: MinIO Documents Backup ─────────────────────────────────────────
echo ""
echo "[2/4] Backing up MinIO documents..."

# Use mc inside the MinIO container to mirror the bucket to /backups
# (which is mounted to the host at /opt/dukaanlocker/backups/minio)
if docker exec dukaanlocker-minio mc alias set local http://localhost:9000 minioadmin minioadmin >/dev/null 2>&1 \
   && docker exec dukaanlocker-minio mc mirror --overwrite local/dukaanlocker-documents "/backups/documents_${DATE}" >/dev/null 2>&1; then
    # Files are written directly to host via volume mount
    MINIO_COUNT=$(find "$MINIO_DIR" -type f 2>/dev/null | wc -l)
    MINIO_SIZE=$(du -sh "$MINIO_DIR" 2>/dev/null | cut -f1)
    echo "  ✓ MinIO documents saved: $MINIO_DIR ($MINIO_COUNT files, $MINIO_SIZE)"
else
    echo "  ✗ MinIO mirror FAILED" >&2
    exit 1
fi

# ─── Step 3: Upload to External S3 ──────────────────────────────────────────
if [ "$LOCAL_ONLY" = false ]; then
    echo ""
    echo "[3/4] Uploading to external S3..."

    if [ -z "${BACKUP_S3_ENDPOINT:-}" ] || [ -z "${BACKUP_S3_ACCESS_KEY:-}" ] || [ -z "${BACKUP_S3_SECRET_KEY:-}" ] || [ -z "${BACKUP_S3_BUCKET:-}" ]; then
        echo "  ⚠ External S3 not configured (missing BACKUP_S3_* env vars). Skipping upload."
        echo "  Set BACKUP_S3_ENDPOINT, BACKUP_S3_ACCESS_KEY, BACKUP_S3_SECRET_KEY, BACKUP_S3_BUCKET in .env"
    else
        # Configure mc alias for remote S3
        REMOTE_ALIAS="remote-backups"
        mc alias set "$REMOTE_ALIAS" \
            "$BACKUP_S3_ENDPOINT" \
            "$BACKUP_S3_ACCESS_KEY" \
            "$BACKUP_S3_SECRET_KEY" \
            --api S3v4 >/dev/null 2>&1 || true

        REMOTE_PREFIX="$REMOTE_ALIAS/$BACKUP_S3_BUCKET/dukaanlocker"

        # Upload PostgreSQL dump
        echo "  Uploading PostgreSQL dump..."
        if mc cp "$PG_DUMP_FILE" "$REMOTE_PREFIX/postgres/dukaanlocker_${DATE}.sql.gz"; then
            echo "  ✓ PostgreSQL dump uploaded"
        else
            echo "  ✗ PostgreSQL upload FAILED" >&2
        fi

        # Upload MinIO documents
        echo "  Uploading MinIO documents..."
        if mc mirror --overwrite "$MINIO_DIR" "$REMOTE_PREFIX/minio/documents_${DATE}/"; then
            echo "  ✓ MinIO documents uploaded"
        else
            echo "  ✗ MinIO upload FAILED" >&2
        fi
    fi
else
    echo ""
    echo "[3/4] Skipping external S3 upload (--local-only mode)"
fi

# ─── Step 4: Rotate Old Backups ─────────────────────────────────────────────
echo ""
echo "[4/4] Rotating old local backups (keeping last $RETENTION_DAYS days)..."

DELETED_COUNT=0
if [ -d "$POSTGRES_DIR" ]; then
    while IFS= read -r -d '' old_file; do
        rm -f "$old_file"
        DELETED_COUNT=$((DELETED_COUNT + 1))
    done < <(find "$POSTGRES_DIR" -type f -name "*.sql.gz" -mtime +"$RETENTION_DAYS" -print0)
fi

if [ -d "$BACKUP_ROOT/minio" ]; then
    while IFS= read -r -d '' old_dir; do
        rm -rf "$old_dir"
        DELETED_COUNT=$((DELETED_COUNT + 1))
    done < <(find "$BACKUP_ROOT/minio" -maxdepth 1 -type d -name "documents_*" -mtime +"$RETENTION_DAYS" -print0)
fi

echo "  ✓ Removed $DELETED_COUNT old backup(s)"

# ─── Summary ─────────────────────────────────────────────────────────────────
echo ""
echo "========================================"
echo "  Backup Complete"
echo "  Location: $BACKUP_ROOT"
echo "  PostgreSQL: $PG_DUMP_FILE ($PG_SIZE)"
echo "  MinIO: $MINIO_DIR ($MINIO_COUNT files)"
echo "  Retention: $RETENTION_DAYS days"
echo "========================================"
