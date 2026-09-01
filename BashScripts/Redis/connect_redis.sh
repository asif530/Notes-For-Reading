#!/bin/bash
# =====================================================
# Script to connect to a Redis server using redis-cli
# =====================================================

# Default values (you can change them)
REDIS_HOST="192.168.169.74"
REDIS_PORT=6379
REDIS_PASSWORD="sxbab36de6d04e2e67b0e"

# 🔍 Check if redis-cli (from redis-tools) is installed
if ! command -v redis-cli >/dev/null 2>&1; then
    echo "❌ redis-cli not found."
    echo "👉 Install redis-tools with: sudo apt install redis-tools"
    exit 1
fi

# Parse command-line arguments (optional overrides)
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h|--host) REDIS_HOST="$2"; shift ;;
        -p|--port) REDIS_PORT="$2"; shift ;;
        -a|--password) REDIS_PASSWORD="$2"; shift ;;
        *) echo "Unknown parameter passed: $1"; exit 1 ;;
    esac
    shift
done

echo "Connecting to Redis server at ${REDIS_HOST}:${REDIS_PORT}..."

# Build redis-cli command
if [ -n "$REDIS_PASSWORD" ]; then
    CMD="redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} -a ${REDIS_PASSWORD}"
else
    CMD="redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT}"
fi

# Test connection
PING_RESULT=$($CMD ping 2>/dev/null)

if [ "$PING_RESULT" == "PONG" ]; then
    echo "✅ Connected successfully!"
    echo "Opening Redis CLI..."
    $CMD
else
    echo "❌ Failed to connect to Redis at ${REDIS_HOST}:${REDIS_PORT}"
    echo "Please verify host, port, and password."
    exit 1
fi
