package com.forexzim.repository;

import com.forexzim.model.TelegramAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TelegramAlertRepository extends JpaRepository<TelegramAlert, Long> {
    List<TelegramAlert> findByActiveTrue();
    List<TelegramAlert> findByChatIdAndActiveTrue(Long chatId);
}
