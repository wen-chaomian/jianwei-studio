package com.shiguang.studio.service.booking;

import static com.shiguang.studio.service.support.CurrentActor.admin;
import static com.shiguang.studio.service.support.CurrentActor.user;
import static com.shiguang.studio.service.support.ServiceChecks.require;

import com.shiguang.studio.cache.BookingReadCache;
import com.shiguang.studio.dto.BookingRequests.PackageRequest;
import com.shiguang.studio.dto.BookingRequests.VenueRequest;
import com.shiguang.studio.entity.Photographer;
import com.shiguang.studio.entity.ServicePackage;
import com.shiguang.studio.entity.Venue;
import com.shiguang.studio.exception.BusinessException;
import com.shiguang.studio.mapper.BookingMapper;
import com.shiguang.studio.service.BookingConfigurationLock;
import com.shiguang.studio.service.support.BusinessEvents;
import com.shiguang.studio.vo.BookingViews.PackageVO;
import com.shiguang.studio.vo.BookingViews.ResourceVO;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  private final BookingMapper mapper;
  private final BookingReadCache cache;
  private final BookingConfigurationLock configurationLock;
  private final BusinessEvents events;

  public CatalogService(
      BookingMapper mapper,
      BookingReadCache cache,
      BookingConfigurationLock configurationLock,
      BusinessEvents events) {
    this.mapper = mapper;
    this.cache = cache;
    this.configurationLock = configurationLock;
    this.events = events;
  }

  private ResourceVO venueVO(Venue v) {
    return new ResourceVO(
        v.getId(), v.getName(), v.getLocation(), v.getDescription(), v.getEnabled(), false);
  }

  private ResourceVO photographerVO(Photographer p) {
    return new ResourceVO(p.getId(), p.getName(), "", "", p.getEnabled(), p.getBookable());
  }

  public List<ResourceVO> venues() {
    return cache.catalog().venues();
  }

  public List<ResourceVO> photographers() {
    return cache.catalog().photographers();
  }

  private PackageVO visiblePackage(PackageVO p, boolean all) {
    return new PackageVO(
        p.id(),
        p.name(),
        p.description(),
        p.coverUrl(),
        p.price(),
        p.durationMinutes(),
        p.bufferMinutes(),
        p.enabled(),
        p.photographers().stream().filter(v -> all || v.enabled() && v.bookable()).toList(),
        p.venues().stream().filter(v -> all || v.enabled()).toList());
  }

  private PackageVO packageVO(ServicePackage p, boolean all) {
    return cache.catalog().packages().stream()
        .filter(v -> v.id().equals(p.getId()))
        .map(v -> visiblePackage(v, all))
        .findFirst()
        .orElseThrow(() -> new BusinessException("套餐不存在"));
  }

  public List<PackageVO> packages(boolean all) {
    if (all) admin();
    return (all ? cache.catalog() : cache.displayCatalog())
        .packages().stream()
            .filter(p -> all || p.enabled())
            .map(p -> visiblePackage(p, all))
            .toList();
  }

  public PackageVO packageDetail(long id, boolean all) {
    if (all) admin();
    return cache.catalog().packages().stream()
        .filter(p -> p.id() == id && (all || p.enabled()))
        .map(p -> visiblePackage(p, all))
        .findFirst()
        .orElseThrow(() -> new BusinessException("套餐不存在或已下架"));
  }

  @Transactional
  public PackageVO savePackage(Long id, PackageRequest r) {
    admin();
    configurationLock.write();
    if (id != null) require(mapper.lockPackage(id) != null, "套餐不存在");
    require(r.durationMinutes() % 30 == 0 && r.bufferMinutes() % 30 == 0, "拍摄及缓冲时长需为30分钟的倍数");
    var ps = new LinkedHashSet<>(r.photographerIds());
    var vs = new LinkedHashSet<>(r.venueIds());
    require(ps.size() == r.photographerIds().size() && vs.size() == r.venueIds().size(), "资源不能重复");
    require(
        photographers().stream()
                .filter(v -> ps.contains(v.id()) && (!r.enabled() || v.enabled() && v.bookable()))
                .count()
            == ps.size(),
        "请选择可预约的在职摄影师");
    require(
        venues().stream().filter(v -> vs.contains(v.id()) && (!r.enabled() || v.enabled())).count()
            == vs.size(),
        "请选择启用的场地");
    ServicePackage p = new ServicePackage();
    p.setId(id);
    p.setName(r.name().strip());
    p.setDescription(r.description());
    p.setCoverUrl(r.coverUrl());
    p.setPrice(r.price());
    p.setDurationMinutes(r.durationMinutes());
    p.setBufferMinutes(r.bufferMinutes());
    p.setEnabled(r.enabled());
    if (id == null) mapper.insertPackage(p);
    else mapper.updatePackage(p);
    mapper.clearPhotographers(p.getId());
    mapper.clearVenues(p.getId());
    ps.forEach(x -> mapper.linkPhotographer(p.getId(), x));
    vs.forEach(x -> mapper.linkVenue(p.getId(), x));
    events.audit(user(), "PACKAGE", "SAVE", p.getId(), p.getName());
    cache.invalidateConfigurationAfterCommit();
    return packageVO(p, true);
  }

  @Transactional
  public ResourceVO saveVenue(Long id, VenueRequest r) {
    admin();
    configurationLock.write();
    if (id != null) require(mapper.venue(id) != null, "场地不存在");
    Venue v = new Venue();
    v.setId(id);
    v.setName(r.name().strip());
    v.setLocation(r.location());
    v.setDescription(r.description());
    v.setEnabled(r.enabled());
    if (id == null) mapper.insertVenue(v);
    else mapper.updateVenue(v);
    events.audit(user(), "VENUE", "SAVE", v.getId(), v.getName());
    cache.invalidateConfigurationAfterCommit();
    return venueVO(v);
  }

  @Transactional
  public void setBookable(long id, boolean bookable) {
    admin();
    configurationLock.write();
    require(photographers().stream().anyMatch(p -> p.id() == id), "摄影师不存在");
    mapper.setBookable(id, bookable);
    events.audit(user(), "EMPLOYEE", "BOOKABLE", id, "可预约：" + bookable);
    cache.invalidateConfigurationAfterCommit();
  }
}
