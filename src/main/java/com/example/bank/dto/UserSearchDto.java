package com.example.bank.dto;

public class UserSearchDto {
    private Long userId;
    private String username;
    private String fullName;
    private String accountNumber;

    // Constructor, getters, setters
    public UserSearchDto(Long userId, String username, String fullName, String accountNumber) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.accountNumber = accountNumber;
    }
    // Add getters/setters

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }
}