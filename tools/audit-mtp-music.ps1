[CmdletBinding()]
param(
    [string]$DeviceName = 'TCL NXTPAPER 70 Pro',
    [string]$StorageName = 'Internal shared storage',
    [string]$RootFolderName = 'Music',
    [string]$OutputDirectory = '',
    [switch]$CopySamples,
    [ValidateRange(1, 100)]
    [int]$MaximumAudioSamples = 30,
    [ValidateRange(1, 100)]
    [int]$MaximumArtworkSamples = 30,
    [ValidateRange(1, 2048)]
    [int]$MaximumSampleMegabytes = 512
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '..\local-audits\music'
}

# The source MTP tree is deliberately accessed only through Shell FolderItem
# enumeration. CopyHere is used only when -CopySamples is explicitly supplied,
# and its destination is always the local audit directory.
$supportedAudioExtensions = @('.mp3', '.m4a', '.aac', '.flac', '.ogg', '.oga', '.wav', '.opus', '.mka')
$audioLookingExtensions = @('.mp3', '.m4a', '.aac', '.flac', '.ogg', '.oga', '.wav', '.opus', '.mka', '.wma', '.ape', '.alac', '.aiff', '.aif', '.dsf', '.dff')
$artworkExtensions = @('.jpg', '.jpeg', '.png', '.webp')
$preferredArtworkNames = @('cover', 'folder', 'front', 'album', 'albumart')

function Get-ChildByName {
    param($Folder, [string]$Name)
    return @($Folder.Items()) | Where-Object { $_.Name -eq $Name } | Select-Object -First 1
}

function Get-NameClassification {
    param([string]$Name)
    $stem = [IO.Path]::GetFileNameWithoutExtension($Name).Trim()
    if ($stem -match '^\d{1,2}[-._\s]+\d{1,2}[-._\s]+.+$') { return 'disc-track-separated' }
    if ($stem -match '^\d{3,4}[-._\s]+.+$') { return 'disc-track-compact' }
    if ($stem -match '^\d{1,2}[-._\s]+.+$') { return 'track-prefix' }
    return 'no-prefix'
}

function Find-ShellItemPath {
    param([string]$Device, [string]$Storage, [string]$Root)
    $shell = New-Object -ComObject Shell.Application
    $thisPc = $shell.Namespace(17)
    $deviceItem = @($thisPc.Items()) | Where-Object { $_.Name -eq $Device } | Select-Object -First 1
    if (-not $deviceItem) {
        throw "Device '$Device' is not visible under This PC. Reconnect the USB cable and enable File transfer / Android Auto."
    }
    $deviceFolder = $deviceItem.GetFolder
    $storageItem = Get-ChildByName -Folder $deviceFolder -Name $Storage
    if (-not $storageItem) {
        throw "Device '$Device' is visible but '$Storage' is not accessible. Unlock the phone, select File transfer / Android Auto, and accept the phone's data-access prompt."
    }
    $storageFolder = $storageItem.GetFolder
    $rootItem = Get-ChildByName -Folder $storageFolder -Name $Root
    if (-not $rootItem) {
        throw "Storage '$Storage' is accessible but folder '$Root' was not found."
    }
    return [PSCustomObject]@{ Shell = $shell; Item = $rootItem }
}

function Copy-LocalSample {
    param($Shell, $Item, [string]$Destination)
    $destinationFolder = $Shell.Namespace($Destination)
    if (-not $destinationFolder) { throw "Could not open local sample destination '$Destination'." }
    $destinationFolder.CopyHere($Item, 20) # no UI and answer Yes to local name collisions
}

try {
    $resolved = Find-ShellItemPath -Device $DeviceName -Storage $StorageName -Root $RootFolderName
} catch {
    Write-Error $_.Exception.Message
    exit 2
}

$rootItem = $resolved.Item
$shell = $resolved.Shell
$queue = [Collections.Generic.Queue[object]]::new()
$queue.Enqueue([PSCustomObject]@{ Item = $rootItem; RelativePath = ''; Depth = 0 })
$files = [Collections.Generic.List[object]]::new()
$folders = [Collections.Generic.List[object]]::new()
$extensionCounts = @{}
$classificationCounts = @{}
$totalBytes = [int64]0
$knownSizeFileCount = 0
$unknownSizeFileCount = 0
$maximumDepth = 0
$lastProgressAt = Get-Date

while ($queue.Count -gt 0) {
    $node = $queue.Dequeue()
    $children = @($node.Item.GetFolder.Items())
    foreach ($child in $children) {
        $relativePath = if ([string]::IsNullOrEmpty($node.RelativePath)) { $child.Name } else { "$($node.RelativePath)/$($child.Name)" }
        $depth = $node.Depth + 1
        if ($depth -gt $maximumDepth) { $maximumDepth = $depth }
        if ($child.IsFolder) {
            $folders.Add([PSCustomObject]@{ RelativePath = $relativePath; Depth = $depth })
            $queue.Enqueue([PSCustomObject]@{ Item = $child; RelativePath = $relativePath; Depth = $depth })
            continue
        }

        $extension = [IO.Path]::GetExtension($child.Name).ToLowerInvariant()
        if (-not $extensionCounts.ContainsKey($extension)) { $extensionCounts[$extension] = 0 }
        $extensionCounts[$extension]++
        $hasKnownSize = $null -ne $child.Size -and [int64]$child.Size -gt 0L
        $size = if ($hasKnownSize) { [int64]$child.Size } else { $null }
        if ($hasKnownSize) {
            $totalBytes += $size
            $knownSizeFileCount++
        } else {
            $unknownSizeFileCount++
        }
        $stem = [IO.Path]::GetFileNameWithoutExtension($child.Name).ToLowerInvariant()
        $isAppleDouble = $child.Name.StartsWith('._', [StringComparison]::Ordinal)
        $isSupportedAudio = ($supportedAudioExtensions -contains $extension) -and -not $isAppleDouble
        $classification = if ($isSupportedAudio) { Get-NameClassification $child.Name } else { $null }
        if ($classification) {
            if (-not $classificationCounts.ContainsKey($classification)) { $classificationCounts[$classification] = 0 }
            $classificationCounts[$classification]++
        }
        $files.Add([PSCustomObject]@{
            RelativePath = $relativePath
            Name = $child.Name
            Extension = $extension
            Size = $size
            Depth = $depth
            IsSupportedAudio = $isSupportedAudio
            IsUnsupportedAudioCandidate = (($audioLookingExtensions -contains $extension) -and -not $isSupportedAudio)
            IsArtworkCandidate = ($artworkExtensions -contains $extension) -and ($preferredArtworkNames -contains $stem)
            NameClassification = $classification
            ShellItem = if ($CopySamples) { $child } else { $null }
        })
        if ($files.Count % 1000 -eq 0 -or ((Get-Date) - $lastProgressAt).TotalSeconds -ge 30) {
            Write-Output "Inspected $($files.Count) files and $($folders.Count) folders (source remains read-only)..."
            $lastProgressAt = Get-Date
        }
    }
}

$reportDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$jsonPath = Join-Path $reportDirectory "music-audit-$timestamp.json"
$markdownPath = Join-Path $reportDirectory "music-audit-$timestamp.md"
$supported = @($files | Where-Object IsSupportedAudio)
$unsupported = @($files | Where-Object IsUnsupportedAudioCandidate)
$artwork = @($files | Where-Object IsArtworkCandidate)
$fingerprintInput = ($folders.RelativePath + $files.RelativePath | Sort-Object) -join "`n"
$fingerprintBytes = [Text.Encoding]::UTF8.GetBytes($fingerprintInput)
$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    $fingerprint = -join ($sha256.ComputeHash($fingerprintBytes) | ForEach-Object { $_.ToString('x2') })
} finally {
    $sha256.Dispose()
}
$report = [ordered]@{
    generatedAt = (Get-Date).ToString('o')
    source = "$DeviceName/$StorageName/$RootFolderName"
    readOnly = $true
    folderCount = $folders.Count
    fileCount = $files.Count
    knownBytes = $totalBytes
    knownSizeFileCount = $knownSizeFileCount
    unknownSizeFileCount = $unknownSizeFileCount
    structureFingerprintSha256 = $fingerprint
    maximumDepth = $maximumDepth
    supportedAudioCount = $supported.Count
    unsupportedAudioCandidateCount = $unsupported.Count
    artworkCandidateCount = $artwork.Count
    extensionCounts = $extensionCounts
    namingClassifications = $classificationCounts
    representativeAudioPaths = @($supported | Select-Object -First 100 -ExpandProperty RelativePath)
    unsupportedAudioCandidates = @($unsupported | Select-Object -First 100 -ExpandProperty RelativePath)
    artworkCandidates = @($artwork | Select-Object -First 100 -ExpandProperty RelativePath)
}
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

$markdown = @(
    '# Music library audit'
    ''
    "- Source: ``$DeviceName/$StorageName/$RootFolderName``"
    '- Source access: read-only'
    "- Folders: $($folders.Count)"
    "- Files: $($files.Count)"
    "- Known bytes: $totalBytes ($knownSizeFileCount files supplied a size; $unknownSizeFileCount did not)"
    "- Structure fingerprint: ``$fingerprint``"
    "- Maximum depth: $maximumDepth"
    "- Supported audio: $($supported.Count)"
    "- Unsupported audio candidates: $($unsupported.Count)"
    "- External artwork candidates: $($artwork.Count)"
    ''
    'The JSON report contains bounded representative paths and aggregate extension/naming counts.'
)
$markdown | Set-Content -LiteralPath $markdownPath -Encoding UTF8

if ($CopySamples) {
    $sampleDirectory = Join-Path $reportDirectory 'samples'
    New-Item -ItemType Directory -Force -Path $sampleDirectory | Out-Null
    $limitBytes = [int64]$MaximumSampleMegabytes * 1MB
    $copiedBytes = [int64]0
    $audioSamples = @($supported | Group-Object Extension, NameClassification | ForEach-Object { $_.Group | Select-Object -First 1 } | Select-Object -First $MaximumAudioSamples)
    $artworkSamples = @($artwork | Select-Object -First $MaximumArtworkSamples)
    foreach ($sample in @($audioSamples + $artworkSamples)) {
        if (($copiedBytes + $sample.Size) -gt $limitBytes) { break }
        Copy-LocalSample -Shell $shell -Item $sample.ShellItem -Destination $sampleDirectory
        $copiedBytes += $sample.Size
    }
}

Write-Output "Read-only audit complete."
Write-Output "JSON: $jsonPath"
Write-Output "Markdown: $markdownPath"
