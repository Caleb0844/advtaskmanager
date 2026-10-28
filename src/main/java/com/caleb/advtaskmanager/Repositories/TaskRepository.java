package com.caleb.advtaskmanager.Repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.caleb.advtaskmanager.Entities.Task;
import com.caleb.advtaskmanager.Enums.TaskStatus;

import jakarta.persistence.LockModeType;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("select t from Task t where (:userId is null or t.user.id = :userId) "
        + "and (:status is null or t.status = :status) order by t.dueDate, t.id")
    List<Task> findForList(@Param("userId") Long userId, @Param("status") TaskStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.status = com.caleb.advtaskmanager.Enums.TaskStatus.POSTPONED "
        + "and ((t.endsAt is not null and t.endsAt <= :now) "
        + "or (t.endsAt is null and t.dueDate <= :now))")
    List<Task> findMissedPostponedTasks(@Param("now") LocalDateTime now);

    List<Task> findByUser_IdAndStartsAtIsNotNull(Long userId);

    @Query("select t from Task t where (:userId is null or t.user.id = :userId) "
        + "and t.startsAt >= :from and t.startsAt < :to order by t.startsAt, t.id")
    List<Task> findStartingBetween(@Param("userId") Long userId, @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("select t from Task t where (:userId is null or t.user.id = :userId) "
        + "and t.status <> com.caleb.advtaskmanager.Enums.TaskStatus.COMPLETED "
        + "and t.startsAt >= :from and t.startsAt < :to order by t.startsAt, t.id")
    List<Task> findUpcomingBetween(@Param("userId") Long userId, @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("select t from Task t where (:userId is null or t.user.id = :userId) "
        + "and t.status <> com.caleb.advtaskmanager.Enums.TaskStatus.COMPLETED "
        + "and ((t.endsAt is not null and t.endsAt <= :now) "
        + "or (t.endsAt is null and t.dueDate <= :now)) order by t.startsAt, t.id")
    List<Task> findOverdue(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.sourceTaskId is null and t.nextOccurrenceAt <= :now "
        + "order by t.nextOccurrenceAt, t.id")
    List<Task> findDueRepeatingTasks(@Param("now") LocalDateTime now, Pageable pageable);

}
