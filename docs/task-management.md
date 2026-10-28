# Task status, reminders, and editing

Restart the backend after the changes. All requests below use `http://localhost:8080`.

## UI options and lists

`GET /api/tasks/options` returns the status, view/filter, repetition, priority, reminder, reminder-unit, and postpone choices. Reminder/postpone entries have `value` and `label` properties for UI controls.

Stored statuses are `PENDING`, `POSTPONED`, and `COMPLETED`. `ALL` is a list filter.

```http
GET /api/tasks?userId=1&status=ALL
GET /api/tasks?userId=1&status=PENDING
GET /api/tasks?userId=1&status=POSTPONED
GET /api/tasks?userId=1&status=COMPLETED
```

Lists are ordered by deadline ascending (then ID), so the earliest closing task appears first. The user profile's `tasks` list uses the same order. Omit `userId` to list tasks for all users.

The boolean `completed` is synchronized with status. `true` marks a task `COMPLETED`; unticking a completed task returns it to `PENDING`. Choosing `COMPLETED` as a status also checks the checkbox. `POSTPONED` remains an incomplete status.

Pending lists contain incomplete tasks, including missed tasks. Every minute, and before returning task lists, overdue postponed tasks return to `PENDING`. A task is overdue when its end time passes, or its deadline when no end time is set. Completed tasks stay completed.

## Optional reminders

Add any of these reminder values to a task: `NONE`, `MINUTES_5`, `MINUTES_10`, `MINUTES_30`, `HOURS_1`, `HOURS_3`, or `CUSTOM`. Omitting the reminder defaults to `NONE`.

```json
{
  "name": "Prepare report",
  "description": "Finish the report",
  "priority": "HIGH",
  "status": "PENDING",
  "user": {"id": 1},
  "dueDate": "2026-10-23T12:00:00",
  "startsAt": "2026-10-23T09:00:00",
  "endsAt": "2026-10-23T10:00:00",
  "reminder": "CUSTOM",
  "reminderAmount": 2,
  "reminderUnit": "HOURS"
}
```

Send that body to `POST /api/tasks`. Custom units are `MINUTES`, `HOURS`, `DAYS`, and `WEEKS`; amounts must be positive integers. For 1 hour 30 minutes, use 90 `MINUTES`.

The response contains `remindAt`, calculated before `startsAt`, or before `dueDate` if no start time is set. The example returns `2026-10-23T07:00:00`. Times use `Africa/Nairobi` by default. Reminders are stored and calculated now; no notifications are sent yet. Repeating occurrences inherit the reminder and calculate a new `remindAt`.

## Edit and complete with PUT

`PUT /api/tasks/1` accepts changed fields only. You can edit name, description, priority, status, completed, owning user, deadline, start/end time, repetition, and reminder settings. Omitted fields remain unchanged. IDs, creation/update timestamps, and generated recurrence/reminder metadata are managed by the backend.

Checkbox request:

```json
{"completed": true}
```

Uncheck with `{"completed": false}`. Edit a reminder with `{"reminder":"MINUTES_10"}`. Clear an end time with `{"endsAt":null}` or disable the reminder with `{"reminder":"NONE"}`.

If a time edit overlaps another task, the backend returns HTTP 409 and leaves the entire saved task unchanged, including its other edited fields. Confirm by resending the same body to `PUT /api/tasks/1?confirmOverlap=true`. This updates the same task and keeps the other task. Decline by discarding the request.

## Postpone/reschedule

Send `PUT /api/tasks/1/postpone`:

```json
{"option":"MINUTES_5"}
```

Options are `TOMORROW`, `MINUTES_5`, `MINUTES_10`, `MINUTES_30`, `HOURS_1`, `HOURS_2`, `HOURS_3`, `HOURS_6`, `HOURS_12`, and `DAYS_1`.

Minute/hour/day delays count from now. `TOMORROW` means the next calendar day at the task's original start time, or deadline time if it has no start. `DAYS_1` means 24 hours from now. Duration and the interval between start and deadline are preserved. The reminder is recalculated, repetition is rescheduled, and status becomes `POSTPONED` with `completed: false`.

This action also returns HTTP 409 without changing the task if it overlaps. Resend to `/api/tasks/1/postpone?confirmOverlap=true` to accept. For an exact date/time instead of a preset, edit the scheduling fields with `PUT /api/tasks/1`.
