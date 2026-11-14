param(
    [Parameter(Mandatory = $true)]
    [string]$WslIp,
    [Parameter(Mandatory = $true)]
    [string]$HostIps,
    [Parameter(Mandatory = $true)]
    [int]$AppPort,
    [Parameter(Mandatory = $true)]
    [int]$GamePort
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runDirectory = Join-Path $projectRoot '.run'
$statePath = Join-Path $runDirectory 'hotspot-forwarding.state'
$hostAddresses = @($HostIps.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Sort-Object -Unique)
$ports = @($AppPort, $GamePort) | Sort-Object -Unique

$parsedWslIp = $null
if (-not [System.Net.IPAddress]::TryParse($WslIp, [ref]$parsedWslIp) -or $parsedWslIp.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
    throw "Invalid WSL IPv4 address: $WslIp"
}
if ($hostAddresses.Count -eq 0) {
    throw 'No Windows LAN or hotspot IPv4 addresses were found.'
}
foreach ($address in $hostAddresses) {
    $parsedAddress = $null
    if (-not [System.Net.IPAddress]::TryParse($address, [ref]$parsedAddress) -or $parsedAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
        throw "Invalid Windows IPv4 address: $address"
    }
}
foreach ($port in $ports) {
    if ($port -lt 1 -or $port -gt 65535) {
        throw "Invalid service port: $port"
    }
}

$state = "$WslIp|$($hostAddresses -join ',')|$($ports -join ',')"
if ((Test-Path $statePath) -and ((Get-Content -Raw $statePath).Trim() -eq $state)) {
    Write-Output 'Hotspot forwarding is already configured.'
    exit 0
}

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
$isAdministrator = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdministrator) {
    $arguments = '-NoProfile -ExecutionPolicy Bypass -File "{0}" -WslIp {1} -HostIps {2} -AppPort {3} -GamePort {4}' -f `
        $PSCommandPath, $WslIp, ($hostAddresses -join ','), $AppPort, $GamePort
    $process = Start-Process -FilePath (Join-Path $PSHOME 'powershell.exe') `
        -Verb RunAs -Wait -PassThru -ArgumentList $arguments
    if ($process.ExitCode -ne 0) {
        throw "Elevated hotspot setup failed with exit code $($process.ExitCode)."
    }
    exit 0
}

New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
$oldState = $null
if (Test-Path $statePath) {
    $oldState = (Get-Content -Raw $statePath).Trim().Split('|')
}

if ($oldState -and $oldState.Count -ge 3) {
    $oldAddresses = $oldState[1].Split(',')
    $oldPorts = $oldState[2].Split(',')
    foreach ($address in $oldAddresses) {
        foreach ($port in $oldPorts) {
            & netsh.exe interface portproxy delete v4tov4 listenaddress=$address listenport=$port | Out-Null
        }
    }
}

Set-Service -Name iphlpsvc -StartupType Automatic
if ((Get-Service -Name iphlpsvc).Status -ne 'Running') {
    Start-Service -Name iphlpsvc
}

foreach ($address in $hostAddresses) {
    foreach ($port in $ports) {
        & netsh.exe interface portproxy add v4tov4 `
            listenaddress=$address listenport=$port connectaddress=$WslIp connectport=$port | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Could not forward $address`:$port to WSL $WslIp`:$port."
        }
    }
}

Get-NetFirewallRule -DisplayName 'Collab Draw Hotspot Access' -ErrorAction SilentlyContinue |
    Remove-NetFirewallRule -ErrorAction SilentlyContinue
New-NetFirewallRule -DisplayName 'Collab Draw Hotspot Access' `
    -Direction Inbound -Action Allow -Protocol TCP -LocalPort $ports `
    -LocalAddress $hostAddresses -RemoteAddress LocalSubnet -Profile Any | Out-Null

[System.IO.File]::WriteAllText($statePath, $state, [System.Text.Encoding]::ASCII)
Write-Output "Hotspot forwarding configured for ports $($ports -join ', ')."
