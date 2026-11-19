package com.shiguang.studio.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.shiguang.studio.service.assistant.*;
import com.shiguang.studio.service.BookingService;
import com.shiguang.studio.service.OperationsService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class BusinessTools {
  @org.springframework.beans.factory.annotation.Autowired
  private com.shiguang.studio.service.knowledge.KnowledgeSearch knowledge;
  @org.springframework.beans.factory.annotation.Autowired private ActionCards cards;
  @org.springframework.beans.factory.annotation.Autowired private CustomerPreferences preferences;
  @org.springframework.beans.factory.annotation.Autowired private com.shiguang.studio.service.KnowledgeService articles;
  private final BookingService booking;
  private final OperationsService operations;
  private final com.shiguang.studio.service.booking.WeeklyScheduleService weekly;
  public BusinessTools(BookingService booking, OperationsService operations, com.shiguang.studio.service.booking.WeeklyScheduleService weekly) {
    this.booking=booking; this.operations=operations; this.weekly=weekly;
  }
  private static final Set<String> ALL=Set.of("CUSTOMER","RECEPTIONIST","PHOTOGRAPHER","ADMIN");
  private static final Set<String> DESK=Set.of("CUSTOMER","RECEPTIONIST","ADMIN");
  public record Definition(String name,String description,Set<String> roles,Map<String,Object> properties,List<String> required) {
    Map<String,Object> schema(){return Map.of("type","object","properties",properties,"required",required,"additionalProperties",false);}
    Map<String,Object> publicValue(){return Map.of("name",name,"description",description,"inputSchema",schema(),"annotations",Map.of("readOnlyHint",true,"destructiveHint",false,"idempotentHint",true,"openWorldHint",false));}
  }
  private static Map<String,Object> integer(String description,int max){return Map.of("type","integer","minimum",1,"maximum",max,"description",description);}
  private static Map<String,Object> date(String description){return Map.of("type","string","format","date","description",description+"，YYYY-MM-DD");}
  private static Definition def(String n,String d,Set<String> roles,Map<String,Object> props,String... required){return new Definition(n,d,roles,props,List.of(required));}
  public List<Definition> definitions(){return List.of(
      def("get_my_preferences","在推荐拍摄方案或询问已记住的偏好时读取本人已保存偏好。",Set.of("CUSTOMER"),Map.of()),
      def("list_knowledge_articles","管理端分页读取知识库原文，维护已有文章前先核对标题、内容、可见范围和版本。",Set.of("ADMIN"),Map.of("page",integer("页码",100000),"size",integer("每页条数",50))),
      def("list_packages","查询真实套餐价格、拍摄时长、支持的摄影师及影棚；预订前先查询此工具取得 ID。",ALL,Map.of()),
      def("search_knowledge","查询工作室服务说明，宠物、服装、到店准备和服务政策须检索原文。",ALL,Map.of("question",Map.of("type","string","maxLength",500)),"question"),
      def("get_booking_rules","查询营业时间、提前预约与取消改期规则。",ALL,Map.of()),
      def("list_available_slots","查询指定套餐、摄影师、日期可预约的开始时间。结果不代表已锁定；缺少套餐或摄影师应先查询套餐并确认。",ALL,Map.of("packageId",integer("套餐 ID",Integer.MAX_VALUE),"photographerId",integer("摄影师 ID",Integer.MAX_VALUE),"date",date("查询日期")),"packageId","photographerId","date"),
      def("list_reschedule_slots","查询已有预约的改期可用时段，排除该预约原占用；不要用新建预约的时段工具判断改期冲突。",DESK,Map.of("appointmentId",integer("原预约 ID",Integer.MAX_VALUE),"photographerId",integer("摄影师 ID",Integer.MAX_VALUE),"date",date("改期日期")),"appointmentId","photographerId","date"),
      def("list_appointments","查询预约，客户只返回自己的，摄影师只返回分配给自己的，接待和管理员返回全部。date 可选；结果有分页，勿将当前页当成全部。",ALL,Map.of("date",date("预约日期，可不传"),"page",integer("页码，默认1",100000),"size",integer("每页条数，默认20",50))),
      def("get_appointment","按预约 ID 查询详情及拍摄需求，越权返回不可访问。",ALL,Map.of("appointmentId",integer("预约 ID",Integer.MAX_VALUE)),"appointmentId"),
      def("get_appointment_activity","查询指定预约的申请处理进度、到店拍摄进度、收款与退款记录。摄影师不可调用财务工具。",DESK,Map.of("appointmentId",integer("预约 ID",Integer.MAX_VALUE)),"appointmentId"),
      def("list_requests","分页查询申请；客户只返回自己的，接待和管理员返回全部。可根据返回的状态识别待处理项，不要遗漏分页。",DESK,Map.of("page",integer("页码，默认1",100000),"size",integer("每页条数，默认20",50))),
      def("get_my_weekly_schedule","摄影师查询本人周一至周日固定工作安排。具体日期可能有特殊安排，应再查询当天排班。",Set.of("PHOTOGRAPHER"),Map.of()),
      def("get_employee_weekly_schedule","管理员查询指定摄影师周一至周日固定工作安排。",Set.of("ADMIN"),Map.of("photographerId",integer("摄影师 ID",Integer.MAX_VALUE)),"photographerId"),
      def("get_my_schedule","摄影师查询本人指定日期的工作排班；拍摄任务请另查 list_appointments。",Set.of("PHOTOGRAPHER"),Map.of("date",date("排班日期")),"date"),
      def("get_employee_schedule","管理员查询指定摄影师的工作排班，摄影师 ID 可从套餐获取。",Set.of("ADMIN"),Map.of("photographerId",integer("摄影师 ID",Integer.MAX_VALUE),"date",date("排班日期")),"photographerId","date"),
      def("get_business_summary","仅管理员查询区间内预约及收退款统计；待处理申请数是当前总数，不是日期区间统计。最长366天。",Set.of("ADMIN"),Map.of("from",date("开始日期，包含"),"to",date("结束日期，包含")),"from","to")
  );}
  public List<Map<String,Object>> list(String role){var list=new ArrayList<>(definitions().stream().filter(d->d.roles().contains(role)).map(Definition::publicValue).toList());
    for(var kind:ActionKind.values())if(kind.permits(role))list.add(kind.definition());
    return list;}
  public Object call(String name,JsonNode args,McpAuthorization.Grant actor){
    var action=ActionKind.find(name);
    if(action.isPresent() && args instanceof com.fasterxml.jackson.databind.node.ObjectNode fields)
      return cards.prepare(action.get(),fields,actor);
    var d=definitions().stream().filter(x->x.name().equals(name)&&x.roles().contains(actor.role())).findFirst().orElseThrow(()->new IllegalArgumentException("无权调用或工具不存在"));
    validate(d,args);
    Object data=switch(name){
      case "get_my_preferences" -> preferences.list();
      case "list_knowledge_articles" -> articles.list(true,args.path("page").asInt(1),args.path("size").asInt(10));
      case "list_packages" -> packageFacts(actor.role().equals("ADMIN"));
      case "search_knowledge" -> knowledge.search(new com.shiguang.studio.integration.agent.AgentIdentity(actor.userId(),actor.portal(),actor.role()),args.get("question").asText());
      case "get_booking_rules" -> operations.rules();
      case "list_available_slots" -> booking.availability(args.get("packageId").asLong(),args.get("photographerId").asLong(),LocalDate.parse(args.get("date").asText()));
      case "list_reschedule_slots" -> booking.rescheduleAvailability(args.get("appointmentId").asLong(),args.get("photographerId").asLong(),LocalDate.parse(args.get("date").asText()));
      case "list_appointments" -> booking.appointments(args.path("page").asInt(1),args.path("size").asInt(20),args.has("date")?LocalDate.parse(args.get("date").asText()):null);
      case "get_appointment" -> booking.detail(args.get("appointmentId").asLong());
      case "get_appointment_activity" -> operations.activity(args.get("appointmentId").asLong());
      case "list_requests" -> operations.requests(args.path("page").asInt(1),args.path("size").asInt(20));
      case "get_my_weekly_schedule" -> weekly.myWeek();
      case "get_employee_weekly_schedule" -> weekly.week("photographer",args.get("photographerId").asLong());
      case "get_my_schedule" -> operations.mySchedule(LocalDate.parse(args.get("date").asText()));
      case "get_employee_schedule" -> booking.schedule("photographer",args.get("photographerId").asLong(),LocalDate.parse(args.get("date").asText()));
      case "get_business_summary" -> operations.dashboard(LocalDate.parse(args.get("from").asText()),LocalDate.parse(args.get("to").asText()));
      default -> throw new IllegalArgumentException("工具不存在");
    };
    if(name.equals("list_available_slots")||name.equals("list_reschedule_slots")){
      var facts=new LinkedHashMap<String,Object>();
      facts.put("date",args.get("date").asText());facts.put("photographerId",args.get("photographerId").asLong());
      facts.put(name.equals("list_reschedule_slots")?"appointmentId":"packageId",args.get(name.equals("list_reschedule_slots")?"appointmentId":"packageId").asLong());
      facts.put("slots",data);facts.put("note","仅 slots 内时段可约，已综合校验摄影师、场地和占用；空数组表示该条件下没有可约时段，最终确认时仍会重新校验。");
      data=facts;
    }
    String base="/"+actor.portal();
    String link=switch(name){
      case "get_appointment","get_appointment_activity","list_reschedule_slots" -> base+"/appointments/"+args.get("appointmentId").asLong();
      case "get_my_preferences" -> base+"/profile";
      case "list_knowledge_articles" -> base+"/knowledge";
      case "list_requests" -> base+"/requests";
      case "get_business_summary" -> "/admin";
      case "get_my_schedule","get_employee_schedule","get_my_weekly_schedule","get_employee_weekly_schedule" -> base+"/schedules";
      case "get_booking_rules" -> base+"/notices";
      case "list_packages","list_available_slots" -> actor.portal().equals("customer")?"/customer":base+"/appointments";
      default -> base+"/appointments";
    };
    return Map.of("data",data,"pageUrl",link,"queriedAt",OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString(),"scope",actor.role());
  }
  private List<Map<String,Object>> packageFacts(boolean admin){
    return booking.packages(admin).stream().map(p->{
      Map<String,Object> row=new LinkedHashMap<>();
      row.put("id",p.id());row.put("name",p.name());row.put("description",p.description());
      row.put("price",p.price());row.put("durationMinutes",p.durationMinutes());row.put("bufferMinutes",p.bufferMinutes());
      row.put("enabled",p.enabled());row.put("photographers",p.photographers());
      row.put("venues",p.venues().stream().map(v->Map.of("id",v.id(),"name",v.name(),"enabled",v.enabled())).toList());
      row.put("availabilityNote","资源启用状态不代表某天可预约，具体日期请查询可约时段；场地由系统分配。");
      return row;
    }).toList();
  }
  private void validate(Definition d,JsonNode a){
    if(a==null||!a.isObject())throw new IllegalArgumentException("参数必须是对象");
    for(String k:d.required())if(!a.has(k))throw new IllegalArgumentException("缺少参数："+k);
    a.fieldNames().forEachRemaining(k->{
      if(!d.properties().containsKey(k))throw new IllegalArgumentException("不支持的参数："+k);
      var spec=(Map<?,?>)d.properties().get(k);var v=a.get(k);
      if(spec.get("type").equals("integer")){
        if(!v.isIntegralNumber()||!v.canConvertToLong()||v.asLong()<1||v.asLong()>((Number)spec.get("maximum")).longValue())throw new IllegalArgumentException("数值参数不正确："+k);
      }else if(k.equals("question")){
        if(!v.isTextual() || v.asText().isBlank() || v.asText().length()>500) throw new IllegalArgumentException("问题长度不正确");
      }else{if(!v.isTextual()||!v.asText().matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException("日期参数不正确："+k);LocalDate.parse(v.asText());}
    });
  }
}
