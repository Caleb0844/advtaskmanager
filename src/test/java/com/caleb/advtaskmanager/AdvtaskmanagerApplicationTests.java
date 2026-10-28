package com.caleb.advtaskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.caleb.advtaskmanager.Entities.User;
import com.caleb.advtaskmanager.Entities.Task;
import com.caleb.advtaskmanager.Enums.Priority;
import com.caleb.advtaskmanager.Enums.TaskStatus;
import com.caleb.advtaskmanager.Repositories.TaskRepository;
import com.caleb.advtaskmanager.Repositories.UserRepository;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TaskClockTestConfiguration.class)
@Transactional
class AdvtaskmanagerApplicationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void thunderClientCanConfirmConflictAndReadScheduledTasksOnProfile() throws Exception {
		User user = userRepository.saveAndFlush(User.builder().name("API user").username("api")
			.email("api@example.com").build());
		String original = body(user.getId(), "12:00:00", "14:00:00", "DAILY");
		String saved = mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(original))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.repetition").value("DAILY"))
			.andExpect(jsonPath("$.nextOccurrenceAt").value("2026-10-24T12:00:00"))
			.andExpect(jsonPath("$.nextOccurrenceNumber").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		long originalId = JsonMapper.builder().build().readTree(saved).get("id").asLong();
		String overlapping = body(user.getId(), "13:00:00", "14:00:00", "NONE")
			.replace("\"endsAt\": \"2026-10-23T14:00:00\",", "");

		mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(overlapping))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.confirmationRequired").value(true))
			.andExpect(jsonPath("$.conflictingTasks[0].taskId").value((int) originalId));
		assertEquals(1, taskRepository.count());
		mvc.perform(post("/api/tasks").param("confirmOverlap", "true")
				.contentType(MediaType.APPLICATION_JSON).content(overlapping))
			.andExpect(status().isOk());
		assertEquals(2, taskRepository.count());
		assertEquals("2026-10-23T12:00", taskRepository.findById(originalId).orElseThrow().getStartsAt().toString());
		assertEquals("2026-10-23T14:00", taskRepository.findById(originalId).orElseThrow().getEndsAt().toString());
		mvc.perform(get("/api/users/{id}", user.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tasks.length()").value(2));
	}

	@Test
	void endTimeAndRepetitionCanBothBeOmittedWithoutWarnings() throws Exception {
		User user = userRepository.saveAndFlush(User.builder().name("Optional user").username("optionalapi")
			.email("optionalapi@example.com").build());
		String task = """
			{
			  "name": "Task without end time",
			  "description": "Optional scheduling fields",
			  "priority": "MEDIUM",
			  "status": "PENDING",
			  "user": {"id": %d},
			  "dueDate": "2026-10-23T14:00:00",
			  "startsAt": "2026-10-23T12:00:00"
			}
			""".formatted(user.getId());
		for (String request : new String[] {task, task, task.replace("T12:00:00", "T12:05:00")}) {
			mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.repetition").value("NONE"))
				.andExpect(jsonPath("$.endsAt").isEmpty());
		}
		assertEquals(3, taskRepository.count());
	}

	@Test
	void reschedulingUsesTheSameConfirmationProtocol() throws Exception {
		User user = userRepository.saveAndFlush(User.builder().name("Update user").username("updateapi")
			.email("updateapi@example.com").build());
		mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
				.content(body(user.getId(), "09:00:00", "10:00:00", "NONE")))
			.andExpect(status().isOk());
		String saved = mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
				.content(body(user.getId(), "10:00:00", "11:00:00", "NONE")))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		long taskId = JsonMapper.builder().build().readTree(saved).get("id").asLong();
		String update = body(user.getId(), "09:30:00", "10:30:00", "NONE");

		mvc.perform(put("/api/tasks/{id}", taskId).contentType(MediaType.APPLICATION_JSON).content(update))
			.andExpect(status().isConflict());
		mvc.perform(put("/api/tasks/{id}", taskId).param("confirmOverlap", "true")
				.contentType(MediaType.APPLICATION_JSON).content(update))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.startsAt").value("2026-10-23T09:30:00"));
		assertEquals(2, taskRepository.count());
	}

	@Test
	void invalidEndOrRepetitionReturnsBadRequestEvenWhenConfirmed() throws Exception {
		User user = userRepository.saveAndFlush(User.builder().name("Validation user").username("validationapi")
			.email("validationapi@example.com").build());
		mvc.perform(post("/api/tasks").param("confirmOverlap", "true").contentType(MediaType.APPLICATION_JSON)
				.content(body(user.getId(), "10:00:00", "09:00:00", "DAILY")))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
				.content(body(user.getId(), "09:00:00", "10:00:00", "HOURLY")))
			.andExpect(status().isBadRequest());
		assertEquals(0, taskRepository.count());
	}

	@Test
	void dateWindowsUseConfiguredTimezoneAndHalfOpenBoundaries() throws Exception {
		User user = userRepository.saveAndFlush(User.builder().name("Date windows").username("datewindows")
			.email("datewindows@example.com").build());
		taskRepository.saveAll(List.of(
			windowTask(user, "today start", "2026-10-22T00:00", "2026-10-22T01:00", TaskStatus.PENDING),
			windowTask(user, "today end", "2026-10-22T23:59", "2026-10-23T00:00", TaskStatus.PENDING),
			windowTask(user, "before upcoming boundary", "2026-10-23T02:59", "2026-10-23T04:00", TaskStatus.PENDING),
			windowTask(user, "upcoming boundary", "2026-10-23T03:00", "2026-10-23T04:00", TaskStatus.PENDING),
			windowTask(user, "week start", "2026-10-19T00:00", "2026-10-19T01:00", TaskStatus.PENDING),
			windowTask(user, "week end", "2026-10-25T23:59", "2026-10-26T00:00", TaskStatus.PENDING),
			windowTask(user, "next week", "2026-10-26T00:00", "2026-10-26T01:00", TaskStatus.PENDING)));
		taskRepository.flush();

		mvc.perform(get("/api/tasks/today")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].name").value("today start"))
			.andExpect(jsonPath("$[1].name").value("today end"));
		mvc.perform(get("/api/tasks/week")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(6))
			.andExpect(jsonPath("$[0].name").value("week start"))
			.andExpect(jsonPath("$[5].name").value("week end"));
		mvc.perform(get("/api/tasks/upcoming").param("days", "1")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].name").value("today end"))
			.andExpect(jsonPath("$[1].name").value("before upcoming boundary"));
		mvc.perform(get("/api/tasks/upcoming").param("days", "0"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void overdueExcludesCompletedTasksAndOrdersByStartTime() throws Exception {
		User user = userRepository.saveAndFlush(User.builder().name("Overdue").username("overdue")
			.email("overdue@example.com").build());
		taskRepository.saveAll(List.of(
			windowTask(user, "late start", "2026-10-20T01:00", "2026-10-20T02:00", TaskStatus.PENDING),
			windowTask(user, "late due", "2026-10-21T01:00", null, TaskStatus.POSTPONED),
			windowTask(user, "completed late", "2026-10-19T01:00", "2026-10-19T02:00", TaskStatus.COMPLETED),
			windowTask(user, "future", "2026-10-23T01:00", "2026-10-23T02:00", TaskStatus.PENDING)));
		taskRepository.flush();

		mvc.perform(get("/api/tasks/overdue")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].name").value("late start"))
			.andExpect(jsonPath("$[1].name").value("late due"));
	}

	private Task windowTask(User user, String name, String startsAt, String endsAt, TaskStatus status) {
		return Task.builder().user(user).name(name).description("Date window test").priority(Priority.MEDIUM)
			.status(status).startsAt(LocalDateTime.parse(startsAt))
			.endsAt(endsAt == null ? null : LocalDateTime.parse(endsAt))
			.dueDate(LocalDateTime.parse(endsAt == null ? startsAt : endsAt)).build();
	}

	private String body(Long userId, String start, String end, String repetition) {
		return """
			{
			  "name": "Planning",
			  "description": "Weekly planning",
			  "priority": "MEDIUM",
			  "status": "PENDING",
			  "user": {"id": %d},
			  "dueDate": "2026-10-23T12:00:00",
			  "startsAt": "2026-10-23T%s",
			  "endsAt": "2026-10-23T%s",
			  "repetition": "%s"
			}
			""".formatted(userId, start, end, repetition);
	}

	@Test
	void uiCanFetchAllSelectableOptions() throws Exception {
		mvc.perform(get("/api/tasks/options"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.statuses.length()").value(3))
			.andExpect(jsonPath("$.views[0]").value("ALL"))
			.andExpect(jsonPath("$.reminders.length()").value(7))
			.andExpect(jsonPath("$.reminderUnits.length()").value(4))
			.andExpect(jsonPath("$.postponeOptions.length()").value(10));
	}

	@Test
	void uiCanEditReminderTickCheckboxAndFilterCompletedTasks() throws Exception {
		User user = userRepository.saveAndFlush(User.builder().name("UI").username("ui")
			.email("ui@example.com").build());
		String saved = mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
				.content(body(user.getId(), "09:00:00", "10:00:00", "NONE")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(false))
			.andReturn().getResponse().getContentAsString();
		long id = JsonMapper.builder().build().readTree(saved).get("id").asLong();
		mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Edited\",\"reminder\":\"CUSTOM\",\"reminderAmount\":2,\"reminderUnit\":\"HOURS\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Edited"))
			.andExpect(jsonPath("$.remindAt").value("2026-10-23T07:00:00"))
			.andExpect(jsonPath("$.startsAt").value("2026-10-23T09:00:00"));
		mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.completed").value(true));
		mvc.perform(get("/api/tasks").param("userId", user.getId().toString()).param("status", "COMPLETED"))
			.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value((int) id));
		mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"completed\":false}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
		mvc.perform(get("/api/tasks").param("userId", user.getId().toString()).param("status", "COMPLETED"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void uiCanPostponeAndClearOptionalEndTimeAndReminder() throws Exception {
		User user = userRepository.saveAndFlush(User.builder().name("Postpone UI").username("postponeui")
			.email("postponeui@example.com").build());
		String saved = mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
				.content(body(user.getId(), "09:00:00", "10:00:00", "NONE")))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		long id = JsonMapper.builder().build().readTree(saved).get("id").asLong();
		mvc.perform(put("/api/tasks/{id}/postpone", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"option\":\"MINUTES_5\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("POSTPONED"))
			.andExpect(jsonPath("$.startsAt").value("2026-10-22T03:05:00"))
			.andExpect(jsonPath("$.endsAt").value("2026-10-22T04:05:00"));
		mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"endsAt\":null,\"reminder\":\"NONE\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.endsAt").isEmpty())
			.andExpect(jsonPath("$.remindAt").isEmpty());
		assertEquals(1, taskRepository.count());
	}
}
