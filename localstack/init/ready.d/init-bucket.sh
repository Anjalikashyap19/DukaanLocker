#!/bin/bash
# Runs once LocalStack services are ready. Creates the dev S3 bucket if missing.
awslocal s3 mb s3://dukaanlocker-documents-local 2>/dev/null || true
echo "LocalStack bucket dukaanlocker-documents-local ready"
