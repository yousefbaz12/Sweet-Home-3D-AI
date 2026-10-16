$ErrorActionPreference = 'Stop'

$lab = Split-Path -Parent $PSScriptRoot
$downloads = Join-Path $lab '.downloads'
$tools = Join-Path $lab 'tools'
$jdkFolder = Join-Path $tools 'jdk8u504-b01'
$antFolder = Join-Path $tools 'apache-ant-1.10.18'

New-Item -ItemType Directory -Force -Path $downloads, $tools | Out-Null
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Install-ZipTool {
  param(
    [string]$Name,
    [string]$Url,
    [string]$Archive,
    [string]$ExpectedFolder
  )

  if (Test-Path -LiteralPath $ExpectedFolder) {
    Write-Host "$Name already exists."
    return
  }

  if (-not (Test-Path -LiteralPath $Archive)) {
    Write-Host "Downloading $Name..."
    Invoke-WebRequest -Uri $Url -OutFile $Archive -UseBasicParsing
  }

  Write-Host "Extracting $Name..."
  Expand-Archive -LiteralPath $Archive -DestinationPath $tools -Force
  if (-not (Test-Path -LiteralPath $ExpectedFolder)) {
    throw "$Name extraction didn't create the expected folder: $ExpectedFolder"
  }
}

Install-ZipTool `
  -Name 'Temurin JDK 8u504' `
  -Url 'https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u504-b01/OpenJDK8U-jdk_x64_windows_hotspot_8u504b01.zip' `
  -Archive (Join-Path $downloads 'OpenJDK8U-jdk_x64_windows_hotspot_8u504b01.zip') `
  -ExpectedFolder $jdkFolder

Install-ZipTool `
  -Name 'Apache Ant 1.10.18' `
  -Url 'https://archive.apache.org/dist/ant/binaries/apache-ant-1.10.18-bin.zip' `
  -Archive (Join-Path $downloads 'apache-ant-1.10.18-bin.zip') `
  -ExpectedFolder $antFolder

& (Join-Path $jdkFolder 'bin\java.exe') -version
& (Join-Path $antFolder 'bin\ant.bat') -version
Write-Host 'Local build tools are ready.' -ForegroundColor Green
