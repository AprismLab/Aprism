# Despotes-driven auto world join for the Aprism smoke soak (v26.7-Alpha.8).
# Navigates whatever screen the game shows until inGame=true or timeout.
# Requires: dual-agent game running (Aprism + Despotes-native), HTTP 25585.
# GitHub@NDBlockConnect | BlockConnect@StarsailsClover

param(
    [int]$TimeoutSec = 300
)

$ErrorActionPreference = "SilentlyContinue"
$base = "http://127.0.0.1:25585/despotes/v1"

function Invoke-Act($body) {
    return Invoke-RestMethod -Method Post -Uri "$base/actions" `
        -ContentType "application/json" -Body $body
}

function Invoke-Qry($body) {
    return Invoke-RestMethod -Method Post -Uri "$base/query" `
        -ContentType "application/json" -Body $body
}

function Wait-ScreenChange {
    param([string]$FromTitle, [int]$Sec = 20)
    for ($i = 0; $i -lt $Sec; $i++) {
        Start-Sleep 1
        $sc = Invoke-Qry '{"type":"screen"}'
        if ($sc.result.title -ne $FromTitle) { return $sc.result.title }
    }
    return $FromTitle
}

$deadline = (Get-Date).AddSeconds($TimeoutSec)
$inGame = $false
$lastTitle = ""
$script:lastTitleBtnY = 99999   # start assuming the topmost button row

while ((Get-Date) -lt $deadline) {
    $s = Invoke-Qry '{"type":"status"}'
    if ($s.result.inGame) {
        $inGame = $true
        break
    }

    $sc = Invoke-Qry '{"type":"screen"}'
    $t = $sc.result.title
    if ($t -ne $lastTitle) {
        Write-Host "nav: $t"
        $lastTitle = $t
    }

    if ($t -eq "Title Screen") {
        # Click the first 200x20 button STRICTLY ABOVE the last wrong one.
        $sc2 = Invoke-Qry '{"type":"screen"}'
        $btn = $sc2.result.widgets |
            Where-Object { $_.class -eq "Plain" -and $_.w -eq 200 -and $_.h -eq 20 -and $_.y -lt $script:lastTitleBtnY } |
            Sort-Object y | Select-Object -First 1
        if ($btn) {
            $cx = $btn.x + [int]($btn.w / 2); $cy = $btn.y + [int]($btn.h / 2)
            $script:lastTitleBtnY = $btn.y
            $null = Invoke-Act ('{"type":"click","x":' + $cx + ',"y":' + $cy + ',"button":0}')
        }
        Start-Sleep 3
    }
    elseif ($t -eq "Select World") {
        # Select the first world entry, then double-click Play Selected World.
        $null = Invoke-Act '{"type":"click","x":213,"y":60,"button":0}'
        Start-Sleep 1
        $null = Invoke-Act '{"type":"click","x":134,"y":198,"button":0,"op":"double"}'
        Start-Sleep 5
    }
    elseif ($t -like "*Multiplayer*") {
        # Wrong button earlier: click Back (leftmost bottom widget) to return.
        $sc2 = Invoke-Qry '{"type":"screen"}'
        $back = $sc2.result.widgets |
            Where-Object { $_.class -eq "Plain" -and $_.y -gt 180 -and $_.x -lt 100 } |
            Sort-Object y | Select-Object -First 1
        if ($back) {
            $cx = $back.x + [int]($back.w / 2); $cy = $back.y + [int]($back.h / 2)
            $null = Invoke-Act ('{"type":"click","x":' + $cx + ',"y":' + $cy + ',"button":0}')
        }
        Start-Sleep 3
    }
    elseif ($t -eq "Select World") {
        # (handled above; kept for clarity)
    }
    elseif ($t -like "*backup*") {
        # Create backup, then proceed on the confirmation screen.
        $null = Invoke-Act '{"type":"click","x":133,"y":146,"button":0}'
        Start-Sleep 4
        $null = Invoke-Act '{"type":"click","x":133,"y":170,"button":0}'
        Start-Sleep 5
    }
    elseif ($t -like "*Upgrading*Completed*") {
        # Done button (right-hand Plain widget at y=135).
        $null = Invoke-Act '{"type":"click","x":290,"y":145,"button":0}'
        Start-Sleep 4
    }
    elseif ($t -like "*Upgrading*") {
        # In progress; wait.
        Start-Sleep 5
    }
    else {
        # Unknown/transitioning screen: WAIT (never blind-click - a stray
        # click here hit Quit Game once and killed the run).
        Start-Sleep 3
    }
}

Write-Output "inGame=$inGame"
if (-not $inGame) { exit 1 }
