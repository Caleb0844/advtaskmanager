# Task scheduling and repetition

Restart the backend after updating the code. Existing tasks default to `repetition: "NONE"`.

Send a JSON object to `POST http://localhost:8080/api/tasks`:

```json
{
  "name": "Weekly planning",
  "description": "Plan the coming week",
  "priority": "MEDIUM",
  "status": "PENDING",
  "user": { "id": 1 },
  "dueDate": "2026-10-23T12:00:00",
  "startsAt": "2026-10-23T09:00:00",
  "endsAt": "2026-10-23T10:00:00",
  "repetition": "WEEKLY"
}
```

`dueDate` and `startsAt` are required. `endsAt` and `repetition` are optional. When supplied, an end time must be later than the start time. Existing tasks without an end time never trigger overlap warnings, including for another task at the exact same start time or five minutes later. A new task without an end time still prompts for confirmation if its start falls within an existing task's explicit start/end window. Adjacent tasks, where one ends exactly when another starts, are allowed.

Times use the configured `tasks.time-zone`, which defaults to `Africa/Nairobi`.

## Confirming overlaps

Creating or rescheduling a task checks the same user's active task windows, including future repeating occurrences. Completed occurrences do not block that time; a completed repeating source can still have future occurrences.

An overlap returns HTTP 409 without saving the change:

```json
{
  "confirmationRequired": true,
  "message": "Another task is scheduled during this time. Do you want to add this task as well?",
  "conflictingTasks": [
    {
      "taskId": 4,
      "name": "Existing meeting",
      "startsAt": "2026-10-23T09:30:00",
      "endsAt": "2026-10-23T10:30:00"
    }
  ]
}
```

**Yes:** resend the same JSON to `POST /api/tasks?confirmOverlap=true`. Both tasks are kept; confirming never replaces, deletes, or changes the existing task. For example, task A from 12:00 to 14:00 stays unchanged when task B at 13:00 is confirmed.

**No:** discard the pending request and clear task B's input in the client. The backend has not saved task B or a draft of its details, so no delete request is needed. Task A stays unchanged.

In Thunder Client the HTTP 409 JSON is the confirmation prompt; resend only when choosing Yes. The same confirmation parameter applies to `PUT /api/tasks/{id}`. PUT accepts only the fields you want to change and preserves omitted fields. Set optional fields such as `endsAt` to `null` to clear them, or `repetition` to `NONE` to stop repetition. Declining a conflicting edit preserves all original details.

## Automatic repetition

Supported values are `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`, and `YEARLY`. Omit `repetition` (or set it to `NONE`) for a task that does not repeat.

The first task is saved immediately. While the backend is running, a scheduler checks every 60 seconds and creates each later occurrence when its start time arrives. If no start time is provided, it uses the deadline. The response's `nextOccurrenceAt` shows the next scheduled occurrence.

Each generated task belongs to the same user, starts with status `PENDING`, preserves the original duration and time between start and deadline, and has `sourceTaskId` set to the original task's ID. Generated occurrences have `repetition: "NONE"`; the original task controls future generation. Confirmed schedule overlaps also apply to automatically generated occurrences.

Calendar repeats stay anchored to the original date: January 31 becomes February 28/29 and then March 31; a yearly February 29 repeat returns to February 29 in a leap year. New repeating tasks with a past start/deadline begin future repetition at the next upcoming occurrence. Occurrences missed during downtime are caught up after restart, in batches of up to 100 per scheduler run.

Changing a source task's schedule resets its future schedule. Setting its repetition to `NONE` or deleting it stops new occurrences. Already generated tasks remain independent tasks. Completing a source task does not stop its repetition.
