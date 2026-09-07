package cc.ivera.util;

import java.math.BigDecimal;

public final class MoneyUtils {

    private MoneyUtils() {
    }

    public static BigDecimal centsToYuan(Number cents) {
        if (cents == null) {
            throw new IllegalArgumentException("金额不能为空");
        }
        return BigDecimal.valueOf(cents.longValue(), 2);
    }

    public static int yuanToCents(String yuan) {
        if (yuan == null || yuan.trim().isEmpty()) {
            throw new IllegalArgumentException("金额不能为空");
        }
        return new BigDecimal(yuan).movePointRight(2).intValueExact();
    }
}
