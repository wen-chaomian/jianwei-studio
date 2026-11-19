package com.shiguang.studio.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_user")
public class SysUser {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String phone;
  private String passwordHash;
  private String displayName;
  private Boolean enabled;
  private Integer authVersion;
}
