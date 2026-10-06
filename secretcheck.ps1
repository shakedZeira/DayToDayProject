Set-Location "D:\AI Projects\DayToDayProject"
Remove-Item ".\cleanup2.ps1" -Force -ErrorAction SilentlyContinue

Write-Output "=== is connection_String.txt tracked by git? ==="
$t = git ls-files --error-unmatch connection_String.txt 2>&1
Write-Output ("  result: " + ($t -join " | "))

Write-Output ""
Write-Output "=== is it ignored? ==="
git check-ignore -v connection_String.txt 2>&1 | ForEach-Object { Write-Output ("  " + $_) }

Write-Output ""
Write-Output "=== does it appear in git HISTORY? ==="
$h = git log --oneline --all -- connection_String.txt 2>&1
if ($h) { $h | Select-Object -First 5 | ForEach-Object { Write-Output ("  " + $_) } } else { Write-Output "  (no commits touch it)" }

Write-Output ""
Write-Output "=== .gitignore contents ==="
Get-Content .gitignore | ForEach-Object { Write-Output ("  " + $_) }