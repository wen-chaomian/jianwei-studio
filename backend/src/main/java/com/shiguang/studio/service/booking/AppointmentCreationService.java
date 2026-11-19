package com.shiguang.studio.service.booking;

import static com.shiguang.studio.service.booking.BookingPolicy.validDate;
import static com.shiguang.studio.service.booking.BookingPolicy.validStart;
import static com.shiguang.studio.service.support.CurrentActor.role;
import static com.shiguang.studio.service.support.CurrentActor.user;
import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.dto.BookingRequests.CreateAppointmentRequest;
import com.shiguang.studio.entity.Appointment;
import com.shiguang.studio.entity.Venue;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.mapper.OperationsMapper;
import com.shiguang.studio.service.BookingConfigurationLock;
import com.shiguang.studio.service.BookingSlotLock;
import com.shiguang.studio.service.support.BusinessEvents;
import com.shiguang.studio.vo.BookingViews.AppointmentVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 预约创建的数据库事务；调用方在事务外先取得 Redis 资源时段锁。 */
@Service
public class AppointmentCreationService {
  private final OperationsMapper ops;
  private final BookingMapper mapper;
  private final BookingReadCache cache;
  private final BookingConfigurationLock configurationLock;
  private final BookingSlotLock slotLock;
  private final BusinessEvents events;
  private final BookingAllocationService allocation;
  private final AppointmentQueryService queries;

  public AppointmentCreationService(
      OperationsMapper ops,
      BookingMapper mapper,
      BookingReadCache cache,
      BookingConfigurationLock configurationLock,
      BookingSlotLock slotLock,
      BusinessEvents events,
      BookingAllocationService allocation,
      AppointmentQueryService queries) {
    this.ops = ops;
    this.mapper = mapper;
    this.cache = cache;
    this.configurationLock = configurationLock;
    this.slotLock = slotLock;
    this.events = events;
    this.allocation = allocation;
    this.queries = queries;
  }

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  private String fingerprint(CreateAppointmentRequest r) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (r.packageId()
                              + "|"
                              + r.photographerId()
                              + "|"
                              + r.startTime()
                              + "|"
                              + Objects.toString(r.remark(), "")
                              + "|"
                              + Objects.toString(r.customerName(), "")
                              + "|"
                              + Objects.toString(r.customerPhone(), ""))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public AppointmentVO create(CreateAppointmentRequest r) {
    configurationLock.read();
    boolean customer = role("CUSTOMER");
    require(customer || role("ADMIN") || role("RECEPTIONIST"), "摄影师不能代客创建预约");
    long user = user();
    mapper.lockUser(user);
    String fingerprint = fingerprint(r);
    var existing = mapper.byRequest(r.requestId());
    if (existing != null) {
      require(
          existing.getCreatedBy() == user && existing.getRequestFingerprint().equals(fingerprint),
          "提交请求号已使用，请重新确认预约");
      return queries.vo(existing);
    }
    var rule = cache.currentRules();
    validDate(r.startTime().toLocalDate(), rule);
    require(validStart(r.startTime(), rule, LocalDateTime.now(ZONE)), "开始时间不符合预约间隔或最少提前时间规则");
    var p = mapper.sharePackage(r.packageId());
    require(p != null && p.getEnabled(), "套餐不存在或已下架");
    var photographer = allocation.photographer(r.photographerId());
    require(mapper.photographerIds(p.getId()).contains(r.photographerId()), "该摄影师不支持此套餐");
    var end = r.startTime().plusMinutes(p.getDurationMinutes() + p.getBufferMinutes());
    slotLock.lock(allocation.allocationSlots(p.getId(), r.photographerId(), r.startTime(), end));
    require(
        allocation.free("photographer", r.photographerId(), r.startTime(), end, rule),
        "摄影师时段已被占用或未排班，请重新选择");
    Venue venue = null;
    for (long id : mapper.venueIds(p.getId())) {
      var v = mapper.venue(id);
      if (v != null && v.getEnabled() && allocation.free("venue", id, r.startTime(), end, rule)) {
        venue = v;
        break;
      }
    }
    require(venue != null, "场地时段已被占用或未排班，请重新选择");
    Long customerId;
    String name, phone;
    if (customer) {
      customerId = mapper.customerId(user);
      require(customerId != null, "客户档案不存在");
      name = mapper.customerName(user);
      phone = Objects.toString(ops.customer(customerId).get("phone"));
    } else {
      name = r.customerName();
      phone = r.customerPhone();
      require(
          name != null && !name.isBlank() && phone != null && phone.matches("1[3-9][0-9]{9}"),
          "请填写客户姓名和11位手机号");
      customerId = mapper.registeredCustomerByPhone(phone);
      if (customerId == null) {
        mapper.insertGuest(name.strip(), phone);
        customerId = mapper.lastId();
      }
    }
    Appointment a = new Appointment();
    a.setAppointmentNo("SG" + UUID.randomUUID().toString().replace("-", ""));
    a.setCustomerId(customerId);
    a.setPackageId(p.getId());
    a.setPhotographerId(photographer.getId());
    a.setVenueId(venue.getId());
    a.setStartTime(r.startTime());
    a.setShootEndTime(r.startTime().plusMinutes(p.getDurationMinutes()));
    a.setOccupiedEndTime(end);
    a.setStatus("CONFIRMED");
    a.setAmount(p.getPrice());
    a.setPackageName(p.getName());
    a.setPhotographerName(photographer.getName());
    a.setVenueName(venue.getName());
    a.setCustomerName(name);
    a.setCustomerPhone(phone);
    a.setDurationMinutes(p.getDurationMinutes());
    a.setBufferMinutes(p.getBufferMinutes());
    a.setSource(customer ? "CUSTOMER" : "STAFF");
    a.setRemark(r.remark());
    a.setRequestId(r.requestId());
    a.setRequestFingerprint(fingerprint);
    a.setCreatedBy(user);
    mapper.insertAppointment(a);
    mapper.log(a.getId(), user);
    events.audit(user, "APPOINTMENT", "CREATE", a.getId(), "创建预约 " + a.getAppointmentNo());
    Long owner = ops.customerUser(customerId);
    if (owner != null)
      events.notifyUser(owner, "预约已确认", a.getPackageName() + " · " + a.getStartTime(), a.getId());
    events.notifyUser(
        ops.employeeUser(a.getPhotographerId()),
        "新的拍摄安排",
        a.getPackageName() + " · " + a.getStartTime(),
        a.getId());
    cache.invalidateAvailabilityAfterCommit(a.getStartTime().toLocalDate());
    return queries.vo(a);
  }
}
