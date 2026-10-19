package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.OrderDesign;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDesignRepository extends JpaRepository<OrderDesign, UUID> {

    List<OrderDesign> findByOrder_IdOrderByVersionNoDesc(UUID orderId);

    Optional<OrderDesign> findTopByOrder_IdOrderByVersionNoDesc(UUID orderId);

    int countByOrder_Id(UUID orderId);
}
