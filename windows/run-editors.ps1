$ErrorActionPreference = 'Stop'

$lab = Split-Path -Parent $PSScriptRoot
$javaw = Join-Path $lab 'tools\jdk8u504-b01\bin\javaw.exe'
$furnitureEditor = Join-Path $lab 'source\FurnitureLibraryEditor\FurnitureLibraryEditor-1.20.jar'
$texturesEditor = Join-Path $lab 'source\TexturesLibraryEditor\TexturesLibraryEditor-1.5.jar'

foreach ($jar in @($furnitureEditor, $texturesEditor)) {
  if (-not (Test-Path -LiteralPath $jar)) {
    throw "Editor is not built yet: $jar. Run windows\build.ps1 first."
  }
  Start-Process -FilePath $javaw -ArgumentList @('-jar', $jar)
}

