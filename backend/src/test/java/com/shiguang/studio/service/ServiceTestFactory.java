package com.shiguang.studio.service;

import static com.shiguang.studio.service.booking.BookingPolicy.*;
import static com.shiguang.studio.service.support.CurrentActor.*;
import static com.shiguang.studio.service.support.ServiceChecks.*;

import com.shiguang.studio.cache.*;
import com.shiguang.studio.dto.BookingRequests.*;
import com.shiguang.studio.dto.OperationsRequests.*;
import com.shiguang.studio.entity.*;
import com.shiguang.studio.mapper.*;
import com.shiguang.studio.security.AccountStateCache;
import com.shiguang.studio.service.booking.*;
import com.shiguang.studio.service.impl.*;
import com.shiguang.studio.service.operations.*;
import com.shiguang.studio.service.support.*;
import com.shiguang.studio.vo.BookingViews.*;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.*;

public final class ServiceTestFactory {
  public static com.shiguang.studio.service.impl.BookingServiceImpl booking(
      BookingMapper mapper,
      OperationsMapper ops,
      BookingReadCache cache,
      BookingConfigurationLock configurationLock,
      SyncVersions sync,
      BookingRedisLock redisLock,
      BookingSlotLock slotLock) {
    var events = new BusinessEvents(ops, sync);
    var catalog = new CatalogService(mapper, cache, configurationLock, events);
    var schedules =
        new ResourceScheduleService(mapper, cache, configurationLock, sync, events, catalog);
    var queries = new AppointmentQueryService(mapper);
    var availabilityService = new AvailabilityService(mapper, cache, catalog, queries);
    var allocation =
        new BookingAllocationService(mapper, cache, configurationLock, redisLock, slotLock);
    var creation =
        new AppointmentCreationService(
            ops, mapper, cache, configurationLock, slotLock, events, allocation, queries);
    return new BookingServiceImpl(
        redisLock, catalog, schedules, availabilityService, allocation, queries, creation);
  }

  public static com.shiguang.studio.service.impl.OperationsServiceImpl operations(
      OperationsMapper ops,
      BookingMapper data,
      BookingService booking,
      UserMapper users,
      PasswordEncoder encoder,
      AccountStateCache accountStateCache,
      BookingReadCache readCache,
      BookingConfigurationLock configurationLock,
      SyncVersions sync) {
    var events = new BusinessEvents(ops, sync);
    var commands = new CommandReceipts(ops);
    var guard = new AppointmentGuard(ops, booking, sync);
    var rulesService =
        new StudioManagementService(ops, data, booking, readCache, configurationLock, events);
    var activityService = new AppointmentActivityService(ops, data, booking, rulesService);
    var workflow =
        new AppointmentWorkflowService(
            ops, booking, readCache, configurationLock, events, commands, guard);
    var payments = new PaymentService(ops, events, commands, guard, activityService);
    var requestsService =
        new CustomerRequestService(
            ops, data, booking, readCache, configurationLock, events, commands, guard);
    var notificationsService = new NotificationService(ops, sync);
    var accounts =
        new AccountManagementService(
            ops,
            data,
            booking,
            users,
            encoder,
            accountStateCache,
            readCache,
            configurationLock,
            events);
    return new OperationsServiceImpl(
        activityService,
        rulesService,
        workflow,
        payments,
        requestsService,
        accounts,
        notificationsService);
  }
}
