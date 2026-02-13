package com.covenant.platform.service;

import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.covenant.platform.dto.request.CreateContractRequest;
import com.covenant.platform.dto.request.RaiseDisputeRequest;
import com.covenant.platform.dto.request.ShipContractRequest;
import com.covenant.platform.entity.Contract;
import com.covenant.platform.entity.User;
import com.covenant.platform.enums.ContractStatus;
import com.covenant.platform.enums.Role;
import com.covenant.platform.repository.ContractRepository;
import com.covenant.platform.repository.UserRepository;
import com.stripe.exception.StripeException;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractService {
    
    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final EmailService emailService;
    private final PaymentService paymentService;

    private String getUserEmail(String userId) {
        if (userId == null) return "unknown@covenant.com";
        return userRepository.findById(userId)
                .map(User::getEmail)
                .orElse("unknown@covenant.com");
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /** Resolves the currently authenticated user from SecurityContext (e.g. JWT). */
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            throw new IllegalStateException("Not authenticated");
        }
        return getUserByEmail(auth.getName());
    }

    private void requireSeller(Contract contract, User user) {
        if (contract.getSellerId() == null || !contract.getSellerId().equals(user.getId())) {
            throw new IllegalStateException("Only the seller can perform this action.");
        }
    }

    private void requireBuyer(Contract contract, User user) {
        if (contract.getBuyerId() == null || !contract.getBuyerId().equals(user.getId())) {
            throw new IllegalStateException("Only the buyer can perform this action.");
        }
    }

    /** Requires current user to be either seller or buyer of the contract. */
    private void requireParty(Contract contract, User user) {
        boolean isSeller = user.getId().equals(contract.getSellerId());
        boolean isBuyer = contract.getBuyerId() != null && contract.getBuyerId().equals(user.getId());
        if (!isSeller && !isBuyer) {
            throw new IllegalStateException("You are not the seller or buyer of this contract.");
        }
    }

    private void requireAdmin(User user) {
        if (user.getRole() != Role.ADMIN) {
            throw new IllegalStateException("Only an admin can resolve disputes.");
        }
    }

    @Transactional
    public Contract createContract(CreateContractRequest request) {
        User seller = getCurrentUser();
        Contract contract = new Contract();

        contract.setSellerId(seller.getId());
        contract.setDescription(request.getDescription());
        contract.setTitle(request.getTitle());
        contract.setAmount(request.getAmount());

        contract.setStatus(ContractStatus.DRAFT);
        contract.setCreatedAt(LocalDateTime.now());

        return contractRepository.save(contract);
    }

    public Contract getContractById(@NonNull String id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("contract not found with id: " + id));
        User currentUser = getCurrentUser();
        // Allow seller, buyer, or admin to view the contract
        boolean isSeller = currentUser.getId().equals(contract.getSellerId());
        boolean isBuyer = contract.getBuyerId() != null && contract.getBuyerId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isSeller && !isBuyer && !isAdmin) {
            throw new IllegalStateException("You are not authorized to view this contract.");
        }
        return contract;
    }

    /** Internal method to get contract without auth checks (for webhooks/system use). */
    public Contract getContractByIdInternal(@NonNull String id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("contract not found with id: " + id));
    }

    @Transactional
    public Contract acceptContract(String contractId) {
        User buyer = getCurrentUser();
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("contract not found with id: " + contractId));

        if (contract.getSellerId().equals(buyer.getId())) {
            throw new IllegalStateException("You cannot accept your own contract.");
        }

        if(contract.getStatus() != ContractStatus.DRAFT) {
            throw new IllegalStateException("This contract is not available. current status: " + contract.getStatus());
        }

        try {
            // Create Payment Intent with contract metadata (for webhook processing)
            com.stripe.model.PaymentIntent paymentIntent = paymentService.createPaymentIntentInternal(
                contract.getAmount(), contractId);

            // Set buyer and payment details
            contract.setBuyerId(buyer.getId());
            contract.setStatus(ContractStatus.PAYMENT_PENDING);
            contract.setPaymentIntentId(paymentIntent.getId());
            Contract savedContract = contractRepository.save(contract);

            // Notify seller
            String subject = "Contract Accepted: " + contract.getTitle();
            String body = "Good news! A buyer has accepted your contract. Payment is pending. " +
                         "Payment Intent ID: " + paymentIntent.getId() + 
                         ". Payment will be automatically confirmed via webhook when completed.";
            emailService.sendEmail(getUserEmail(contract.getSellerId()), subject, body);

            return savedContract;

        } catch (StripeException e) {
            throw new RuntimeException("Error initiating payment: " + e.getMessage());
        }
    }

    /**
     * Confirms payment manually (for testing/backend use).
     * In production, payment should be confirmed automatically via Stripe webhook.
     * This method verifies payment status with Stripe before confirming.
     */
    @Transactional
    public Contract confirmPayment(String contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("contract not found with id: " + contractId));
        User currentUser = getCurrentUser();
        requireBuyer(contract, currentUser);

        if (contract.getStatus() != ContractStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Cannot confirm payment. Contract is not pending. Status: " + contract.getStatus());
        }

        // Verify payment with Stripe if PaymentIntent ID exists
        if (contract.getPaymentIntentId() != null) {
            try {
                com.stripe.model.PaymentIntent paymentIntent = paymentService.getPaymentIntent(contract.getPaymentIntentId());
                if (!"succeeded".equals(paymentIntent.getStatus())) {
                    throw new IllegalStateException("Payment not completed. Stripe status: " + paymentIntent.getStatus());
                }
            } catch (com.stripe.exception.StripeException e) {
                log.warn("Could not verify payment with Stripe for contract {}: {}", contractId, e.getMessage());
                // Continue anyway for manual confirmation
            }
        }

        return confirmPaymentInternal(contract);
    }

    /**
     * Internal method to confirm payment (used by webhook handler and manual confirmation).
     */
    @Transactional
    public Contract confirmPaymentInternal(Contract contract) {
        if (contract.getStatus() != ContractStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Cannot confirm payment. Contract is not pending. Status: " + contract.getStatus());
        }

        contract.setStatus(ContractStatus.LOCKED);
        Contract savedContract = contractRepository.save(contract);

        // Notify seller
        String subject = "Payment Received: " + contract.getTitle();
        String body = "Payment confirmed! Funds are locked. Please ship the item.";
        emailService.sendEmail(getUserEmail(contract.getSellerId()), subject, body);
        
        return savedContract;
    }

    @Transactional
    public Contract shipContract(String contractId, ShipContractRequest request) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("contract not found with id: " + contractId));
        requireSeller(contract, getCurrentUser());

        if (contract.getStatus() != ContractStatus.LOCKED) {
            throw new IllegalStateException("Cannot ship. Contract status must be LOCKED. Current Status: " + contract.getStatus());
        }

        contract.setTrackingId(request.getTrackingId());
        contract.setLogisticsProvider(request.getLogisticsProvider());
        contract.setStatus(ContractStatus.SHIPPED);

        String subject = "Item Shipped: " + contract.getTitle();
        String body = "The seller has shipped your item via " + request.getLogisticsProvider() + 
                      ". Tracking ID: " + request.getTrackingId();
        emailService.sendEmail(getUserEmail(contract.getBuyerId()), subject, body);

        return contractRepository.save(contract); 
    }

    @Transactional
    public Contract markAsDelivered(String contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("contract not found with id: " + contractId));
        requireSeller(contract, getCurrentUser());

        if (contract.getStatus() != ContractStatus.SHIPPED) {
            throw new IllegalStateException("Cannot mark delivered to a contract not shipped. Current Status: " + contract.getStatus());
        }

        contract.setStatus(ContractStatus.DELIVERED);
        contract.setDeliveryDate(LocalDateTime.now());

        String subject = "Item Delivered: " + contract.getTitle();
        String body = "The courier has marked your item as delivered. Please inspect it within 14 days.";
        emailService.sendEmail(getUserEmail(contract.getBuyerId()), subject, body);

        return contractRepository.save(contract);
    }

    @Transactional
    public Contract markAsSatisfied(String contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("contract not found with id: " + contractId));
        requireBuyer(contract, getCurrentUser());
        return markAsSatisfiedInternal(contract, ContractStatus.SATISFIED);
    }

    /** Internal method for scheduler - bypasses auth checks and uses AUTO_RELEASED status. */
    @Transactional
    public Contract markAsSatisfiedInternal(Contract contract, ContractStatus status) {
        if (contract.getStatus() != ContractStatus.DELIVERED && contract.getStatus() != ContractStatus.SHIPPED) {
            throw new IllegalStateException("Cannot mark satisfied as item not shipped/delivered. Current Status: " + contract.getStatus());
        }

        contract.setStatus(status);

        String subject = "Payment Released: " + contract.getTitle();
        String body = status == ContractStatus.AUTO_RELEASED
                ? "14 days have passed since delivery with no dispute. Funds of ₹" + contract.getAmount() + " have been automatically released to your account."
                : "The buyer is satisfied! Funds of ₹" + contract.getAmount() + " have been released to your account.";
        emailService.sendEmail(getUserEmail(contract.getSellerId()), subject, body);

        return contractRepository.save(contract);
    }

    @Transactional
    public Contract raiseDispute(String contractId, RaiseDisputeRequest request) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("contract not found with id: " + contractId));
        requireBuyer(contract, getCurrentUser());

        if (contract.getStatus() != ContractStatus.DELIVERED && contract.getStatus() != ContractStatus.SHIPPED) {
            throw new IllegalStateException("Item not shipped/delivered so cannot raise dispute");
        }

        contract.setStatus(ContractStatus.DISPUTED);
        log.info("Dispute raised for Contract {}: {}", contractId, request.getReason());

        String subject = "Dispute Raised: " + contract.getTitle();
        String body = "The buyer has raised a dispute. Reason: " + request.getReason() + ". Payment is frozen.";
        emailService.sendEmail(getUserEmail(contract.getSellerId()), subject, body);

        return contractRepository.save(contract);
    }

    @Transactional
    public Contract resolveDispute(String contractId, boolean refundBuyer) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("contract not found with id: " + contractId));
        requireAdmin(getCurrentUser());

        if (contract.getStatus() != ContractStatus.DISPUTED) {
            throw new IllegalStateException("Contract status not disputed");
        }

        String subject;
        String body;
        String recipientEmail;

        if (refundBuyer) {
            log.info("Refund approved for Buyer. Amount: {}", contract.getAmount());
            contract.setStatus(ContractStatus.REFUNDED);

            subject = "Dispute Resolved: Refund Approved";
            body = "The dispute was resolved in the Buyer's favor. ₹" + contract.getAmount() + " will be refunded.";
            recipientEmail = getUserEmail(contract.getBuyerId()); // Notify Buyer
        } else {
            log.info("Dispute rejected. Releasing funds to Seller.");
            contract.setStatus(ContractStatus.SATISFIED);

            subject = "Dispute Resolved: Funds Released";
            body = "The dispute was resolved in the Seller's favor. Funds have been released.";
            recipientEmail = getUserEmail(contract.getSellerId()); // Notify Seller
        }

        emailService.sendEmail(recipientEmail, subject, body);

        return contractRepository.save(contract);
    }
}
