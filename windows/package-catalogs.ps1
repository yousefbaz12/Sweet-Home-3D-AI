$ErrorActionPreference = 'Stop'

$lab = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $lab 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$packages = @(
  @{
    Source = Join-Path $lab 'custom-catalogs\furniture'
    Output = Join-Path $dist 'elsalab-products.sh3f'
  },
  @{
    Source = Join-Path $lab 'custom-catalogs\textures'
    Output = Join-Path $dist 'elsalab-textures.sh3t'
  }
)

foreach ($package in $packages) {
  Remove-Item -LiteralPath $package.Output -Force -ErrorAction SilentlyContinue
  $sourceRoot = [System.IO.Path]::GetFullPath($package.Source).TrimEnd([System.IO.Path]::DirectorySeparatorChar)
  $sourcePrefix = $sourceRoot + [System.IO.Path]::DirectorySeparatorChar
  $outputStream = [System.IO.File]::Open($package.Output, [System.IO.FileMode]::CreateNew)
  try {
    $archive = New-Object System.IO.Compression.ZipArchive(
      $outputStream,
      [System.IO.Compression.ZipArchiveMode]::Create,
      $false
    )
    try {
      Get-ChildItem -LiteralPath $sourceRoot -Recurse -File |
        Where-Object { $_.Name -ne '.gitkeep' } |
        ForEach-Object {
          # ZIP entry names are URL-like paths. A backslash works in Explorer but Java treats it
          # as a literal character, which makes Sweet Home 3D show a red X for every image.
          $entryName = $_.FullName.Substring($sourcePrefix.Length).Replace('\', '/')
          $entry = $archive.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
          $entryStream = $entry.Open()
          $inputStream = [System.IO.File]::OpenRead($_.FullName)
          try {
            $inputStream.CopyTo($entryStream)
          }
          finally {
            $inputStream.Dispose()
            $entryStream.Dispose()
          }
        }
    }
    finally {
      $archive.Dispose()
    }
  }
  finally {
    $outputStream.Dispose()
  }
  Write-Host "Created $($package.Output)"
}

