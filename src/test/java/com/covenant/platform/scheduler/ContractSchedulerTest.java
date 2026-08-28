package com.covenant.platform.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.covenant.platform.entity.Contract;
import com.covenant.platform.entity.TrackingDetails;
import com.covenant.platform.enums.ContractStatus;
import com.covenant.platform.repository.ContractRepository;
import com.covenant.platform.service.ContractService;

/**
 * Unit tests for {@link ContractScheduler}.
 *
 * <p>Verifies the auto-release scheduling logic:
 * <ul>
 *   <li>Queries for DELIVERED contracts older than 14 days.</li>
 *   <li>Calls {@link ContractService#markAsSatisfiedInternal} for each expired contract.</li>
 *   <li>Continues processing remaining contracts even if one fails (resilience).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContractScheduler Unit Tests")
class ContractSchedulerTest {

    @Mock private ContractRepository contractRepository;
    @Mock private ContractService    contractService;

    @InjectMocks
    private ContractScheduler scheduler;

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private Contract buildDeliveredContract(String id) {
        Contract c = new Contract();
        c.setId(id);
        c.setSellerId("seller-1");
        c.setStatus(ContractStatus.DELIVERED);
        TrackingDetails td = TrackingDetails.builder()
                .trackingId("TRACK-" + id)
                .logisticsProvider("DHL")
                .deliveryDate(LocalDateTime.now().minusDays(15))
                .build();
        c.setTrackingDetails(td);
        return c;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  processAutoRelease()
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("processAutoRelease()")
    class ProcessAutoRelease {

        @Test
        @DisplayName("should call markAsSatisfiedInternal for each expired DELIVERED contract")
        void processAutoRelease_whenExpiredContractsExist_shouldCallMarkAsSatisfiedInternal() {
            // Given
            Contract c1 = buildDeliveredContract("c-001");
            Contract c2 = buildDeliveredContract("c-002");
            Contract c3 = buildDeliveredContract("c-003");

            when(contractRepository.findByStatusAndTrackingDetailsDeliveryDateBefore(
                    eq(ContractStatus.DELIVERED), any(LocalDateTime.class)))
                    .thenReturn(List.of(c1, c2, c3));

            // When
            scheduler.processAutoRelease();

            // Then — markAsSatisfiedInternal called exactly once per contract
            verify(contractService, times(3))
                    .markAsSatisfiedInternal(any(Contract.class), eq(ContractStatus.AUTO_RELEASED));
        }

        @Test
        @DisplayName("should pass AUTO_RELEASED as the target status to markAsSatisfiedInternal")
        void processAutoRelease_shouldPassAutoReleasedStatus() {
            // Given
            Contract c1 = buildDeliveredContract("c-001");
            when(contractRepository.findByStatusAndTrackingDetailsDeliveryDateBefore(
                    eq(ContractStatus.DELIVERED), any(LocalDateTime.class)))
                    .thenReturn(List.of(c1));

            // When
            scheduler.processAutoRelease();

            // Then
            ArgumentCaptor<ContractStatus> statusCaptor = ArgumentCaptor.forClass(ContractStatus.class);
            verify(contractService).markAsSatisfiedInternal(any(Contract.class), statusCaptor.capture());
            org.assertj.core.api.Assertions.assertThat(statusCaptor.getValue())
                    .isEqualTo(ContractStatus.AUTO_RELEASED);
        }

        @Test
        @DisplayName("should NOT call markAsSatisfiedInternal when there are no expired contracts")
        void processAutoRelease_whenNoExpiredContracts_shouldNotCallMarkAsSatisfied() {
            // Given
            when(contractRepository.findByStatusAndTrackingDetailsDeliveryDateBefore(
                    eq(ContractStatus.DELIVERED), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            // When
            scheduler.processAutoRelease();

            // Then
            verify(contractService, never())
                    .markAsSatisfiedInternal(any(Contract.class), any(ContractStatus.class));
        }

        @Test
        @DisplayName("should continue processing remaining contracts when one contract throws an exception")
        void processAutoRelease_whenOneContractFails_shouldContinueWithOthers() {
            // Given
            Contract failing = buildDeliveredContract("c-bad");
            Contract good    = buildDeliveredContract("c-good");

            when(contractRepository.findByStatusAndTrackingDetailsDeliveryDateBefore(
                    eq(ContractStatus.DELIVERED), any(LocalDateTime.class)))
                    .thenReturn(List.of(failing, good));

            // Make the first contract throw, second should still be processed
            doThrow(new RuntimeException("Unexpected error"))
                    .when(contractService).markAsSatisfiedInternal(eq(failing), any());

            // When — must NOT throw even though one contract fails
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> scheduler.processAutoRelease());

            // Then — second contract is still processed despite the first one failing
            verify(contractService).markAsSatisfiedInternal(eq(good), eq(ContractStatus.AUTO_RELEASED));
        }

        @Test
        @DisplayName("should use a cutoff date of 14 days ago when querying expired contracts")
        void processAutoRelease_shouldQueryWithCutoffDate14DaysAgo() {
            // Given
            when(contractRepository.findByStatusAndTrackingDetailsDeliveryDateBefore(
                    eq(ContractStatus.DELIVERED), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            LocalDateTime before = LocalDateTime.now().minusDays(14).minusSeconds(1);

            // When
            scheduler.processAutoRelease();

            // Then — capture the cutoff date passed to the repository
            ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(contractRepository).findByStatusAndTrackingDetailsDeliveryDateBefore(
                    eq(ContractStatus.DELIVERED), cutoffCaptor.capture());

            LocalDateTime after = LocalDateTime.now().minusDays(14).plusSeconds(1);
            LocalDateTime captured = cutoffCaptor.getValue();

            org.assertj.core.api.Assertions.assertThat(captured)
                    .isAfter(before)
                    .isBefore(after);
        }
    }
}
