package com.caleb.advtaskmanager.Services;

import java.time.DateTimeException;
import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.caleb.advtaskmanager.Entities.Task;
import com.caleb.advtaskmanager.Enums.Reminder;

@Service
public class TaskReminderService {

    public void configure(Task task) {
        Reminder reminder = task.getReminder() == null ? Reminder.NONE : task.getReminder();
        task.setReminder(reminder);
        if (reminder == Reminder.NONE) {
            task.setReminderAmount(null);
            task.setReminderUnit(null);
            task.setRemindAt(null);
            return;
        }
        if (reminder == Reminder.CUSTOM && (task.getReminderAmount() == null
                || task.getReminderAmount() <= 0 || task.getReminderUnit() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Custom reminders require a positive reminderAmount and reminderUnit");
        }
        try {
            Duration before = reminder == Reminder.CUSTOM
                ? task.getReminderUnit().duration(task.getReminderAmount()) : Duration.ofMinutes(reminder.minutes);
            task.setRemindAt((task.getStartsAt() == null ? task.getDueDate() : task.getStartsAt()).minus(before));
        } catch (ArithmeticException | DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reminder duration is too large");
        }
        if (reminder != Reminder.CUSTOM) {
            task.setReminderAmount(null);
            task.setReminderUnit(null);
        }
    }
}
