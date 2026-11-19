package com.shiguang.studio.service.impl;

import com.shiguang.studio.dto.BookingRequests.CreateAppointmentRequest;
import com.shiguang.studio.dto.BookingRequests.PackageRequest;
import com.shiguang.studio.dto.BookingRequests.ScheduleRequest;
import com.shiguang.studio.dto.BookingRequests.VenueRequest;
import com.shiguang.studio.entity.Appointment;
import com.shiguang.studio.service.BookingRedisLock;
import com.shiguang.studio.service.BookingService;
import com.shiguang.studio.service.booking.AppointmentCreationService;
import com.shiguang.studio.service.booking.AppointmentQueryService;
import com.shiguang.studio.service.booking.AvailabilityService;
import com.shiguang.studio.service.booking.BookingAllocationService;
import com.shiguang.studio.service.booking.CatalogService;
import com.shiguang.studio.service.booking.ResourceScheduleService;
import com.shiguang.studio.vo.BookingViews.AppointmentVO;
import com.shiguang.studio.vo.BookingViews.PackageVO;
import com.shiguang.studio.vo.BookingViews.PageVO;
import com.shiguang.studio.vo.BookingViews.ResourceVO;
import com.shiguang.studio.vo.BookingViews.ScheduleVO;
import com.shiguang.studio.vo.BookingViews.SlotVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** 预约功能入口。创建时先取得分布式锁，再通过独立服务开启数据库事务。 */
@Service
public class BookingServiceImpl implements BookingService {
  private final BookingRedisLock redisLock;
  private final CatalogService catalog;
  private final ResourceScheduleService schedules;
  private final AvailabilityService availabilityService;
  private final BookingAllocationService allocation;
  private final AppointmentQueryService queries;
  private final AppointmentCreationService creation;

  public BookingServiceImpl(
      BookingRedisLock redisLock,
      CatalogService catalog,
      ResourceScheduleService schedules,
      AvailabilityService availabilityService,
      BookingAllocationService allocation,
      AppointmentQueryService queries,
      AppointmentCreationService creation) {
    this.redisLock = redisLock;
    this.catalog = catalog;
    this.schedules = schedules;
    this.availabilityService = availabilityService;
    this.allocation = allocation;
    this.queries = queries;
    this.creation = creation;
  }

  public List<ResourceVO> venues() {
    return catalog.venues();
  }

  public List<ResourceVO> photographers() {
    return catalog.photographers();
  }

  public List<PackageVO> packages(boolean all) {
    return catalog.packages(all);
  }

  public PackageVO packageDetail(long id, boolean all) {
    return catalog.packageDetail(id, all);
  }

  public PackageVO savePackage(Long id, PackageRequest r) {
    return catalog.savePackage(id, r);
  }

  public ResourceVO saveVenue(Long id, VenueRequest r) {
    return catalog.saveVenue(id, r);
  }

  public void setBookable(long id, boolean bookable) {
    catalog.setBookable(id, bookable);
  }

  public ScheduleVO schedule(String kind, long id, LocalDate date) {
    return schedules.schedule(kind, id, date);
  }

  public ScheduleVO saveSchedule(String kind, ScheduleRequest r) {
    return schedules.saveSchedule(kind, r);
  }

  public List<SlotVO> availability(long packageId, long photographerId, LocalDate date) {
    return availabilityService.availability(packageId, photographerId, date);
  }

  public PageVO<AppointmentVO> appointments(int page, int size, LocalDate date) {
    return queries.appointments(page, size, date);
  }

  public AppointmentVO detail(long id) {
    return queries.detail(id);
  }

  public void reallocate(Appointment a, long photographerId, LocalDateTime start) {
    allocation.reallocate(a, photographerId, start);
  }

  public List<SlotVO> rescheduleAvailability(
      long appointmentId, long photographerId, LocalDate date) {
    return availabilityService.rescheduleAvailability(appointmentId, photographerId, date);
  }

  public AppointmentVO create(CreateAppointmentRequest r) {
    return redisLock.execute(
        redisLock.keys(r.packageId(), r.photographerId(), r.startTime()), () -> creation.create(r));
  }
}
