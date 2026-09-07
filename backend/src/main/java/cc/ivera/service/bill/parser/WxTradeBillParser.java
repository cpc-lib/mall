package cc.ivera.service.bill.parser;

import cc.ivera.entity.bill.BillRecord;
import cc.ivera.enums.bill.BillRecordType;
import cc.ivera.exception.BizException;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信交易账单 CSV 解析器（无 Spring 依赖，可独立单测）。
 *
 * <p>依据微信支付 v3《交易账单详细说明》实现：
 * <ul>
 *   <li>账单分 ALL / SUCCESS / REFUND 三种，明细表头列数与列序各不相同（ALL 27 列、
 *       SUCCESS 20 列、REFUND 29 列），因此按表头列名定位字段而非固定下标；</li>
 *   <li>行类型由「交易状态」列判定：SUCCESS=支付行，REFUND=转入退款行，REVOKED=付款码撤销行；</li>
 *   <li>明细行与汇总行每个字段值前有一个反引号（`）前缀（防止 Excel 科学计数法），解析时去除；</li>
 *   <li>表头行（交易时间,...）、汇总表头行（总交易单数,...）与汇总数据行（首字段非时间）一律跳过；
 *       旧版账单中以 % 开头的汇总行同样跳过；</li>
 *   <li>金额单位为元，精确乘 100 转分；退款金额 v3 为非负数、历史版本可能为负数，统一取绝对值；</li>
 *   <li>首字段为时间但关键字段缺失/金额不可解析的坏行计数并跳过，不中断整单解析。</li>
 * </ul>
 */
public final class WxTradeBillParser {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ZoneId ZONE = ZoneId.systemDefault();

    // ==================== 账单表头列名 ====================
    private static final String COL_TRADE_TIME = "交易时间";
    private static final String COL_WX_ORDER_NO = "微信订单号";
    private static final String COL_MCH_ORDER_NO = "商户订单号";
    private static final String COL_TRADE_TYPE = "交易类型";
    private static final String COL_TRADE_STATUS = "交易状态";
    private static final String COL_SETTLE_AMOUNT = "应结订单金额";
    private static final String COL_ORDER_AMOUNT = "订单金额";
    private static final String COL_LEGACY_TOTAL_AMOUNT = "总金额";
    private static final String COL_WX_REFUND_NO = "微信退款单号";
    private static final String COL_MCH_REFUND_NO = "商户退款单号";
    private static final String COL_REFUND_AMOUNT = "退款金额";
    private static final String COL_APPLY_REFUND_AMOUNT = "申请退款金额";
    private static final String COL_REFUND_STATUS = "退款状态";
    private static final String COL_REFUND_APPLY_TIME = "退款申请时间";
    private static final String COL_REFUND_SUCCESS_TIME = "退款成功时间";

    private static final String STATUS_REFUND = "REFUND";
    private static final String STATUS_REVOKED = "REVOKED";

    /**
     * 解析微信交易账单 CSV 文本。
     *
     * @param content     账单文件文本（UTF-8）
     * @param channelCode 渠道编码（WXPAY），写入解析结果
     * @param billDate    账单日期（yyyy-MM-dd），写入解析结果
     * @return 解析结果（支付/退款流水、行数统计、账单种类）
     */
    public ParsedBill parse(String content, String channelCode, String billDate) {
        ParsedBill result = new ParsedBill();
        if (!StringUtils.hasText(content)) {
            return result;
        }

        String text = stripBom(content);
        Map<String, Integer> headerIndex = null;

        for (String rawLine : text.split("\r?\n", -1)) {
            result.setTotalLines(result.getTotalLines() + 1);
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            List<String> fields = splitCsv(line);

            if (headerIndex == null) {
                Map<String, Integer> header = mapHeader(fields);
                if (header.containsKey(COL_TRADE_TIME) && header.containsKey(COL_WX_ORDER_NO)) {
                    headerIndex = header;
                    result.setBillKind(detectBillKind(header));
                    continue;
                }
                throw new BizException("账单文件格式不正确：缺少微信交易账单表头（交易时间/微信订单号）");
            }

            // 首字段不是交易时间的行：表头、汇总表头（总交易单数,...）、汇总数据行、旧版 % 汇总行，跳过
            Date tradeTime = parseTime(clean(firstField(fields)));
            if (tradeTime == null) {
                continue;
            }

            String tradeStatus = value(fields, headerIndex, COL_TRADE_STATUS);
            boolean refundRow = STATUS_REFUND.equalsIgnoreCase(tradeStatus)
                    || STATUS_REVOKED.equalsIgnoreCase(tradeStatus);

            BillRecord record = new BillRecord();
            record.setChannelCode(channelCode);
            record.setBillDate(billDate);
            record.setTradeTime(tradeTime);
            record.setRawLine(rawLine);

            if (refundRow) {
                if (!fillRefundRecord(record, fields, headerIndex, tradeStatus)) {
                    result.setBadLines(result.getBadLines() + 1);
                    continue;
                }
                result.getRefundRecords().add(record);
            } else {
                if (!fillPayRecord(record, fields, headerIndex)) {
                    result.setBadLines(result.getBadLines() + 1);
                    continue;
                }
                result.getPayRecords().add(record);
            }
        }

        if (headerIndex == null) {
            throw new BizException("账单文件格式不正确：未找到微信交易账单表头");
        }
        return result;
    }

    private boolean fillPayRecord(BillRecord record, List<String> fields, Map<String, Integer> headerIndex) {
        String serialNo = value(fields, headerIndex, COL_WX_ORDER_NO);
        String bizNo = value(fields, headerIndex, COL_MCH_ORDER_NO);
        Integer amount = parseAmount(
                value(fields, headerIndex, COL_ORDER_AMOUNT),
                value(fields, headerIndex, COL_SETTLE_AMOUNT),
                value(fields, headerIndex, COL_LEGACY_TOTAL_AMOUNT));
        if (!StringUtils.hasText(serialNo) || !StringUtils.hasText(bizNo) || amount == null) {
            return false;
        }
        record.setRecordType(BillRecordType.PAY.getType());
        record.setChannelSerialNo(serialNo);
        record.setBizNo(bizNo);
        record.setTotalAmount(amount);
        record.setTradeType(value(fields, headerIndex, COL_TRADE_TYPE));
        record.setTradeStatus(value(fields, headerIndex, COL_TRADE_STATUS));
        return true;
    }

    private boolean fillRefundRecord(BillRecord record, List<String> fields, Map<String, Integer> headerIndex,
                                     String tradeStatus) {
        // 退款行：退款单号优先；撤销行(REVOKED)无退款单号时回退到原支付订单号（撤销单与原支付单订单号一致）
        String serialNo = value(fields, headerIndex, COL_WX_REFUND_NO);
        String bizNo = value(fields, headerIndex, COL_MCH_REFUND_NO);
        if (!StringUtils.hasText(serialNo) || "0".equals(serialNo)) {
            serialNo = value(fields, headerIndex, COL_WX_ORDER_NO);
        }
        if (!StringUtils.hasText(bizNo) || "0".equals(bizNo)) {
            bizNo = value(fields, headerIndex, COL_MCH_ORDER_NO);
        }
        Integer refundAmount = parseAmount(
                value(fields, headerIndex, COL_REFUND_AMOUNT),
                value(fields, headerIndex, COL_APPLY_REFUND_AMOUNT));
        if (!StringUtils.hasText(serialNo) || !StringUtils.hasText(bizNo) || refundAmount == null) {
            return false;
        }
        record.setRecordType(BillRecordType.REFUND.getType());
        record.setChannelSerialNo(serialNo);
        record.setBizNo(bizNo);
        record.setRefundAmount(Math.abs(refundAmount));
        record.setTradeType(STATUS_REVOKED.equalsIgnoreCase(tradeStatus) ? STATUS_REVOKED : STATUS_REFUND);
        // 退款状态：REFUND/ALL 账单有「退款状态」列（SUCCESS/PROCESSING/FAIL/CHANGE），缺失时回退交易状态列
        String refundStatus = value(fields, headerIndex, COL_REFUND_STATUS);
        record.setTradeStatus(StringUtils.hasText(refundStatus) ? refundStatus : tradeStatus);
        record.setRefundApplyTime(parseTime(value(fields, headerIndex, COL_REFUND_APPLY_TIME)));
        record.setRefundSuccessTime(parseTime(value(fields, headerIndex, COL_REFUND_SUCCESS_TIME)));
        return true;
    }

    /**
     * 按表头列名建立 列名 -> 列下标 映射。
     */
    private Map<String, Integer> mapHeader(List<String> fields) {
        Map<String, Integer> header = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            String name = clean(fields.get(i));
            if (StringUtils.hasText(name) && !header.containsKey(name)) {
                header.put(name, i);
            }
        }
        return header;
    }

    /**
     * 依据表头列识别账单种类：含「退款申请时间」列为 REFUND 账单；含「微信退款单号」列为 ALL；否则 SUCCESS。
     */
    private String detectBillKind(Map<String, Integer> header) {
        if (header.containsKey(COL_REFUND_APPLY_TIME) || header.containsKey(COL_REFUND_SUCCESS_TIME)) {
            return "REFUND";
        }
        if (header.containsKey(COL_WX_REFUND_NO)) {
            return "ALL";
        }
        return "SUCCESS";
    }

    /**
     * 按下标取列值并清洗；列不存在或为空返回空串。
     */
    private String value(List<String> fields, Map<String, Integer> headerIndex, String columnName) {
        Integer index = headerIndex.get(columnName);
        if (index == null || index >= fields.size()) {
            return "";
        }
        return clean(fields.get(index));
    }

    private String firstField(List<String> fields) {
        return fields.isEmpty() ? "" : fields.get(0);
    }

    /**
     * 字段清洗：去空白、去除微信账单字段前的单个反引号前缀、去除包裹双引号。
     */
    private String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.startsWith("`")) {
            value = value.substring(1).trim();
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value.trim();
    }

    /**
     * 简单 CSV 行切分：支持双引号包裹与 "" 转义（微信账单本身使用反斜杠转义，引号兼容保护）。
     */
    private List<String> splitCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private Date parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(value.trim(), TIME_FORMATTER);
            return Date.from(dateTime.atZone(ZONE).toInstant());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 金额元转分：依次取第一个可解析的非空候选值，BigDecimal 精确乘 100；全部不可解析返回 null。
     */
    private Integer parseAmount(String... candidates) {
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            try {
                return new BigDecimal(candidate.trim()).movePointRight(2).intValueExact();
            } catch (NumberFormatException | ArithmeticException e) {
                // 当前候选列不可解析，尝试下一候选列
            }
        }
        return null;
    }

    private String stripBom(String content) {
        if (content != null && content.startsWith("\uFEFF")) {
            return content.substring(1);
        }
        return content;
    }
}
