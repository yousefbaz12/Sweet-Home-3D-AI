$ErrorActionPreference = 'Stop'

$lab = Split-Path -Parent $PSScriptRoot
$javaw = Join-Path $lab 'tools\jdk8u504-b01\bin\javaw.exe'
$jar = Join-Path $lab 'source\SweetHome3D\install\SweetHome3D-5.4.jar'
$runtimeData = Join-Path $lab 'runtime-data'
$runtimeTextures = Join-Path $runtimeData 'textures'
$runtimeFurniture = Join-Path $runtimeData 'furniture'

if (-not (Test-Path -LiteralPath $jar)) {
  throw "Sweet Home 3D is not built yet. Run windows\build.ps1 first."
}

New-Item -ItemType Directory -Force -Path $runtimeTextures, $runtimeFurniture | Out-Null

$texturesLibrary = Join-Path $lab 'dist\elsalab-textures.sh3t'
if (Test-Path -LiteralPath $texturesLibrary) {
  Copy-Item -LiteralPath $texturesLibrary -Destination (Join-Path $runtimeTextures 'elsalab-textures.sh3t') -Force
}

$furnitureLibrary = Join-Path $lab 'dist\elsalab-products.sh3f'
if (Test-Path -LiteralPath $furnitureLibrary) {
  Copy-Item -LiteralPath $furnitureLibrary -Destination (Join-Path $runtimeFurniture 'elsalab-products.sh3f') -Force
}

$applicationFolders = '-Dcom.eteks.sweethome3d.applicationFolders=' + $runtimeData
Start-Process -FilePath $javaw -ArgumentList @($applicationFolders, '-jar', $jar)

