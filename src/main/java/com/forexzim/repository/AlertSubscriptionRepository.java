package com.forexzim.repository;

import com.forexzim.model.AlertSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertSubscriptionRepository extends JpaRepository<AlertSubscription, Long> {
    List<AlertSubscription> findByActiveTrue();
    List<AlertSubscription> findByEmail(String email);
}