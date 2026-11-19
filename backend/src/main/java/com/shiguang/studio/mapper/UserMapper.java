package com.shiguang.studio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiguang.studio.entity.SysUser;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper extends BaseMapper<SysUser> {
  @Select("SELECT * FROM sys_user WHERE phone=#{phone} FOR UPDATE")
  SysUser lockByPhone(String phone);

  @Select(
      "SELECT r.code FROM sys_role r JOIN sys_user_role ur ON r.id=ur.role_id WHERE"
          + " ur.user_id=#{id}")
  List<String> roles(Long id);

  @Insert(
      "INSERT INTO sys_user_role(user_id,role_id) SELECT #{id},id FROM sys_role WHERE code=#{role}")
  void addRole(@Param("id") Long id, @Param("role") String role);

  @Insert(
      "INSERT INTO customer(user_id,name,phone,source)"
          + " VALUES(#{id},#{displayName},#{phone},'REGISTER')")
  void addCustomer(SysUser user);

  @Insert(
      "INSERT INTO employee(user_id,employee_no,name,bookable)"
          + " VALUES(#{id},#{number},#{name},#{bookable})")
  void addEmployee(
      @Param("id") Long id,
      @Param("number") String number,
      @Param("name") String name,
      @Param("bookable") boolean bookable);
}
