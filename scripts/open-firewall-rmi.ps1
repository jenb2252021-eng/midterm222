# Run this script as Administrator on the SERVER PC to allow RMI connections from the LAN.
# Right-click PowerShell > "Run as administrator", then execute this script.

$ruleName = "TheLorax-RMI"

# Remove old rules if they exist
Remove-NetFirewallRule -DisplayName "$ruleName*" -ErrorAction SilentlyContinue

# RMI Registry (1099) + Service ports (1100-1103) — TCP inbound
New-NetFirewallRule -DisplayName "$ruleName-TCP" `
    -Direction Inbound -Protocol TCP `
    -LocalPort 1099,1100,1101,1102,1103 `
    -Action Allow -Profile Private,Domain `
    -Description "Allow The Lorax RMI services on LAN"

# UDP Discovery port (50000) — inbound
New-NetFirewallRule -DisplayName "$ruleName-UDP" `
    -Direction Inbound -Protocol UDP `
    -LocalPort 50000 `
    -Action Allow -Profile Private,Domain `
    -Description "Allow The Lorax LAN server discovery"

Write-Host ""
Write-Host "Firewall rules created successfully." -ForegroundColor Green
Write-Host "  TCP 1099,1100-1103 (RMI) - OPEN on Private/Domain networks"
Write-Host "  UDP 50000 (Discovery)     - OPEN on Private/Domain networks"
Write-Host ""
Write-Host "To remove these rules later, run:" -ForegroundColor Yellow
Write-Host "  Remove-NetFirewallRule -DisplayName 'TheLorax-RMI*'"
