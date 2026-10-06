$base = "D:\AI Projects\SydneyWorkout\app\src\main\java\com\sydneyworkout"
$files = @(
  "data\health\HealthRepository.kt",
  "data\health\HealthRepositoryImpl.kt",
  "data\health\HealthConnectPermissionRequest.kt",
  "data\health\HealthConnectPermissionActivity.kt",
  "domain\model\DailySteps.kt",
  "domain\model\CalorieBreakdown.kt",
  "domain\usecase\CalorieCalculator.kt"
)
foreach ($f in $files) {
  $p = Join-Path $base $f
  Write-Output ("################ " + $f + " ################")
  if (Test-Path $p) { Get-Content $p | ForEach-Object { Write-Output $_ } }
  else { Write-Output "  (MISSING)" }
  Write-Output ""
}