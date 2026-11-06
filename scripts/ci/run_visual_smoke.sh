#!/usr/bin/env bash
set -euo pipefail

/usr/bin/python3 scripts/ci/minecraft_visual_smoke.py "$@"
