package com.shiguang.studio.mapper.contracts;

import com.shiguang.studio.entity.*;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

public interface BookingIdentityStatements {
  @Select("SELECT id FROM sys_user WHERE id=#{id} FOR UPDATE")
  Long lockUser(long id);

  @Select("SELECT id FROM customer WHERE user_id=#{id}")
  Long customerId(long id);

  @Select("SELECT display_name FROM sys_user WHERE id=#{id}")
  String customerName(long id);

  @Select("SELECT phone FROM sys_user WHERE id=#{id}")
  String customerPhone(long id);

  @Insert("INSERT INTO customer(name,phone,source) VALUES(#{name},#{phone},'STAFF')")
  void insertGuest(@Param("name") String name, @Param("phone") String phone);

  // Match the login phone, never an editable contact phone, when linking a staff booking.
  @Select("SELECT c.id FROM customer c JOIN sys_user u ON u.id=c.user_id WHERE u.phone=#{phone}")
  Long registeredCustomerByPhone(String phone);

  @Select("SELECT LAST_INSERT_ID()")
  Long lastId();

  @Select("SELECT id FROM employee WHERE user_id=#{id}")
  Long employeeId(long id);
}
