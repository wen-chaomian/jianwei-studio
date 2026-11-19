package com.shiguang.studio.service.assistant;
import static com.shiguang.studio.service.support.CurrentActor.*;
import static com.shiguang.studio.service.support.ServiceChecks.require;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class CustomerPreferences {
  private final JdbcTemplate db;
  public List<Map<String,Object>> list(){
    allowed(role("CUSTOMER"));
    return db.queryForList("SELECT topic,preference,version FROM customer_preference WHERE user_id=? ORDER BY topic",user());
  }
  public long version(String topic){
    allowed(role("CUSTOMER"));
    return db.queryForObject("SELECT COALESCE(MAX(version),0) FROM customer_preference WHERE user_id=? AND topic=?",Long.class,user(),topic);
  }
  public void save(String topic,String value,long version){
    allowed(role("CUSTOMER"));
    require(topic!=null&&!topic.isBlank()&&topic.length()<=40&&value!=null&&!value.isBlank()&&value.length()<=300,"请填写偏好名称和内容");
    int changed= version==0
      ? db.update("INSERT IGNORE INTO customer_preference(user_id,topic,preference) VALUES(?,?,?)",user(),topic,value.strip())
      : db.update("UPDATE customer_preference SET preference=?,version=version+1 WHERE user_id=? AND topic=? AND version=?",value.strip(),user(),topic,version);
    require(changed==1,"偏好已变化，请重新查看");
  }
  public void remove(String topic,long version){
    allowed(role("CUSTOMER"));
    require(db.update("DELETE FROM customer_preference WHERE user_id=? AND topic=? AND version=?",user(),topic,version)==1,"偏好已变化，请重新查看");
  }
}
