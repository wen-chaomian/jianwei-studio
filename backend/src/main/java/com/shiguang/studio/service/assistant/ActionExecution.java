package com.shiguang.studio.service.assistant;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shiguang.studio.dto.BookingRequests.CreateAppointmentRequest;
import com.shiguang.studio.dto.OperationsRequests.*;
import com.shiguang.studio.dto.KnowledgeRequest;
import com.shiguang.studio.mapper.*;
import com.shiguang.studio.service.*;
import static com.shiguang.studio.service.support.ServiceChecks.require;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class ActionExecution {
  private final BookingService booking;
  private final OperationsService ops;
  private final OperationsMapper data;
  private final KnowledgeMapper articles;
  private final KnowledgeService knowledge;
  private final CustomerPreferences preferences;
  private final BookingMapper catalog;
  private final BookingConfigurationLock configuration;

  public String describe(ActionKind kind,ObjectNode p,String role){
    if(kind==ActionKind.BOOK){
      long photographer=p.get("photographerId").asLong();
      var pkg=booking.packageDetail(p.get("packageId").asLong(),false);
      var person=pkg.photographers().stream().filter(x->x.id()==photographer).findFirst().orElseThrow();
      var start=time(p);
      require(booking.availability(pkg.id(),photographer,start.toLocalDate()).stream().anyMatch(slot->slot.startTime().equals(start)),"时段不可约，请重新选择");
      require(role.equals("CUSTOMER")||(!p.path("customerName").asText().isBlank()&&p.path("customerPhone").asText().matches("1[3-9][0-9]{9}")),"代客预约需要客户姓名和手机号");
      if(role.equals("CUSTOMER")){p.remove("customerName");p.remove("customerPhone");}
      p.put("price",pkg.price());p.put("duration",pkg.durationMinutes());
      return "拍摄套餐："+pkg.name()+"\n摄影师："+person.name()+"\n拍摄时间："+start.toString().replace('T',' ')+" – "+start.plusMinutes(pkg.durationMinutes()).toLocalTime()
          +"\n拍摄时长："+pkg.durationMinutes()+"分钟\n预约金额：¥"+pkg.price()
          +(p.path("customerName").asText().isBlank()?"":"\n客户姓名："+p.path("customerName").asText())
          +(p.path("customerPhone").asText().isBlank()?"":"\n联系电话："+p.path("customerPhone").asText())
          +(p.path("remark").asText().isBlank()?"":"\n备注："+p.path("remark").asText());
    }
    if(kind==ActionKind.REMEMBER){
      p.put("version",preferences.version(p.get("topic").asText()));
      return "偏好主题："+p.get("topic").asText()+"\n偏好内容："+p.get("preference").asText();
    }
    if(kind==ActionKind.KNOWLEDGE){
      var article=p.has("articleId")?articles.find(p.get("articleId").asLong()):null;
      require(!p.has("articleId")||article!=null,"文章不存在");
      if(article!=null){require(article.getVersion()==p.path("articleVersion").asLong(),"请重新查询文章并提交其当前版本");p.put("category",article.getCategory());}
      p.put("version",article==null?0:article.getVersion());
      String status=switch(p.get("status").asText()){case "PUBLISHED"->"发布";case "DRAFT"->"草稿";default->"停用";};
      String audience=switch(p.get("audience").asText()){case "PUBLIC"->"客户可见";case "STAFF"->"员工可见";default->"仅管理端";};
      return "文章信息："+status+" · "+audience+" · "+(article==null?"新文章":"文章 #"+article.getId())+"《"+p.get("title").asText()+"》\n正文："+p.get("content").asText();
    }
    long appointment;
    String extra="";
    if(kind==ActionKind.HANDLE){
      var request=data.request(p.get("requestNumber").asLong());
      require(request!=null,"申请不存在");appointment=((Number)request.get("appointment_id")).longValue();
      p.put("requestVersion",((Number)request.get("version")).intValue());
      p.put("appointmentId",appointment);
      extra=" · "+switch(Objects.toString(request.get("type"))){case "CANCEL"->"取消预约";case "RESCHEDULE"->"改期";default->"客户";}+"申请 #"+p.get("requestNumber")+" · "+switch(p.get("decision").asText()){case "RESOLVED"->"同意办理";case "CLOSED"->"结束申请";default->"转为处理中";};
      if(p.has("startTime")) extra+=" · 改至 "+time(p)+" · 摄影师 #"+p.path("photographerId").asText();
    } else if(kind==ActionKind.REFUND){
      var payment=data.payment(p.get("paymentId").asLong());
      require(payment!=null&&"PAYMENT".equals(payment.get("type")),"原收款不存在");
      appointment=((Number)payment.get("appointment_id")).longValue();p.put("appointmentId",appointment);
      extra=" · 原流水 #"+p.get("paymentId")+" · ¥"+p.get("amount");
    } else appointment=p.get("appointmentId").asLong();
    var detail=booking.detail(appointment);
    p.put("version",data.version(appointment));
    if(kind==ActionKind.PAYMENT)extra=" · ¥"+p.get("amount")+" · "+switch(p.get("method").asText()){case "WECHAT"->"微信";case "ALIPAY"->"支付宝";case "BANK"->"银行转账";default->"现金";};
    return "预约信息：#"+appointment+" · "+detail.customerName()+"\n拍摄套餐："+detail.packageName()+"\n拍摄时间："+detail.startTime().toString().replace('T',' ')
        +(extra.isBlank()?"":"\n办理内容："+extra.replaceFirst("^ · ",""))
        +(p.path("reason").asText().isBlank()?"":"\n办理说明："+p.path("reason").asText())
        +(p.path("content").asText().isBlank()?"":"\n申请内容："+p.path("content").asText());
  }
  private LocalDateTime time(ObjectNode p){return LocalDateTime.parse(p.get("startTime").asText());}
  public String execute(ActionKind kind,ObjectNode p,String id){
    long appointment=p.path("appointmentId").asLong();
    int version=p.path("version").asInt();
    if(appointment>0){
      configuration.read();data.lockAppointment(appointment);
      booking.detail(appointment);
      require(data.version(appointment)==version,"预约已变化，请重新核对并生成确认单");
    }
    switch(kind){
      case BOOK -> {
        configuration.read();var pkg=catalog.sharePackage(p.get("packageId").asLong());
        require(pkg!=null&&pkg.getPrice().compareTo(p.get("price").decimalValue())==0&&pkg.getDurationMinutes()==p.get("duration").asInt(),"套餐已变化，请重新确认");
        var result=booking.create(new CreateAppointmentRequest(pkg.getId(),p.get("photographerId").asLong(),time(p),id,p.path("remark").asText(),p.path("customerName").asText(null),p.path("customerPhone").asText(null)));
        return "预约已创建，预约 #"+result.id();
      }
      case RESCHEDULE_REQUEST,CANCEL_REQUEST -> ops.createRequest(new RequestCreate(appointment,kind==ActionKind.CANCEL_REQUEST?"CANCEL":"RESCHEDULE",p.get("content").asText(),id));
      case HANDLE -> ops.handleRequest(p.get("requestNumber").asLong(),new RequestHandle(p.get("decision").asText(),p.path("reason").asText(),p.get("requestVersion").asInt(),id,version,p.has("photographerId")?p.get("photographerId").asLong():null,p.has("startTime")?time(p):null));
      case CHECK_IN,START,COMPLETE -> ops.act(appointment,kind==ActionKind.CHECK_IN?"check-in":kind.name().toLowerCase(Locale.ROOT),new ActionRequest(id,version,""));
      case PAYMENT,REFUND -> {
        var r=new PaymentRequest(id,p.get("amount").decimalValue(),p.path("method").asText("ORIGINAL"),p.path("reason").asText());
        if(kind==ActionKind.PAYMENT)ops.payment(appointment,r);else ops.refund(p.get("paymentId").asLong(),r);
      }
      case KNOWLEDGE -> knowledge.save(p.has("articleId")?p.get("articleId").asLong():null,new KnowledgeRequest(p.get("title").asText(),p.path("category").asText("服务说明"),p.get("content").asText(),p.get("status").asText(),(long)version,p.get("audience").asText()));
      case REMEMBER -> preferences.save(p.get("topic").asText(),p.get("preference").asText(),version);
    }
    return kind.label+"已完成";
  }
}
