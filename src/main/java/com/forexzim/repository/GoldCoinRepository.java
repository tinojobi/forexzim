package com.forexzim.repository;

import com.forexzim.model.GoldCoinPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoldCoinRepository extends JpaRepository<GoldCoinPrice, Long> {

    Optional<GoldCoinPrice> findTopByOrderByValidDateDescCreatedAtDesc();
}
