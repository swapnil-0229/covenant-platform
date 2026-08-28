package com.covenant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.covenant.platform.dto.request.CreateContractRequest;
import com.covenant.platform.dto.request.RaiseDisputeRequest;
import com.covenant.platform.dto.request.ShipContractRequest;
import com.covenant.platform.dto.response.ContractResponse;
import com.covenant.platform.entity.Contract;
import com.covenant.platform.entity.TrackingDetails;
import com.covenant.platform.entity.User;
import com.covenant.platform.enums.ContractStatus;
import com.covenant.platform.enums.Role;
import com.covenant.platform.exception.ResourceNotFoundException;
import com.covenant.platform.repository.ContractRepository;
import com.covenant.platform.repository.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;

/**
 * Comprehensive unit tests for {@link ContractService}.
 *
 * <p>Strategy:
 * <ul>
 *   <li>No Spring context — fast, isolated Mockito tests.</li>
 *   <li>SecurityContext is mocked per-test via {@link #mockAuthenticatedUser}.</li>
 *   <li>Every test follows the <em>Given-When-Then</em> (AAA) pattern.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContractService Unit Tests")
class ContractServiceTest {

    // ─── Mocks ───────────────────────────────────────────────────────────────────

    @Mock private ContractRepository contractRepository;
    @Mock private UserRepository     userRepository;
    @Mock private EmailService       emailService;
    @Mock private PaymentService     paymentService;

    @InjectMocks
    private ContractService contractService;

    // ─── Shared fixtures ──────────────────────────────────────────────────────────

    private User seller;
    private User buyer;
    private User admin;

    @BeforeEach
    void setUpUsers() {
        seller = buildUser("seller-1", "seller@test.com", Role.USER);
        buyer  = buildUser("buyer-1",  "buyer@test.com",  Role.USER);
        admin  = buildUser("admin-1",  "admin@test.com",  Role.ADMIN);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private User buildUser(String id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRole(role);
        return u;
    }

    /**
     * Stubs the SecurityContext so {@code getCurrentUser()} resolves to {@code user}.
     *
     * <p>Uses {@code lenient()} to avoid {@code UnnecessaryStubbingException} in tests
     * that throw before the userRepository stub is ever consumed (e.g. when
     * {@code contractRepository.findById()} already returns empty and the exception
     * is raised before auth resolution).
     */
    private void mockAuthenticatedUser(User user) {
        Authentication auth    = mock(Authentication.class);
        SecurityContext ctx    = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getName()).thenReturn(user.getEmail());
        SecurityContextHolder.setContext(ctx);
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private Contract buildContract(ContractStatus status) {
        Contract c = new Contract();
        c.setId("contract-1");
        c.setSellerId(seller.getId());
        c.setTitle("Test Item");
        c.setDescription("A test contract");
        c.setAmount(5000.0);
        c.setStatus(status);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    /** Makes {@code contractRepository.save()} return the same object (identity stub). */
    private void stubSaveIdentity() {
        when(contractRepository.save(any(Contract.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  getUserContracts()
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getUserContracts()")
    class GetUserContracts {

        @Test
        @DisplayName("should return all contracts for the currently authenticated user")
        void getUserContracts_shouldReturnContractsForCurrentUser() {
            // Given
            mockAuthenticatedUser(seller);
            Contract c1 = buildContract(ContractStatus.DRAFT);
            Contract c2 = buildContract(ContractStatus.LOCKED);
            when(contractRepository.findBySellerIdOrBuyerId(seller.getId(), seller.getId()))
                    .thenReturn(List.of(c1, c2));

            // When
            List<ContractResponse> result = contractService.getUserContracts();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(ContractResponse::getSellerId)
                    .containsOnly(seller.getId());
        }

        @Test
        @DisplayName("should return empty list when user has no contracts")
        void getUserContracts_whenNoContracts_shouldReturnEmptyList() {
            // Given
            mockAuthenticatedUser(buyer);
            when(contractRepository.findBySellerIdOrBuyerId(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            // When
            List<ContractResponse> result = contractService.getUserContracts();

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  createContract()
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createContract()")
    class CreateContract {

        @Test
        @DisplayName("should persist contract with DRAFT status and correct sellerId")
        void createContract_shouldSetStatusToDraftAndSellerId() {
            // Given
            mockAuthenticatedUser(seller);
            stubSaveIdentity();

            CreateContractRequest req = new CreateContractRequest(
                    "Gaming Laptop", "Mint condition", 50000.0, null);

            // When
            ContractResponse resp = contractService.createContract(req);

            // Then
            assertThat(resp.getStatus()).isEqualTo(ContractStatus.DRAFT);
            assertThat(resp.getSellerId()).isEqualTo(seller.getId());
            assertThat(resp.getTitle()).isEqualTo("Gaming Laptop");
        }

        @Test
        @DisplayName("should send email to buyerEmail when it is provided")
        void createContract_withBuyerEmail_shouldSendEmailToBuyer() {
            // Given
            mockAuthenticatedUser(seller);
            stubSaveIdentity();

            CreateContractRequest req = new CreateContractRequest(
                    "Laptop", "Good", 10000.0, "buyer@example.com");

            // When
            contractService.createContract(req);

            // Then — emailService.sendEmail called with the buyer's address
            verify(emailService, atLeastOnce()).sendEmail(eq("buyer@example.com"), anyString(), anyString());
        }

        @Test
        @DisplayName("should NOT send buyer email when buyerEmail is null")
        void createContract_withNoBuyerEmail_shouldNotSendEmailToBuyer() {
            // Given
            mockAuthenticatedUser(seller);
            stubSaveIdentity();

            CreateContractRequest req = new CreateContractRequest("Laptop", "Good", 10000.0, null);

            // When
            contractService.createContract(req);

            // Then — only the seller confirmation email is sent
            verify(emailService, never()).sendEmail(eq(null), anyString(), anyString());
            verify(emailService).sendEmail(eq(seller.getEmail()), anyString(), anyString());
        }

        @Test
        @DisplayName("should NOT send buyer email when buyerEmail is empty string")
        void createContract_withEmptyBuyerEmail_shouldNotSendEmailToBuyer() {
            // Given
            mockAuthenticatedUser(seller);
            stubSaveIdentity();

            CreateContractRequest req = new CreateContractRequest("Laptop", "Good", 10000.0, "");

            // When
            contractService.createContract(req);

            // Then — only 1 email: seller confirmation
            verify(emailService).sendEmail(eq(seller.getEmail()), anyString(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  getContractById()
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getContractById()")
    class GetContractById {

        @Test
        @DisplayName("should return contract DTO when seller is the caller")
        void getContractById_whenCalledBySeller_shouldReturnContract() {
            // Given
            mockAuthenticatedUser(seller);
            Contract contract = buildContract(ContractStatus.DRAFT);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When
            ContractResponse resp = contractService.getContractById("contract-1");

            // Then
            assertThat(resp.getId()).isEqualTo("contract-1");
            assertThat(resp.getTitle()).isEqualTo("Test Item");
        }

        @Test
        @DisplayName("should return contract DTO when buyer is the caller")
        void getContractById_whenCalledByBuyer_shouldReturnContract() {
            // Given
            mockAuthenticatedUser(buyer);
            Contract contract = buildContract(ContractStatus.LOCKED);
            contract.setBuyerId(buyer.getId());
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When
            ContractResponse resp = contractService.getContractById("contract-1");

            // Then
            assertThat(resp.getId()).isEqualTo("contract-1");
        }

        @Test
        @DisplayName("should return contract DTO when admin is the caller")
        void getContractById_whenCalledByAdmin_shouldReturnContract() {
            // Given
            mockAuthenticatedUser(admin);
            Contract contract = buildContract(ContractStatus.LOCKED);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When
            ContractResponse resp = contractService.getContractById("contract-1");

            // Then
            assertThat(resp).isNotNull();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when contract does not exist")
        void getContractById_whenContractNotFound_shouldThrowResourceNotFoundException() {
            // Given
            mockAuthenticatedUser(seller);
            when(contractRepository.findById("nonexistent-id")).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> contractService.getContractById("nonexistent-id"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Contract not found with id: nonexistent-id");
        }

        @Test
        @DisplayName("should throw IllegalStateException when unrelated user tries to view the contract")
        void getContractById_whenUnrelatedUser_shouldThrowIllegalStateException() {
            // Given
            User stranger = buildUser("stranger-99", "stranger@test.com", Role.USER);
            mockAuthenticatedUser(stranger);
            Contract contract = buildContract(ContractStatus.DRAFT);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.getContractById("contract-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not authorized");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  acceptContract()
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("acceptContract()")
    class AcceptContract {

        @Test
        @DisplayName("should move status to PAYMENT_PENDING when valid buyer accepts a DRAFT contract")
        void acceptContract_byValidBuyer_onDraftContract_shouldMoveToPaymentPending()
                throws StripeException {
            // Given
            mockAuthenticatedUser(buyer);
            Contract contract = buildContract(ContractStatus.DRAFT);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            Session mockSession = mock(Session.class);
            when(mockSession.getId()).thenReturn("sess-123");
            when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/sess-123");
            when(paymentService.createCheckoutSession(
                    eq(5000.0), eq("contract-1"), eq("buyer-1"), eq("Test Item")))
                    .thenReturn(mockSession);
            stubSaveIdentity();

            // When
            ContractResponse resp = contractService.acceptContract("contract-1");

            // Then
            assertThat(resp.getStatus()).isEqualTo(ContractStatus.PAYMENT_PENDING);
            assertThat(resp.getBuyerId()).isEqualTo(buyer.getId());
            assertThat(resp.getStripeSessionId()).isEqualTo("sess-123");
        }

        @Test
        @DisplayName("should throw IllegalStateException when the seller tries to accept their own contract")
        void acceptContract_bySeller_shouldThrowIllegalStateException() {
            // Given
            mockAuthenticatedUser(seller);
            Contract contract = buildContract(ContractStatus.DRAFT);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.acceptContract("contract-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("You cannot accept your own contract.");
        }

        @Test
        @DisplayName("should throw IllegalStateException when contract is not in DRAFT status")
        void acceptContract_whenContractNotDraft_shouldThrowIllegalStateException() {
            // Given
            mockAuthenticatedUser(buyer);
            Contract contract = buildContract(ContractStatus.PAYMENT_PENDING);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.acceptContract("contract-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not available");
        }

        @Test
        @DisplayName("should throw RuntimeException wrapping StripeException when Stripe fails")
        void acceptContract_whenStripeThrows_shouldWrapInRuntimeException() throws StripeException {
            // Given
            mockAuthenticatedUser(buyer);
            Contract contract = buildContract(ContractStatus.DRAFT);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
            when(paymentService.createCheckoutSession(any(), any(), any(), any()))
                    .thenThrow(mock(StripeException.class));

            // When / Then
            assertThatThrownBy(() -> contractService.acceptContract("contract-1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error initiating payment");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when contract does not exist")
        void acceptContract_whenContractNotFound_shouldThrow() {
            // Given
            mockAuthenticatedUser(buyer);
            when(contractRepository.findById("bad-id")).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> contractService.acceptContract("bad-id"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  confirmPaymentInternal()
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("confirmPaymentInternal()")
    class ConfirmPaymentInternal {

        @Test
        @DisplayName("should move contract status to LOCKED when it is PAYMENT_PENDING")
        void confirmPaymentInternal_whenPaymentPending_shouldMoveToLocked() {
            // Given
            Contract contract = buildContract(ContractStatus.PAYMENT_PENDING);
            contract.setBuyerId(buyer.getId());
            stubSaveIdentity();

            // When
            Contract result = contractService.confirmPaymentInternal(contract);

            // Then
            assertThat(result.getStatus()).isEqualTo(ContractStatus.LOCKED);
            verify(contractRepository).save(contract);
        }

        @Test
        @DisplayName("should send email to seller after payment is confirmed")
        void confirmPaymentInternal_shouldNotifySellerByEmail() {
            // Given
            Contract contract = buildContract(ContractStatus.PAYMENT_PENDING);
            stubSaveIdentity();
            when(userRepository.findById(seller.getId())).thenReturn(Optional.of(seller));

            // When
            contractService.confirmPaymentInternal(contract);

            // Then
            verify(emailService).sendEmail(eq(seller.getEmail()), anyString(), anyString());
        }

        @Test
        @DisplayName("should throw IllegalStateException when contract is not PAYMENT_PENDING")
        void confirmPaymentInternal_whenNotPending_shouldThrow() {
            // Given
            Contract contract = buildContract(ContractStatus.LOCKED);

            // When / Then
            assertThatThrownBy(() -> contractService.confirmPaymentInternal(contract))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot confirm payment");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  shipContract()
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("shipContract()")
    class ShipContract {

        private ShipContractRequest buildShipRequest() {
            ShipContractRequest req = new ShipContractRequest();
            req.setTrackingId("FX-9876");
            req.setLogisticsProvider("FedEx");
            return req;
        }

        @Test
        @DisplayName("should move contract to SHIPPED and set TrackingDetails when status is LOCKED")
        void shipContract_whenLocked_shouldMoveToShipped() {
            // Given
            mockAuthenticatedUser(seller);
            Contract contract = buildContract(ContractStatus.LOCKED);
            contract.setBuyerId(buyer.getId());
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
            stubSaveIdentity();

            // When
            ContractResponse resp = contractService.shipContract("contract-1", buildShipRequest());

            // Then
            assertThat(resp.getStatus()).isEqualTo(ContractStatus.SHIPPED);
            assertThat(resp.getTrackingDetails()).isNotNull();
            assertThat(resp.getTrackingDetails().getTrackingId()).isEqualTo("FX-9876");
            assertThat(resp.getTrackingDetails().getLogisticsProvider()).isEqualTo("FedEx");
        }

        @Test
        @DisplayName("should throw IllegalStateException when contract is not LOCKED")
        void shipContract_whenNotLocked_shouldThrow() {
            // Given
            mockAuthenticatedUser(seller);
            Contract contract = buildContract(ContractStatus.DRAFT);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.shipContract("contract-1", buildShipRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot ship");
        }

        @Test
        @DisplayName("should throw IllegalStateException when caller is not the seller")
        void shipContract_whenNotSeller_shouldThrow() {
            // Given — buyer is authenticated, but contract belongs to seller
            mockAuthenticatedUser(buyer);
            Contract contract = buildContract(ContractStatus.LOCKED);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.shipContract("contract-1", buildShipRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only the seller");
        }

        @Test
        @DisplayName("should send shipping notification email to buyer")
        void shipContract_shouldSendEmailToBuyer() {
            // Given
            mockAuthenticatedUser(seller);
            Contract contract = buildContract(ContractStatus.LOCKED);
            contract.setBuyerId(buyer.getId());
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
            when(userRepository.findById(buyer.getId())).thenReturn(Optional.of(buyer));
            stubSaveIdentity();

            // When
            contractService.shipContract("contract-1", buildShipRequest());

            // Then
            verify(emailService).sendEmail(eq(buyer.getEmail()), anyString(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  markAsDelivered()
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("markAsDelivered()")
    class MarkAsDelivered {

        @Test
        @DisplayName("should move contract to DELIVERED when status is SHIPPED")
        void markAsDelivered_whenShipped_shouldMoveToDelivered() {
            // Given
            mockAuthenticatedUser(seller);
            Contract contract = buildContract(ContractStatus.SHIPPED);
            contract.setBuyerId(buyer.getId());
            TrackingDetails td = TrackingDetails.builder().trackingId("FX-123").logisticsProvider("FedEx").build();
            contract.setTrackingDetails(td);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
            stubSaveIdentity();

            // When
            ContractResponse resp = contractService.markAsDelivered("contract-1");

            // Then
            assertThat(resp.getStatus()).isEqualTo(ContractStatus.DELIVERED);
        }

        @Test
        @DisplayName("should throw IllegalStateException when contract is not SHIPPED")
        void markAsDelivered_whenNotShipped_shouldThrow() {
            // Given
            mockAuthenticatedUser(seller);
            Contract contract = buildContract(ContractStatus.LOCKED);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.markAsDelivered("contract-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot mark delivered");
        }

        @Test
        @DisplayName("should throw IllegalStateException when caller is not the seller")
        void markAsDelivered_whenNotSeller_shouldThrow() {
            // Given
            mockAuthenticatedUser(buyer);
            Contract contract = buildContract(ContractStatus.SHIPPED);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.markAsDelivered("contract-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only the seller");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  markAsSatisfied() / markAsSatisfiedInternal()
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("markAsSatisfied() & markAsSatisfiedInternal()")
    class MarkAsSatisfied {

        @Test
        @DisplayName("should move contract to SATISFIED when buyer calls on DELIVERED contract")
        void markAsSatisfied_whenDeliveredAndBuyer_shouldMoveToSatisfied() {
            // Given
            mockAuthenticatedUser(buyer);
            Contract contract = buildContract(ContractStatus.DELIVERED);
            contract.setBuyerId(buyer.getId());
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
            stubSaveIdentity();

            // When
            ContractResponse resp = contractService.markAsSatisfied("contract-1");

            // Then
            assertThat(resp.getStatus()).isEqualTo(ContractStatus.SATISFIED);
        }

        @Test
        @DisplayName("markAsSatisfiedInternal should succeed on SHIPPED status (auto-release path)")
        void markAsSatisfiedInternal_withShippedStatus_shouldSucceed() {
            // Given
            Contract contract = buildContract(ContractStatus.SHIPPED);
            contract.setBuyerId(buyer.getId());
            stubSaveIdentity();

            // When
            Contract result = contractService.markAsSatisfiedInternal(contract, ContractStatus.AUTO_RELEASED);

            // Then
            assertThat(result.getStatus()).isEqualTo(ContractStatus.AUTO_RELEASED);
        }

        @Test
        @DisplayName("markAsSatisfiedInternal should throw when status is neither DELIVERED nor SHIPPED")
        void markAsSatisfiedInternal_withInvalidStatus_shouldThrow() {
            // Given
            Contract contract = buildContract(ContractStatus.DRAFT);

            // When / Then
            assertThatThrownBy(() ->
                    contractService.markAsSatisfiedInternal(contract, ContractStatus.SATISFIED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot mark satisfied");
        }

        @Test
        @DisplayName("should throw IllegalStateException when caller is not the buyer")
        void markAsSatisfied_whenNotBuyer_shouldThrow() {
            // Given
            mockAuthenticatedUser(seller); // seller tries to mark satisfied
            Contract contract = buildContract(ContractStatus.DELIVERED);
            contract.setBuyerId(buyer.getId()); // buyer is different from seller
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.markAsSatisfied("contract-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only the buyer");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  cancelContract()
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("cancelContract()")
    class CancelContract {

        @Test
        @DisplayName("should move contract to CANCELLED when status is DRAFT and caller is seller")
        void cancelContract_whenDraft_shouldMoveToCANCELLED() {
            // Given
            mockAuthenticatedUser(seller);
            Contract contract = buildContract(ContractStatus.DRAFT);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
            stubSaveIdentity();

            // When
            ContractResponse resp = contractService.cancelContract("contract-1");

            // Then
            assertThat(resp.getStatus()).isEqualTo(ContractStatus.CANCELLED);
        }

        @Test
        @DisplayName("should throw IllegalStateException when contract is not in DRAFT status")
        void cancelContract_whenNotDraft_shouldThrow() {
            // Given
            mockAuthenticatedUser(seller);
            Contract contract = buildContract(ContractStatus.LOCKED);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.cancelContract("contract-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot cancel");
        }

        @Test
        @DisplayName("should throw IllegalStateException when caller is not the seller")
        void cancelContract_whenNotSeller_shouldThrow() {
            // Given
            mockAuthenticatedUser(buyer);
            Contract contract = buildContract(ContractStatus.DRAFT);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.cancelContract("contract-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only the seller");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  raiseDispute()
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("raiseDispute()")
    class RaiseDispute {

        private RaiseDisputeRequest buildDisputeRequest() {
            RaiseDisputeRequest req = new RaiseDisputeRequest();
            req.setReason("Item arrived damaged");
            return req;
        }

        @Test
        @DisplayName("should move contract to DISPUTED when buyer raises dispute on DELIVERED contract")
        void raiseDispute_whenDelivered_shouldMoveToDisputed() {
            // Given
            mockAuthenticatedUser(buyer);
            Contract contract = buildContract(ContractStatus.DELIVERED);
            contract.setBuyerId(buyer.getId());
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
            stubSaveIdentity();

            // When
            ContractResponse resp = contractService.raiseDispute("contract-1", buildDisputeRequest());

            // Then
            assertThat(resp.getStatus()).isEqualTo(ContractStatus.DISPUTED);
        }

        @Test
        @DisplayName("should move contract to DISPUTED when buyer raises dispute on SHIPPED contract")
        void raiseDispute_whenShipped_shouldMoveToDisputed() {
            // Given
            mockAuthenticatedUser(buyer);
            Contract contract = buildContract(ContractStatus.SHIPPED);
            contract.setBuyerId(buyer.getId());
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
            stubSaveIdentity();

            // When
            ContractResponse resp = contractService.raiseDispute("contract-1", buildDisputeRequest());

            // Then
            assertThat(resp.getStatus()).isEqualTo(ContractStatus.DISPUTED);
        }

        @Test
        @DisplayName("should throw IllegalStateException when contract is not SHIPPED or DELIVERED")
        void raiseDispute_whenInvalidStatus_shouldThrow() {
            // Given
            mockAuthenticatedUser(buyer);
            Contract contract = buildContract(ContractStatus.DRAFT);
            contract.setBuyerId(buyer.getId());
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.raiseDispute("contract-1", buildDisputeRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot raise dispute");
        }

        @Test
        @DisplayName("should throw IllegalStateException when caller is not the buyer")
        void raiseDispute_whenNotBuyer_shouldThrow() {
            // Given — seller tries to raise a dispute
            mockAuthenticatedUser(seller);
            Contract contract = buildContract(ContractStatus.DELIVERED);
            contract.setBuyerId(buyer.getId()); // seller != buyer
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.raiseDispute("contract-1", buildDisputeRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only the buyer");
        }

        @Test
        @DisplayName("should notify seller via email when dispute is raised")
        void raiseDispute_shouldNotifySellerByEmail() {
            // Given
            mockAuthenticatedUser(buyer);
            Contract contract = buildContract(ContractStatus.DELIVERED);
            contract.setBuyerId(buyer.getId());
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
            when(userRepository.findById(seller.getId())).thenReturn(Optional.of(seller));
            stubSaveIdentity();

            // When
            contractService.raiseDispute("contract-1", buildDisputeRequest());

            // Then
            verify(emailService).sendEmail(eq(seller.getEmail()), anyString(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  resolveDispute()
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("resolveDispute()")
    class ResolveDispute {

        @Test
        @DisplayName("should move contract to REFUNDED when admin chooses to refund buyer")
        void resolveDispute_withRefund_shouldMoveToRefunded() {
            // Given
            mockAuthenticatedUser(admin);
            Contract contract = buildContract(ContractStatus.DISPUTED);
            contract.setBuyerId(buyer.getId());
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
            when(userRepository.findById(buyer.getId())).thenReturn(Optional.of(buyer));
            stubSaveIdentity();

            // When
            ContractResponse resp = contractService.resolveDispute("contract-1", true);

            // Then
            assertThat(resp.getStatus()).isEqualTo(ContractStatus.REFUNDED);
        }

        @Test
        @DisplayName("should move contract to SATISFIED and notify seller when admin releases funds")
        void resolveDispute_withoutRefund_shouldMoveToSatisfiedAndNotifySeller() {
            // Given
            mockAuthenticatedUser(admin);
            Contract contract = buildContract(ContractStatus.DISPUTED);
            contract.setBuyerId(buyer.getId());
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
            when(userRepository.findById(seller.getId())).thenReturn(Optional.of(seller));
            stubSaveIdentity();

            // When
            ContractResponse resp = contractService.resolveDispute("contract-1", false);

            // Then
            assertThat(resp.getStatus()).isEqualTo(ContractStatus.SATISFIED);
            verify(emailService).sendEmail(eq(seller.getEmail()), anyString(), anyString());
        }

        @Test
        @DisplayName("should throw IllegalStateException when contract is not DISPUTED")
        void resolveDispute_whenNotDisputed_shouldThrow() {
            // Given
            mockAuthenticatedUser(admin);
            Contract contract = buildContract(ContractStatus.LOCKED);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.resolveDispute("contract-1", true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not disputed");
        }

        @Test
        @DisplayName("should throw IllegalStateException when caller is not ADMIN")
        void resolveDispute_whenNotAdmin_shouldThrow() {
            // Given — a regular USER tries to resolve
            mockAuthenticatedUser(seller);
            Contract contract = buildContract(ContractStatus.DISPUTED);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When / Then
            assertThatThrownBy(() -> contractService.resolveDispute("contract-1", false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only an admin");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when contract does not exist")
        void resolveDispute_whenContractNotFound_shouldThrow() {
            // Given
            mockAuthenticatedUser(admin);
            when(contractRepository.findById("bad-id")).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> contractService.resolveDispute("bad-id", true))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  getContractByIdInternal() — system / webhook path
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getContractByIdInternal()")
    class GetContractByIdInternal {

        @Test
        @DisplayName("should return Contract entity without any auth check")
        void getContractByIdInternal_whenContractExists_shouldReturnEntity() {
            // Given
            Contract contract = buildContract(ContractStatus.LOCKED);
            when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

            // When
            Contract result = contractService.getContractByIdInternal("contract-1");

            // Then
            assertThat(result).isSameAs(contract);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when contract does not exist")
        void getContractByIdInternal_whenNotFound_shouldThrow() {
            // Given
            when(contractRepository.findById("bad-id")).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> contractService.getContractByIdInternal("bad-id"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  Security context edge cases
    // ═══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Authentication edge cases")
    class AuthEdgeCases {

        @Test
        @DisplayName("should throw UsernameNotFoundException when authenticated email has no matching user")
        void getCurrentUser_whenEmailNotInDB_shouldThrowUsernameNotFoundException() {
            // Given — SecurityContext has a name, but userRepository returns empty
            Authentication auth = mock(Authentication.class);
            SecurityContext ctx = mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(auth);
            when(auth.getName()).thenReturn("ghost@test.com");
            SecurityContextHolder.setContext(ctx);
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> contractService.getUserContracts())
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }
}
