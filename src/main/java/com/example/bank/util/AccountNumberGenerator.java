// src/main/java/com/example/bank/util/AccountNumberGenerator.java
package com.example.bank.util;

import java.security.SecureRandom;

public class AccountNumberGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountNumberGenerator() {
    }

    public static String generate() {
        return "ACCT-" + String.format("%010d", RANDOM.nextLong(10_000_000_000L));
    }
}
