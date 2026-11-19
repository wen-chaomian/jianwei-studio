package com.shiguang.studio.config;

import com.shiguang.studio.cache.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.*;

@Configuration
public class SyncRedisEventsConfig {
  @Bean
  RedisMessageListenerContainer syncRedisEvents(
      RedisConnectionFactory factory, SyncEventStreams streams) {
    var container = new RedisMessageListenerContainer();
    container.setConnectionFactory(factory);
    container.addMessageListener(
        (message, pattern) ->
            streams.changed(
                new HashSet<>(
                    Arrays.asList(
                        new String(message.getBody(), StandardCharsets.UTF_8).split(",")))),
        new ChannelTopic(SyncVersions.CHANNEL));
    return container;
  }
}
