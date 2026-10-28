# Configuration

The application imports an optional `.env` file and reads database credentials
from the environment. Copy `.env.example` to `.env` for local development and
replace every placeholder with a real value. The `.env` file is ignored by
Git and must never contain credentials that are committed to the repository.

Required variables:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `AUTH_JWT_SECRET` (at least 32 characters)

`SPRING_JPA_SHOW_SQL` controls Hibernate SQL logging and defaults to `false`.
Set it to `true` only when SQL logging is needed for local troubleshooting.
`AUTH_ENABLED` can disable authentication for local or test scenarios; the
application logs a warning whenever it is set to `false`.

The test profile supplies an isolated H2 database and its own valid JWT secret.

## Task date windows

Task date endpoints use the zone configured by `tasks.time-zone` (default
`Africa/Nairobi`):

- `GET /api/tasks/today` includes tasks whose `startsAt` falls from local
  midnight, inclusive, through the next local midnight, exclusive.
- `GET /api/tasks/week` uses an ISO week: Monday at local midnight, inclusive,
  through the following Monday at local midnight, exclusive.
- `GET /api/tasks/upcoming?days=N` includes non-completed tasks starting from
  the current local time, inclusive, through N local days later, exclusive.
  `N` must be positive and defaults to `7`.
- `GET /api/tasks/overdue` includes non-completed tasks whose `endsAt` has
  passed, or whose `dueDate` has passed when no end time is set.

All four endpoints are scoped to the authenticated user and order results by
`startsAt`, then task ID. When authentication is deliberately disabled, the
application's existing unrestricted test/development behavior applies.

## Account endpoints

- `GET /api/users/me` returns the authenticated user's profile and never
  includes password fields or the stored password hash.
- `PUT /api/users/me/password` requires `currentPassword` and
  `newPassword`; the new password must contain at least 8 characters.
- Login failures are tracked in memory per username. After 5 consecutive
  failures, login is locked for 15 minutes. A successful login resets the
  counter. Locked attempts return HTTP 429. This state is local to one
  application process.
