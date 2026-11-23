#!/bin/sh
# Nightly (or every few hours) copy of the database into a private bucket, kept for RETENTION_DAYS days.
# Needs: DATABASE_URL, BACKUP_BUCKET, BACKUP_S3_ENDPOINT, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY.
# Optional: RETENTION_DAYS (14), BACKUP_PREFIX (db), MIN_BYTES (100000: a smaller file is treated as a failed dump).
# Never prints DATABASE_URL. Exits non-zero on any failure so the scheduler shows it as failed.
set -eu

: "${DATABASE_URL:?DATABASE_URL is not set}"
: "${BACKUP_BUCKET:?BACKUP_BUCKET is not set}"
: "${BACKUP_S3_ENDPOINT:?BACKUP_S3_ENDPOINT is not set}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
BACKUP_PREFIX="${BACKUP_PREFIX:-db}"
MIN_BYTES="${MIN_BYTES:-100000}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-auto}"

stamp=$(date -u +%Y-%m-%dT%H-%M-%SZ)
file="/tmp/wrappzo-$stamp.dump"
key="$BACKUP_PREFIX/wrappzo-$stamp.dump"
s3="aws s3 --endpoint-url $BACKUP_S3_ENDPOINT"
api="aws s3api --endpoint-url $BACKUP_S3_ENDPOINT"

echo "dumping..."
pg_dump --format=custom --no-owner --no-privileges --file="$file" "$DATABASE_URL"

bytes=$(wc -c < "$file")
echo "dump size: $bytes bytes"
[ "$bytes" -ge "$MIN_BYTES" ] || { echo "ERROR: dump is smaller than $MIN_BYTES bytes, not uploading" >&2; exit 1; }
# A readable table of contents proves the file is a complete archive, not a cut-off one.
pg_restore --list "$file" > /dev/null || { echo "ERROR: dump is not a readable archive" >&2; exit 1; }

echo "uploading to $key..."
$s3 cp "$file" "s3://$BACKUP_BUCKET/$key" --only-show-errors
$api head-object --bucket "$BACKUP_BUCKET" --key "$key" > /dev/null
rm -f "$file"

echo "removing backups older than $RETENTION_DAYS days..."
cutoff=$(date -u -d "@$(( $(date +%s) - RETENTION_DAYS * 86400 ))" +%Y-%m-%dT%H:%M:%SZ)
$api list-objects-v2 --bucket "$BACKUP_BUCKET" --prefix "$BACKUP_PREFIX/" \
  --query "Contents[?LastModified<='$cutoff'].Key" --output text | tr '\t' '\n' | while read -r old; do
  [ -n "$old" ] && [ "$old" != "None" ] && { echo "  deleting $old"; $api delete-object --bucket "$BACKUP_BUCKET" --key "$old" > /dev/null; }
done
echo "backup done: $key"
