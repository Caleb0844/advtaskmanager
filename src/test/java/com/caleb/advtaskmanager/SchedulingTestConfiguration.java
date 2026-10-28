package com.caleb.advtaskmanager;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.caleb.advtaskmanager.Services.TaskRepetitionService;
import com.caleb.advtaskmanager.Services.TaskScheduleService;
import com.caleb.advtaskmanager.Services.TaskService;
import com.caleb.advtaskmanager.Services.UserService;
import com.caleb.advtaskmanager.Services.TaskReminderService;
import com.caleb.advtaskmanager.Services.TaskPendingService;

@TestConfiguration
@Import({TaskService.class, UserService.class, TaskScheduleService.class, TaskRepetitionService.class,
    TaskClockTestConfiguration.class, TaskReminderService.class, TaskPendingService.class})
public class SchedulingTestConfiguration {
}
