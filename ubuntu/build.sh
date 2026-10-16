#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
lab_dir="$(cd -- "$script_dir/.." && pwd)"
source_dir="$lab_dir/source"

command -v java >/dev/null || { echo "Java is missing. Install a JDK 8 distribution." >&2; exit 1; }
command -v javac >/dev/null || { echo "javac is missing. Install a full JDK 8, not a JRE." >&2; exit 1; }
command -v ant >/dev/null || { echo "Ant is missing. Install it with: sudo apt install ant" >&2; exit 1; }

java_version="$(java -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
case "$java_version" in
  1.8.*) ;;
  *)
    echo "This 2017 source snapshot is safest with JDK 8; found Java $java_version." >&2
    echo "Set JAVA_HOME to a JDK 8 installation and put its bin directory first in PATH." >&2
    exit 1
    ;;
esac

echo "Building Sweet Home 3D 5.4..."
(cd "$source_dir/SweetHome3D" && ant jarExecutable)

echo "Building Furniture Library Editor 1.20..."
(cd "$source_dir/FurnitureLibraryEditor" && ant furnitureLibraryEditorJarExecutable)

echo "Building Textures Library Editor 1.5..."
(cd "$source_dir/TexturesLibraryEditor" && ant texturesLibraryEditorJarExecutable)

echo "Build finished. Run $script_dir/run.sh"

