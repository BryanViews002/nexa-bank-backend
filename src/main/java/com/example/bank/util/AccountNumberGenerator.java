// src/main/java/com/example/bank/util/AccountNumberGenerator.java
package com.example.bank.util;

import java.util.Random;

public class AccountNumberGenerator {
    public static String generate() {
        return "ACCT-" + String.format("%010d", new Random().nextInt(1000000000));
    }
}