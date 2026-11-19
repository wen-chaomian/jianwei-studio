package com.shiguang.studio.mapper.contracts;

import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

public interface NotificationStatements {
  @Insert(
      "INSERT INTO notification(user_id,type,title,content,appointment_id)"
          + " VALUES(#{user},'BOOKING',#{title},#{content},#{appointment})")
  void notify(
      @Param("user") long user,
      @Param("title") String title,
      @Param("content") String content,
      @Param("appointment") Long appointment);

  @Select("SELECT user_id FROM customer WHERE id=#{id}")
  Long customerUser(long id);

  @Select("SELECT user_id FROM employee WHERE id=#{id}")
  Long employeeUser(long id);

  @Select(
      "SELECT id,title,content,appointment_id AS appointmentId,read_at AS readAt,created_at AS"
          + " createdAt FROM notification WHERE user_id=#{user} AND (#{financial}=TRUE OR title NOT"
          + " IN ('payment','refund','已登记收款','已登记退款')) ORDER BY id DESC LIMIT #{size} OFFSET"
          + " #{offset}")
  List<Map<String, Object>> notifications(
      @Param("user") long user,
      @Param("size") int size,
      @Param("offset") int offset,
      @Param("financial") boolean financial);

  @Select(
      "SELECT COUNT(*) FROM notification WHERE user_id=#{user} AND (#{financial}=TRUE OR title NOT"
          + " IN ('payment','refund','已登记收款','已登记退款'))")
  long notificationCount(@Param("user") long user, @Param("financial") boolean financial);

  @Select(
      "SELECT COUNT(*) FROM notification WHERE user_id=#{user} AND (#{financial}=TRUE OR title NOT"
          + " IN ('payment','refund','已登记收款','已登记退款')) AND read_at IS NULL")
  long unread(@Param("user") long user, @Param("financial") boolean financial);

  @Update(
      "UPDATE notification SET read_at=COALESCE(read_at,NOW()) WHERE id=#{id} AND user_id=#{user}")
  int read(@Param("id") long id, @Param("user") long user);

  @Insert(
      "INSERT INTO notification(user_id,type,title,content,appointment_id) SELECT"
          + " u.id,'BOOKING','客户申请待处理',#{content},#{appointment} FROM sys_user u WHERE u.enabled=1"
          + " AND EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE"
          + " ur.user_id=u.id AND r.code IN ('ADMIN','RECEPTIONIST'))")
  void notifyDesk(@Param("appointment") long appointment, @Param("content") String content);
}
