$ErrorActionPreference = 'Stop'

$lab = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $lab '.env.local'
$runner = Join-Path $PSScriptRoot 'run.ps1'

if (-not (Test-Path -LiteralPath $envFile)) {
  throw "Missing $envFile. Copy .env.local.example to .env.local, then add your NEW Groq API key."
}

foreach ($line in Get-Content -LiteralPath $envFile) {
  $trimmed = $line.Trim()
  if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
    continue
  }

  $parts = $trimmed -split '=', 2
  if ($parts.Count -ne 2) {
    throw "Invalid line in .env.local: expected NAME=value."
  }

  $name = $parts[0].Trim()
  $value = $parts[1].Trim()
  if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
      ($value.StartsWith("'") -and $value.EndsWith("'"))) {
    $value = $value.Substring(1, $value.Length - 2)
  }

  if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
    throw "Invalid variable name in .env.local."
  }
  Set-Item -LiteralPath "Env:$name" -Value $value
}

if ([string]::IsNullOrWhiteSpace($env:GROQ_API_KEY) -or
    $env:GROQ_API_KEY -eq 'gsk_replace_with_your_new_key') {
  throw 'GROQ_API_KEY is missing or still contains the placeholder value.'
}

if ([string]::IsNullOrWhiteSpace($env:GROQ_MODEL)) {
  $env:GROQ_MODEL = 'openai/gpt-oss-120b'
}

try {
  & $runner
} finally {
  Remove-Item Env:\GROQ_API_KEY -ErrorAction SilentlyContinue
  Remove-Item Env:\GROQ_MODEL -ErrorAction SilentlyContinue
}
