package com.shiguang.studio.mapper;

import com.shiguang.studio.mapper.contracts.*;
import org.apache.ibatis.annotations.Mapper;

/** SQL 按业务契约拆分；保留网关名称以兼容已有 XML 映射。 */
@Mapper
public interface BookingMapper
    extends CatalogStatements,
        ScheduleStatements,
        AppointmentStatements,
        BookingIdentityStatements {}
