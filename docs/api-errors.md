# API errors

User and task endpoints return the same JSON structure for errors:

```json
{
  "timestamp": "2026-10-22T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Please correct the invalid fields",
  "path": "/api/tasks/1",
  "fieldErrors": {
    "name": "Task name is required"
  }
}
```

The UI can display `message` and put each `fieldErrors` entry beside its input. Fields without errors are omitted. Stack traces and database error details stay in server logs.

| HTTP status | Meaning |
| --- | --- |
| 400 | Invalid JSON, invalid options/dates/IDs, missing fields, or an invalid update |
| 404 | Task, user, or route does not exist |
| 405 | HTTP method is unsupported; the `Allow` header lists supported methods |
| 409 | Overlap confirmation, duplicate username, user still owns tasks, or concurrent change |
| 415 | Unsupported request content type; use `application/json` |
| 500 | Unexpected server error |
| 503 | Database connection/resource failure; try again later |

Only an overlap response includes `confirmationRequired: true` and `conflictingTasks`. For an overlap, repeat the original POST or PUT with `?confirmOverlap=true` to save alongside existing tasks. Declining means discarding the request; neither task is changed. Other 409 responses do not request overlap confirmation.

Task names and descriptions must be nonblank and at most 255 characters. User names, usernames, and email addresses have the same length limit; email addresses must be valid. IDs must be positive. `endsAt` remains optional; when supplied it requires `startsAt` and must follow it. Repetition and reminders remain optional.

`PUT /api/tasks/{id}` accepts any subset of editable fields. Empty updates and unknown fields are rejected to catch input mistakes. `completed` must be a JSON boolean. Failed validation or declined overlap confirmation leaves the saved task intact. Server-generated IDs, timestamps, reminder times, and recurrence metadata cannot be changed through this endpoint.

Deleting a task that does not exist returns 404. A user who still owns tasks cannot be deleted: first delete those tasks or reassign each task with `PUT /api/tasks/{taskId}` and `{"user":{"id":anotherUserId}}`. Deleting a user does not automatically delete their tasks.

Authentication is enabled by default. Register with `POST /api/users` using a password of at least 8 characters, then call `POST /api/auth/login` with the username and password. Send the returned token as `Authorization: Bearer <token>` on all user/task requests. Users can access only their own profile and tasks; task ownership is taken from the authenticated user and cannot be used to access another user's data. Reminder notifications remain future work.
