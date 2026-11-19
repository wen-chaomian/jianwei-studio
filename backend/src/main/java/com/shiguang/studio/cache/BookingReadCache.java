package com.shiguang.studio.cache;

import com.shiguang.studio.dto.OperationsRequests.RuleRequest;
import com.shiguang.studio.entity.PackageResource;
import com.shiguang.studio.entity.ResourceOccupancy;
import com.shiguang.studio.entity.ResourcePeriod;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.mapper.OperationsMapper;
import com.shiguang.studio.vo.BookingViews.PackageVO;
import com.shiguang.studio.vo.BookingViews.ResourceVO;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class BookingReadCache {
  public record Catalog(
      List<PackageVO> packages, List<ResourceVO> photographers, List<ResourceVO> venues) {}

  public record DaySnapshot(
      List<ResourcePeriod> photographerPeriods,
      List<ResourcePeriod> venuePeriods,
      List<ResourceOccupancy> occupancy) {}

  private final BookingMapper mapper;
  private final OperationsMapper operations;
  private final RedisReadCache cache;
  private final SyncVersions sync;

  public BookingReadCache(
      BookingMapper mapper, OperationsMapper operations, RedisReadCache cache, SyncVersions sync) {
    this.sync = sync;
    this.mapper = mapper;
    this.operations = operations;
    this.cache = cache;
  }

  public Catalog catalog() {
    return cache.get(
        List.of("configuration"),
        "catalog",
        Catalog.class,
        Duration.ofMinutes(5),
        this::loadCatalog);
  }

  public Catalog displayCatalog() {
    return cache.getDisplay(
        List.of("configuration"),
        "catalog",
        Catalog.class,
        Duration.ofMinutes(5),
        this::loadCatalog);
  }

  private Catalog loadCatalog() {
    // Load relationships in batches instead of querying both resource lists once per package.
    var packages = mapper.packages();
    var photographers =
        mapper.photographers().stream()
            .map(
                p ->
                    new ResourceVO(p.getId(), p.getName(), "", "", p.getEnabled(), p.getBookable()))
            .toList();
    var venues =
        mapper.venues().stream()
            .map(
                v ->
                    new ResourceVO(
                        v.getId(),
                        v.getName(),
                        v.getLocation(),
                        v.getDescription(),
                        v.getEnabled(),
                        false))
            .toList();
    var peopleByPackage = links(mapper.packagePhotographers());
    var venuesByPackage = links(mapper.packageVenues());
    var views =
        packages.stream()
            .map(
                p ->
                    new PackageVO(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        p.getCoverUrl(),
                        p.getPrice(),
                        p.getDurationMinutes(),
                        p.getBufferMinutes(),
                        p.getEnabled(),
                        photographers.stream()
                            .filter(
                                v ->
                                    peopleByPackage
                                        .getOrDefault(p.getId(), Set.of())
                                        .contains(v.id()))
                            .toList(),
                        venues.stream()
                            .filter(
                                v ->
                                    venuesByPackage
                                        .getOrDefault(p.getId(), Set.of())
                                        .contains(v.id()))
                            .toList()))
            .toList();
    return new Catalog(views, photographers, venues);
  }

  private Map<Long, Set<Long>> links(List<PackageResource> rows) {
    return rows.stream()
        .collect(
            Collectors.groupingBy(
                PackageResource::getPackageId,
                Collectors.mapping(PackageResource::getResourceId, Collectors.toSet())));
  }

  public RuleRequest rules() {
    return cache.get(
        List.of("configuration"),
        "rules",
        RuleRequest.class,
        Duration.ofMinutes(5),
        this::currentRules);
  }

  /** Always query the authoritative rule once inside a guarded booking transaction. */
  public RuleRequest currentRules() {
    var r = operations.rule();
    return new RuleRequest(
        LocalTime.parse(r.get("open_time").toString()),
        LocalTime.parse(r.get("close_time").toString()),
        ((Number) r.get("slot_minutes")).intValue(),
        ((Number) r.get("min_advance_hours")).intValue(),
        ((Number) r.get("max_advance_days")).intValue(),
        ((Number) r.get("change_cutoff_hours")).intValue());
  }

  public DaySnapshot day(LocalDate date) {
    return cache.get(
        List.of("configuration", "day:" + date),
        "day:" + date,
        DaySnapshot.class,
        Duration.ofSeconds(20),
        () ->
            new DaySnapshot(
                mapper.dayPeriods("photographer_schedule", date),
                mapper.dayPeriods("venue_schedule", date),
                mapper.dayOccupancy(date.atStartOfDay(), date.plusDays(1).atStartOfDay())));
  }

  public void invalidateConfigurationAfterCommit() {
    cache.invalidateAfterCommit("configuration");
    sync.changed("configuration", "desk", "schedule");
  }

  public void invalidateAvailabilityAfterCommit(LocalDate... dates) {
    sync.changed("availability");
    cache.invalidateAfterCommit(Arrays.stream(dates).map(d -> "day:" + d).toArray(String[]::new));
  }
}
