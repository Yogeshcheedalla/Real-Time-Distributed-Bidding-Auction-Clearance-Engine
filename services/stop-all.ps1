# stop-all.ps1
Write-Host "Finding and stopping all microservices..."

# The ports used by our microservices
$ports = @(8080, 8081, 8082, 8083, 8084, 8761)

foreach ($port in $ports) {
    # Find processes listening on this port
    $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    
    if ($connections) {
        foreach ($conn in $connections) {
            Write-Host "Stopping process with ID $($conn.OwningProcess) listening on port $port"
            Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    } else {
        Write-Host "No process found listening on port $port"
    }
}

Write-Host "All specified ports have been freed."
