// InsufficientFundsException.java
package com.example.bank.exception;

/**
 * Custom exception thrown when a user attempts to perform a transaction
 * that would result in insufficient funds in their account.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }

    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }
}