package com.shiguang.studio.integration.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class AgentMqTest {
  @Test
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "REDIS_IT_PORT", matches = "[0-9]+")
  void cacheRejectsOutOfOrderSnapshotsAndOtherAccounts() throws Exception {
    var factory=new LettuceConnectionFactory("127.0.0.1",Integer.parseInt(System.getenv("REDIS_IT_PORT")));
    factory.afterPropertiesSet();factory.start();
    var redis=new StringRedisTemplate(factory);
    var json=new ObjectMapper();
    var cache=new AgentResultCache(redis,json);
    UUID id=UUID.randomUUID();
    try {
      var event=json.valueToTree(Map.of("owner_id",812345,"portal","customer","snapshot",
          Map.of("id",id.toString(),"revision",8,"status","completed","output","final")));
      cache.save(event);
      cache.save(json.valueToTree(Map.of("owner_id",812345,"portal","customer","snapshot",
          Map.of("id",id.toString(),"revision",2,"status","running","output","old"))));
      assertEquals("final",cache.find(new AgentIdentity(812345,"customer","CUSTOMER"),id).path("output").asText());
      assertNull(cache.find(new AgentIdentity(812346,"customer","CUSTOMER"),id));
      assertNull(cache.find(new AgentIdentity(812345,"staff","RECEPTIONIST"),id));
    } finally {redis.delete("jianwei:agent:run:"+id);factory.destroy();}
  }
  @Test
  void resultSignatureMustMatchBeforeAcceptingContent() throws Exception {
    String token="test-only-internal-token-at-least-32-characters";
    var json=new ObjectMapper();
    var gateway=new AgentMqGateway(json,mock(AgentResultCache.class),"localhost:9876","command","result",token);
    String body="{\"owner_id\":31}";
    Mac mac=Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
    String signature=HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    assertEquals(31,gateway.decode(json.writeValueAsBytes(Map.of("body",body,"signature",signature))).path("owner_id").asInt());
    assertThrows(IllegalArgumentException.class,()->gateway.decode(json.writeValueAsBytes(Map.of("body","{\"owner_id\":32}","signature",signature))));
    gateway.close();
  }
}
