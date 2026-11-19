package com.shiguang.studio.mapper.contracts;

import com.shiguang.studio.entity.*;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

public interface CatalogStatements {
  @Select("SELECT * FROM service_package WHERE id=#{id} FOR SHARE")
  ServicePackage sharePackage(long id);

  @Select(
      "SELECT package_id,employee_id AS resource_id FROM package_photographer ORDER BY"
          + " package_id,employee_id")
  List<PackageResource> packagePhotographers();

  @Select(
      "SELECT package_id,venue_id AS resource_id FROM package_venue ORDER BY package_id,venue_id")
  List<PackageResource> packageVenues();

  @Select("SELECT * FROM service_package ORDER BY id")
  List<ServicePackage> packages();

  @Select("SELECT * FROM service_package WHERE id=#{id}")
  ServicePackage packageById(long id);

  @Select("SELECT * FROM service_package WHERE id=#{id} FOR UPDATE")
  ServicePackage lockPackage(long id);

  @Insert(
      "INSERT INTO"
          + " service_package(name,description,cover_url,price,duration_minutes,buffer_minutes,enabled)"
          + " VALUES(#{name},#{description},#{coverUrl},#{price},#{durationMinutes},#{bufferMinutes},#{enabled})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertPackage(ServicePackage p);

  @Update(
      "UPDATE service_package SET"
          + " name=#{name},description=#{description},cover_url=#{coverUrl},price=#{price},duration_minutes=#{durationMinutes},buffer_minutes=#{bufferMinutes},enabled=#{enabled}"
          + " WHERE id=#{id}")
  int updatePackage(ServicePackage p);

  @Select("SELECT * FROM venue ORDER BY id")
  List<Venue> venues();

  @Select("SELECT * FROM venue WHERE id=#{id}")
  Venue venue(long id);

  @Insert(
      "INSERT INTO venue(name,location,description,enabled)"
          + " VALUES(#{name},#{location},#{description},#{enabled})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertVenue(Venue v);

  @Update(
      "UPDATE venue SET"
          + " name=#{name},location=#{location},description=#{description},enabled=#{enabled} WHERE"
          + " id=#{id}")
  int updateVenue(Venue v);

  @Select(
      "SELECT e.* FROM employee e JOIN sys_user u ON u.id=e.user_id WHERE EXISTS(SELECT 1 FROM"
          + " sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=e.user_id AND"
          + " r.code='PHOTOGRAPHER') AND u.enabled=1 ORDER BY e.id")
  List<Photographer> photographers();

  @Update("UPDATE employee SET bookable=#{bookable} WHERE id=#{id}")
  int setBookable(@Param("id") long id, @Param("bookable") boolean bookable);

  @Select(
      "SELECT employee_id FROM package_photographer WHERE package_id=#{id} ORDER BY employee_id")
  List<Long> photographerIds(long id);

  @Select("SELECT venue_id FROM package_venue WHERE package_id=#{id} ORDER BY venue_id")
  List<Long> venueIds(long id);

  @Delete("DELETE FROM package_photographer WHERE package_id=#{id}")
  void clearPhotographers(long id);

  @Delete("DELETE FROM package_venue WHERE package_id=#{id}")
  void clearVenues(long id);

  @Insert("INSERT INTO package_photographer VALUES(#{packageId},#{id})")
  void linkPhotographer(@Param("packageId") long packageId, @Param("id") long id);

  @Insert("INSERT INTO package_venue VALUES(#{packageId},#{id})")
  void linkVenue(@Param("packageId") long packageId, @Param("id") long id);
}
