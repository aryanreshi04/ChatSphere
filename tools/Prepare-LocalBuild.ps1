param([Parameter(Mandatory = $true)][string]$Destination)
$ErrorActionPreference = 'Stop'
$sourceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$targetRoot = [IO.Path]::GetFullPath($Destination)
if ($targetRoot.Equals($sourceRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $targetRoot.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Choose a new destination outside the source project and outside OneDrive.'
}
# Never overwrite an existing directory. This is a byte-for-byte build snapshot, not a Git checkout.
if (Test-Path -LiteralPath $targetRoot) { throw 'Destination already exists. Choose a new empty location.' }
$projectFiles = @(& git -C $sourceRoot ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { throw 'Could not enumerate source files from Git.' }
if (Test-Path -LiteralPath (Join-Path $sourceRoot 'local.properties')) { $projectFiles += 'local.properties' }
New-Item -ItemType Directory -Path $targetRoot | Out-Null
$manifest = foreach ($relativePath in ($projectFiles | Sort-Object -Unique)) {
    $sourceFile = [IO.Path]::GetFullPath((Join-Path $sourceRoot $relativePath))
    $targetFile = [IO.Path]::GetFullPath((Join-Path $targetRoot $relativePath))
    if (!$sourceFile.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        !$targetFile.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe relative path.' }
    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($targetFile)) | Out-Null
    # Do not preserve OneDrive reparse tags: materialize the actual bytes as ordinary local files.
    [IO.File]::WriteAllBytes($targetFile, [IO.File]::ReadAllBytes($sourceFile))
    $originalHash = (Get-FileHash -LiteralPath $sourceFile -Algorithm SHA256).Hash
    $copiedHash = (Get-FileHash -LiteralPath $targetFile -Algorithm SHA256).Hash
    if ($originalHash -ne $copiedHash) { throw "Snapshot mismatch: $relativePath" }
    [pscustomobject]@{ path = $relativePath; sha256 = $copiedHash }
}
[pscustomobject]@{ source = $sourceRoot; branch = (& git -C $sourceRoot branch --show-current); files = @($manifest) } |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $targetRoot 'SOURCE_SNAPSHOT.json') -Encoding utf8
Write-Output "Verified $($manifest.Count) files in $targetRoot. Edit the original repository; create a fresh snapshot to rebuild later."
