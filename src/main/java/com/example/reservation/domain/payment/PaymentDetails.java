package com.example.reservation.domain.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString(exclude = {"cardLastFourDigits", "bankAccountNumber"})
public class PaymentDetails {
    
    @Column(length = 4)
    private String cardLastFourDigits;
    
    @Column(length = 50)
    private String cardBrand;
    
    @Column(length = 100)
    private String cardHolderName;
    
    @Column(length = 100)
    private String bankName;
    
    @Column(length = 50)
    private String bankAccountNumber;
    
    @Column(length = 100)
    private String digitalWalletType;
    
    @Column(length = 200)
    private String billingAddress;
    
    @Column(length = 100)
    private String billingCity;
    
    @Column(length = 20)
    private String billingPostalCode;
    
    @Column(length = 3)
    private String billingCountryCode;
    
    public String getFullBillingAddress() {
        StringBuilder address = new StringBuilder();
        if (billingAddress != null) address.append(billingAddress);
        if (billingCity != null) address.append(", ").append(billingCity);
        if (billingPostalCode != null) address.append(" ").append(billingPostalCode);
        if (billingCountryCode != null) address.append(", ").append(billingCountryCode);
        return address.toString();
    }
    
    public String getMaskedCardNumber() {
        if (cardLastFourDigits != null) {
            return "****-****-****-" + cardLastFourDigits;
        }
        return null;
    }
    
    public String getMaskedBankAccount() {
        if (bankAccountNumber != null && bankAccountNumber.length() > 4) {
            String masked = "*".repeat(bankAccountNumber.length() - 4);
            return masked + bankAccountNumber.substring(bankAccountNumber.length() - 4);
        }
        return bankAccountNumber;
    }
}