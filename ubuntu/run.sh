#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
lab_dir="$(cd -- "$script_dir/.." && pwd)"
jar="$lab_dir/source/SweetHome3D/install/SweetHome3D-5.4.jar"

if [[ ! -f "$jar" ]]; then
  echo "Build output not found. Run $script_dir/build.sh first." >&2
  exit 1
fi

exec java -jar "$jar" "$@"

