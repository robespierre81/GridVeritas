#!/bin/sh
# Start the isolated GridVeritas stack
set -e
cd "$(dirname "$0")"
echo "Building and starting GridVeritas on network 'gridveritas-net'..."
docker compose up -d --build
echo ""
echo "Stack is up:"
echo "  UI:  http://localhost:3080"
echo "  API: http://localhost:18080/api/v1/sources"
echo ""
echo "Useful commands:"
echo "  docker compose ps"
echo "  docker compose logs -f"
echo "  docker compose down        # stop"
echo "  docker compose down -v     # stop + delete DB volume"
