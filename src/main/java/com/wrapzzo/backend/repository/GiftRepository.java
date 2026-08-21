package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Gift;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GiftRepository extends JpaRepository<Gift, UUID> {

    List<Gift> findByActiveTrue();

    Optional<Gift> findByName(String name);
}
