package com.forexzim.repository;

import com.forexzim.model.TelegramAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TelegramAlertRepository extends JpaRepository<TelegramAlert, Long> {
    List<TelegramAlert> findByActiveTrue();
    List<TelegramAlert> findByChatIdAndActiveTrue(Long chatId);

    @Query("SELECT DISTINCT a.chatId FROM TelegramAlert a")
    List<Long> findDistinctChatIds();
}
