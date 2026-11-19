package com.shiguang.studio.config;

import java.time.*;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
@ConditionalOnProperty(name = "studio.seed.enabled", havingValue = "true")
public class StudioDataInitializer implements ApplicationRunner {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;

  private final String seedPassword;

  public StudioDataInitializer(JdbcTemplate db, PasswordEncoder encoder,
      @org.springframework.beans.factory.annotation.Value("${studio.seed.password}") String seedPassword) {
    if (seedPassword.isBlank() || seedPassword.length() < 8
        || seedPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("Set STUDIO_SEED_PASSWORD to 8 or more characters (max 72 UTF-8 bytes)");
    this.seedPassword = seedPassword;
    this.db = db;
    this.encoder = encoder;
  }

  private long id(String sql, Object... args) {
    return db.queryForObject(sql, Long.class, args);
  }

  private long user(String phone, String name, String password, String role) {
    var ids = db.queryForList("SELECT id FROM sys_user WHERE phone=?", Long.class, phone);
    if (!ids.isEmpty())
      throw new IllegalStateException("Demo account already exists. Use a fresh demo database and a separate bootstrap phone.");
    db.update(
        "INSERT INTO sys_user(phone,password_hash,display_name) VALUES(?,?,?)",
        phone, encoder.encode(password), name);
    long uid = id("SELECT LAST_INSERT_ID()");
    db.update("INSERT IGNORE INTO sys_user_role SELECT ?,id FROM sys_role WHERE code=?", uid, role);
    if (role.equals("CUSTOMER")) {
      db.update(
          "INSERT INTO customer(user_id,name,phone,source,remark) VALUES(?,?,?,'REGISTER','') ON"
              + " DUPLICATE KEY UPDATE name=VALUES(name),phone=VALUES(phone)",
          uid,
          name,
          phone);
    } else {
      db.update(
          "INSERT INTO employee(user_id,employee_no,name,bookable) VALUES(?,?,?,?) ON DUPLICATE KEY"
              + " UPDATE name=VALUES(name),bookable=VALUES(bookable)",
          uid,
          "SG-" + uid,
          name,
          role.equals("PHOTOGRAPHER"));
    }
    return uid;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (db.update("INSERT IGNORE INTO initialization_marker(name) VALUES('studio-v1-20261025')")
        == 0) return;
    long admin = user("19999000001", "工作室管理员", seedPassword, "ADMIN");
    user("19999000002", "王女士", seedPassword, "RECEPTIONIST");
    long li = user("19999000003", "李老师", seedPassword, "PHOTOGRAPHER");
    long chen = user("19999000004", "陈老师", seedPassword, "PHOTOGRAPHER");
    user("19999000005", "周女士", seedPassword, "RECEPTIONIST");
    long zhao = user("19999000006", "赵老师", seedPassword, "PHOTOGRAPHER");
    long[] photos = {
      id("SELECT id FROM employee WHERE user_id=?", li),
      id("SELECT id FROM employee WHERE user_id=?", chen),
      id("SELECT id FROM employee WHERE user_id=?", zhao)
    };
    String[] names = {
      "李女士", "张先生", "王女士", "陈先生", "刘女士", "赵先生", "林女士", "周先生", "许女士", "郑先生", "孙女士", "吴先生"
    };
    long[] customers = new long[names.length];
    for (int i = 0; i < names.length; i++) {
      long uid = user(String.format("19899%06d", i + 1), names[i], seedPassword, "CUSTOMER");
      customers[i] = id("SELECT id FROM customer WHERE user_id=?", uid);
    }
    String[] venues = {"自然光影棚", "奶油风摄影间", "城市街景区"};
    long[] v = new long[3];
    for (int i = 0; i < 3; i++) {
      db.update(
          "INSERT INTO venue(name,location,description) VALUES(?,?,?)",
          venues[i],
          "见微工作室 · " + (i + 1) + "号拍摄区",
          "适合人像、日常写真与纪念拍摄");
      v[i] = id("SELECT LAST_INSERT_ID()");
    }
    String[] packages = {"自然光个人写真", "双人纪念写真", "温暖家庭合影"};
    int[] prices = {399, 699, 999};
    int[] durations = {60, 90, 90};
    long[] pkg = new long[3];
    for (int i = 0; i < 3; i++) {
      db.update(
          "INSERT INTO service_package(name,description,price,duration_minutes,buffer_minutes)"
              + " VALUES(?,?,?,?,30)",
          packages[i],
          switch (i) {
            case 0 -> "在柔和自然光里，记录松弛而真实的自己。含基础妆造、两套服装与精选精修照片。";
            case 1 -> "和重要的人留下一组自在的纪念。适合情侣、朋友与周年纪念。";
            default -> "把一家人的笑容留在日常里。支持亲子与家庭合影，拍摄节奏温和从容。";
          },
          prices[i],
          durations[i]);
      pkg[i] = id("SELECT LAST_INSERT_ID()");
      for (long p : photos) db.update("INSERT INTO package_photographer VALUES(?,?)", pkg[i], p);
      for (long venue : v) db.update("INSERT INTO package_venue VALUES(?,?)", pkg[i], venue);
    }
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    for (int d = -14; d <= 30; d++)
      for (String kind : List.of("photographer", "venue"))
        for (long resource : kind.equals("photographer") ? photos : v) {
          String table = kind + "_schedule";
          db.update(
              "INSERT INTO "
                  + table
                  + "(resource_id,work_date,enabled) VALUES(?,?,1) ON DUPLICATE KEY UPDATE id=id",
              resource,
              today.plusDays(d));
          long s =
              id(
                  "SELECT id FROM " + table + " WHERE resource_id=? AND work_date=?",
                  resource,
                  today.plusDays(d));
          if (id("SELECT COUNT(*) FROM " + table + "_period WHERE schedule_id=?", s) == 0) {
            db.update(
                "INSERT INTO "
                    + table
                    + "_period VALUES(NULL,?,'09:00','12:00'),(NULL,?,'13:00','20:00')",
                s,
                s);
          }
        }
    for (int n = 0; n < 32; n++) {
      int pi = n % 3, ci = n % customers.length;
      int day = n < 20 ? -12 + n / 2 : (n - 20) / 2;
      LocalDateTime start = today.plusDays(day).atTime(n % 2 == 0 ? 10 : 15, 0);
      String status =
          n < 20 ? (n % 9 == 0 ? "CANCELLED" : n % 7 == 0 ? "NO_SHOW" : "COMPLETED") : "CONFIRMED";
      long photographer = photos[n % 3], venue = v[n % 3];
      String request = UUID.randomUUID().toString();
      long customerUser = id("SELECT user_id FROM customer WHERE id=?", customers[ci]);
      String phone = String.format("19899%06d", ci + 1);
      String photographerName =
          db.queryForObject("SELECT name FROM employee WHERE id=?", String.class, photographer);
      db.update(
          "INSERT INTO"
              + " appointment(appointment_no,customer_id,package_id,photographer_id,venue_id,start_time,shoot_end_time,occupied_end_time,status,amount,package_name,photographer_name,venue_name,customer_name,customer_phone,duration_minutes,buffer_minutes,source,remark,request_id,request_fingerprint,created_by,created_at)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,30,'CUSTOMER',?,?,?,?,?)",
          "SG" + start.toLocalDate().toString().replace("-", "") + String.format("%04d", n + 1),
          customers[ci],
          pkg[pi],
          photographer,
          venue,
          start,
          start.plusMinutes(durations[pi]),
          start.plusMinutes(durations[pi] + 30),
          status,
          prices[pi],
          packages[pi],
          photographerName,
          venues[n % 3],
          names[ci],
          phone,
          durations[pi],
          n % 3 == 0 ? "喜欢自然、轻松的拍摄风格" : "希望提前沟通服装搭配",
          request,
          "seed-" + request,
          customerUser,
          start.minusDays(3));
      long appointment = id("SELECT LAST_INSERT_ID()");
      db.update(
          "INSERT INTO appointment_log(appointment_id,action,operator_id,detail,created_at)"
              + " VALUES(?,'CREATE',?,'预约创建并确认',?)",
          appointment,
          customerUser,
          start.minusDays(3));
      if (status.equals("COMPLETED")) {
        db.update(
            "INSERT INTO"
                + " service_record(appointment_id,checked_in_at,started_at,completed_at,operator_id,notes)"
                + " VALUES(?,?,?,?,?,?)",
            appointment,
            start.minusMinutes(10),
            start,
            start.plusMinutes(durations[pi]),
            admin,
            "拍摄已完成，已与客户确认选片安排。");
        db.update(
            "INSERT INTO"
                + " payment_record(appointment_id,type,amount,method,reason,operator_id,created_at)"
                + " VALUES(?,'PAYMENT',?,'WECHAT','到店收款',?,?)",
            appointment,
            prices[pi],
            admin,
            start);
      }
      db.update(
          "INSERT INTO notification(user_id,type,title,content,appointment_id,created_at)"
              + " VALUES(?,'BOOKING',?,?,?,?)",
          customerUser,
          status.equals("COMPLETED") ? "本次拍摄已完成" : "预约安排",
          packages[pi] + " · " + start + " · " + photographerName,
          appointment,
          start.minusDays(3));
      if (n >= 20)
        db.update(
            "INSERT INTO notification(user_id,type,title,content,appointment_id)"
                + " VALUES(?,'BOOKING','新的拍摄安排',?,?)",
            id("SELECT user_id FROM employee WHERE id=?", photographer),
            packages[pi] + " · " + start,
            appointment);
      if (n == 25 || n == 28)
        db.update(
            "INSERT INTO customer_request(customer_id,appointment_id,type,content)"
                + " VALUES(?,?,'RESCHEDULE','临时有事，希望调整拍摄时间，麻烦帮忙确认合适的空档。')",
            customers[ci],
            appointment);
    }
    db.update(
        "INSERT INTO operation_log(operator_id,module,action,detail)"
            + " VALUES(?,'STUDIO','INITIALIZE','工作室资料与预约记录已建立')",
        admin);
  }
}
