package com.shiguang.studio.service;

import com.shiguang.studio.dto.BookingRequests.*;
import com.shiguang.studio.vo.BookingViews.*;
import java.time.*;
import java.util.*;

public interface BookingService {
  List<SlotVO> rescheduleAvailability(
      long appointmentId, long photographerId, java.time.LocalDate date);

  void reallocate(
      com.shiguang.studio.entity.Appointment a, long photographerId, java.time.LocalDateTime start);

  List<PackageVO> packages(boolean admin);

  PackageVO packageDetail(long id, boolean admin);

  PackageVO savePackage(Long id, PackageRequest request);

  List<ResourceVO> venues();

  ResourceVO saveVenue(Long id, VenueRequest request);

  List<ResourceVO> photographers();

  void setBookable(long id, boolean bookable);

  ScheduleVO schedule(String kind, long id, LocalDate date);

  ScheduleVO saveSchedule(String kind, ScheduleRequest request);

  List<SlotVO> availability(long packageId, long photographerId, LocalDate date);

  AppointmentVO create(CreateAppointmentRequest request);

  PageVO<AppointmentVO> appointments(int page, int size, LocalDate date);

  AppointmentVO detail(long id);
}
