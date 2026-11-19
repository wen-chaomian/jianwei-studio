package com.shiguang.studio.mapper.contracts;

import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

public interface ManagementStatements {
  @Select("SELECT * FROM booking_rule WHERE id=1")
  Map<String, Object> rule();

  @Update(
      "UPDATE booking_rule SET"
          + " open_time=#{openTime},close_time=#{closeTime},slot_minutes=#{slotMinutes},min_advance_hours=#{minAdvanceHours},max_advance_days=#{maxAdvanceDays},change_cutoff_hours=#{changeCutoffHours}"
          + " WHERE id=1")
  void saveRule(com.shiguang.studio.dto.OperationsRequests.RuleRequest r);

  @Insert(
      "INSERT INTO operation_log(operator_id,module,action,target_id,detail)"
          + " VALUES(#{user},#{module},#{action},#{target},#{detail})")
  void audit(
      @Param("user") long user,
      @Param("module") String module,
      @Param("action") String action,
      @Param("target") Long target,
      @Param("detail") String detail);

  @Select(
      "SELECT l.*,u.display_name AS operatorName FROM operation_log l JOIN sys_user u ON"
          + " l.operator_id=u.id ORDER BY l.id DESC LIMIT #{size} OFFSET #{offset}")
  List<Map<String, Object>> audits(@Param("size") int size, @Param("offset") int offset);

  @Select("SELECT COUNT(*) FROM operation_log")
  long auditCount();

  @Select(
      "SELECT COUNT(*) AS appointments,COALESCE(SUM(status='COMPLETED'),0) AS"
          + " completed,COALESCE(SUM(status='CANCELLED'),0) AS"
          + " cancelled,COALESCE(SUM(status='NO_SHOW'),0) AS noShow FROM appointment WHERE"
          + " start_time >= #{from} AND start_time < #{to}")
  Map<String, Object> stats(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Select(
      "SELECT COALESCE(SUM(CASE WHEN type='PAYMENT' THEN amount ELSE 0 END),0) AS"
          + " received,COALESCE(SUM(CASE WHEN type='REFUND' THEN amount ELSE 0 END),0) AS"
          + " refunded,COALESCE(SUM(CASE WHEN type='PAYMENT' THEN amount ELSE -amount END),0) AS"
          + " net FROM payment_record WHERE created_at >= #{from} AND created_at < #{to}")
  Map<String, Object> revenue(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
