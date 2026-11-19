package com.shiguang.studio.integration.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class AgentResultCache {
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>("""
      local old=redis.call('GET',KEYS[1])
      local incoming=cjson.decode(ARGV[1])
      if old then
        local previous=cjson.decode(old)
        if tonumber(previous.snapshot.revision)>=tonumber(incoming.snapshot.revision) then return 0 end
      end
      redis.call('SET',KEYS[1],ARGV[1],'EX',3600)
      return 1
      """, Long.class);

  public AgentResultCache(StringRedisTemplate redis, ObjectMapper json) {
    this.redis=redis; this.json=json;
  }
  private String key(String id) { return "jianwei:agent:run:"+UUID.fromString(id); }
  public void save(JsonNode envelope) throws Exception {
    var snapshot=envelope.required("snapshot");
    if (!envelope.hasNonNull("owner_id") || !envelope.hasNonNull("portal")
        || !snapshot.hasNonNull("revision") || !snapshot.hasNonNull("status"))
      throw new IllegalArgumentException("Invalid agent event");
    redis.execute(SAVE,List.of(key(snapshot.required("id").asText())),json.writeValueAsString(envelope));
  }
  public JsonNode find(AgentIdentity actor, UUID id) throws Exception {
    String value=redis.opsForValue().get(key(id.toString()));
    if(value==null)return null;
    var envelope=json.readTree(value);
    if(envelope.path("owner_id").asLong()!=actor.userId()
        || !envelope.path("portal").asText().equals(actor.portal()))return null;
    return envelope.path("snapshot");
  }
}
