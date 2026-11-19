package com.shiguang.studio.mapper.contracts;

import com.shiguang.studio.entity.Appointment;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

public interface WorkflowStatements {
  @Select("SELECT * FROM appointment WHERE id=#{id} FOR UPDATE")
  Appointment lockAppointment(long id);

  @Select("SELECT version FROM appointment WHERE id=#{id}")
  int version(long id);

  @Update("UPDATE appointment SET status=#{status},version=version+1 WHERE id=#{id}")
  void status(@Param("id") long id, @Param("status") String status);

  @Update(
      "UPDATE appointment SET"
          + " start_time=#{startTime},shoot_end_time=#{shootEndTime},occupied_end_time=#{occupiedEndTime},photographer_id=#{photographerId},photographer_name=#{photographerName},venue_id=#{venueId},venue_name=#{venueName},version=version+1"
          + " WHERE id=#{id}")
  void reschedule(Appointment a);

  @Insert(
      "INSERT INTO appointment_log(appointment_id,action,operator_id,detail)"
          + " VALUES(#{id},#{action},#{user},#{detail})")
  void log(
      @Param("id") long id,
      @Param("action") String action,
      @Param("user") long user,
      @Param("detail") String detail);

  @Select(
      "SELECT l.id,l.action,l.detail,l.created_at AS createdAt,u.display_name AS operator FROM"
          + " appointment_log l JOIN sys_user u ON l.operator_id=u.id WHERE appointment_id=#{id}"
          + " ORDER BY l.id DESC")
  List<Map<String, Object>> logs(long id);

  @Select(
      "SELECT appointment_id AS appointmentId,checked_in_at AS checkedInAt,started_at AS"
          + " startedAt,completed_at AS completedAt,notes FROM service_record WHERE"
          + " appointment_id=#{id}")
  Map<String, Object> service(long id);

  @Insert("INSERT IGNORE INTO service_record(appointment_id) VALUES(#{id})")
  void ensureService(long id);

  @Update(
      "UPDATE service_record SET"
          + " checked_in_at=COALESCE(checked_in_at,#{checkin}),started_at=COALESCE(started_at,#{start}),completed_at=COALESCE(completed_at,#{complete}),operator_id=#{user}"
          + " WHERE appointment_id=#{id}")
  void serviceTimes(
      @Param("id") long id,
      @Param("checkin") LocalDateTime checkin,
      @Param("start") LocalDateTime start,
      @Param("complete") LocalDateTime complete,
      @Param("user") long user);

  @Update("UPDATE service_record SET notes=#{notes},operator_id=#{user} WHERE appointment_id=#{id}")
  void notes(@Param("id") long id, @Param("notes") String notes, @Param("user") long user);

  @Update("UPDATE appointment SET version=version+1 WHERE id=#{id}")
  void touch(long id);
}
