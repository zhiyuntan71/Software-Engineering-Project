package com.sc2006group5.car_one_stop.mapper.announcement;

import com.sc2006group5.car_one_stop.dto.admin.AnnouncementResponse;
import com.sc2006group5.car_one_stop.entity.admin.Announcement;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AnnouncementMapper {

    AnnouncementResponse toResponse(Announcement announcement);
}
