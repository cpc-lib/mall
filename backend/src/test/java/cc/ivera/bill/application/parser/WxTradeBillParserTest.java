package cc.ivera.bill.application.parser;

import cc.ivera.bill.domain.enums.BillRecordType;
import cc.ivera.bill.domain.model.BillRecord;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.infrastructure.constant.DatePatterns;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WxTradeBillParser 纯单测（无 Spring 依赖）：锁定微信交易账单 CSV 解析现状——
 * 三种表头识别、支付/退款/撤销行、反引号清洗、元转分、退款金额绝对值、坏行跳过、缺表头报错。
 */
class WxTradeBillParserTest {

    private static final String CHANNEL = "WXPAY";
    private static final String BILL_DATE = "2027-05-06";

    private final WxTradeBillParser parser = new WxTradeBillParser();

    /** SUCCESS 账单表头：无微信退款单号、无退款申请时间列。 */
    private static Map<String, String> successHeader() {
        return cols(
            "交易时间", "", "微信订单号", "", "商户订单号", "", "交易类型", "",
            "交易状态", "", "订单金额", "", "应结订单金额", "");
    }

    /** ALL 账单表头：含微信退款单号，不含退款申请/成功时间列。 */
    private static Map<String, String> allHeader() {
        Map<String, String> cols = successHeader();
        cols.put("微信退款单号", "");
        cols.put("商户退款单号", "");
        cols.put("退款金额", "");
        cols.put("申请退款金额", "");
        return cols;
    }

    /** REFUND 账单表头：含退款申请/成功时间列与退款状态列。 */
    private static Map<String, String> refundHeader() {
        Map<String, String> cols = allHeader();
        cols.put("退款状态", "");
        cols.put("退款申请时间", "");
        cols.put("退款成功时间", "");
        return cols;
    }

    @Test
    void parse_blankContent_returnsEmptyResult() {
        ParsedBill r1 = parser.parse(null, CHANNEL, BILL_DATE);
        ParsedBill r2 = parser.parse("   ", CHANNEL, BILL_DATE);

        assertTrue(r1.getPayRecords().isEmpty());
        assertTrue(r1.getRefundRecords().isEmpty());
        assertNull(r1.getBillKind());
        assertEquals(0, r1.getTotalLines());
        assertTrue(r2.getPayRecords().isEmpty());
        assertEquals(0, r2.validRecordCount());
    }

    @Test
    void parse_firstLineWithoutHeader_throwsBizException() {
        BizException ex = assertThrows(BizException.class,
            () -> parser.parse("微信支付账单明细\n", CHANNEL, BILL_DATE));
        assertTrue(ex.getMessage().contains("缺少微信交易账单表头"));
    }

    @Test
    void parse_blankMultilineContent_returnsEmptyResult() {
        // 现状：纯空白内容（含换行）被 hasText 闸门拦截，静默返回空结果而非抛异常
        ParsedBill result = parser.parse("\n  \n", CHANNEL, BILL_DATE);
        assertTrue(result.getPayRecords().isEmpty());
        assertTrue(result.getRefundRecords().isEmpty());
        assertNull(result.getBillKind());
    }

    @Test
    void parse_successBillPayRow_parsedWithFenAmountAndSummarySkipped() {
        Map<String, String> header = successHeader();
        Map<String, String> pay = successHeader();
        pay.put("交易时间", "2027-05-06 10:00:00");
        pay.put("微信订单号", "WX123");
        pay.put("商户订单号", "MCH456");
        pay.put("交易类型", "JSAPI");
        pay.put("交易状态", "SUCCESS");
        pay.put("订单金额", "1.99");
        pay.put("应结订单金额", "1.99");
        // 汇总数据行：首字段非交易时间，须跳过
        String summary = "总交易单数,`1,`2,`3,`4,`5,`6";
        String content = "\uFEFF" + headerLine(header) + "\n" + dataLine(pay) + "\n" + summary + "\n";

        ParsedBill result = parser.parse(content, CHANNEL, BILL_DATE);

        assertEquals("SUCCESS", result.getBillKind());
        assertEquals(1, result.getPayRecords().size());
        assertTrue(result.getRefundRecords().isEmpty());
        assertEquals(0, result.getBadLines());
        assertEquals(1, result.validRecordCount());
        // 物理行数：表头 + 数据行 + 汇总行 + 末尾换行产生的空段
        assertEquals(4, result.getTotalLines());

        BillRecord rec = result.getPayRecords().get(0);
        assertEquals(BillRecordType.PAY.getType(), rec.getRecordType());
        assertEquals("WX123", rec.getChannelSerialNo());
        assertEquals("MCH456", rec.getBizNo());
        assertEquals("JSAPI", rec.getTradeType());
        assertEquals("SUCCESS", rec.getTradeStatus());
        assertEquals(199, rec.getTotalAmount());
        assertNull(rec.getRefundAmount());
        assertEquals(CHANNEL, rec.getChannelCode());
        assertEquals(BILL_DATE, rec.getBillDate());
        assertEquals(dateTime("2027-05-06 10:00:00"), rec.getTradeTime());
        assertNotNull(rec.getRawLine());
        assertTrue(rec.getRawLine().startsWith("`2027-05-06"));
    }

    @Test
    void parse_allBillRefundRow_negativeRefundAmountTakenAbsolute() {
        Map<String, String> header = allHeader();
        Map<String, String> refund = allHeader();
        refund.put("交易时间", "2027-05-06 10:05:00");
        refund.put("微信订单号", "WX123");
        refund.put("商户订单号", "MCH456");
        refund.put("交易类型", "REFUND");
        refund.put("交易状态", "REFUND");
        refund.put("微信退款单号", "R100");
        refund.put("商户退款单号", "RR200");
        refund.put("退款金额", "-0.50");
        refund.put("申请退款金额", "0.50");
        String content = headerLine(header) + "\n" + dataLine(refund) + "\n";

        ParsedBill result = parser.parse(content, CHANNEL, BILL_DATE);

        assertEquals("ALL", result.getBillKind());
        assertEquals(1, result.getRefundRecords().size());
        assertTrue(result.getPayRecords().isEmpty());

        BillRecord rec = result.getRefundRecords().get(0);
        assertEquals(BillRecordType.REFUND.getType(), rec.getRecordType());
        assertEquals("R100", rec.getChannelSerialNo());
        assertEquals("RR200", rec.getBizNo());
        assertEquals(50, rec.getRefundAmount());
        assertEquals("REFUND", rec.getTradeType());
        // ALL 账单无「退款状态」列时回退交易状态列
        assertEquals("REFUND", rec.getTradeStatus());
        assertNull(rec.getRefundApplyTime());
        assertNull(rec.getRefundSuccessTime());
    }

    @Test
    void parse_revokedRow_fallsBackToOrderNosAndTradeTypeRevoked() {
        Map<String, String> header = allHeader();
        Map<String, String> revoked = allHeader();
        revoked.put("交易时间", "2027-05-06 10:10:00");
        revoked.put("微信订单号", "WX900");
        revoked.put("商户订单号", "MCH900");
        revoked.put("交易类型", "MICROPAY");
        revoked.put("交易状态", "REVOKED");
        revoked.put("微信退款单号", "0");
        revoked.put("商户退款单号", "0");
        revoked.put("退款金额", "2.00");
        revoked.put("申请退款金额", "2.00");
        String content = headerLine(header) + "\n" + dataLine(revoked) + "\n";

        ParsedBill result = parser.parse(content, CHANNEL, BILL_DATE);

        assertEquals(1, result.getRefundRecords().size());
        BillRecord rec = result.getRefundRecords().get(0);
        // 撤销行无退款单号，回退原支付订单号
        assertEquals("WX900", rec.getChannelSerialNo());
        assertEquals("MCH900", rec.getBizNo());
        assertEquals("REVOKED", rec.getTradeType());
        assertEquals("REVOKED", rec.getTradeStatus());
        assertEquals(200, rec.getRefundAmount());
    }

    @Test
    void parse_refundBill_usesRefundStatusColumnAndRefundTimes() {
        Map<String, String> header = refundHeader();
        Map<String, String> refund = refundHeader();
        refund.put("交易时间", "2027-05-06 10:20:00");
        refund.put("微信订单号", "WX123");
        refund.put("商户订单号", "MCH456");
        refund.put("交易类型", "REFUND");
        refund.put("交易状态", "REFUND");
        refund.put("微信退款单号", "R300");
        refund.put("商户退款单号", "RR400");
        refund.put("退款金额", "3.00");
        refund.put("申请退款金额", "3.00");
        refund.put("退款状态", "SUCCESS");
        refund.put("退款申请时间", "2027-05-06 11:00:00");
        refund.put("退款成功时间", "2027-05-06 12:00:00");
        String content = headerLine(header) + "\n" + dataLine(refund) + "\n";

        ParsedBill result = parser.parse(content, CHANNEL, BILL_DATE);

        assertEquals("REFUND", result.getBillKind());
        BillRecord rec = result.getRefundRecords().get(0);
        assertEquals("SUCCESS", rec.getTradeStatus());
        assertEquals(dateTime("2027-05-06 11:00:00"), rec.getRefundApplyTime());
        assertEquals(dateTime("2027-05-06 12:00:00"), rec.getRefundSuccessTime());
        assertEquals(300, rec.getRefundAmount());
    }

    @Test
    void parse_badLine_countedAndSkipped() {
        Map<String, String> header = successHeader();
        Map<String, String> bad = successHeader();
        bad.put("交易时间", "2027-05-06 10:30:00");
        bad.put("微信订单号", "WX1");
        bad.put("商户订单号", "MCH1");
        bad.put("交易状态", "SUCCESS");
        // 订单金额/应结订单金额均为空 → 金额不可解析 → 坏行
        Map<String, String> good = successHeader();
        good.put("交易时间", "2027-05-06 10:31:00");
        good.put("微信订单号", "WX2");
        good.put("商户订单号", "MCH2");
        good.put("交易类型", "JSAPI");
        good.put("交易状态", "SUCCESS");
        good.put("订单金额", "5.00");
        good.put("应结订单金额", "5.00");
        String content = headerLine(header) + "\n" + dataLine(bad) + "\n" + dataLine(good) + "\n";

        ParsedBill result = parser.parse(content, CHANNEL, BILL_DATE);

        assertEquals(1, result.getBadLines());
        assertEquals(1, result.getPayRecords().size());
        assertEquals(1, result.validRecordCount());
        assertEquals("MCH2", result.getPayRecords().get(0).getBizNo());
    }

    // ==================== 测试辅助 ====================

    private static Map<String, String> cols(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private static String headerLine(Map<String, String> columns) {
        return String.join(",", columns.keySet());
    }

    /** 数据行每个字段值加微信账单的反引号前缀。 */
    private static String dataLine(Map<String, String> columns) {
        return columns.values().stream().map(v -> "`" + v).collect(Collectors.joining(","));
    }

    private static Date dateTime(String text) {
        return Date.from(LocalDateTime.parse(text, DateTimeFormatter.ofPattern(DatePatterns.DATETIME))
            .atZone(ZoneId.systemDefault()).toInstant());
    }
}
