$ErrorActionPreference = 'Stop'

$lab = Split-Path -Parent $PSScriptRoot
$upstream = Join-Path $lab 'upstream'
$source = Join-Path $lab 'source'
$downloads = Join-Path $lab '.downloads'
$archive = Join-Path $downloads 'sweethome3d-5.4-source.tar'
$overlay = Join-Path $lab 'overlays\SweetHome3D'
$homePanePatch = Join-Path $lab 'patches\home-pane-ai-sidebar.patch'
$editorPatch = Join-Path $lab 'patches\furniture-library-editor-java3d-classpath.patch'
$commit = '179a9a91a9fa9f2aff13b16cdc2e669885402af2'
$repository = 'https://github.com/ralic/sweethome-3d.git'

foreach ($command in @('git.exe', 'tar.exe')) {
  if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
    throw "$command is required. Install Git for Windows first."
  }
}

New-Item -ItemType Directory -Force -Path $downloads | Out-Null

if (-not (Test-Path -LiteralPath $upstream)) {
  Write-Host 'Fetching the pinned Sweet Home 3D upstream snapshot...'
  & git.exe clone --bare $repository $upstream
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$homePane = Join-Path $source 'SweetHome3D\src\com\eteks\sweethome3d\swing\HomePane.java'
if (-not (Test-Path -LiteralPath $homePane)) {
  Write-Host 'Exporting the Windows-compatible source modules...'
  New-Item -ItemType Directory -Force -Path $source | Out-Null
  # The upstream tree contains one unrelated model-license filename that is
  # invalid on Windows. Disable the path guard only while archiving the three
  # explicitly selected, Windows-compatible source modules below.
  & git.exe -c core.protectNTFS=false --git-dir=$upstream archive --format=tar --output=$archive $commit `
    SweetHome3D FurnitureLibraryEditor TexturesLibraryEditor
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  & tar.exe -xf $archive -C $source
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not (Test-Path -LiteralPath $overlay)) {
  throw "Missing AI source overlay: $overlay"
}

Write-Host 'Applying the El Sallab AI source overlay...'
Copy-Item -Path (Join-Path $overlay '*') -Destination (Join-Path $source 'SweetHome3D') -Recurse -Force

Push-Location $lab
try {
  $homePaneFile = Join-Path $source 'SweetHome3D\src\com\eteks\sweethome3d\swing\HomePane.java'
  if (-not (Select-String -LiteralPath $homePaneFile -SimpleMatch 'AiDesignPanel.wrap' -Quiet)) {
    & git.exe apply --directory=source $homePanePatch
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  }

  $furnitureBuild = Join-Path $source 'FurnitureLibraryEditor\build.xml'
  $patchMarker = 'The upstream 1.20 script omitted Java 3D'
  if (-not (Select-String -LiteralPath $furnitureBuild -SimpleMatch $patchMarker -Quiet)) {
    & git.exe apply --directory=source $editorPatch
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  }
} finally {
  Pop-Location
}

Write-Host "Source is ready at $source" -ForegroundColor Green
