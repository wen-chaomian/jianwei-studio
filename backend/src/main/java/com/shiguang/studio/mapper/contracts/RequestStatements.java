package com.shiguang.studio.mapper.contracts;

import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

public interface RequestStatements {
  @Insert(
      "INSERT INTO customer_request(customer_id,appointment_id,type,content)"
          + " VALUES(#{customer},#{appointment},#{type},#{content})")
  void requestCreate(
      @Param("customer") long customer,
      @Param("appointment") long appointment,
      @Param("type") String type,
      @Param("content") String content);

  @Select("SELECT * FROM customer_request WHERE id=#{id}")
  Map<String, Object> request(long id);

  @Select(
      "SELECT COUNT(*) FROM customer_request WHERE appointment_id=#{id} AND type IN"
          + " ('CANCEL','RESCHEDULE') AND status IN ('PENDING','PROCESSING')")
  long pendingChanges(long id);

  @Select(
      "SELECT id,type,content,status,result,version,created_at AS createdAt FROM customer_request"
          + " WHERE appointment_id=#{id} ORDER BY id DESC")
  List<Map<String, Object>> appointmentRequests(long id);

  @Select("SELECT * FROM customer_request WHERE id=#{id} FOR UPDATE")
  Map<String, Object> lockRequest(long id);

  @Update(
      "UPDATE customer_request SET"
          + " status=#{status},handler_id=#{user},result=#{result},version=version+1 WHERE"
          + " id=#{id}")
  void handle(
      @Param("id") long id,
      @Param("status") String status,
      @Param("user") long user,
      @Param("result") String result);

  List<Map<String, Object>> requests(
      @Param("customer") Long customer, @Param("size") int size, @Param("offset") int offset);

  long requestCount(@Param("customer") Long customer);

  @Select("SELECT COUNT(*) FROM customer_request WHERE status IN ('PENDING','PROCESSING')")
  long pendingRequests();
}
