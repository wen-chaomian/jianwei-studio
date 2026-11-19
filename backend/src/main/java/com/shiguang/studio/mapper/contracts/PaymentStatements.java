package com.shiguang.studio.mapper.contracts;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

public interface PaymentStatements {
  @Select(
      "SELECT id,type,amount,method,original_id AS originalId,reason,created_at AS createdAt FROM"
          + " payment_record WHERE appointment_id=#{id} ORDER BY id DESC")
  List<Map<String, Object>> payments(long id);

  @Select("SELECT * FROM payment_record WHERE id=#{id}")
  Map<String, Object> payment(long id);

  @Select(
      "SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE original_id=#{id} AND"
          + " type='REFUND'")
  BigDecimal refunded(long id);

  @Select(
      "SELECT COALESCE(SUM(CASE WHEN type='PAYMENT' THEN amount ELSE -amount END),0) FROM"
          + " payment_record WHERE appointment_id=#{id}")
  BigDecimal net(long id);

  @Select(
      "SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE appointment_id=#{id} AND"
          + " type=#{type}")
  BigDecimal total(@Param("id") long id, @Param("type") String type);

  @Insert(
      "INSERT INTO payment_record(appointment_id,type,amount,method,original_id,reason,operator_id)"
          + " VALUES(#{id},#{type},#{amount},#{method},#{original},#{reason},#{user})")
  void paymentSave(
      @Param("id") long id,
      @Param("type") String type,
      @Param("amount") BigDecimal amount,
      @Param("method") String method,
      @Param("original") Long original,
      @Param("reason") String reason,
      @Param("user") long user);

  @Select("SELECT fingerprint FROM command_receipt WHERE user_id=#{user} AND request_id=#{request}")
  String receipt(@Param("user") long user, @Param("request") String request);

  @Insert("INSERT INTO command_receipt VALUES(#{user},#{request},#{fingerprint})")
  void receiptSave(
      @Param("user") long user,
      @Param("request") String request,
      @Param("fingerprint") String fingerprint);
}
