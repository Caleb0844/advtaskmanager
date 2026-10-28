# Verify the backend in Thunder Client

Start `AdvtaskmanagerApplication` from your IDE. The base URL is `http://localhost:8080`.
For POST and PUT requests, select a JSON body and send `Content-Type: application/json`.

Use a new test user for this walkthrough. Keep the IDs returned by the server; the examples use placeholders such as `USER_ID`, `TASK_A_ID`, and `TASK_B_ID`. Replace those in URLs. In JSON bodies, replace the example user ID `123` with your actual numeric user ID.

Dates below use 23 October 2026 in Africa/Nairobi time. If testing on a later date, change them to a future day and keep the same times.

## 1. Register and log in

```http
POST http://localhost:8080/api/users
Content-Type: application/json
```

```json
{
  "name": "Thunder test",
  "username": "thunder_test",
  "email": "thunder_test@example.com",
  "password": "change-me-123"
}
```

Then call `POST /api/auth/login` with the username and password. Add the returned token to an `Authorization: Bearer <token>` header on every request below.

## 2. Read the choices for your UI

```http
GET http://localhost:8080/api/tasks/options
```

Expected: 200 with priorities, statuses, filters, reminders, repetition, and postpone choices.

## 3. Add task A, from noon until 2 pm

```http
POST http://localhost:8080/api/tasks
```

```json
{
  "name": "Task A",
  "description": "Prepare the report",
  "priority": "HIGH",
  "user": {"id": 123},
  "dueDate": "2026-10-23T14:00:00",
  "startsAt": "2026-10-23T12:00:00",
  "endsAt": "2026-10-23T14:00:00",
  "reminder": "MINUTES_10"
}
```

Expected: 200, `status: PENDING`, `completed: false`, and `remindAt: 2026-10-23T11:50:00`. Save its ID as `TASK_A_ID`. Omitting repetition defaults to `NONE`.

## 4. Try adding task B at 1 pm

Send this to the same POST endpoint:

```json
{
  "name": "Task B",
  "description": "Review the report",
  "priority": "MEDIUM",
  "user": {"id": 123},
  "dueDate": "2026-10-23T15:00:00",
  "startsAt": "2026-10-23T13:00:00"
}
```

Expected: 409 with `confirmationRequired: true` and task A in `conflictingTasks`. B has not been saved. Thunder Client displays this JSON; the future phone interface will display the Yes/No dialog.

- **No:** discard B's input and do not resend it.
- **Yes:** resend the same body to `POST http://localhost:8080/api/tasks?confirmOverlap=true`.

After Yes, expect 200. Save the returned ID as `TASK_B_ID`. Both A and B must exist, and A must still run from noon to 2 pm. B has no end time and no reminder.

## 5. View the profile and task lists

```http
GET http://localhost:8080/api/users/USER_ID
GET http://localhost:8080/api/tasks?userId=USER_ID&status=ALL
GET http://localhost:8080/api/tasks?userId=USER_ID&status=PENDING
```

Expected: the profile contains both tasks, and the task lists put the earliest deadline first. Change the filter to `COMPLETED` or `POSTPONED` as needed.

## 6. Edit a task and check overlap protection

```http
PUT http://localhost:8080/api/tasks/TASK_B_ID
```

```json
{"name":"Edited B","startsAt":"2026-10-23T12:30:00"}
```

Expected: 409. Read B with `GET /api/tasks/TASK_B_ID`: both its original name and start time must remain unchanged. Resend the PUT with `?confirmOverlap=true` to accept the complete edit. A remains unchanged.

## 7. Test the completion checkbox

Send to `PUT /api/tasks/TASK_B_ID`:

```json
{"completed":true}
```

Expected: `status: COMPLETED` and `completed: true`. B should appear in the `COMPLETED` list.

Uncheck with `{"completed":false}`. Reactivating B can prompt for overlap confirmation because A is still scheduled; if you accept, resend with `?confirmOverlap=true`. Expected: `status: PENDING` and `completed: false`.

## 8. Postpone, then set a custom reminder and repetition

```http
PUT http://localhost:8080/api/tasks/TASK_B_ID/postpone
```

```json
{"option":"MINUTES_5"}
```

Expected: B's start becomes five minutes from now and its status becomes `POSTPONED`. Its deadline shifts by the same amount. As with edits, resolve any 409 overlap prompt before saving.

Then send to `PUT /api/tasks/TASK_B_ID`:

```json
{"reminder":"CUSTOM","reminderAmount":1,"reminderUnit":"HOURS","repetition":"DAILY"}
```

Expected: `remindAt` is one hour before the new start, and `nextOccurrenceAt` is set. The scheduler creates future occurrences when their scheduled time arrives. Notification delivery is not implemented yet.

## 9. Check optional end times and errors

Create two more tasks for this user, starting at 4 pm and 4:05 pm, both with no `endsAt`. Both should save without overlap confirmation. Use the body from step 4 with the new names and times.

Try `PUT /api/tasks/TASK_A_ID` with `{"name":" "}`. Expected: 400 with `fieldErrors.name`; the saved name stays unchanged.

Try deleting this test user while they own tasks. Expected: 409, with instructions to delete or reassign their tasks first.

## 10. Remove your test data

Delete only the tasks you created for this walkthrough, then the test user:

```http
DELETE http://localhost:8080/api/tasks/TASK_A_ID
DELETE http://localhost:8080/api/tasks/TASK_B_ID
DELETE http://localhost:8080/api/users/USER_ID
```

Also delete the two optional-end-time test tasks and any generated occurrences before deleting the user. Deleting a repeating task stops future occurrences, but already-generated occurrences remain separate tasks.

## Repeatable live check

The script at `scripts/Test-Backend.ps1` runs the main scenarios against a running server, creates a unique temporary user, and removes its test records afterward. It does not use user 1 or existing tasks.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-Backend.ps1
```

`-ExecutionPolicy Bypass` applies only to that PowerShell process; it does not change the system execution policy.
