package com.shiguang.studio.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.entity.Appointment;
import com.shiguang.studio.mapper.*;
import com.shiguang.studio.service.impl.BookingServiceImpl;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class BookingAccessTest {
  BookingMapper mapper;
  BookingServiceImpl service;

  @BeforeEach
  void setup() {
    mapper = mock(BookingMapper.class);
    service =
        ServiceTestFactory.booking(
            mapper,
            mock(OperationsMapper.class),
            mock(BookingReadCache.class),
            mock(BookingConfigurationLock.class),
            mock(com.shiguang.studio.cache.SyncVersions.class),
            mock(com.shiguang.studio.service.BookingRedisLock.class),
            mock(com.shiguang.studio.service.BookingSlotLock.class));
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
  }

  void role(String role) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "10", "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
  }

  @Test
  void customerCannotReadAnotherCustomersAppointment() {
    role("CUSTOMER");
    when(mapper.customerId(10)).thenReturn(1L);
    var a = new Appointment();
    a.setCustomerId(2L);
    when(mapper.appointment(20)).thenReturn(a);
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> service.detail(20))
            .getStatusCode()
            .value());
  }

  @Test
  void photographerCannotReadAnotherPhotographersAppointment() {
    role("PHOTOGRAPHER");
    when(mapper.employeeId(10)).thenReturn(1L);
    var a = new Appointment();
    a.setPhotographerId(2L);
    when(mapper.appointment(20)).thenReturn(a);
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> service.detail(20))
            .getStatusCode()
            .value());
  }

  @Test
  void listScopeIsEnforcedForCustomerAndPhotographer() {
    role("CUSTOMER");
    when(mapper.customerId(10)).thenReturn(1L);
    service.appointments(1, 10, null);
    verify(mapper).appointments(1L, null, null, 10, 0);
    role("PHOTOGRAPHER");
    when(mapper.employeeId(10)).thenReturn(2L);
    service.appointments(1, 10, null);
    verify(mapper).appointments(null, 2L, null, 10, 0);
  }
}
