package com.shiguang.studio.mapper.contracts;

import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

public interface AccountStatements {
  @Select(
      "SELECT id,user_id AS userId,name,phone,source,remark FROM customer WHERE name LIKE"
          + " CONCAT('%',#{q},'%') OR phone LIKE CONCAT('%',#{q},'%') ORDER BY id DESC LIMIT"
          + " #{size} OFFSET #{offset}")
  List<Map<String, Object>> customers(
      @Param("q") String q, @Param("size") int size, @Param("offset") int offset);

  @Select(
      "SELECT COUNT(*) FROM customer WHERE name LIKE CONCAT('%',#{q},'%') OR phone LIKE"
          + " CONCAT('%',#{q},'%')")
  long customerCount(String q);

  @Select("SELECT id,user_id AS userId,name,phone,source,remark FROM customer WHERE id=#{id}")
  Map<String, Object> customer(long id);

  @Update("UPDATE customer SET name=#{name},phone=#{phone},remark=#{remark} WHERE id=#{id}")
  void customerSave(
      @Param("id") long id,
      @Param("name") String name,
      @Param("phone") String phone,
      @Param("remark") String remark);

  @Select(
      "SELECT e.id,e.user_id AS userId,e.employee_no AS"
          + " employeeNo,e.name,e.bookable,e.enabled,u.phone,GROUP_CONCAT(r.code ORDER BY r.code)"
          + " AS roles FROM employee e JOIN sys_user u ON e.user_id=u.id JOIN sys_user_role ur ON"
          + " ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id GROUP BY e.id,u.id ORDER BY e.id")
  List<Map<String, Object>> employees();

  @Select("SELECT id,user_id AS userId FROM employee WHERE id=#{id} FOR UPDATE")
  Map<String, Object> lockEmployee(long id);

  @Update(
      "UPDATE employee SET"
          + " name=#{name},employee_no=#{number},enabled=#{enabled},bookable=#{bookable} WHERE"
          + " id=#{id}")
  void employeeSave(
      @Param("id") long id,
      @Param("name") String name,
      @Param("number") String number,
      @Param("enabled") boolean enabled,
      @Param("bookable") boolean bookable);

  @Update(
      "UPDATE sys_user SET"
          + " phone=#{phone},display_name=#{name},enabled=#{enabled},password_hash=COALESCE(#{passwordHash},password_hash)"
          + " WHERE id=#{id}")
  void employeeAccount(
      @Param("id") long id,
      @Param("phone") String phone,
      @Param("name") String name,
      @Param("enabled") boolean enabled,
      @Param("passwordHash") String passwordHash);

  @Update("UPDATE sys_user SET password_hash=#{passwordHash} WHERE id=#{id}")
  void password(@Param("id") long id, @Param("passwordHash") String passwordHash);

  @Update("UPDATE customer SET name=#{name},phone=#{phone} WHERE id=#{id}")
  void customerContact(
      @Param("id") long id, @Param("name") String name, @Param("phone") String phone);

  @Delete("DELETE FROM sys_user_role WHERE user_id=#{id}")
  void clearRoles(long id);

  @Update("UPDATE sys_user SET auth_version=auth_version+1 WHERE id=#{id}")
  void revoke(long id);

  @Select("SELECT auth_version FROM sys_user WHERE id=#{id}")
  Integer authVersion(long id);

  @Select("SELECT enabled FROM sys_user WHERE id=#{id}")
  Boolean enabled(long id);

  @Update("UPDATE sys_user SET display_name=#{name} WHERE id=#{id}")
  void profile(@Param("id") long id, @Param("name") String name);
}
