package com.covenant.platform.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.covenant.platform.entity.Contract;
import com.covenant.platform.enums.ContractStatus;


@Repository
public interface ContractRepository extends MongoRepository<Contract, String>{
    List<Contract> findBySellerId(Long sellerId);
    List<Contract> findByStatusAndDeliveryDateBefore(ContractStatus status, LocalDateTime cutoffDate);
}
