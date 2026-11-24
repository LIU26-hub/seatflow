param(
    [string]$BaseUrl = "http://localhost:8080",
    [Parameter(Mandatory = $true)][string]$Token,
    [string]$EvalPath = (Join-Path $PSScriptRoot "..\src\test\resources\eval\qa-eval.json")
)

# Runs the evaluation set against a live instance and reports retrieval hit@5.
# Reindex first: POST /api/qa/reindex (admin token required).

$cases = Get-Content -LiteralPath $EvalPath -Raw -Encoding UTF8 | ConvertFrom-Json
$hits = 0
$rows = @()

foreach ($case in $cases) {
    $body = @{ question = $case.question } | ConvertTo-Json -Compress
    $response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/qa" `
        -Headers @{ Authorization = "Bearer $Token" } `
        -ContentType "application/json" -Body $body

    $actual = @($response.data.citations | ForEach-Object { $_.sourceId })
    $matched = @($case.expectedSourceIds | Where-Object { $actual -contains $_ }).Count -gt 0
    if ($matched) { $hits++ }

    $rows += [pscustomobject]@{
        Question = $case.question
        Hit      = $matched
        Expected = ($case.expectedSourceIds -join ",")
        Actual   = ($actual -join ",")
    }
}

$rows | Format-Table -AutoSize
"hit@5: {0}/{1} ({2:P0})" -f $hits, $cases.Count, ($hits / $cases.Count)
