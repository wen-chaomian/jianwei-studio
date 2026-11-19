package com.shiguang.studio.service.booking;

import static com.shiguang.studio.service.booking.BookingPolicy.*;
import static com.shiguang.studio.service.support.CurrentActor.*;
import static com.shiguang.studio.service.support.ServiceChecks.require;
import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.dto.BookingRequests.*;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.service.BookingConfigurationLock;
import com.shiguang.studio.service.support.BusinessEvents;
import com.shiguang.studio.vo.BookingViews.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class WeeklyScheduleService {
  private final BookingMapper mapper;
  private final BookingConfigurationLock guard;
  private final BookingReadCache cache;
  private final BusinessEvents events;
  private final ResourceScheduleService schedules;
  private final CatalogService catalog;
  public WeeklyScheduleService(BookingMapper mapper,BookingConfigurationLock guard,BookingReadCache cache,
      BusinessEvents events,ResourceScheduleService schedules,CatalogService catalog){
    this.mapper=mapper;this.guard=guard;this.cache=cache;this.events=events;this.schedules=schedules;this.catalog=catalog;
  }
  private LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
  private void exists(String kind,long id){
    table(kind);
    require(kind.equals("photographer")?catalog.photographers().stream().anyMatch(x->x.id().equals(id)):mapper.venue(id)!=null,"资源不存在");
  }
  public List<WeeklyDayVO> week(String kind,long id){
    String t=table(kind);
    if(!role("ADMIN")) require(kind.equals("photographer")&&role("PHOTOGRAPHER")&&Objects.equals(mapper.employeeId(user()),id),"只能查看本人排班");
    exists(kind,id);
    var days=new ArrayList<WeeklyDayVO>();
    for(int day=1;day<=7;day++){
      var w=mapper.weeklySchedule(t,id,day);
      days.add(new WeeklyDayVO(day,w!=null&&w.getEnabled(),w==null?List.of():mapper.periods(t+"_weekly",w.getId()).stream().map(p->new PeriodVO(p.getStartTime(),p.getEndTime())).toList()));
    }
    return days;
  }
  public List<WeeklyDayVO> myWeek(){
    require(role("PHOTOGRAPHER"),"仅摄影师可查看拍摄排班");
    Long id=mapper.employeeId(user());require(id!=null,"员工档案不存在");return week("photographer",id);
  }
  private List<PeriodRequest> validate(WeeklyDayRequest d){
    require(d.dayOfWeek()>=1&&d.dayOfWeek()<=7,"星期不正确");
    var ps=d.periods().stream().sorted(Comparator.comparing(PeriodRequest::startTime)).toList();
    require(!d.enabled()||!ps.isEmpty(),"工作日请至少设置一个时段");
    LocalTime last=null;
    for(var p:ps){
      require(p.startTime().isBefore(p.endTime())&&p.startTime().getSecond()==0&&p.endTime().getSecond()==0&&p.startTime().getNano()==0&&p.endTime().getNano()==0,"排班时间不正确");
      require(last==null||!p.startTime().isBefore(last),"排班时段不能重叠");last=p.endTime();
    }
    return ps;
  }
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public List<WeeklyDayVO> save(String kind,WeeklyScheduleRequest request){
    admin();guard.write();exists(kind,request.resourceId());String t=table(kind);
    require(request.days().size()==7&&request.days().stream().map(WeeklyDayRequest::dayOfWeek).distinct().count()==7,"请完整设置周一至周日，星期不能重复");
    var days=new HashMap<Integer,WeeklyDayRequest>();
    for(var d:request.days())days.put(d.dayOfWeek(),new WeeklyDayRequest(d.dayOfWeek(),d.enabled(),validate(d)));
    // Booking transactions hold the shared configuration lock; changes cannot race a new booking.
    for(var a:mapper.conflicts(column(kind),request.resourceId(),today().atStartOfDay(),LocalDate.of(9999,12,31).atStartOfDay())){
      var date=a.getStartTime().toLocalDate();
      if(mapper.dateSchedule(t,request.resourceId(),date)!=null)continue;
      var d=days.get(date.getDayOfWeek().getValue());
      require(d.enabled()&&d.periods().stream().anyMatch(p->!a.getStartTime().isBefore(date.atTime(p.startTime()))&&!a.getOccupiedEndTime().isAfter(date.atTime(p.endTime()))),"周排班影响已有预约（"+date+"），请先处理预约或设置当天特殊安排");
    }
    for(var d:days.values()){
      mapper.setWeeklySchedule(t,request.resourceId(),d.dayOfWeek(),d.enabled());
      var w=mapper.weeklySchedule(t,request.resourceId(),d.dayOfWeek());mapper.clearPeriods(t+"_weekly",w.getId());
      if(d.enabled())for(var p:d.periods())mapper.insertPeriod(t+"_weekly",w.getId(),p.startTime(),p.endTime());
    }
    events.audit(user(),"SCHEDULE","SAVE_WEEKLY",request.resourceId(),kind+" / 固定周排班");
    cache.invalidateConfigurationAfterCommit();return week(kind,request.resourceId());
  }
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public ScheduleVO restore(String kind,long id,LocalDate date){
    admin();guard.write();exists(kind,id);require(!date.isBefore(today()),"不能修改过去日期的排班");
    String t=table(kind);var w=mapper.weeklySchedule(t,id,date.getDayOfWeek().getValue());
    var ps=w==null?List.<com.shiguang.studio.entity.SchedulePeriod>of():mapper.periods(t+"_weekly",w.getId());
    for(var a:mapper.conflicts(column(kind),id,date.atStartOfDay(),date.plusDays(1).atStartOfDay()))
      require(w!=null&&w.getEnabled()&&ps.stream().anyMatch(p->!a.getStartTime().isBefore(date.atTime(p.getStartTime()))&&!a.getOccupiedEndTime().isAfter(date.atTime(p.getEndTime()))),"恢复周排班会影响已有预约，请保留特殊安排或先处理预约");
    var d=mapper.lockSchedule(t,id,date);
    if(d!=null){mapper.clearPeriods(t,d.getId());mapper.deleteDateSchedule(t,d.getId());}
    events.audit(user(),"SCHEDULE","RESTORE_WEEKLY",id,kind+" / "+date);
    cache.invalidateAvailabilityAfterCommit(date);cache.invalidateConfigurationAfterCommit();
    return schedules.schedule(kind,id,date);
  }
}
