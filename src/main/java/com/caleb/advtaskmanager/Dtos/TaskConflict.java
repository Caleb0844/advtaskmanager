package com.caleb.advtaskmanager.Dtos;

import java.time.LocalDateTime;

public record TaskConflict(Long taskId, String name, LocalDateTime startsAt, LocalDateTime endsAt) {
}
