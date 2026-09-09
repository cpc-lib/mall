package cc.ivera.shared.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Money 值对象纯单元测试：不连数据库/Redis/MQ。
 */
class MoneyTest {

    @Test
    void yuanFenRoundtrip() {
        Money money = Money.of(new BigDecimal("12.34"));
        assertEquals(1234L, money.toFen());
        assertEquals(new BigDecimal("12.34"), money.toYuan());
    }

    @Test
    void ofRoundsToTwoDecimalsHalfUp() {
        assertEquals(101L, Money.of(new BigDecimal("1.005")).toFen());
        assertEquals(100L, Money.of(new BigDecimal("1.004")).toFen());
    }

    @Test
    void ofFenAndZero() {
        assertEquals(0L, Money.ZERO.toFen());
        assertEquals(5L, Money.ofFen(5).toFen());
    }

    @Test
    void addAndSubtract() {
        Money a = Money.of(new BigDecimal("10.00"));
        Money b = Money.of(new BigDecimal("0.30"));
        assertEquals(1030L, a.add(b).toFen());
        assertEquals(970L, a.subtract(b).toFen());
        // 不可变性：原对象不被运算修改
        assertEquals(1000L, a.toFen());
    }

    @Test
    void equalityByValue() {
        assertEquals(Money.of(new BigDecimal("1.00")), Money.of(new BigDecimal("1.0")));
        assertEquals(Money.of(new BigDecimal("1.00")).hashCode(), Money.of(new BigDecimal("1.0")).hashCode());
    }

    @Test
    void compareAndPredicates() {
        Money small = Money.of(new BigDecimal("1.00"));
        Money big = Money.of(new BigDecimal("2.00"));
        assertTrue(small.isLessThan(big));
        assertFalse(small.isGreaterThan(big));
        assertTrue(big.compareTo(small) > 0);
        assertEquals(0, small.compareTo(Money.ofFen(100)));
    }

    @Test
    void nullRejected() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(null));
    }
}
