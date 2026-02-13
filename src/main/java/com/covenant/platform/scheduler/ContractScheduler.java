package com.covenant.platform.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.covenant.platform.entity.Contract;
import com.covenant.platform.enums.ContractStatus;
import com.covenant.platform.repository.ContractRepository;
import com.covenant.platform.service.ContractService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractScheduler {
    
    public final ContractRepository contractRepository;
    public final ContractService contractService;

    @Scheduled(cron = "0 0 0 * * ?")
    public void processAutoRelease(){
        log.info("Running Auto-Release Scheduler...");

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(14);

        List<Contract> expiredContracts = contractRepository.findByStatusAndDeliveryDateBefore(
            ContractStatus.DELIVERED, 
            cutoffDate
        );

        if(expiredContracts.isEmpty()){
            log.info("No expired Contracts found");
            return;
        }

        log.info("Found {} expired Contracts", expiredContracts.size());

        for(Contract contract: expiredContracts){
            try {
                log.info("Auto releasing funds for contract id: {}", contract.getId());
                contractService.markAsSatisfiedInternal(contract, ContractStatus.AUTO_RELEASED);
            } catch (Exception e) {
                log.error("Error while auto releasing funds for contract id: {}", contract.getId(), e);
            }
        }
    }
}
