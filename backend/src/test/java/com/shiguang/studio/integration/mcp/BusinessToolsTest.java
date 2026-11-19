package com.shiguang.studio.integration.mcp;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.studio.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
class BusinessToolsTest {
  ObjectMapper json=new ObjectMapper(); BookingService booking=mock(BookingService.class); OperationsService ops=mock(OperationsService.class);
  BusinessTools tools=new BusinessTools(booking,ops,mock(com.shiguang.studio.service.booking.WeeklyScheduleService.class));
  McpAuthorization.Grant actor(String role){return new McpAuthorization.Grant(10,role.equals("ADMIN")?"admin":role.equals("CUSTOMER")?"customer":"staff",role,"session",1);}
  @Test void packageToolDoesNotMislabelEnabledVenueAsUnbookable()throws Exception{
    var venue=new com.shiguang.studio.vo.BookingViews.ResourceVO(1L,"影棚","","",true,false);
    var photographer=new com.shiguang.studio.vo.BookingViews.ResourceVO(3L,"李老师","","",true,true);
    var pkg=new com.shiguang.studio.vo.BookingViews.PackageVO(4L,"写真","","",new java.math.BigDecimal("399"),60,30,true,List.of(photographer),List.of(venue));
    when(booking.packages(false)).thenReturn(List.of(pkg));
    var result=json.valueToTree(tools.call("list_packages",json.createObjectNode(),actor("CUSTOMER")));
    var v=result.path("data").get(0).path("venues").get(0);
    assertTrue(v.path("enabled").asBoolean());assertFalse(v.has("bookable"));
    assertTrue(result.path("data").get(0).path("photographers").get(0).path("bookable").asBoolean());
  }
  @Test void rescheduleToolUsesExistingBookingAndKeepsDateContext()throws Exception{
    var day=java.time.LocalDate.of(2026,11,10);
    when(booking.rescheduleAvailability(350,3,day)).thenReturn(List.of());
    var result=json.valueToTree(tools.call("list_reschedule_slots",json.readTree("{\"appointmentId\":350,\"photographerId\":3,\"date\":\"2026-11-10\"}"),actor("CUSTOMER")));
    assertEquals(350,result.path("data").path("appointmentId").asInt());
    assertEquals("2026-11-10",result.path("data").path("date").asText());
    assertTrue(result.path("data").path("slots").isEmpty());
    verify(booking).rescheduleAvailability(350,3,day);
    verify(booking,never()).availability(anyLong(),anyLong(),any());
    assertThrows(IllegalArgumentException.class,()->tools.call("list_reschedule_slots",json.readTree("{\"appointmentId\":350,\"photographerId\":3,\"date\":\"2026-11-10\"}"),actor("PHOTOGRAPHER")));
  }
  @Test void scheduleLinksOpenSchedulePage()throws Exception{
    when(ops.mySchedule(java.time.LocalDate.parse("2026-11-06"))).thenReturn(Map.of());
    var result=(Map<?,?>)tools.call("get_my_schedule",json.readTree("{\"date\":\"2026-11-06\"}"),actor("PHOTOGRAPHER"));
    assertEquals("/staff/schedules",result.get("pageUrl"));
  }
  @Test void roleToolsAreRestricted(){
    assertFalse(tools.list("CUSTOMER").stream().anyMatch(x->x.get("name").equals("get_business_summary")));
    assertFalse(tools.list("PHOTOGRAPHER").stream().anyMatch(x->x.get("name").equals("list_requests")||x.get("name").equals("get_appointment_activity")));
    assertTrue(tools.list("RECEPTIONIST").stream().anyMatch(x->x.get("name").equals("get_appointment_activity")));
    assertTrue(tools.list("ADMIN").stream().anyMatch(x->x.get("name").equals("get_business_summary")));
  }
  @Test void hiddenToolsCannotBeInvokedDirectly()throws Exception{
    for(String role:List.of("CUSTOMER","RECEPTIONIST","PHOTOGRAPHER"))assertThrows(IllegalArgumentException.class,()->tools.call("get_business_summary",json.readTree("{\"from\":\"2026-11-01\",\"to\":\"2026-11-05\"}"),actor(role)));
    assertThrows(IllegalArgumentException.class,()->tools.call("cancel_appointment",json.createObjectNode(),actor("ADMIN")));
    verifyNoInteractions(booking,ops);
  }
  @Test void cannotInjectIdentityOrSql()throws Exception{
    assertThrows(IllegalArgumentException.class,()->tools.call("list_appointments",json.readTree("{\"userId\":99}"),actor("CUSTOMER")));
    assertThrows(IllegalArgumentException.class,()->tools.call("list_appointments",json.readTree("{\"sql\":\"select *\"}"),actor("ADMIN")));
    assertThrows(IllegalArgumentException.class,()->tools.call("list_appointments",json.readTree("{\"size\":999}"),actor("ADMIN")));
    verifyNoInteractions(booking,ops);
  }
  @Test void rejectsMissingOrMalformedParameters()throws Exception{
    assertThrows(IllegalArgumentException.class,()->tools.call("list_available_slots",json.createObjectNode(),actor("CUSTOMER")));
    assertThrows(RuntimeException.class,()->tools.call("list_appointments",json.readTree("{\"date\":\"2026-02-30\"}"),actor("CUSTOMER")));
    assertThrows(IllegalArgumentException.class,()->tools.call("get_appointment",json.readTree("{\"appointmentId\":1.5}"),actor("CUSTOMER")));
  }
  @Test void controllerUsesTrustedIdentityAndRestoresThread()throws Exception{
    var auth=mock(McpAuthorization.class);when(auth.verify("Bearer valid")).thenReturn(actor("RECEPTIONIST"));
    when(booking.packages(false)).thenAnswer(inv->{assertTrue(com.shiguang.studio.service.support.CurrentActor.role("RECEPTIONIST"));assertEquals(10,com.shiguang.studio.service.support.CurrentActor.user());return List.of();});
    String secret="internal-secret-at-least-32-characters";
    var controller=new McpController(auth,tools,json,secret);
    var before=SecurityContextHolder.getContext();
    var body=json.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"list_packages\",\"arguments\":{}}}");
    var result=controller.post(body,"Bearer valid",secret,null);
    assertTrue(result.getStatusCode().is2xxSuccessful());
    assertSame(before,SecurityContextHolder.getContext());
    assertThrows(ResponseStatusException.class,()->controller.post(body,"Bearer valid","wrong",null));
    assertThrows(ResponseStatusException.class,()->controller.post(body,"Bearer valid",secret,"http://evil.test"));
  }

 @Test void ruleLinksStayInEachPortal() {
  when(ops.rules()).thenReturn(new com.shiguang.studio.dto.OperationsRequests.RuleRequest(java.time.LocalTime.of(9,0),java.time.LocalTime.of(20,0),30,2,15,2));
  for(String role:List.of("ADMIN","CUSTOMER","PHOTOGRAPHER","RECEPTIONIST")) {
   var a=actor(role);var result=(Map<?,?>)tools.call("get_booking_rules",json.createObjectNode(),a);
   assertEquals("/"+a.portal()+"/notices",result.get("pageUrl"));
  }
 }
}
