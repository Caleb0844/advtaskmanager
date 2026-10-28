param([string]$BaseUrl = "http://localhost:8080")

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd('/')
$verificationUserId = $null
$verificationTaskIds = [System.Collections.Generic.List[long]]::new()
$passedChecks = 0

function Send-Api {
    param([string]$Method, [string]$Path, $Body = $null)
    $request = @{ Uri = "$BaseUrl$Path"; Method = $Method; UseBasicParsing = $true; TimeoutSec = 15 }
    if ($null -ne $Body) {
        $request.ContentType = "application/json"
        $request.Body = ConvertTo-Json -InputObject $Body -Depth 10 -Compress
    }
    try {
        $response = Invoke-WebRequest @request
        $statusCode = [int]$response.StatusCode
        $content = $response.Content
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $statusCode = [int]$_.Exception.Response.StatusCode
        $content = $_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($content)) {
            $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
            try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
        }
    }
    $json = if ([string]::IsNullOrWhiteSpace($content)) { $null } else { ConvertFrom-Json -InputObject $content }
    return [pscustomobject]@{ Status = $statusCode; Json = $json }
}

function Check {
    param([string]$Name, [bool]$Condition)
    if (!$Condition) { throw "FAILED: $Name" }
    $script:passedChecks++
    Write-Output "PASS: $Name"
}

function Save-TestTask {
    param($Body, [bool]$Confirm = $false)
    $path = if ($Confirm) { "/api/tasks?confirmOverlap=true" } else { "/api/tasks" }
    $response = Send-Api POST $path $Body
    if ($response.Status -ne 200 -or !$response.Json.id) {
        throw "Test task could not be created (HTTP $($response.Status)): $($response.Json.message) $($response.Json.fieldErrors | ConvertTo-Json -Compress)"
    }
    $script:verificationTaskIds.Add([long]$response.Json.id)
    return $response.Json
}

try {
    $options = Send-Api GET "/api/tasks/options"
    Check "Task options are available" ($options.Status -eq 200 -and $options.Json.reminders.Count -eq 7)

    $username = "backendcheck_" + [guid]::NewGuid().ToString("N")
    $createdUser = Send-Api POST "/api/users" @{ name = "Backend verification"; username = $username; email = "$username@example.com" }
    if ($createdUser.Status -ne 200 -or !$createdUser.Json.id) { throw "Could not create temporary verification user" }
    $verificationUserId = [long]$createdUser.Json.id
    Check "Create a user" ($verificationUserId -gt 0)

    $tomorrow = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(3)).Date.AddDays(1)
    $start = $tomorrow.AddHours(12)
    $format = "yyyy-MM-dd'T'HH:mm:ss"
    $first = Save-TestTask @{
        name = "Verification task A"; description = "Temporary live API check"; priority = "HIGH"
        user = @{id = $verificationUserId}; dueDate = $start.ToString($format)
        startsAt = $start.ToString($format); endsAt = $start.AddHours(2).ToString($format)
        reminder = "MINUTES_10"
    }
    Check "Create task with calculated reminder" ($first.remindAt -eq $start.AddMinutes(-10).ToString($format) -and !$first.completed)
    $profile = Send-Api GET "/api/users/$verificationUserId"
    Check "User profile contains the task" ($profile.Status -eq 200 -and @($profile.Json.tasks).Count -eq 1)

    $secondBody = @{
        name = "Verification task B"; description = "Temporary live API check"; priority = "MEDIUM"
        user = @{id = $verificationUserId}; dueDate = $start.AddHours(3).ToString($format)
        startsAt = $start.AddHours(1).ToString($format)
    }
    $conflict = Send-Api POST "/api/tasks" $secondBody
    Check "Overlap asks for confirmation" ($conflict.Status -eq 409 -and $conflict.Json.confirmationRequired)
    $list = Send-Api GET "/api/tasks?userId=$verificationUserId"
    Check "Declining an overlap saves nothing" (@($list.Json).Count -eq 1)
    $second = Save-TestTask $secondBody $true
    $list = Send-Api GET "/api/tasks?userId=$verificationUserId"
    Check "Confirming an overlap keeps both tasks" (@($list.Json).Count -eq 2)

    $edit = @{name = "Edited task B"; startsAt = $start.AddMinutes(30).ToString($format)}
    $rejected = Send-Api PUT "/api/tasks/$($second.id)" $edit
    $unchanged = Send-Api GET "/api/tasks/$($second.id)"
    Check "Declined edit preserves the saved task" ($rejected.Status -eq 409 -and $unchanged.Json.name -eq $second.name -and $unchanged.Json.startsAt -eq $second.startsAt)
    $accepted = Send-Api PUT "/api/tasks/$($second.id)?confirmOverlap=true" $edit
    $original = Send-Api GET "/api/tasks/$($first.id)"
    Check "Confirmed edit leaves the other task intact" ($accepted.Status -eq 200 -and $accepted.Json.name -eq "Edited task B" -and $original.Json.startsAt -eq $first.startsAt -and $original.Json.endsAt -eq $first.endsAt)

    $complete = Send-Api PUT "/api/tasks/$($second.id)" @{completed = $true}
    $completedList = Send-Api GET "/api/tasks?userId=$verificationUserId&status=COMPLETED"
    Check "Checkbox and completed filter work" ($complete.Json.status -eq "COMPLETED" -and $complete.Json.completed -and @($completedList.Json).Count -eq 1)
    $incomplete = Send-Api PUT "/api/tasks/$($second.id)?confirmOverlap=true" @{completed = $false}
    Check "Unticking the checkbox returns task to pending" ($incomplete.Status -eq 200 -and $incomplete.Json.status -eq "PENDING" -and !$incomplete.Json.completed)

    $postponed = Send-Api PUT "/api/tasks/$($second.id)/postpone" @{option = "MINUTES_5"}
    Check "Postpone moves task and sets its status" ($postponed.Status -eq 200 -and $postponed.Json.status -eq "POSTPONED")
    $reminder = Send-Api PUT "/api/tasks/$($second.id)" @{reminder = "CUSTOM"; reminderAmount = 1; reminderUnit = "HOURS"; repetition = "DAILY"}
    if ($reminder.Status -ne 200) { throw "Reminder update failed (HTTP $($reminder.Status)): $($reminder.Json.message)" }
    $expectedReminder = [DateTime]::Parse($reminder.Json.startsAt).AddHours(-1)
    $actualReminder = [DateTime]::Parse($reminder.Json.remindAt)
    Check "Custom reminder is one hour before the new start" ([Math]::Abs(($actualReminder - $expectedReminder).TotalMilliseconds) -lt 1)
    Check "Daily repetition has a next occurrence" ($null -ne $reminder.Json.nextOccurrenceAt)

    foreach ($minutes in @(0, 5)) {
        $point = $start.AddHours(4).AddMinutes($minutes)
        $noEnd = Save-TestTask @{
            name = "No end time $minutes"; description = "Temporary live API check"; priority = "LOW"
            user = @{id = $verificationUserId}; dueDate = $point.ToString($format); startsAt = $point.ToString($format)
        }
    }
    Check "Tasks without end times allow nearby starts" ($null -eq $noEnd.endsAt)
    $list = Send-Api GET "/api/tasks?userId=$verificationUserId&status=ALL"
    $dates = @($list.Json | ForEach-Object { $_.dueDate })
    Check "Task list is ordered by deadline" (($dates -join ',') -eq (($dates | Sort-Object) -join ','))

    $invalid = Send-Api PUT "/api/tasks/$($first.id)" @{name = " "}
    Check "Invalid edits return UI field errors" ($invalid.Status -eq 400 -and $null -ne $invalid.Json.fieldErrors.name)
    $missing = Send-Api GET "/api/tasks/9223372036854775807"
    Check "Missing task returns a JSON 404" ($missing.Status -eq 404 -and $missing.Json.status -eq 404)
    $deleteOwner = Send-Api DELETE "/api/users/$verificationUserId"
    Check "User with tasks is protected from deletion" ($deleteOwner.Status -eq 409 -and !$deleteOwner.Json.confirmationRequired)
} finally {
    $cleanupFailures = [System.Collections.Generic.List[string]]::new()
    foreach ($taskId in $verificationTaskIds) {
        try {
            $deleted = Send-Api DELETE "/api/tasks/$taskId"
            if ($deleted.Status -ne 200 -and $deleted.Status -ne 404) { $cleanupFailures.Add("task $taskId") }
        } catch { $cleanupFailures.Add("task $taskId") }
    }
    if ($null -ne $verificationUserId) {
        try {
            $deleted = Send-Api DELETE "/api/users/$verificationUserId"
            if ($deleted.Status -ne 200 -and $deleted.Status -ne 404) { $cleanupFailures.Add("user $verificationUserId") }
        } catch { $cleanupFailures.Add("user $verificationUserId") }
    }
    if ($cleanupFailures.Count -gt 0) {
        throw "Could not remove temporary verification data: $($cleanupFailures -join ', ')"
    }
}
Write-Output "$passedChecks live API checks passed; temporary tasks and user removed."
