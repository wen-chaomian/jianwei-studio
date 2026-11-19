package com.shiguang.studio.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.HashMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DynamicMapperTest {
  @Test
  void dynamicFiltersAndParameterBindingsSurviveXmlExtraction() throws Exception {
    var configuration = new Configuration();
    for (String mapper : new String[] {"BookingMapper", "OperationsMapper"}) {
      String resource = "mapper/" + mapper + ".xml";
      try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
        assertNotNull(stream);
        new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments())
            .parse();
      }
    }
    for (int mask = 0; mask < 8; mask++) {
      var parameters = new HashMap<String, Object>();
      parameters.put("customer", (mask & 1) != 0 ? 10L : null);
      parameters.put("photographer", (mask & 2) != 0 ? 20L : null);
      parameters.put("date", (mask & 4) != 0 ? LocalDate.of(2026, 10, 30) : null);
      parameters.put("size", 20);
      parameters.put("offset", 0);
      for (String method :
          new String[] {"appointments", "countAppointments", "requests", "requestCount"}) {
        boolean booking = method.equals("appointments") || method.equals("countAppointments");
        boolean paged = method.equals("appointments") || method.equals("requests");
        String mapper = booking ? "BookingMapper" : "OperationsMapper";
        var statement =
            configuration.getMappedStatement("com.shiguang.studio.mapper." + mapper + "." + method);
        var bound = statement.getBoundSql(parameters);
        String sql = bound.getSql().replaceAll("\\s+", " ");
        assertFalse(sql.contains("<if"));
        assertEquals((mask & 1) != 0, sql.contains("customer_id=?"));
        if (booking) {
          assertEquals((mask & 2) != 0, sql.contains("photographer_id=?"));
          assertEquals((mask & 4) != 0, sql.contains("DATE_ADD"));
        }
        int expected = ((mask & 1) != 0 ? 1 : 0) + (paged ? 2 : 0);
        if (booking) expected += ((mask & 2) != 0 ? 1 : 0) + ((mask & 4) != 0 ? 2 : 0);
        assertEquals(expected, bound.getParameterMappings().size());
        assertEquals(paged, sql.contains("LIMIT ? OFFSET ?"));
      }
    }
  }
}
