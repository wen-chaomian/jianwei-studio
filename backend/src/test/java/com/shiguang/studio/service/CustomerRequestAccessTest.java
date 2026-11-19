package com.shiguang.studio.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.shiguang.studio.mapper.*;
import com.shiguang.studio.service.operations.CustomerRequestService;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
class CustomerRequestAccessTest {
  final BookingMapper data=mock(BookingMapper.class);
  final OperationsMapper ops=mock(OperationsMapper.class);
  final CustomerRequestService service=new CustomerRequestService(ops,data,null,null,null,null,null,null);
  void login(String role) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("10","",List.of(new SimpleGrantedAuthority("ROLE_"+role)))); }
  @AfterEach void clear(){ SecurityContextHolder.clearContext(); }
  @Test void missingCustomerProfileCannotQueryAllRequests(){
    login("CUSTOMER");when(data.customerId(10)).thenReturn(null);
    assertEquals(403,assertThrows(ResponseStatusException.class,()->service.requests(1,20)).getStatusCode().value());
    verifyNoInteractions(ops);
  }
  @Test void customerScopeAndReceptionAllScope(){
    login("CUSTOMER");when(data.customerId(10)).thenReturn(7L);
    service.requests(1,20);verify(ops).requests(7L,20,0);
    login("RECEPTIONIST");service.requests(1,20);verify(ops).requests(null,20,0);
  }
}
