$ErrorActionPreference = 'Stop'

$lab = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $lab 'tools\jdk8u504-b01'
$env:ANT_HOME = Join-Path $lab 'tools\apache-ant-1.10.18'
$env:Path = "$env:JAVA_HOME\bin;$env:ANT_HOME\bin;$env:Path"

$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$ant = Join-Path $env:ANT_HOME 'bin\ant.bat'
$furnitureBuild = Join-Path $lab 'source\FurnitureLibraryEditor\build.xml'

foreach ($required in @($java, $ant, $furnitureBuild)) {
  if (-not (Test-Path -LiteralPath $required)) {
    throw "Missing required file: $required"
  }
}

if (-not (Select-String -LiteralPath $furnitureBuild -SimpleMatch 'The upstream 1.20 script omitted Java 3D' -Quiet)) {
  throw "Furniture editor classpath patch is missing. Apply patches\furniture-library-editor-java3d-classpath.patch first."
}

Write-Host 'Building Sweet Home 3D 5.4...'
& $ant -f (Join-Path $lab 'source\SweetHome3D\build.xml') jarExecutable
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host 'Building Furniture Library Editor 1.20...'
& $ant -f $furnitureBuild furnitureLibraryEditorJarExecutable
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host 'Building Textures Library Editor 1.5...'
& $ant -f (Join-Path $lab 'source\TexturesLibraryEditor\build.xml') texturesLibraryEditorJarExecutable
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host 'All Windows builds completed successfully.' -ForegroundColor Green

