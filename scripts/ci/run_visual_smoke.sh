#!/usr/bin/env bash
set -euo pipefail

openbox --sm-disable >/tmp/vantaloader-openbox.log 2>&1 &
window_manager_pid=$!
trap 'kill "$window_manager_pid" 2>/dev/null || true' EXIT
sleep 2

/usr/bin/python3 scripts/ci/minecraft_visual_smoke.py "$@"
