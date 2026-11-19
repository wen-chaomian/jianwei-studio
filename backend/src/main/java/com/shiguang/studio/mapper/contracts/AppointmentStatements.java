package com.shiguang.studio.mapper.contracts;

import com.shiguang.studio.entity.*;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

public interface AppointmentStatements {
  @Select(
      "SELECT id AS appointment_id,photographer_id,venue_id,start_time,occupied_end_time AS"
          + " end_time FROM appointment WHERE status NOT IN ('CANCELLED','NO_SHOW') AND start_time"
          + " >= #{start} AND start_time < #{end} AND occupied_end_time > #{start}")
  List<ResourceOccupancy> dayOccupancy(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Select(
      "SELECT * FROM appointment WHERE ${column}=#{id} AND status NOT IN ('CANCELLED','NO_SHOW')"
          + " AND start_time < #{end} AND occupied_end_time > #{start}")
  List<Appointment> conflicts(
      @Param("column") String column,
      @Param("id") long id,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Select("SELECT * FROM appointment WHERE request_id=#{id}")
  Appointment byRequest(String id);

  @Select(
      "SELECT a.*,(SELECT r.type FROM customer_request r WHERE r.appointment_id=a.id AND r.type IN"
          + " ('CANCEL','RESCHEDULE') AND r.status IN ('PENDING','PROCESSING') ORDER BY r.id LIMIT"
          + " 1) AS pending_request_type FROM appointment a WHERE a.id=#{id}")
  Appointment appointment(long id);

  @Insert(
      "INSERT INTO"
          + " appointment(appointment_no,customer_id,package_id,photographer_id,venue_id,start_time,shoot_end_time,occupied_end_time,amount,package_name,photographer_name,venue_name,customer_name,customer_phone,duration_minutes,buffer_minutes,source,remark,request_id,request_fingerprint,created_by)"
          + " VALUES(#{appointmentNo},#{customerId},#{packageId},#{photographerId},#{venueId},#{startTime},#{shootEndTime},#{occupiedEndTime},#{amount},#{packageName},#{photographerName},#{venueName},#{customerName},#{customerPhone},#{durationMinutes},#{bufferMinutes},#{source},#{remark},#{requestId},#{requestFingerprint},#{createdBy})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insertAppointment(Appointment a);

  @Insert(
      "INSERT INTO appointment_log(appointment_id,action,operator_id,detail)"
          + " VALUES(#{id},'CREATE',#{user},'预约创建并确认')")
  void log(@Param("id") long id, @Param("user") long user);

  List<Appointment> appointments(
      @Param("customer") Long customer,
      @Param("photographer") Long photographer,
      @Param("date") LocalDate date,
      @Param("size") int size,
      @Param("offset") int offset);

  long countAppointments(
      @Param("customer") Long customer,
      @Param("photographer") Long photographer,
      @Param("date") LocalDate date);
}
