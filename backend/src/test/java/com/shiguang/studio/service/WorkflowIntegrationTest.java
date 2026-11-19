package com.shiguang.studio.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.dto.BookingRequests.*;
import com.shiguang.studio.dto.OperationsRequests.*;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.vo.BookingViews.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;

/** Opt-in real MySQL workflow checks. All fixtures and mutations roll back per test. */
@SpringBootTest
@Transactional(isolation = Isolation.READ_COMMITTED)
@EnabledIfEnvironmentVariable(named = "RUN_WORKFLOW_IT", matches = "true")
class WorkflowIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired org.mybatis.spring.SqlSessionTemplate session;
  @Autowired BookingService booking;
  @Autowired OperationsService operations;
  @Autowired BookingMapper mapper;
  @MockitoBean BookingReadCache cache;
  long customer, other, desk, photoUser, photo, venue, pkg;
  String phone;
  LocalDate day = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(3);

  @Autowired com.shiguang.studio.service.assistant.ActionCards actionCards;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

  UUID preparePreference(UUID thread, UUID run, String value) {
    var args=json.createObjectNode().put("_conversation",thread.toString()).put("_run",run.toString())
        .put("topic","风格").put("preference",value);
    var actor=new com.shiguang.studio.integration.mcp.McpAuthorization.Grant(customer,"customer","CUSTOMER","scope",1);
    var result=(Map<?,?>)actionCards.prepare(com.shiguang.studio.service.assistant.ActionKind.REMEMBER,args,actor);
    var url=result.get("pageUrl").toString();return UUID.fromString(url.substring(url.lastIndexOf('/')+1));
  }
  @Test void actionReplayAndChangedIntent() {
    as(customer,"CUSTOMER");
    var thread=UUID.randomUUID();var run=UUID.randomUUID();
    var card=preparePreference(thread,run,"侧光");
    assertEquals(card,preparePreference(thread,run,"侧光"));
    assertThrows(RuntimeException.class,()->preparePreference(thread,run,"逆光"));
    var owner=new com.shiguang.studio.integration.agent.AgentIdentity(customer,"customer","CUSTOMER");
    actionCards.decide(owner,card,true);actionCards.decide(owner,card,true);
    assertEquals(1L,db.queryForObject("SELECT version FROM customer_preference WHERE user_id=? AND topic='风格'",Long.class,customer));
  }
  @Test void supersededExpiredAndForeignCardsCannotExecute() {
    as(customer,"CUSTOMER");
    var owner=new com.shiguang.studio.integration.agent.AgentIdentity(customer,"customer","CUSTOMER");
    var foreign=new com.shiguang.studio.integration.agent.AgentIdentity(other,"customer","CUSTOMER");
    var thread=UUID.randomUUID();var a=preparePreference(thread,UUID.randomUUID(),"侧光");
    var b=preparePreference(thread,UUID.randomUUID(),"逆光");
    assertEquals("REPLACED",((Map<?,?>)actionCards.decide(owner,a,true)).get("state"));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->actionCards.decide(foreign,b,true));
    db.update("UPDATE assistant_action SET expires_at=NOW() WHERE id=?",b.toString());
    assertEquals("EXPIRED",((Map<?,?>)actionCards.fetch(owner,b)).get("state"));
    assertThrows(RuntimeException.class,()->actionCards.decide(owner,b,true));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM customer_preference WHERE user_id=?",Integer.class,customer));
  }

  long insert(String sql, Object... args) {
    db.update(sql, args);
    return db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  long user(String role) {
    String number = "audit-" + UUID.randomUUID();
    long id =
        insert(
            "INSERT INTO sys_user(phone,password_hash,display_name) VALUES(?, 'unused', '流程回归')",
            number.substring(0, 20));
    db.update("INSERT INTO sys_user_role SELECT ?,id FROM sys_role WHERE code=?", id, role);
    return id;
  }

  void as(long id, String role) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "" + id, "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
  }

  @BeforeEach
  void fixtures() {
    customer = user("CUSTOMER");
    other = user("CUSTOMER");
    desk = user("RECEPTIONIST");
    photoUser = user("PHOTOGRAPHER");
    phone = "138" + String.format("%08d", customer);
    db.update("UPDATE sys_user SET phone=? WHERE id=?", phone, customer);
    for (long id : List.of(customer, other))
      db.update(
          "INSERT INTO customer(user_id,name,phone,source) VALUES(?,'流程回归',?,'REGISTER')",
          id,
          id == customer ? phone : "13899999999");
    photo =
        insert(
            "INSERT INTO employee(user_id,employee_no,name,bookable) VALUES(?,?,'回归摄影师',1)",
            photoUser,
            "IT" + photoUser);
    venue = insert("INSERT INTO venue(name,location,description) VALUES('回归场地','','')");
    pkg =
        insert(
            "INSERT INTO service_package(name,description,price,duration_minutes,buffer_minutes)"
                + " VALUES('回归套餐','',399,60,30)");
    db.update("INSERT INTO package_photographer VALUES(?,?)", pkg, photo);
    db.update("INSERT INTO package_venue VALUES(?,?)", pkg, venue);
    for (LocalDate d : List.of(day, day.plusDays(1)))
      for (String kind : List.of("photographer", "venue")) {
        long id =
            insert(
                "INSERT INTO " + kind + "_schedule(resource_id,work_date) VALUES(?,?)",
                kind.equals("venue") ? venue : photo,
                d);
        db.update(
            "INSERT INTO "
                + kind
                + "_schedule_period(schedule_id,start_time,end_time) VALUES(?,'09:00','20:00')",
            id);
      }
    var rule = new RuleRequest(LocalTime.of(9, 0), LocalTime.of(20, 0), 30, 2, 30, 24);
    when(cache.rules()).thenReturn(rule);
    when(cache.currentRules()).thenReturn(rule);
    var p = new ResourceVO(photo, "回归摄影师", "", "", true, true);
    var v = new ResourceVO(venue, "回归场地", "", "", true, false);
    when(cache.catalog())
        .thenReturn(
            new BookingReadCache.Catalog(
                List.of(
                    new PackageVO(
                        pkg,
                        "回归套餐",
                        "",
                        "",
                        new BigDecimal("399"),
                        60,
                        30,
                        true,
                        List.of(p),
                        List.of(v))),
                List.of(p),
                List.of(v)));
    when(cache.day(any()))
        .thenAnswer(
            i -> {
              LocalDate d = i.getArgument(0);
              return new BookingReadCache.DaySnapshot(
                  mapper.dayPeriods("photographer_schedule", d),
                  mapper.dayPeriods("venue_schedule", d),
                  mapper.dayOccupancy(d.atStartOfDay(), d.plusDays(1).atStartOfDay()));
            });
    as(customer, "CUSTOMER");
  }

  @org.springframework.test.context.transaction.AfterTransaction
  void fixturesWereRolledBack() {
    for (long id : List.of(customer, other, desk, photoUser))
      assertEquals(
          0, db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=?", Integer.class, id));
    assertEquals(
        0,
        db.queryForObject("SELECT COUNT(*) FROM service_package WHERE id=?", Integer.class, pkg));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM appointment WHERE package_id=?", Integer.class, pkg));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  String request() {
    return UUID.randomUUID().toString();
  }

  CreateAppointmentRequest createRequest(LocalDateTime start) {
    return new CreateAppointmentRequest(pkg, photo, start, request(), "回归验证", null, null);
  }

  AppointmentVO create() {
    return booking.create(createRequest(day.atTime(10, 0)));
  }

  ActionRequest action(long id) {
    return new ActionRequest(
        request(),
        db.queryForObject("SELECT version FROM appointment WHERE id=?", Integer.class, id),
        "流程验证");
  }

  void act(long id, String name) {
    operations.act(id, name, action(id));
  }

  PaymentRequest money(String amount) {
    return new PaymentRequest(request(), new BigDecimal(amount), "CASH", "流程验证");
  }

  Map<?, ?> activity(long id) {
    return (Map<?, ?>) operations.activity(id);
  }

  @Test
  void createRescheduleCancelAndReleasedSlot() {
    var r = createRequest(day.atTime(10, 0));
    var a = booking.create(r);
    assertEquals(a.id(), booking.create(r).id());
    assertFalse(
        booking.availability(pkg, photo, day).stream()
            .anyMatch(s -> s.startTime().equals(r.startTime())));
    var change = new RescheduleRequest(request(), 0, "调整", photo, day.plusDays(1).atTime(11, 0));
    operations.reschedule(a.id(), change);
    operations.reschedule(a.id(), change);
    assertEquals(change.startTime(), booking.detail(a.id()).startTime());
    assertEquals(1, activity(a.id()).get("version"));
    assertTrue(
        booking.availability(pkg, photo, day).stream()
            .anyMatch(s -> s.startTime().equals(r.startTime())));
    var cancel = action(a.id());
    operations.act(a.id(), "cancel", cancel);
    operations.act(a.id(), "cancel", cancel);
    assertEquals("CANCELLED", booking.detail(a.id()).status());
    assertTrue(
        booking.availability(pkg, photo, day.plusDays(1)).stream()
            .anyMatch(s -> s.startTime().equals(change.startTime())));
  }

  @Test
  void staffBookingBelongsToRegisteredCustomerEvenWhenContactPhoneChanged() {
    db.update("UPDATE customer SET phone='13888888888' WHERE user_id=?", customer);
    as(desk, "RECEPTIONIST");
    var a =
        booking.create(
            new CreateAppointmentRequest(
                pkg, photo, day.atTime(10, 0), request(), "", "客户", phone));
    as(customer, "CUSTOMER");
    assertEquals(a.id(), booking.detail(a.id()).id());
    assertEquals(1, booking.appointments(1, 100, day).total());
    as(other, "CUSTOMER");
    assertThrows(Exception.class, () -> booking.detail(a.id()));
  }

  @Test
  void checkInShootCollectPartialRefundAndRecollect() {
    var a = create();
    long id = a.id();
    // Fixture clock positioning only: the service still performs every real transition.
    db.update(
        "UPDATE appointment SET start_time=?,shoot_end_time=?,occupied_end_time=? WHERE id=?",
        LocalDate.now(ZoneId.of("Asia/Shanghai")).atTime(10, 0),
        LocalDate.now(ZoneId.of("Asia/Shanghai")).atTime(11, 0),
        LocalDate.now(ZoneId.of("Asia/Shanghai")).atTime(11, 30),
        id);
    as(desk, "RECEPTIONIST");
    act(id, "check-in");
    var payment = money("399.00");
    operations.payment(id, payment);
    operations.payment(id, payment);
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM payment_record WHERE appointment_id=?", Integer.class, id));
    as(photoUser, "PHOTOGRAPHER");
    act(id, "start");
    act(id, "complete");
    assertEquals("COMPLETED", booking.detail(id).status());
    assertFalse(activity(id).containsKey("payments"));
    as(desk, "RECEPTIONIST");
    long paymentId =
        db.queryForObject(
            "SELECT id FROM payment_record WHERE appointment_id=? AND type='PAYMENT'",
            Long.class,
            id);
    var refund = money("99.00");
    operations.refund(paymentId, refund);
    operations.refund(paymentId, refund);
    assertEquals(new BigDecimal("300.00"), activity(id).get("net"));
    operations.payment(id, money("99.00"));
    assertEquals(new BigDecimal("399.00"), activity(id).get("net"));
    var service = (Map<?, ?>) activity(id).get("service");
    for (String key : List.of("checkedInAt", "startedAt", "completedAt"))
      assertNotNull(service.get(key));
  }

  @Test
  void photographerCannotReadFinancialLogsOrLegacyNotifications() {
    long id = create().id();
    db.update("UPDATE appointment SET status='COMPLETED' WHERE id=?", id);
    as(desk, "RECEPTIONIST");
    operations.payment(id, money("399"));
    long paymentId =
        db.queryForObject(
            "SELECT id FROM payment_record WHERE appointment_id=? AND type='PAYMENT'",
            Long.class,
            id);
    operations.refund(paymentId, money("99"));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM notification WHERE user_id=? AND title IN ('已登记收款','已登记退款')",
            Integer.class,
            photoUser));
    for (String title : List.of("payment", "refund", "已登记收款", "已登记退款"))
      db.update(
          "INSERT INTO notification(user_id,type,title,content,appointment_id)"
              + " VALUES(?,'BOOKING',?,'历史财务详情',?)",
          photoUser,
          title,
          id);
    as(photoUser, "PHOTOGRAPHER");
    var a = activity(id);
    assertFalse(a.containsKey("payments"));
    assertTrue(
        ((List<Map<String, Object>>) a.get("logs"))
            .stream().noneMatch(l -> Set.of("payment", "refund").contains(l.get("action"))));
    var n = (Map<?, ?>) operations.notifications(1, 1);
    assertEquals(1L, n.get("total"));
    assertEquals(1L, n.get("unread"));
    assertEquals(1, ((List<?>) n.get("items")).size());
    assertFalse(n.toString().contains("历史财务详情"));
    as(desk, "RECEPTIONIST");
    assertEquals(2, ((List<?>) activity(id).get("payments")).size());
    assertTrue(
        ((List<Map<String, Object>>) activity(id).get("logs"))
            .stream().anyMatch(l -> "refund".equals(l.get("action"))));
  }

  @Test
  void invalidTransitionsAndPermissionsCannotWrite() {
    var a = create();
    long id = a.id();
    as(desk, "RECEPTIONIST");
    assertThrows(Exception.class, () -> act(id, "check-in"));
    assertThrows(Exception.class, () -> act(id, "start"));
    assertThrows(Exception.class, () -> operations.payment(id, money("1")));
    as(photoUser, "PHOTOGRAPHER");
    assertThrows(Exception.class, () -> act(id, "complete"));
    assertThrows(Exception.class, () -> act(id, "cancel"));
    assertThrows(Exception.class, () -> operations.payment(id, money("1")));
    as(other, "CUSTOMER");
    assertThrows(Exception.class, () -> act(id, "cancel"));
    as(customer, "CUSTOMER");
    assertEquals("CONFIRMED", booking.detail(id).status());
  }

  @Test
  void conflictsAndCutoffKeepOriginalBooking() {
    var first = create();
    var second = booking.create(createRequest(day.atTime(13, 0)));
    assertThrows(
        Exception.class,
        () ->
            operations.reschedule(
                second.id(), new RescheduleRequest(request(), 0, "冲突", photo, first.startTime())));
    assertEquals(day.atTime(13, 0), booking.detail(second.id()).startTime());
    db.update(
        "UPDATE appointment SET start_time=? WHERE id=?",
        LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusMinutes(1),
        second.id());
    session.clearCache();
    assertThrows(Exception.class, () -> act(second.id(), "cancel"));
    as(desk, "RECEPTIONIST");
    act(second.id(), "cancel");
    assertEquals("CANCELLED", booking.detail(second.id()).status());
  }

  @Test
  void overCollectionAndOverRefundLeaveLedgerUnchanged() {
    long id = create().id();
    db.update("UPDATE appointment SET status='COMPLETED' WHERE id=?", id);
    as(desk, "RECEPTIONIST");
    operations.payment(id, money("399"));
    assertThrows(Exception.class, () -> operations.payment(id, money("0.01")));
    long p =
        db.queryForObject(
            "SELECT id FROM payment_record WHERE appointment_id=? AND type='PAYMENT'",
            Long.class,
            id);
    operations.refund(p, money("399"));
    assertThrows(Exception.class, () -> operations.refund(p, money("0.01")));
    assertEquals(new BigDecimal("0.00"), activity(id).get("net"));
  }

  @Test
  void contactPhoneCannotLinkAnotherCustomersBooking() {
    db.update("UPDATE customer SET phone='13877777777' WHERE user_id=?", customer);
    as(desk, "RECEPTIONIST");
    var a =
        booking.create(
            new CreateAppointmentRequest(
                pkg, photo, day.atTime(10, 0), request(), "", "访客", "13877777777"));
    as(customer, "CUSTOMER");
    assertThrows(Exception.class, () -> booking.detail(a.id()));
  }

  @Test
  void noShowReleasesCapacityAndCannotBeCheckedInAgain() {
    long id = create().id();
    as(desk, "RECEPTIONIST");
    assertThrows(Exception.class, () -> act(id, "mark-no-show"));
    db.update(
        "UPDATE appointment SET shoot_end_time=? WHERE id=?",
        LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusMinutes(1),
        id);
    session.clearCache();
    act(id, "mark-no-show");
    assertEquals("NO_SHOW", booking.detail(id).status());
    assertThrows(Exception.class, () -> act(id, "check-in"));
    assertTrue(
        booking.availability(pkg, photo, day).stream()
            .anyMatch(s -> s.startTime().equals(day.atTime(10, 0))));
  }

  @Test
  void sameRequestIdWithDifferentPayloadAndStaleVersionCannotMutate() {
    var r = createRequest(day.atTime(10, 0));
    var a = booking.create(r);
    assertThrows(
        Exception.class,
        () ->
            booking.create(
                new CreateAppointmentRequest(
                    pkg, photo, day.atTime(13, 0), r.requestId(), "", null, null)));
    operations.reschedule(
        a.id(), new RescheduleRequest(request(), 0, "调整", photo, day.atTime(13, 0)));
    assertThrows(
        Exception.class,
        () -> operations.act(a.id(), "cancel", new ActionRequest(request(), 0, "过期页面")));
    assertEquals("CONFIRMED", booking.detail(a.id()).status());
    assertEquals(day.atTime(13, 0), booking.detail(a.id()).startTime());
  }

  long applyFor(long id, String type) {
    operations.createRequest(new RequestCreate(id, type, "客户希望调整", request()));
    return db.queryForObject(
        "SELECT MAX(id) FROM customer_request WHERE appointment_id=?", Long.class, id);
  }

  RequestHandle handle(long q, long id, String state, Long person, LocalDateTime start) {
    return new RequestHandle(
        state,
        "已与客户核对",
        db.queryForObject("SELECT version FROM customer_request WHERE id=?", Integer.class, q),
        request(),
        db.queryForObject("SELECT version FROM appointment WHERE id=?", Integer.class, id),
        person,
        start);
  }

  @Test
  void pendingCancellationShowsInTimelineAndBlocksDirectCheckIn() {
    long id = create().id();
    long q = applyFor(id, "CANCEL");
    assertEquals("CANCEL", booking.detail(id).pendingRequestType());
    assertEquals("CANCEL", booking.appointments(1, 100, day).items().get(0).pendingRequestType());
    assertTrue(
        ((List<Map<String, Object>>) activity(id).get("logs"))
            .stream().anyMatch(l -> l.get("detail").toString().contains("取消申请")));
    as(desk, "RECEPTIONIST");
    assertThrows(Exception.class, () -> act(id, "check-in"));
    assertThrows(Exception.class, () -> act(id, "cancel"));
    var approval = handle(q, id, "RESOLVED", null, null);
    operations.handleRequest(q, approval);
    operations.handleRequest(q, approval);
    assertEquals("CANCELLED", booking.detail(id).status());
    assertNull(booking.detail(id).pendingRequestType());
    assertEquals(
        "RESOLVED",
        db.queryForObject("SELECT status FROM customer_request WHERE id=?", String.class, q));
    assertTrue(
        booking.availability(pkg, photo, day).stream()
            .anyMatch(slot -> slot.startTime().equals(day.atTime(10, 0))));
  }

  @Test
  void approvalReschedulesBothRecordsAndFreesOriginalTime() {
    long id = create().id();
    long q = applyFor(id, "RESCHEDULE");
    as(desk, "RECEPTIONIST");
    operations.handleRequest(q, handle(q, id, "RESOLVED", photo, day.plusDays(1).atTime(14, 0)));
    assertEquals(day.plusDays(1).atTime(14, 0), booking.detail(id).startTime());
    assertNull(booking.detail(id).pendingRequestType());
    assertEquals(
        "RESOLVED",
        db.queryForObject("SELECT status FROM customer_request WHERE id=?", String.class, q));
  }

  @Test
  void conflictingApprovalLeavesRequestPendingAndAppointmentUnchanged() {
    var original = create();
    booking.create(createRequest(day.atTime(13, 0)));
    long q = applyFor(original.id(), "RESCHEDULE");
    as(desk, "RECEPTIONIST");
    assertThrows(
        Exception.class,
        () ->
            operations.handleRequest(
                q, handle(q, original.id(), "RESOLVED", photo, day.atTime(13, 0))));
    assertEquals(original.startTime(), booking.detail(original.id()).startTime());
    assertEquals(
        "PENDING",
        db.queryForObject("SELECT status FROM customer_request WHERE id=?", String.class, q));
  }

  @Test
  void requestRejectionRestoresCheckInAndNeedsNoExplanation() {
    long id = create().id();
    long q = applyFor(id, "CANCEL");
    as(desk, "RECEPTIONIST");
    operations.handleRequest(q, handle(q, id, "CLOSED", null, null));
    db.update(
        "UPDATE appointment SET start_time=? WHERE id=?",
        LocalDate.now(ZoneId.of("Asia/Shanghai")).atTime(10, 0),
        id);
    session.clearCache();
    operations.act(id, "check-in", new ActionRequest(request(), action(id).version(), ""));
    assertEquals("CHECKED_IN", booking.detail(id).status());
    operations.payment(id, new PaymentRequest(request(), new BigDecimal("399.00"), "WECHAT", null));
    assertEquals(new BigDecimal("399.00"), activity(id).get("net"));
    long payment =
        db.queryForObject("SELECT id FROM payment_record WHERE appointment_id=?", Long.class, id);
    assertThrows(
        Exception.class,
        () ->
            operations.refund(
                payment, new PaymentRequest(request(), BigDecimal.ONE, "WECHAT", "")));
  }

  @Test
  void duplicateOrLateChangeRequestIsRejected() {
    long id = create().id();
    var req = new RequestCreate(id, "CANCEL", "取消", request());
    operations.createRequest(req);
    operations.createRequest(req);
    assertThrows(Exception.class, () -> applyFor(id, "RESCHEDULE"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM customer_request WHERE appointment_id=?", Integer.class, id));
    db.update("UPDATE appointment SET status='CHECKED_IN' WHERE id=?", id);
    session.clearCache();
    assertThrows(Exception.class, () -> applyFor(id, "CANCEL"));
  }

  @Test
  void requestHandlingChecksRoleVersionAndProcessingStillBlocksArrival() {
    long id = create().id();
    long q = applyFor(id, "CANCEL");
    var approval = handle(q, id, "RESOLVED", null, null);
    assertThrows(Exception.class, () -> operations.handleRequest(q, approval));
    as(photoUser, "PHOTOGRAPHER");
    assertThrows(Exception.class, () -> operations.handleRequest(q, approval));
    as(desk, "RECEPTIONIST");
    operations.handleRequest(q, handle(q, id, "PROCESSING", null, null));
    assertThrows(Exception.class, () -> act(id, "check-in"));
    assertThrows(Exception.class, () -> operations.handleRequest(q, approval));
    assertEquals("CONFIRMED", booking.detail(id).status());
  }

  @Test
  void maximumLengthRequestStillCreatesNotifications() {
    long id = create().id();
    operations.createRequest(new RequestCreate(id, "CANCEL", "长".repeat(1000), request()));
    assertEquals(
        1000,
        db.queryForObject(
            "SELECT CHAR_LENGTH(content) FROM customer_request WHERE appointment_id=?",
            Integer.class,
            id));
    assertTrue(
        db.queryForObject(
                "SELECT MAX(CHAR_LENGTH(content)) FROM notification WHERE appointment_id=?",
                Integer.class,
                id)
            <= 1000);
  }

  @Autowired com.shiguang.studio.service.booking.WeeklyScheduleService weekly;
  WeeklyScheduleRequest fullWeek(long id,boolean enabled){
    return new WeeklyScheduleRequest(id,java.util.stream.IntStream.rangeClosed(1,7).mapToObj(d->new WeeklyDayRequest(d,enabled,enabled?List.of(new PeriodRequest(LocalTime.of(9,0),LocalTime.of(20,0))):List.of())).toList());
  }
  void setupWeekly(){
    as(user("ADMIN"),"ADMIN");weekly.save("photographer",fullWeek(photo,true));weekly.save("venue",fullWeek(venue,true));
    for(var d:List.of(day,day.plusDays(1))){weekly.restore("photographer",photo,d);weekly.restore("venue",venue,d);}
  }
  @Test void weeklyFallbackSupportsRealBookingAndNextWeek(){
    setupWeekly();assertEquals("WEEKLY",booking.schedule("photographer",photo,day).source());
    assertTrue(booking.schedule("photographer",photo,day.plusWeeks(2)).enabled());
    as(customer,"CUSTOMER");assertFalse(booking.availability(pkg,photo,day).isEmpty());assertNotNull(create().id());
  }
  @Test void dateClosureOverridesWeeklyAndRestoreReopens(){
    setupWeekly();booking.saveSchedule("photographer",new ScheduleRequest(photo,day,false,List.of()));
    assertEquals("DATE",booking.schedule("photographer",photo,day).source());
    as(customer,"CUSTOMER");assertTrue(booking.availability(pkg,photo,day).isEmpty());assertThrows(RuntimeException.class,this::create);
  }
  @Test void restoreUsesWeeklyAndProtectsBuffer(){
    setupWeekly();booking.saveSchedule("photographer",new ScheduleRequest(photo,day,true,List.of(new PeriodRequest(LocalTime.of(9,0),LocalTime.of(20,0)))));
    as(customer,"CUSTOMER");create();as(user("ADMIN"),"ADMIN");
    var rows=java.util.stream.IntStream.rangeClosed(1,7).mapToObj(d->new WeeklyDayRequest(d,true,List.of(new PeriodRequest(LocalTime.of(9,0),LocalTime.of(11,0))))).toList();
    weekly.save("photographer",new WeeklyScheduleRequest(photo,rows));
    assertThrows(RuntimeException.class,()->weekly.restore("photographer",photo,day));
    assertEquals("DATE",booking.schedule("photographer",photo,day).source());
  }
  @Test void weeklyChangeCannotInvalidateExistingAppointment(){
    setupWeekly();as(customer,"CUSTOMER");create();as(user("ADMIN"),"ADMIN");
    assertThrows(RuntimeException.class,()->weekly.save("photographer",fullWeek(photo,false)));
  }
  @Test void weeklyValidationAndStaffPermission(){
    setupWeekly();as(photoUser,"PHOTOGRAPHER");assertEquals(7,weekly.myWeek().size());
    assertThrows(RuntimeException.class,()->weekly.save("photographer",fullWeek(photo,false)));
    as(user("ADMIN"),"ADMIN");
    var duplicate=new WeeklyScheduleRequest(photo,java.util.Collections.nCopies(7,new WeeklyDayRequest(1,false,List.of())));
    assertThrows(RuntimeException.class,()->weekly.save("photographer",duplicate));
  }

 @Autowired KnowledgeService knowledge;
 @Autowired com.shiguang.studio.mapper.KnowledgeMapper knowledgeMapper;
 com.shiguang.studio.dto.KnowledgeRequest article(String status,long version){return new com.shiguang.studio.dto.KnowledgeRequest("知识库回归","预约须知","原始文字 <script>alert(1)</script>",status,version);}
 @Test void knowledgeLifecycleAndRevisionHistory(){
  as(user("ADMIN"),"ADMIN");
  long initial=knowledgeMapper.publishedCount();
  var a=knowledge.save(null,article("DRAFT",0));
  assertEquals(initial,knowledgeMapper.publishedCount());
  var published=knowledge.save(a.getId(),article("PUBLISHED",a.getVersion()));
  assertEquals(initial+1,knowledgeMapper.publishedCount());
  assertEquals(2L,published.getVersion());
  var disabled=knowledge.save(a.getId(),article("DISABLED",published.getVersion()));
  assertEquals(initial,knowledgeMapper.publishedCount());
  assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM knowledge_revision WHERE article_id=?",Integer.class,a.getId()));
  assertEquals("DISABLED",disabled.getStatus());
 }
 @Test void knowledgeRejectsStaleEditsAndNonAdminWrites(){
  as(user("ADMIN"),"ADMIN");var a=knowledge.save(null,article("DRAFT",0));
  knowledge.save(a.getId(),article("PUBLISHED",a.getVersion()));
  assertThrows(RuntimeException.class,()->knowledge.save(a.getId(),article("DISABLED",a.getVersion())));
  assertEquals("PUBLISHED",knowledgeMapper.find(a.getId()).getStatus());
  as(desk,"RECEPTIONIST");assertThrows(RuntimeException.class,()->knowledge.save(null,article("PUBLISHED",0)));
  assertThrows(RuntimeException.class,()->knowledge.list(true,1,10));
  as(customer,"CUSTOMER");assertThrows(RuntimeException.class,()->knowledge.save(null,article("PUBLISHED",0)));
  assertDoesNotThrow(()->knowledge.list(false,1,10));
 }

 @Autowired com.shiguang.studio.service.knowledge.KnowledgeSearch ragSearch;
 @Autowired com.shiguang.studio.service.knowledge.KnowledgeJobs ragJobs;

 @Test void ragKeepsPreviousReadyVersionButRevokesOnVisibilityChange(){
   as(user("ADMIN"),"ADMIN");
   var a=knowledge.save(null,article("PUBLISHED",0));
   var id=a.getId();
   var token=db.queryForObject("SELECT job_id FROM knowledge_article WHERE id=?",String.class,id);
   var success=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
   success.put("articleId",id).put("version",1).put("jobId",token).put("state","READY");
   ragJobs.receive(success);
   assertEquals(1L,knowledgeMapper.find(id).getIndexedVersion());
   var edit=knowledge.save(id,article("PUBLISHED",1));
   ragJobs.receive(success);
   assertEquals("QUEUED",knowledgeMapper.find(id).getIndexState());
   var failure=success.deepCopy().put("version",2).put("state","FAILED").put("jobId",
     db.queryForObject("SELECT job_id FROM knowledge_article WHERE id=?",String.class,id));
   ragJobs.receive(failure);
   assertEquals(1L,knowledgeMapper.find(id).getIndexedVersion());
   assertEquals("知识库回归",ragSearch.source("customer",id,1).get("title"));
   var restricted=knowledge.save(id,new com.shiguang.studio.dto.KnowledgeRequest(
       "员工说明","说明","仅内部可见","PUBLISHED",edit.getVersion(),"STAFF"));
   assertNull(restricted.getIndexedVersion());
   assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ragSearch.source("customer",id,1));
   assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ragSearch.source("customer",id,3));
   assertEquals("员工说明",ragSearch.source("staff",id,3).get("title"));
   knowledge.save(id,new com.shiguang.studio.dto.KnowledgeRequest("员工说明","说明","内部内容","DISABLED",3L,"STAFF"));
   assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ragSearch.source("staff",id,3));
 }
}
