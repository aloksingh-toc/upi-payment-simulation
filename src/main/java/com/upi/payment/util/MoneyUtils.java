package com.upi.payment.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Centralises all monetary arithmetic so rounding rules live in exactly one place. */
public final class MoneyUtils {

    public static final int CURRENCY_SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private MoneyUtils() {}

    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(CURRENCY_SCALE, ROUNDING);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return round(a.add(b));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return round(a.subtract(b));
    }

    public static boolean isNegative(BigDecimal amount) {
        return amount.signum() < 0;
    }

    public static boolean isSufficient(BigDecimal balance, BigDecimal required) {
        return balance.compareTo(required) >= 0;
    }
}
