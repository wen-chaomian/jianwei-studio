package com.shiguang.studio.mapper.contracts;

import com.shiguang.studio.entity.*;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

public interface ScheduleStatements {
  @Insert(
      "INSERT INTO booking_resource_slot_lock(resource_kind,resource_id,slot_start)"
          + " VALUES(#{kind},#{id},#{start}) ON DUPLICATE KEY UPDATE slot_start=VALUES(slot_start)")
  void lockResourceSlot(
      @Param("kind") String kind, @Param("id") long id, @Param("start") LocalDateTime start);

  @Select("SELECT id FROM booking_rule WHERE id=1 FOR SHARE")
  Long lockConfigurationShared();

  @Select("SELECT id FROM booking_rule WHERE id=1 FOR UPDATE")
  Long lockConfigurationExclusive();

  @Select("SELECT resource_id,start_time,end_time FROM ("
      + "SELECT s.resource_id,p.start_time,p.end_time FROM ${table} s JOIN ${table}_period p ON p.schedule_id=s.id WHERE s.work_date=#{date} AND s.enabled=1 "
      + "UNION ALL SELECT w.resource_id,p.start_time,p.end_time FROM ${table}_weekly w JOIN ${table}_weekly_period p ON p.schedule_id=w.id "
      + "WHERE w.day_of_week=WEEKDAY(#{date})+1 AND w.enabled=1 AND NOT EXISTS(SELECT 1 FROM ${table} d WHERE d.resource_id=w.resource_id AND d.work_date=#{date})"
      + ") periods ORDER BY resource_id,start_time")
  List<ResourcePeriod> dayPeriods(@Param("table") String table,@Param("date") LocalDate date);

  // The service supplies table names from its two-value whitelist only.
  @Select("SELECT id,resource_id,work_date,enabled,'DATE' AS source FROM ${table} WHERE resource_id=#{id} AND work_date=#{date} "
      + "UNION ALL SELECT id,resource_id,#{date} AS work_date,enabled,'WEEKLY' AS source FROM ${table}_weekly w "
      + "WHERE resource_id=#{id} AND day_of_week=WEEKDAY(#{date})+1 AND NOT EXISTS(SELECT 1 FROM ${table} d WHERE d.resource_id=#{id} AND d.work_date=#{date})")
  Schedule schedule(@Param("table") String table,@Param("id") long id,@Param("date") LocalDate date);

  @Select("SELECT * FROM ${table} WHERE resource_id=#{id} AND work_date=#{date}")
  Schedule dateSchedule(@Param("table") String table,@Param("id") long id,@Param("date") LocalDate date);

  @Select("SELECT * FROM ${table}_weekly WHERE resource_id=#{id} AND day_of_week=#{day}")
  Schedule weeklySchedule(@Param("table") String table,@Param("id") long id,@Param("day") int day);

  @Insert("INSERT INTO ${table}_weekly(resource_id,day_of_week,enabled) VALUES(#{id},#{day},#{enabled}) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)")
  void setWeeklySchedule(@Param("table") String table,@Param("id") long id,@Param("day") int day,@Param("enabled") boolean enabled);

  @Delete("DELETE FROM ${table} WHERE id=#{id}")
  void deleteDateSchedule(@Param("table") String table,@Param("id") long id);

  @Select("SELECT * FROM ${table} WHERE resource_id=#{id} AND work_date=#{date} FOR UPDATE")
  Schedule lockSchedule(
      @Param("table") String table, @Param("id") long id, @Param("date") LocalDate date);

  @Insert(
      "INSERT INTO ${table}(resource_id,work_date,enabled) VALUES(#{id},#{date},1) ON DUPLICATE KEY"
          + " UPDATE id=id")
  void ensureSchedule(
      @Param("table") String table, @Param("id") long id, @Param("date") LocalDate date);

  @Update("UPDATE ${table} SET enabled=#{enabled} WHERE id=#{id}")
  void setSchedule(
      @Param("table") String table, @Param("id") long id, @Param("enabled") boolean enabled);

  @Select(
      "SELECT start_time,end_time FROM ${table}_period WHERE schedule_id=#{id} ORDER BY start_time")
  List<SchedulePeriod> periods(@Param("table") String table, @Param("id") long id);

  @Delete("DELETE FROM ${table}_period WHERE schedule_id=#{id}")
  void clearPeriods(@Param("table") String table, @Param("id") long id);

  @Insert(
      "INSERT INTO ${table}_period(schedule_id,start_time,end_time) VALUES(#{id},#{start},#{end})")
  void insertPeriod(
      @Param("table") String table,
      @Param("id") long id,
      @Param("start") LocalTime start,
      @Param("end") LocalTime end);
}
