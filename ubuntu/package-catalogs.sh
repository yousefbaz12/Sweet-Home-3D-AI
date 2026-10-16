#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
lab_dir="$(cd -- "$script_dir/.." && pwd)"
dist_dir="$lab_dir/dist"

command -v zip >/dev/null || { echo "zip is missing. Install it with: sudo apt install zip" >&2; exit 1; }
mkdir -p "$dist_dir"

rm -f "$dist_dir/elsalab-products.sh3f" "$dist_dir/elsalab-textures.sh3t"
(cd "$lab_dir/custom-catalogs/furniture" && zip -r "$dist_dir/elsalab-products.sh3f" PluginFurnitureCatalog.properties models icons)
(cd "$lab_dir/custom-catalogs/textures" && zip -r "$dist_dir/elsalab-textures.sh3t" PluginTexturesCatalog.properties images)

echo "Created:"
echo "  $dist_dir/elsalab-products.sh3f"
echo "  $dist_dir/elsalab-textures.sh3t"

