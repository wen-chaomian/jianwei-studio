package com.shiguang.studio.integration.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentMqGateway {
  private static final Logger log=LoggerFactory.getLogger(AgentMqGateway.class);
  private final ObjectMapper json;
  private final AgentResultCache cache;
  @Value("${studio.agent.mq.result-group:JianweiJavaResults}")
  private String resultGroup="JianweiJavaResults";
  private final String nameserver, commandTopic, resultTopic, token;
  private final ScheduledExecutorService connector=Executors.newSingleThreadScheduledExecutor(r->{
    var t=new Thread(r,"agent-mq-connect");t.setDaemon(true);return t;
  });
  @org.springframework.beans.factory.annotation.Autowired
  private com.shiguang.studio.service.knowledge.KnowledgeJobs knowledgeJobs;
  private volatile DefaultMQProducer producer;
  private DefaultMQPushConsumer consumer;
  private volatile boolean closed;

  public AgentMqGateway(ObjectMapper json,AgentResultCache cache,
      @Value("${studio.agent.mq.nameserver:127.0.0.1:9876}") String nameserver,
      @Value("${studio.agent.mq.command-topic:JianweiAgentCommand}") String commandTopic,
      @Value("${studio.agent.mq.result-topic:JianweiAgentResult}") String resultTopic,
      @Value("${studio.agent.internal-token:}") String token) {
    this.json=json;this.cache=cache;this.nameserver=nameserver;
    this.commandTopic=commandTopic;this.resultTopic=resultTopic;this.token=token;
  }
  @PostConstruct
  void start(){connector.scheduleWithFixedDelay(this::connect,0,5,TimeUnit.SECONDS);}
  private synchronized void connect(){
    if(closed || producer!=null)return;
    DefaultMQProducer p=new DefaultMQProducer("JianweiJavaCommands");
    DefaultMQPushConsumer c=new DefaultMQPushConsumer(resultGroup);
    try {
      p.setNamesrvAddr(nameserver);p.setSendMsgTimeout(1500);p.setRetryTimesWhenSendFailed(0);
      p.setInstanceName("jianwei-producer-"+ProcessHandle.current().pid());
      c.setNamesrvAddr(nameserver);c.setInstanceName("jianwei-results-"+ProcessHandle.current().pid());
      c.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
      c.setConsumeThreadMin(2);c.setConsumeThreadMax(4);c.setConsumeMessageBatchMaxSize(1);
      c.subscribe(resultTopic,"*");
      c.registerMessageListener((MessageListenerConcurrently)(messages,ctx)->{
        for(var message:messages){
          JsonNode event;
          try {event=decode(message.getBody());}
          catch(Exception invalid){log.warn("Discarding invalid agent MQ envelope");continue;}
          try {
            if ("index_result".equals(event.path("action").asText())) knowledgeJobs.receive(event);
            else cache.save(event);
          }
          catch(Exception unavailable){return ConsumeConcurrentlyStatus.RECONSUME_LATER;}
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
      });
      p.start();c.start();consumer=c;producer=p;
      log.info("Agent RocketMQ command/result transport connected");
    }catch(Exception e){p.shutdown();c.shutdown();log.warn("Agent MQ not ready; durable outbox retains tasks ({})",e.getClass().getSimpleName());}
  }
  private byte[] sign(String body) throws Exception {
    Mac mac=Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
    return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
  }
  JsonNode decode(byte[] bytes) throws Exception {
    var envelope=json.readTree(bytes);
    String body=envelope.required("body").asText();
    if(!MessageDigest.isEqual(sign(body),HexFormat.of().parseHex(envelope.required("signature").asText())))
      throw new IllegalArgumentException("Invalid signature");
    return json.readTree(body);
  }
  public void dispatch(UUID runId,String action){
    // Admission has already committed a transactional outbox in PostgreSQL.
    // A failed fast path must not turn a successfully accepted message into a UI error.
    var current=producer;
    if(current==null)return;
    try{
      String body=json.writeValueAsString(Map.of("run_id",runId.toString(),"action",action));
      byte[] envelope=json.writeValueAsBytes(Map.of("body",body,"signature",HexFormat.of().formatHex(sign(body))));
      current.send(new Message(commandTopic,action,runId.toString(),envelope));
    }catch(Exception e){log.warn("Agent command deferred to durable outbox run={}",runId);}
  }
  public boolean sendKnowledge(Map<String,Object> job) {
    var current=producer;
    if(current==null) return false;
    try {
      String body=json.writeValueAsString(job);
      byte[] bytes=json.writeValueAsBytes(Map.of("body",body,"signature",HexFormat.of().formatHex(sign(body))));
      current.send(new Message(commandTopic,"index_knowledge",job.get("jobId").toString(),bytes));
      return true;
    } catch(Exception ex) {
      log.warn("Knowledge index dispatch deferred article={}",job.get("articleId"));
      return false;
    }
  }
  @PreDestroy
  public synchronized void close(){
    closed=true;connector.shutdownNow();
    if(consumer!=null)consumer.shutdown();
    if(producer!=null)producer.shutdown();
  }
}
