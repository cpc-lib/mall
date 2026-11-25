package cc.ivera.bill.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 账账核对 balanced 规则纯域测试：零依赖。
 * balanced=true 必须同时满足 6 个条件。AGENTS.md 5.5 条明确禁止仅凭净额为 0 判平账。
 */
class BillBalancedRuleTest {

    /** 全部一致的基准值 */
    private static boolean isBalancedOk() {
        return BillBalancedRule.isBalanced(
            10, 10,     // 支付笔数
            3, 3,       // 退款笔数
            10000, 10000,   // 支付金额（分）
            500, 500,       // 退款金额（分）
            9500, 9500,     // 净额 = 10000 - 500
            0);             // open discrepancy
    }

    @Test
    @DisplayName("全部条件一致 → balanced=true")
    void allMatchIsBalanced() {
        assertTrue(isBalancedOk());
    }

    @Test
    @DisplayName("仅 open 差异 > 0 → balanced=false，即使净额一致")
    void openDiscrepancyBreaksBalancedEvenIfNetMatches() {
        assertFalse(BillBalancedRule.isBalanced(
            10, 10, 3, 3, 10000, 10000, 500, 500, 9500, 9500, 1));
    }

    @Test
    @DisplayName("支付笔数不一致 → false")
    void payCountMismatchBreaks() {
        assertFalse(BillBalancedRule.isBalanced(
            10, 9, 3, 3, 10000, 10000, 500, 500, 9500, 9500, 0));
    }

    @Test
    @DisplayName("退款笔数不一致 → false")
    void refundCountMismatchBreaks() {
        assertFalse(BillBalancedRule.isBalanced(
            10, 10, 3, 2, 10000, 10000, 500, 500, 9500, 9500, 0));
    }

    @Test
    @DisplayName("支付金额不一致 → false")
    void payAmountMismatchBreaks() {
        assertFalse(BillBalancedRule.isBalanced(
            10, 10, 3, 3, 10000, 9999, 500, 500, 9500, 9500, 0));
    }

    @Test
    @DisplayName("退款金额不一致 → false")
    void refundAmountMismatchBreaks() {
        assertFalse(BillBalancedRule.isBalanced(
            10, 10, 3, 3, 10000, 10000, 500, 499, 9500, 9501, 0));
    }

    @Test
    @DisplayName("净额不一致 → false")
    void netMismatchBreaks() {
        assertFalse(BillBalancedRule.isBalanced(
            10, 10, 3, 3, 10000, 10000, 500, 500, 9500, 9499, 0));
    }

    @Test
    @DisplayName("净额差为 0 但 open 差异存在 → false — 审计强调：禁止仅凭净额判平账")
    void netZeroButOpenDiscrepancyIsNotBalanced() {
        assertFalse(BillBalancedRule.isBalanced(
            10, 10, 3, 3, 10000, 10000, 500, 500, 9500, 9500, 5));
    }

    @Test
    @DisplayName("四笔数全 0 且 open=0 → balanced=true（日切无交易情形）")
    void zeroTransactionDayIsBalanced() {
        assertTrue(BillBalancedRule.isBalanced(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }
}
