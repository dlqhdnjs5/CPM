package com.bowon.cpm.common.util;

public final class MaskingUtils {

    private MaskingUtils() {
    }

    /**
     * 계좌번호 마스킹
     * 예) 12345678-01 → 1234****-**
     */
    public static String maskAccountNo(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) {
            return "****";
        }
        if (accountNo.length() <= 4) {
            return "****";
        }
        return accountNo.substring(0, 4) + "*".repeat(accountNo.length() - 4);
    }

    /**
     * API Key / Secret 마스킹
     * 예) abcd1234efgh5678 → abcd********5678
     */
    public static String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "****";
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "*".repeat(value.length() - 8) + value.substring(value.length() - 4);
    }

    /**
     * 이메일 마스킹
     * 예) user@example.com → us**@example.com
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 2) {
            return "**@" + domain;
        }
        return local.substring(0, 2) + "*".repeat(local.length() - 2) + "@" + domain;
    }
}

