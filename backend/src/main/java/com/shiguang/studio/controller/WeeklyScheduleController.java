package com.shiguang.studio.controller;
import com.shiguang.studio.common.ApiResponse;
import com.shiguang.studio.dto.BookingRequests.WeeklyScheduleRequest;
import com.shiguang.studio.service.booking.WeeklyScheduleService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class WeeklyScheduleController {
  private final WeeklyScheduleService service;
  public WeeklyScheduleController(WeeklyScheduleService service){this.service=service;}
  @GetMapping("/admin/weekly-schedules/{kind}")
  public ApiResponse<?> get(@PathVariable String kind,@RequestParam long resourceId){return ApiResponse.ok(service.week(kind,resourceId));}
  @PutMapping("/admin/weekly-schedules/{kind}")
  public ApiResponse<?> save(@PathVariable String kind,@Valid @RequestBody WeeklyScheduleRequest body){return ApiResponse.ok(service.save(kind,body));}
  @DeleteMapping("/admin/schedules/{kind}")
  public ApiResponse<?> restore(@PathVariable String kind,@RequestParam long resourceId,@RequestParam LocalDate date){return ApiResponse.ok(service.restore(kind,resourceId,date));}
  @GetMapping("/staff/my-weekly-schedule")
  public ApiResponse<?> mine(){return ApiResponse.ok(service.myWeek());}
}
