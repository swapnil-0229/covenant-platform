package com.covenant.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.covenant.platform.dto.request.CreateContractRequest;
import com.covenant.platform.dto.request.ShipContractRequest;
import com.covenant.platform.dto.response.ContractResponse;
import com.covenant.platform.entity.Contract;
import com.covenant.platform.entity.User;
import com.covenant.platform.enums.ContractStatus;
import com.covenant.platform.enums.Role;
import com.covenant.platform.repository.ContractRepository;
import com.covenant.platform.repository.UserRepository;
import com.covenant.platform.service.ContractService;
import com.covenant.platform.service.EmailService;
import com.covenant.platform.service.PaymentService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ContractService contractService;

    private User seller;
    private User buyer;

    @BeforeEach
    void setUp() {
        seller = new User();
        seller.setId("seller-1");
        seller.setName("Seller User");
        seller.setEmail("seller@test.com");
        seller.setRole(Role.USER);

        buyer = new User();
        buyer.setId("buyer-1");
        buyer.setName("Buyer User");
        buyer.setEmail("buyer@test.com");
        buyer.setRole(Role.USER);
    }

    private void mockAuthenticatedUser(User user) {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(user.getEmail());
        SecurityContextHolder.setContext(securityContext);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private Contract createDraftContract() {
        Contract contract = new Contract();
        contract.setId("contract-1");
        contract.setSellerId(seller.getId());
        contract.setTitle("Test Item");
        contract.setDescription("A test contract");
        contract.setAmount(5000.0);
        contract.setStatus(ContractStatus.DRAFT);
        contract.setCreatedAt(LocalDateTime.now());
        return contract;
    }

    // --- Test 1: Creating a contract sets status to DRAFT ---

    @Test
    @DisplayName("Creating a contract should set status to DRAFT")
    void createContract_shouldSetStatusToDraft() {
        mockAuthenticatedUser(seller);

        CreateContractRequest request = new CreateContractRequest();
        request.setTitle("Gaming Laptop");
        request.setDescription("Mint condition");
        request.setAmount(50000.0);

        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> {
            Contract c = invocation.getArgument(0);
            c.setId("contract-new");
            return c;
        });

        ContractResponse response = contractService.createContract(request);

        assertEquals(ContractStatus.DRAFT, response.getStatus());
        assertEquals("Gaming Laptop", response.getTitle());
        assertEquals(seller.getId(), response.getSellerId());
        verify(contractRepository).save(any(Contract.class));
    }

    // --- Test 2: Accepting own contract throws exception ---

    @Test
    @DisplayName("Seller accepting their own contract should throw IllegalStateException")
    void acceptContract_bySeller_shouldThrow() {
        mockAuthenticatedUser(seller);

        Contract contract = createDraftContract();
        when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> contractService.acceptContract("contract-1"));

        assertEquals("You cannot accept your own contract.", exception.getMessage());
    }

    // --- Test 3: Accepting by valid buyer moves status to PAYMENT_PENDING ---

    @Test
    @DisplayName("Valid buyer accepting a DRAFT contract should move status to PAYMENT_PENDING")
    void acceptContract_byValidBuyer_shouldMoveToPaymentPending() throws StripeException {
        mockAuthenticatedUser(buyer);

        Contract contract = createDraftContract();
        when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

        com.stripe.model.checkout.Session mockSession = mock(com.stripe.model.checkout.Session.class);
        when(mockSession.getUrl()).thenReturn("http://checkout.url");
        when(paymentService.createCheckoutSession(eq(5000.0), eq("contract-1"), eq("buyer-1"), eq("Test Item")))
                .thenReturn(mockSession);

        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContractResponse response = contractService.acceptContract("contract-1");

        assertEquals(ContractStatus.PAYMENT_PENDING, response.getStatus());
        assertEquals(buyer.getId(), response.getBuyerId());
    }

    // --- Test 4: Confirming payment moves status to LOCKED ---

    @Test
    @DisplayName("Confirming payment on PAYMENT_PENDING contract should move to LOCKED")
    void confirmPaymentInternal_shouldMoveToLocked() {
        Contract contract = createDraftContract();
        contract.setStatus(ContractStatus.PAYMENT_PENDING);
        contract.setBuyerId(buyer.getId());

        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Contract result = contractService.confirmPaymentInternal(contract);

        assertEquals(ContractStatus.LOCKED, result.getStatus());
        verify(contractRepository).save(contract);
    }

    // --- Test 5: Shipping when status is not LOCKED throws ---

    @Test
    @DisplayName("Shipping when contract is not LOCKED should throw IllegalStateException")
    void shipContract_whenNotLocked_shouldThrow() {
        mockAuthenticatedUser(seller);

        Contract contract = createDraftContract();
        contract.setStatus(ContractStatus.DRAFT);
        when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

        ShipContractRequest request = new ShipContractRequest();
        request.setTrackingId("FX-123");
        request.setLogisticsProvider("FedEx");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> contractService.shipContract("contract-1", request));

        assertTrue(exception.getMessage().contains("Cannot ship"));
    }

    // --- Test 6: Shipping when LOCKED moves to SHIPPED ---

    @Test
    @DisplayName("Shipping a LOCKED contract should move status to SHIPPED")
    void shipContract_whenLocked_shouldMoveToShipped() {
        mockAuthenticatedUser(seller);

        Contract contract = createDraftContract();
        contract.setStatus(ContractStatus.LOCKED);
        contract.setBuyerId(buyer.getId());
        when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShipContractRequest request = new ShipContractRequest();
        request.setTrackingId("FX-123");
        request.setLogisticsProvider("FedEx");

        ContractResponse response = contractService.shipContract("contract-1", request);

        assertEquals(ContractStatus.SHIPPED, response.getStatus());
        assertNotNull(response.getTrackingDetails());
        assertEquals("FX-123", response.getTrackingDetails().getTrackingId());
    }

    // --- Test 7: Satisfying a contract moves to SATISFIED ---

    @Test
    @DisplayName("Marking a DELIVERED contract as satisfied should move to SATISFIED")
    void markAsSatisfiedInternal_shouldMoveToSatisfied() {
        Contract contract = createDraftContract();
        contract.setStatus(ContractStatus.DELIVERED);
        contract.setBuyerId(buyer.getId());

        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Contract result = contractService.markAsSatisfiedInternal(contract, ContractStatus.SATISFIED);

        assertEquals(ContractStatus.SATISFIED, result.getStatus());
    }

    // --- Test 8: Cancelling a DRAFT contract succeeds ---

    @Test
    @DisplayName("Cancelling a DRAFT contract should succeed and set status to CANCELLED")
    void cancelContract_whenDraft_shouldSucceed() {
        mockAuthenticatedUser(seller);

        Contract contract = createDraftContract();
        when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContractResponse response = contractService.cancelContract("contract-1");

        assertEquals(ContractStatus.CANCELLED, response.getStatus());
    }

    // --- Test 9: Cancelling a LOCKED contract throws ---

    @Test
    @DisplayName("Cancelling a LOCKED contract should throw IllegalStateException")
    void cancelContract_whenLocked_shouldThrow() {
        mockAuthenticatedUser(seller);

        Contract contract = createDraftContract();
        contract.setStatus(ContractStatus.LOCKED);
        when(contractRepository.findById("contract-1")).thenReturn(Optional.of(contract));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> contractService.cancelContract("contract-1"));

        assertTrue(exception.getMessage().contains("Cannot cancel"));
    }
}
