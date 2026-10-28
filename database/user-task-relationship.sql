-- One-time MySQL migration for the existing database, before restarting the backend.
-- The four existing tasks all belong to user 1, as confirmed by the user.
-- Legacy priorities use High and Mid; normalize them to the Java enum names.
ALTER TABLE tasks MODIFY COLUMN priority VARCHAR(255) NOT NULL;
UPDATE tasks
SET priority = CASE UPPER(TRIM(priority))
    WHEN 'MID' THEN 'MEDIUM'
    ELSE UPPER(TRIM(priority))
END;
ALTER TABLE tasks MODIFY COLUMN priority ENUM('LOW', 'MEDIUM', 'HIGH', 'NOT_SET') NOT NULL;

ALTER TABLE tasks ADD COLUMN user_id BIGINT NULL;
UPDATE tasks SET user_id = 1;
ALTER TABLE tasks MODIFY COLUMN user_id BIGINT NOT NULL;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users (id);
