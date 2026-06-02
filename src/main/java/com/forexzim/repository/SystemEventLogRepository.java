package com.forexzim.repository;

import com.forexzim.model.SystemEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SystemEventLogRepository extends JpaRepository<SystemEventLog, Long> {

    List<SystemEventLog> findByEventTypeOrderByCreatedAtDesc(SystemEventLog.EventType eventType);

    List<SystemEventLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);

    // Two optional filters; null means "don't filter by this field"
    @Query("SELECT e FROM SystemEventLog e WHERE e.eventType = :type ORDER BY e.createdAt DESC")
    List<SystemEventLog> findByEventTypeOnly(@Param("type") SystemEventLog.EventType type);

    @Query("SELECT e FROM SystemEventLog e WHERE e.createdAt >= :after ORDER BY e.createdAt DESC")
    List<SystemEventLog> findByCreatedAtOnly(@Param("after") LocalDateTime after);

    @Query("SELECT e FROM SystemEventLog e WHERE e.eventType = :type AND e.createdAt >= :after ORDER BY e.createdAt DESC")
    List<SystemEventLog> findByEventTypeAndAfter(
            @Param("type") SystemEventLog.EventType type,
            @Param("after") LocalDateTime after);

    // Catch-all with no filters
    List<SystemEventLog> findAllByOrderByCreatedAtDesc();
}