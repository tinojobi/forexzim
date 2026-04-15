package com.forexzim.service;

import com.forexzim.model.GoldCoinPrice;
import com.forexzim.repository.GoldCoinRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GoldCoinService {

    private final GoldCoinRepository repository;

    public GoldCoinService(GoldCoinRepository repository) {
        this.repository = repository;
    }

    public Optional<GoldCoinPrice> getLatest() {
        return repository.findTopByOrderByValidDateDescCreatedAtDesc();
    }

    public GoldCoinPrice save(GoldCoinPrice price) {
        return repository.save(price);
    }
}
