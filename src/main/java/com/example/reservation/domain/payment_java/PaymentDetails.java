package com.example.reservation.domain.payment_java;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
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

    public PaymentDetails() {}

    public PaymentDetails(String cardLastFourDigits, String cardBrand, String cardHolderName,
                         String bankName, String bankAccountNumber, String digitalWalletType,
                         String billingAddress, String billingCity, String billingPostalCode,
                         String billingCountryCode) {
        this.cardLastFourDigits = cardLastFourDigits;
        this.cardBrand = cardBrand;
        this.cardHolderName = cardHolderName;
        this.bankName = bankName;
        this.bankAccountNumber = bankAccountNumber;
        this.digitalWalletType = digitalWalletType;
        this.billingAddress = billingAddress;
        this.billingCity = billingCity;
        this.billingPostalCode = billingPostalCode;
        this.billingCountryCode = billingCountryCode;
    }

    // 보안을 위한 메서드 - 카드번호 마스킹
    public String getMaskedCardNumber() {
        if (cardLastFourDigits != null && cardLastFourDigits.length() == 4) {
            return "**** **** **** " + cardLastFourDigits;
        }
        return null;
    }

    // 신용카드 결제인지 확인
    public boolean isCreditCardPayment() {
        return cardLastFourDigits != null && cardBrand != null;
    }

    // 은행 이체 결제인지 확인
    public boolean isBankTransferPayment() {
        return bankName != null && bankAccountNumber != null;
    }

    // 디지털 지갑 결제인지 확인
    public boolean isDigitalWalletPayment() {
        return digitalWalletType != null;
    }

    // Getters
    public String getCardLastFourDigits() { return cardLastFourDigits; }
    public String getCardBrand() { return cardBrand; }
    public String getCardHolderName() { return cardHolderName; }
    public String getBankName() { return bankName; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public String getDigitalWalletType() { return digitalWalletType; }
    public String getBillingAddress() { return billingAddress; }
    public String getBillingCity() { return billingCity; }
    public String getBillingPostalCode() { return billingPostalCode; }
    public String getBillingCountryCode() { return billingCountryCode; }

    // Setters
    public void setCardLastFourDigits(String cardLastFourDigits) { this.cardLastFourDigits = cardLastFourDigits; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }
    public void setCardHolderName(String cardHolderName) { this.cardHolderName = cardHolderName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }
    public void setDigitalWalletType(String digitalWalletType) { this.digitalWalletType = digitalWalletType; }
    public void setBillingAddress(String billingAddress) { this.billingAddress = billingAddress; }
    public void setBillingCity(String billingCity) { this.billingCity = billingCity; }
    public void setBillingPostalCode(String billingPostalCode) { this.billingPostalCode = billingPostalCode; }
    public void setBillingCountryCode(String billingCountryCode) { this.billingCountryCode = billingCountryCode; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PaymentDetails that = (PaymentDetails) obj;
        return Objects.equals(cardLastFourDigits, that.cardLastFourDigits) &&
               Objects.equals(cardBrand, that.cardBrand) &&
               Objects.equals(cardHolderName, that.cardHolderName) &&
               Objects.equals(bankName, that.bankName) &&
               Objects.equals(bankAccountNumber, that.bankAccountNumber) &&
               Objects.equals(digitalWalletType, that.digitalWalletType) &&
               Objects.equals(billingAddress, that.billingAddress) &&
               Objects.equals(billingCity, that.billingCity) &&
               Objects.equals(billingPostalCode, that.billingPostalCode) &&
               Objects.equals(billingCountryCode, that.billingCountryCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardLastFourDigits, cardBrand, cardHolderName, bankName,
                          bankAccountNumber, digitalWalletType, billingAddress, billingCity,
                          billingPostalCode, billingCountryCode);
    }
}