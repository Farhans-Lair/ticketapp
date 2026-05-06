package com.ticketapp.repository;

import com.ticketapp.entity.Screen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

    List<Screen> findByCinemaIdOrderByNameAsc(Long cinemaId);

    List<Screen> findByCinemaIdAndScreenTypeOrderByNameAsc(Long cinemaId, String screenType);
}
