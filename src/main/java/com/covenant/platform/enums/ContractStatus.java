package com.covenant.platform.enums;

public enum ContractStatus {
    DRAFT,              // 1. Seller creates the contract link
    PAYMENT_PENDING,    // 2. Buyer accepts terms, waiting for payment
    LOCKED,             // 3. Money received & held by Covenant. Seller must ship now.
    
    SHIPPED,            // 4. Seller provided a Tracking ID
    DELIVERED,          // 5. Logistics API confirms delivery (Timer starts here)
    
    SATISFIED,          // 6a. Buyer explicitly approves -> Release Money
    AUTO_RELEASED,      // 6b. 2 weeks passed with no dispute -> Release Money
    
    DISPUTED,           // 7. Buyer flagged an issue (Money frozen)
    CANCELLED,          // 8. Deal cancelled before payment
    REFUNDED         // 9. Money sent back to Buyer
}
