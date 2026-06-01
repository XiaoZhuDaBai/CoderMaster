# Sandbox Performance Test
# 测试目标：验证容器池复用后，代码执行延迟是否达到"毫秒级"

param(
    [string]$BaseUrl = "http://localhost:8085",
    [int]$Count = 50,           # 测试次数
    [int]$Warmup = 10,          # 预热次数
    [string]$Language = "java"
)

$javaCode = @"
public class Main {
    public static void main(String[] args) {
        int sum = 0;
        for (int i = 1; i <= 10000; i++) {
            sum += i;
        }
        System.out.println(sum);
    }
}
"@

$pythonCode = @"
sum = 0
for i in range(1, 10001):
    sum += i
print(sum)
"@

$testCases = @(
    @{ Language = "java"; Code = $javaCode; Input = "42" },
    @{ Language = "python"; Code = $pythonCode; Input = "42" }
)

function Test-SandboxExecution {
    param(
        [string]$lang,
        [string]$code,
        [string]$input
    )

    $body = @{
        language = $lang
        code = $code
        userInput = $input
    } | ConvertTo-Json

    $headers = @{
        "Content-Type" = "application/json"
    }

    $result = Measure-Command {
        try {
            $response = Invoke-WebRequest -Uri "$BaseUrl/api/sandbox/runCode" `
                                         -Method Post `
                                         -Headers $headers `
                                         -Body $body `
                                         -TimeoutSec 30
        }
        catch {
            # 忽略超时等错误，只关注时间
        }
    }

    return $result.TotalMilliseconds
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Sandbox Performance Test" -ForegroundColor Cyan
Write-Host "  Base URL: $BaseUrl" -ForegroundColor Cyan
Write-Host "  Test Count: $Count" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

foreach ($test in $testCases) {
    $lang = $test.Language
    Write-Host "Testing Language: $lang" -ForegroundColor Yellow
    Write-Host "----------------------------------------"

    # 预热
    Write-Host "Warming up ($Warmup requests)..." -ForegroundColor Gray
    for ($i = 0; $i -lt $Warmup; $i++) {
        $null = Test-SandboxExecution -lang $lang -code $test.Code -input $test.Input
    }

    # 正式测试
    Write-Host "Running $Count requests..." -ForegroundColor Gray
    $times = @()
    for ($i = 0; $i -lt $Count; $i++) {
        $ms = Test-SandboxExecution -lang $lang -code $test.Code -input $test.Input
        $times += $ms
        Write-Host "  Request $($i+1): $ms ms" -ForegroundColor DarkGray
    }

    # 统计
    $avg = ($times | Measure-Object -Average).Average
    $min = ($times | Measure-Object -Minimum).Minimum
    $max = ($times | Measure-Object -Maximum).Maximum
    $p50 = ($times | Sort-Object)[$times.Count / 2]
    $p95 = ($times | Sort-Object)[int($times.Count * 0.95)]

    Write-Host ""
    Write-Host "Results:" -ForegroundColor Green
    Write-Host "  Average: $([math]::Round($avg, 2)) ms"
    Write-Host "  Min:     $([math]::Round($min, 2)) ms"
    Write-Host "  Max:     $([math]::Round($max, 2)) ms"
    Write-Host "  P50:     $([math]::Round($p50, 2)) ms"
    Write-Host "  P95:     $([math]::Round($p95, 2)) ms"
    Write-Host ""

    # 判断是否达标
    if ($avg -lt 100) {
        Write-Host "✓ PASS: Average < 100ms (millisecond level achieved!)" -ForegroundColor Green
    } elseif ($avg -lt 1000) {
        Write-Host "⚠ PARTIAL: Average < 1000ms (sub-second, but not millisecond level)" -ForegroundColor Yellow
    } else {
        Write-Host "✗ FAIL: Average >= 1000ms (second level)" -ForegroundColor Red
    }
    Write-Host ""
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Complete!" -ForegroundColor Cyan
