package cc.ivera.shared.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 金额值对象：单位元、两位小数、不可变。
 *
 * <p>领域聚合内部使用金额时一律以本类型表达，杜绝裸 BigDecimal/Long 传递；
 * 持久化边界由 Converter 与数据库列（BigDecimal/Long 分）互转。
 * 内部以「分」存储，加减精确无浮点误差。</p>
 */
public final class Money implements Comparable<Money> {

    public static final Money ZERO = new Money(0L);

    /**
     * 金额，单位分。
     */
    private final long fen;

    private Money(long fen) {
        this.fen = fen;
    }

    /**
     * 由元构造：超过两位小数时四舍五入（HALF_UP）。
     */
    public static Money of(BigDecimal yuan) {
        if (yuan == null) {
            throw new IllegalArgumentException("金额不能为空");
        }
        return new Money(yuan.setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2).longValueExact());
    }

    /**
     * 由分构造。
     */
    public static Money ofFen(long fen) {
        return new Money(fen);
    }

    public Money add(Money other) {
        return new Money(this.fen + other.fen);
    }

    public Money subtract(Money other) {
        return new Money(this.fen - other.fen);
    }

    public boolean isGreaterThan(Money other) {
        return this.fen > other.fen;
    }

    public boolean isLessThan(Money other) {
        return this.fen < other.fen;
    }

    /**
     * 元，两位小数。
     */
    public BigDecimal toYuan() {
        return BigDecimal.valueOf(fen, 2);
    }

    /**
     * 分。
     */
    public long toFen() {
        return fen;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(this.fen, other.fen);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money)) {
            return false;
        }
        Money money = (Money) o;
        return fen == money.fen;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fen);
    }

    @Override
    public String toString() {
        return toYuan().toPlainString();
    }
}
