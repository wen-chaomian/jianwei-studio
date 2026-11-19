package com.shiguang.studio.service.assistant;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;

public enum ActionKind {
  BOOK("创建预约","CUSTOMER RECEPTIONIST ADMIN","packageId photographerId startTime","customerName customerPhone remark"),
  RESCHEDULE_REQUEST("提交改期申请","CUSTOMER","appointmentId content",""),
  CANCEL_REQUEST("提交取消申请","CUSTOMER","appointmentId content",""),
  HANDLE("处理客户申请","RECEPTIONIST ADMIN","requestNumber decision","reason photographerId startTime"),
  CHECK_IN("登记到店","RECEPTIONIST ADMIN","appointmentId",""),
  START("开始拍摄","PHOTOGRAPHER ADMIN","appointmentId",""),
  COMPLETE("完成拍摄","PHOTOGRAPHER ADMIN","appointmentId",""),
  PAYMENT("登记收款","RECEPTIONIST ADMIN","appointmentId amount method",""),
  REFUND("登记退款","RECEPTIONIST ADMIN","paymentId amount reason",""),
  KNOWLEDGE("保存知识文章","ADMIN","title content status audience","articleId articleVersion"),
  REMEMBER("记住我的偏好","CUSTOMER","topic preference","");
  final String label;
  private final Set<String> roles;
  private final List<String> required, fields;
  ActionKind(String label,String roles,String required,String optional) {
    this.label=label; this.roles=Set.of(roles.split(" ")); this.required=List.of(required.split(" "));
    fields=new ArrayList<>(this.required);
    if(!optional.isEmpty()) fields.addAll(List.of(optional.split(" ")));
  }
  public boolean permits(String role){return roles.contains(role);}
  public String tool(){return "prepare_"+name().toLowerCase(Locale.ROOT);}
  public static Optional<ActionKind> find(String tool){
    return Arrays.stream(values()).filter(kind->kind.tool().equals(tool)).findFirst();
  }
  public Map<String,Object> definition(){
    var properties=new LinkedHashMap<String,Object>();
    for(String field:fields)properties.put(field,schema(field));
    return Map.of("name",tool(),"description","准备"+label+"的确认单。只有用户在页面核对并确认后才会执行。",
        "inputSchema",Map.of("type","object","properties",properties,"required",required,"additionalProperties",false));
  }
  private Map<String,Object> schema(String f){
    if(f.endsWith("Id")||f.equals("requestNumber")||f.equals("articleVersion"))return Map.of("type","integer","minimum",1);
    if(f.equals("amount"))return Map.of("type","number","minimum",0.01);
    var options=switch(f){
      case "decision" -> List.of("RESOLVED","CLOSED","PROCESSING");
      case "method" -> List.of("WECHAT","ALIPAY","CASH","BANK");
      case "status" -> List.of("DRAFT","PUBLISHED","DISABLED");
      case "audience" -> List.of("PUBLIC","STAFF","ADMIN");
      default -> List.of();
    };
    if(!options.isEmpty())return Map.of("type","string","enum",options);
    return Map.of("type","string","maxLength",limit(f),"description",f.equals("startTime")?"北京时间 YYYY-MM-DDTHH:mm:ss":f);
  }
  private int limit(String f){return switch(f){
    case "topic","customerName"->40; case "preference"->300; case "title"->120; case "content"->this==KNOWLEDGE?12000:1000;
    case "customerPhone"->20;default->500;};}
  public void validate(JsonNode args){
    if(!args.isObject()||required.stream().anyMatch(f->!args.hasNonNull(f)))throw new IllegalArgumentException();
    args.fields().forEachRemaining(entry->{
      var f=entry.getKey();var v=entry.getValue();
      if(!fields.contains(f))throw new IllegalArgumentException();
      var spec=schema(f);
      boolean valid=switch(spec.get("type").toString()){
        case "integer"->v.isIntegralNumber()&&v.canConvertToLong()&&v.asLong()>0;
        case "number"->v.isNumber()&&v.decimalValue().signum()>0&&v.decimalValue().scale()<=2&&v.decimalValue().precision()-v.decimalValue().scale()<=8;
        default->v.isTextual()&&!v.asText().isBlank()&&v.asText().length()<=limit(f)&&
          (!spec.containsKey("enum")||((List<?>)spec.get("enum")).contains(v.asText()));
      };
      if(!valid)throw new IllegalArgumentException("请检查 "+f);
    });
  }
}
