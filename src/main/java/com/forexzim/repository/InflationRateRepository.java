package com.forexzim.repository;

import com.forexzim.model.InflationRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InflationRateRepository extends JpaRepository<InflationRate, Long> {

    Optional<InflationRate> findTopByOrderByScrapedAtDesc();

    boolean existsByPeriod(String period);

    List<InflationRate> findTop12ByOrderByScrapedAtDesc();
}
