package com.sc2006group5.car_one_stop.repository.map;

import com.sc2006group5.car_one_stop.entity.map.Facility;
import com.sc2006group5.car_one_stop.enums.map.FacilityType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    // Simple "bounding box" + distance approximation (good enough for 2km)
    @Query("""
        SELECT f FROM Facility f
        WHERE f.type = :type
          AND f.latitude BETWEEN :minLat AND :maxLat
          AND f.longitude BETWEEN :minLng AND :maxLng
    """)
    List<Facility> findInBox(
            @Param("type") FacilityType type,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng
    );

    List<Facility> findAllByType(FacilityType type);

    long countByType(FacilityType type);

    @Modifying
    @Transactional
    @Query("DELETE FROM Facility f WHERE f.type = :type")
    void deleteAllByType(@Param("type") FacilityType type);
}