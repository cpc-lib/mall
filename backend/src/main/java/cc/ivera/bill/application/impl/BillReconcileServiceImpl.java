package cc.ivera.bill.application.impl;

import cc.ivera.bill.application.BillReconcileService;
import cc.ivera.bill.application.parser.ParsedBill;
import cc.ivera.bill.application.parser.WxTradeBillParser;
import cc.ivera.bill.domain.enums.*;
import cc.ivera.bill.domain.model.BillImport;
import cc.ivera.bill.domain.model.BillLedgerSnapshot;
import cc.ivera.bill.domain.model.BillReconcileDiscrepancy;
import cc.ivera.bill.domain.model.BillReconcileDrilldown;
import cc.ivera.bill.domain.model.BillReconcileSummary;
import cc.ivera.bill.domain.model.BillRecord;
import cc.ivera.bill.domain.model.BillVerificationItem;
import cc.ivera.bill.domain.repository.BillImportRepository;
import cc.ivera.bill.domain.repository.BillReconcileDiscrepancyRepository;
import cc.ivera.bill.domain.repository.BillRecordRepository;
import cc.ivera.payment.domain.enums.PaymentOrderStatus;
import cc.ivera.payment.domain.enums.wxpay.WxTradeState;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.domain.repository.PaymentOrderRepository;
import cc.ivera.refund.domain.enums.RefundOrderStatus;
import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.refund.domain.repository.RefundOrderRepository;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import cc.ivera.shared.infrastructure.constant.DatePatterns;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.security.AuthPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 微信交易账单账账核对服务实现：渠道账(t_bill_record) vs 平台交易账(t_payment_order/t_refund_order)。
 *
 * <p>幂等门闩（由前置到兜底）：
 * <ol>
 *   <li>同文件 SHA-256 已存在 → 直接返回原批次；</li>
 *   <li>同渠道同账单日同账单种类(ALL/SUCCESS/REFUND)已存在批次 → 拒绝重复上传；
 *       已存在 ALL 账单时拒绝再传 SUCCESS/REFUND；已存在 SUCCESS/REFUND 时拒绝再传 ALL；
 *       同一账单日允许 SUCCESS 与 REFUND 各一份分别对账；</li>
 *   <li>分布式锁 bill-reconcile:WXPAY:{billDate}（等待 5s、租期 60s）防并发，锁内二次检查；</li>
 *   <li>数据库唯一约束 uk(file_hash)、uk(channel_code,bill_type,bill_date,bill_kind)、
 *       uk(import_id,discrepancy_type,biz_type,biz_no) 为最终防线；</li>
 *   <li>RECONCILED 批次重复对账直接返回现有结果；重跑(IMPORTED/FAILED)先删差异再重算。</li>
 * </ol>
 *
 * <p>支付按渠道交易号逐笔匹配，订单号只作辅助，保留一个订单 1:N 支付尝试；
 * 日切以 PaymentOrder.paidTime / RefundOrder.successTime 为平台入账时间，统一 Asia/Shanghai。
 * 对账范围按微信账单种类收窄：ALL 账单核对支付与退款；
 * SUCCESS 账单仅含支付成功行，只核对支付；REFUND 账单仅含退款行，只核对退款，
 * 避免把账单未覆盖的业务误报为「仅本地有」。
 */
@Service
@Slf4j
public class BillReconcileServiceImpl implements BillReconcileService {

    private static final String CHANNEL_WXPAY = "WXPAY";

    private static final String BILL_CATEGORY_TRADE = "tradebill";

    private static final String LOCK_KEY_PREFIX = "bill-reconcile:WXPAY:";

    private static final long LOCK_WAIT_MILLIS = 5000L;

    private static final long LOCK_LEASE_MILLIS = 60000L;

    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    private static final ZoneId BILL_ZONE = ZoneId.of("Asia/Shanghai");

    private final BillImportRepository billImportRepository;

    private final BillRecordRepository billRecordRepository;

    private final BillReconcileDiscrepancyRepository discrepancyRepository;

    private final PaymentOrderRepository paymentOrderRepository;

    private final RefundOrderRepository refundOrderRepository;

    private final DistributedLockTemplate lockTemplate;

    private final TransactionTemplate transactionTemplate;

    private final WxTradeBillParser billParser = new WxTradeBillParser();

    public BillReconcileServiceImpl(
        BillImportRepository billImportRepository,
        BillRecordRepository billRecordRepository,
        BillReconcileDiscrepancyRepository discrepancyRepository,
        PaymentOrderRepository paymentOrderRepository,
        RefundOrderRepository refundOrderRepository,
        DistributedLockTemplate lockTemplate,
        TransactionTemplate transactionTemplate
    ) {
        this.billImportRepository = billImportRepository;
        this.billRecordRepository = billRecordRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.refundOrderRepository = refundOrderRepository;
        this.lockTemplate = lockTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    // ==================== 上传 ====================

    @Override
    public BillImport uploadBill(MultipartFile file, String billDate, String billType) {
        if (file == null || file.isEmpty()) {
            throw new BizException("账单文件不能为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException("账单文件大小不能超过10MB");
        }
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)
            || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BizException("仅支持微信交易账单 XLSX 文件（.xlsx）");
        }
        if (StringUtils.hasText(billType) && !BILL_CATEGORY_TRADE.equalsIgnoreCase(billType.trim())) {
            throw new BizException("仅支持微信交易账单(tradebill)，资金账单暂不支持上传对账");
        }
        validateBillDate(billDate);
        String normalizedDate = billDate.trim();

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException("读取账单文件失败", e);
        }
        String fileHash = sha256Hex(fileBytes);

        // 快速幂等路径：同文件直接返回原批次
        BillImport existingByHash = billImportRepository.findByFileHash(fileHash);
        if (existingByHash != null) {
            log.info("账单文件已导入过，幂等返回原批次 importNo={}, billDate={}", existingByHash.getImportNo(), normalizedDate);
            return existingByHash;
        }

        // 锁外先做解析校验（解析失败/空账单不入库）；账单种类由表头识别
        ParsedBill parsed = billParser.parseXlsx(fileBytes, CHANNEL_WXPAY, normalizedDate);
        if (parsed.validRecordCount() == 0) {
            throw new BizException("账单解析结果为空，未找到有效的支付/退款记录");
        }
        BillKind billKind = BillKind.of(parsed.getBillKind());

        // 同账单日 + 账单种类幂等校验（锁外快速拒绝）
        assertBillDateKindNotExists(normalizedDate, billKind);

        try {
            return lockTemplate.execute(LOCK_KEY_PREFIX + normalizedDate, LOCK_WAIT_MILLIS, LOCK_LEASE_MILLIS,
                () -> doUploadInLock(file.getOriginalFilename(), normalizedDate, fileHash, billKind, parsed));
        } catch (BizException e) {
            if (Objects.equals("系统繁忙，请勿重复提交", e.getMessage())) {
                throw new BizException("该账单正在对账处理中或已处理完成，请勿重复提交");
            }
            throw e;
        }
    }

    private BillImport doUploadInLock(String fileName, String billDate, String fileHash,
                                      BillKind billKind, ParsedBill parsed) {
        // 锁内二次检查
        BillImport byHash = billImportRepository.findByFileHash(fileHash);
        if (byHash != null) {
            return byHash;
        }
        assertBillDateKindNotExists(billDate, billKind);

        BillImport billImport = new BillImport();
        billImport.setImportNo(generateImportNo());
        billImport.setChannelCode(CHANNEL_WXPAY);
        billImport.setBillType(BillType.TRADE.getType());
        billImport.setBillKind(billKind.getType());
        billImport.setBillDate(billDate);
        billImport.setFileName(fileName);
        billImport.setFileHash(fileHash);
        billImport.setTotalRecordCount(parsed.validRecordCount());
        billImport.setPayRecordCount(parsed.getPayRecords().size());
        billImport.setRefundRecordCount(parsed.getRefundRecords().size());
        billImport.setBadLineCount(parsed.getBadLines());
        billImport.setMatchedCount(0);
        billImport.setDiscrepancyCount(0);
        billImport.setStatus(BillImportStatus.IMPORTED.getType());

        // 事务1：批次 + 流水入库
        transactionTemplate.executeWithoutResult(status -> {
            billImportRepository.save(billImport);
            for (BillRecord record : parsed.getPayRecords()) {
                record.setImportId(billImport.getId());
                billRecordRepository.save(record);
            }
            for (BillRecord record : parsed.getRefundRecords()) {
                record.setImportId(billImport.getId());
                billRecordRepository.save(record);
            }
        });

        // 事务2：自动对账（失败标记 FAILED，批次与流水保留，可重新对账）
        try {
            reconcileInTransaction(billImport.getId());
        } catch (Exception e) {
            markFailed(billImport.getId(), e);
            throw e instanceof BizException ? (BizException) e
                : new BizException("账单对账处理失败：" + e.getMessage(), e);
        }
        return billImportRepository.findById(billImport.getId());
    }

    // ==================== 对账 ====================

    @Override
    public BillImport reconcileByImportNo(String importNo) {
        BillImport billImport = requireByImportNo(importNo);
        // 幂等：已对账直接返回现有结果
        if (BillImportStatus.RECONCILED.getType().equals(billImport.getStatus())) {
            return billImport;
        }
        try {
            return lockTemplate.execute(LOCK_KEY_PREFIX + billImport.getBillDate(),
                LOCK_WAIT_MILLIS, LOCK_LEASE_MILLIS, () -> {
                    BillImport locked = billImportRepository.findById(billImport.getId());
                    if (BillImportStatus.RECONCILED.getType().equals(locked.getStatus())) {
                        return locked;
                    }
                    reconcileInTransaction(locked.getId());
                    return billImportRepository.findById(locked.getId());
                });
        } catch (BizException e) {
            if (Objects.equals("系统繁忙，请勿重复提交", e.getMessage())) {
                throw new BizException("该账单正在对账处理中或已处理完成，请勿重复提交");
            }
            throw e;
        }
    }

    /**
     * 对账核心（单个事务）：重跑前先按 import_id 删除旧差异单，保证结果确定、不重复。
     */
    private void reconcileInTransaction(Long importId) {
        transactionTemplate.executeWithoutResult(status -> {
            BillImport billImport = billImportRepository.findById(importId);
            if (billImport == null) {
                throw new BizException("账单导入批次不存在");
            }

            discrepancyRepository.deleteByImport(importId);

            List<BillRecord> records = billRecordRepository.listByImport(importId, null);
            List<BillRecord> payRecords = filterByType(records, BillRecordType.PAY);
            List<BillRecord> refundRecords = filterByType(records, BillRecordType.REFUND);

            // 对账范围按账单种类收窄：SUCCESS 账单仅含支付行、REFUND 账单仅含退款行，
            // 未覆盖的业务方向不参与核对（避免误报「仅本地有」）；ALL 账单双向核对
            BillKind billKind = BillKind.of(billImport.getBillKind());
            boolean checkPay = billKind != BillKind.REFUND;
            boolean checkRefund = billKind != BillKind.SUCCESS;

            List<BillReconcileDiscrepancy> discrepancies = new ArrayList<>();
            int matchedCount = 0;
            if (checkPay) {
                matchedCount += reconcilePayRecords(billImport, payRecords, discrepancies);
            }
            if (checkRefund) {
                matchedCount += reconcileRefundRecords(billImport, refundRecords, discrepancies);
            }

            // DB 唯一键为(import_id, discrepancy_type, biz_type, biz_no)。异常账单可能重复多行，
            // 落库前按同一幂等键收敛为一条差异，避免重复行反而导致整个对账事务失败。
            Map<String, BillReconcileDiscrepancy> uniqueDiscrepancies = new LinkedHashMap<>();
            for (BillReconcileDiscrepancy discrepancy : discrepancies) {
                String key = discrepancy.getDiscrepancyType() + "|" + discrepancy.getBizType() + "|" + discrepancy.getBizNo();
                uniqueDiscrepancies.putIfAbsent(key, discrepancy);
            }
            for (BillReconcileDiscrepancy discrepancy : uniqueDiscrepancies.values()) {
                discrepancy.setImportId(importId);
                discrepancy.setBillDate(billImport.getBillDate());
                discrepancy.setStatus(BillDiscrepancyStatus.OPEN.getType());
                discrepancyRepository.save(discrepancy);
            }

            billImport.setMatchedCount(matchedCount);
            billImport.setDiscrepancyCount(uniqueDiscrepancies.size());
            billImport.setStatus(BillImportStatus.RECONCILED.getType());
            billImport.setReconcileTime(new Date());
            billImport.setErrorMessage(null);
            billImportRepository.update(billImport);
        });
    }

    /**
     * 支付账账核对：渠道 PAY 行 vs 平台 t_payment_order。
     * 主匹配键为微信订单号(channel_serial_no) ↔ channel_order_no；order_no 仅用于辅助定位，
     * 从而保留“一个业务订单 1:N 支付尝试”的真实账本语义，不再按订单号折叠。
     */
    private int reconcilePayRecords(BillImport billImport, List<BillRecord> payRecords,
                                    List<BillReconcileDiscrepancy> discrepancies) {
        Set<String> billOrderNos = payRecords.stream().map(BillRecord::getBizNo)
            .filter(StringUtils::hasText).collect(Collectors.toSet());
        Set<String> billSerialNos = payRecords.stream().map(BillRecord::getChannelSerialNo)
            .filter(StringUtils::hasText).collect(Collectors.toSet());

        List<PaymentOrder> byOrders = paymentOrderRepository.listByChannelAndOrderNos(CHANNEL_WXPAY, billOrderNos);
        List<PaymentOrder> bySerials = paymentOrderRepository.listByChannelAndChannelOrderNos(CHANNEL_WXPAY, billSerialNos);
        Map<String, List<PaymentOrder>> localByOrder = byOrders.stream()
            .collect(Collectors.groupingBy(PaymentOrder::getOrderNo));
        Map<String, List<PaymentOrder>> localBySerial = bySerials.stream()
            .filter(p -> StringUtils.hasText(p.getChannelOrderNo()))
            .collect(Collectors.groupingBy(PaymentOrder::getChannelOrderNo));

        Set<String> accountedPaymentNos = new HashSet<>();
        Set<String> seenBillSerials = new HashSet<>();
        Set<String> localDuplicateKeys = new HashSet<>();
        int matched = 0;

        for (BillRecord record : payRecords) {
            boolean discrepancyFound = false;
            String serialNo = record.getChannelSerialNo();
            if (StringUtils.hasText(serialNo) && !seenBillSerials.add(serialNo)) {
                discrepancies.add(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_CHANNEL_DUPLICATE,
                    BillRecordType.PAY, record.getBizNo(), serialNo, record.getTotalAmount(), null,
                    record.getTradeStatus(), null));
                continue;
            }

            List<PaymentOrder> exactList = localBySerial.getOrDefault(serialNo, Collections.emptyList());
            PaymentOrder local = choosePayment(exactList, accountedPaymentNos);
            if (exactList.size() > 1 && localDuplicateKeys.add(serialNo)) {
                discrepancies.add(withLocalPayment(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_LOCAL_DUPLICATE,
                    BillRecordType.PAY, record.getBizNo(), serialNo, record.getTotalAmount(),
                    paymentAmount(local), record.getTradeStatus(), local == null ? null : local.getStatus()), local));
                discrepancyFound = true;
            }

            if (local == null) {
                local = choosePayment(localByOrder.get(record.getBizNo()), accountedPaymentNos);
                if (local == null) {
                    discrepancies.add(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_CHANNEL_ONLY,
                        BillRecordType.PAY, record.getBizNo(), serialNo,
                        record.getTotalAmount(), null, record.getTradeStatus(), null));
                    continue;
                }
                accountedPaymentNos.add(local.getPaymentNo());
                if (PaymentOrderStatus.SUCCESS.getType().equals(local.getStatus())) {
                    discrepancies.add(withLocalPayment(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_SERIAL_MISMATCH,
                        BillRecordType.PAY, record.getBizNo(), serialNo, record.getTotalAmount(),
                        paymentAmount(local), record.getTradeStatus(), local.getStatus()), local));
                } else {
                    discrepancies.add(withLocalPayment(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_STATUS_MISMATCH,
                        BillRecordType.PAY, record.getBizNo(), serialNo, record.getTotalAmount(),
                        paymentAmount(local), record.getTradeStatus(), local.getStatus()), local));
                }
                continue;
            }

            accountedPaymentNos.add(local.getPaymentNo());
            if (!Objects.equals(record.getBizNo(), local.getOrderNo())) {
                discrepancies.add(withLocalPayment(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_BIZ_NO_MISMATCH,
                    BillRecordType.PAY, record.getBizNo(), serialNo, record.getTotalAmount(),
                    paymentAmount(local), record.getTradeStatus(), local.getStatus()), local));
                discrepancyFound = true;
            }
            if (!Objects.equals(record.getTotalAmount(), local.getPaidAmount())) {
                discrepancies.add(withLocalPayment(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_AMOUNT_MISMATCH,
                    BillRecordType.PAY, record.getBizNo(), serialNo, record.getTotalAmount(),
                    local.getPaidAmount(), record.getTradeStatus(), local.getStatus()), local));
                discrepancyFound = true;
            }
            if (!PaymentOrderStatus.SUCCESS.getType().equals(local.getStatus())) {
                discrepancies.add(withLocalPayment(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_STATUS_MISMATCH,
                    BillRecordType.PAY, record.getBizNo(), serialNo, record.getTotalAmount(),
                    paymentAmount(local), record.getTradeStatus(), local.getStatus()), local));
                discrepancyFound = true;
            }
            if (!discrepancyFound) {
                matched++;
            }
        }

        Date dayStart = atStartOfDay(billImport.getBillDate());
        Date dayEnd = atStartOfNextDay(billImport.getBillDate());
        List<PaymentOrder> localDayPayments = paymentOrderRepository.listSuccessByChannelAndPaidTimeRange(
            CHANNEL_WXPAY, dayStart, dayEnd);
        Map<String, Long> localSerialCounts = localDayPayments.stream()
            .filter(p -> StringUtils.hasText(p.getChannelOrderNo()))
            .collect(Collectors.groupingBy(PaymentOrder::getChannelOrderNo, Collectors.counting()));
        Set<String> reportedLocalDuplicateBiz = new HashSet<>();
        for (PaymentOrder payment : localDayPayments) {
            String serialNo = payment.getChannelOrderNo();
            if (StringUtils.hasText(serialNo) && localSerialCounts.getOrDefault(serialNo, 0L) > 1
                && reportedLocalDuplicateBiz.add(payment.getOrderNo())) {
                discrepancies.add(withLocalPayment(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_LOCAL_DUPLICATE,
                    BillRecordType.PAY, payment.getOrderNo(), serialNo,
                    null, payment.getPaidAmount(), null, payment.getStatus()), payment));
            }
            if (accountedPaymentNos.contains(payment.getPaymentNo())) {
                continue;
            }
            boolean channelContains = StringUtils.hasText(serialNo)
                ? billSerialNos.contains(serialNo)
                : billOrderNos.contains(payment.getOrderNo());
            if (!channelContains) {
                discrepancies.add(withLocalPayment(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_LOCAL_ONLY,
                    BillRecordType.PAY, payment.getOrderNo(), serialNo,
                    null, payment.getPaidAmount(), null, payment.getStatus()), payment));
            }
        }
        return matched;
    }

    /**
     * 退款账账核对：渠道 REFUND 行 vs 平台 t_refund_order，商户退款单号 refund_no 是主匹配键。
     */
    private int reconcileRefundRecords(BillImport billImport, List<BillRecord> refundRecords,
                                       List<BillReconcileDiscrepancy> discrepancies) {
        Set<String> billRefundNos = refundRecords.stream().map(BillRecord::getBizNo)
            .filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, RefundOrder> localRefundMap = refundOrderRepository.listByRefundNos(billRefundNos).stream()
            .collect(Collectors.toMap(RefundOrder::getRefundNo, Function.identity(), (a, b) -> a));

        Set<String> seenRefundNos = new HashSet<>();
        int matched = 0;
        for (BillRecord record : refundRecords) {
            if (!seenRefundNos.add(record.getBizNo())) {
                discrepancies.add(buildDiscrepancy(billImport, BillDiscrepancyType.REFUND_CHANNEL_DUPLICATE,
                    BillRecordType.REFUND, record.getBizNo(), record.getChannelSerialNo(),
                    record.getRefundAmount(), null, record.getTradeStatus(), null));
                continue;
            }
            RefundOrder local = localRefundMap.get(record.getBizNo());
            boolean discrepancyFound = false;
            if (local == null) {
                discrepancies.add(buildDiscrepancy(billImport, BillDiscrepancyType.REFUND_CHANNEL_ONLY,
                    BillRecordType.REFUND, record.getBizNo(), record.getChannelSerialNo(),
                    record.getRefundAmount(), null, record.getTradeStatus(), null));
                continue;
            }
            if (!Objects.equals(record.getRefundAmount(), local.getRefundAmount())) {
                discrepancies.add(withLocalRefund(buildDiscrepancy(billImport, BillDiscrepancyType.REFUND_AMOUNT_MISMATCH,
                    BillRecordType.REFUND, record.getBizNo(), record.getChannelSerialNo(),
                    record.getRefundAmount(), local.getRefundAmount(),
                    record.getTradeStatus(), local.getStatus()), local));
                discrepancyFound = true;
            }
            boolean channelSettled = isChannelRefundSettled(record);
            boolean localSettled = RefundOrderStatus.SUCCESS.getType().equals(local.getStatus());
            if (channelSettled != localSettled) {
                discrepancies.add(withLocalRefund(buildDiscrepancy(billImport, BillDiscrepancyType.REFUND_STATUS_MISMATCH,
                    BillRecordType.REFUND, record.getBizNo(), record.getChannelSerialNo(),
                    record.getRefundAmount(), local.getRefundAmount(),
                    record.getTradeStatus(), local.getStatus()), local));
                discrepancyFound = true;
            }
            if (!discrepancyFound) {
                matched++;
            }
        }

        Date dayStart = atStartOfDay(billImport.getBillDate());
        Date dayEnd = atStartOfNextDay(billImport.getBillDate());
        for (RefundOrder refund : listLocalWxSuccessRefunds(dayStart, dayEnd)) {
            if (!billRefundNos.contains(refund.getRefundNo())) {
                discrepancies.add(withLocalRefund(buildDiscrepancy(billImport, BillDiscrepancyType.REFUND_LOCAL_ONLY,
                    BillRecordType.REFUND, refund.getRefundNo(), null,
                    null, refund.getRefundAmount(), null, refund.getStatus()), refund));
            }
        }
        return matched;
    }

    private PaymentOrder choosePayment(List<PaymentOrder> candidates, Set<String> accountedPaymentNos) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (PaymentOrder candidate : candidates) {
            if (!accountedPaymentNos.contains(candidate.getPaymentNo())
                && PaymentOrderStatus.SUCCESS.getType().equals(candidate.getStatus())) {
                return candidate;
            }
        }
        for (PaymentOrder candidate : candidates) {
            if (!accountedPaymentNos.contains(candidate.getPaymentNo())) {
                return candidate;
            }
        }
        return null;
    }

    private Integer paymentAmount(PaymentOrder payment) {
        if (payment == null) {
            return null;
        }
        return payment.getPaidAmount() != null ? payment.getPaidAmount() : payment.getRequestAmount();
    }

    private List<RefundOrder> listLocalWxSuccessRefunds(Date dayStart, Date dayEnd) {
        List<RefundOrder> successRefunds = refundOrderRepository.listSuccessBySuccessTimeRange(dayStart, dayEnd);
        Set<String> paymentNos = successRefunds.stream().map(RefundOrder::getPaymentNo)
            .filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, PaymentOrder> payments = paymentOrderRepository.listByPaymentNos(paymentNos).stream()
            .collect(Collectors.toMap(PaymentOrder::getPaymentNo, Function.identity(), (a, b) -> a));
        return successRefunds.stream()
            .filter(r -> {
                PaymentOrder payment = payments.get(r.getPaymentNo());
                return payment != null && CHANNEL_WXPAY.equals(payment.getChannel());
            })
            .collect(Collectors.toList());
    }

    private BillReconcileDiscrepancy withLocalPayment(BillReconcileDiscrepancy discrepancy, PaymentOrder local) {
        if (local != null) {
            discrepancy.setLocalBizNo(local.getOrderNo());
            discrepancy.setLocalLedgerNo(local.getPaymentNo());
            discrepancy.setLocalSerialNo(local.getChannelOrderNo());
        }
        return discrepancy;
    }

    private BillReconcileDiscrepancy withLocalRefund(BillReconcileDiscrepancy discrepancy, RefundOrder local) {
        if (local != null) {
            discrepancy.setLocalBizNo(local.getRefundNo());
            discrepancy.setLocalLedgerNo(local.getRefundNo());
        }
        return discrepancy;
    }

    private BillReconcileDiscrepancy buildDiscrepancy(BillImport billImport, BillDiscrepancyType type,
                                                      BillRecordType bizType, String bizNo,
                                                      String channelSerialNo, Integer channelAmount,
                                                      Integer localAmount, String channelStatus,
                                                      String localStatus) {
        BillReconcileDiscrepancy discrepancy = new BillReconcileDiscrepancy();
        discrepancy.setImportId(billImport.getId());
        discrepancy.setBillDate(billImport.getBillDate());
        discrepancy.setBizType(bizType.getType());
        discrepancy.setDiscrepancyType(type.getType());
        discrepancy.setBizNo(bizNo);
        discrepancy.setChannelSerialNo(channelSerialNo);
        discrepancy.setChannelAmount(channelAmount);
        discrepancy.setLocalAmount(localAmount);
        discrepancy.setChannelStatus(channelStatus);
        discrepancy.setLocalStatus(localStatus);
        return discrepancy;
    }

    private void markFailed(Long importId, Exception error) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                BillImport billImport = billImportRepository.findById(importId);
                if (billImport != null) {
                    billImport.setStatus(BillImportStatus.FAILED.getType());
                    String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    billImport.setErrorMessage(truncate(message, 900));
                    billImportRepository.update(billImport);
                }
            });
        } catch (Exception markError) {
            log.warn("标记账单批次 FAILED 失败 importId={}", importId, markError);
        }
    }

    // ==================== 查询与人工处理 ====================

    @Override
    public BillImport getByImportNo(String importNo) {
        return requireByImportNo(importNo);
    }

    @Override
    public List<BillImport> listImports(String billDate) {
        String billDateValue = StringUtils.hasText(billDate) ? billDate.trim() : null;
        return billImportRepository.listByDate(billDateValue);
    }

    @Override
    public List<BillRecord> listRecords(String importNo, String recordType) {
        BillImport billImport = requireByImportNo(importNo);
        String recordTypeValue = StringUtils.hasText(recordType) ? recordType.trim().toUpperCase() : null;
        return billRecordRepository.listByImport(billImport.getId(), recordTypeValue);
    }

    @Override
    public List<BillReconcileDiscrepancy> listDiscrepancies(String importNo, String bizType,
                                                            String discrepancyType, String status) {
        BillImport billImport = requireByImportNo(importNo);
        String bizTypeValue = StringUtils.hasText(bizType) ? bizType.trim().toUpperCase() : null;
        String discrepancyTypeValue = StringUtils.hasText(discrepancyType) ? discrepancyType.trim() : null;
        String statusValue = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        return discrepancyRepository.listByImport(
            billImport.getId(), bizTypeValue, discrepancyTypeValue, statusValue);
    }

    @Override
    public BillReconcileSummary getSummary(String importNo) {
        BillImport billImport = requireByImportNo(importNo);
        BillKind billKind = BillKind.of(billImport.getBillKind());
        boolean checkPay = billKind != BillKind.REFUND;
        boolean checkRefund = billKind != BillKind.SUCCESS;

        List<BillRecord> records = billRecordRepository.listByImport(billImport.getId(), null);
        List<BillRecord> channelPays = checkPay
            ? filterByType(records, BillRecordType.PAY) : Collections.emptyList();
        List<BillRecord> channelRefunds = checkRefund
            ? filterByType(records, BillRecordType.REFUND).stream()
                .filter(this::isChannelRefundSettled).collect(Collectors.toList())
            : Collections.emptyList();

        Date dayStart = atStartOfDay(billImport.getBillDate());
        Date dayEnd = atStartOfNextDay(billImport.getBillDate());
        List<PaymentOrder> localPays = checkPay
            ? paymentOrderRepository.listSuccessByChannelAndPaidTimeRange(CHANNEL_WXPAY, dayStart, dayEnd)
            : Collections.emptyList();
        List<RefundOrder> localRefunds = checkRefund
            ? listLocalWxSuccessRefunds(dayStart, dayEnd) : Collections.emptyList();

        long channelPayAmount = channelPays.stream().map(BillRecord::getTotalAmount)
            .filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long localPayAmount = localPays.stream().map(PaymentOrder::getPaidAmount)
            .filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long channelRefundAmount = channelRefunds.stream().map(BillRecord::getRefundAmount)
            .filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long localRefundAmount = localRefunds.stream().map(RefundOrder::getRefundAmount)
            .filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long channelNet = channelPayAmount - channelRefundAmount;
        long localNet = localPayAmount - localRefundAmount;

        List<BillReconcileDiscrepancy> allDiscrepancies = discrepancyRepository.listByImport(
            billImport.getId(), null, null, null);
        int openCount = (int) allDiscrepancies.stream()
            .filter(d -> BillDiscrepancyStatus.OPEN.getType().equals(d.getStatus())).count();

        BillReconcileSummary summary = new BillReconcileSummary();
        summary.setImportNo(billImport.getImportNo());
        summary.setBillDate(billImport.getBillDate());
        summary.setChannelCode(billImport.getChannelCode());
        summary.setBillKind(billImport.getBillKind());
        summary.setChannelPayCount(channelPays.size());
        summary.setLocalPayCount(localPays.size());
        summary.setChannelPayAmount(channelPayAmount);
        summary.setLocalPayAmount(localPayAmount);
        summary.setChannelRefundCount(channelRefunds.size());
        summary.setLocalRefundCount(localRefunds.size());
        summary.setChannelRefundAmount(channelRefundAmount);
        summary.setLocalRefundAmount(localRefundAmount);
        summary.setChannelNetAmount(channelNet);
        summary.setLocalNetAmount(localNet);
        summary.setNetDifference(channelNet - localNet);
        summary.setMatchedCount(billImport.getMatchedCount() == null ? 0 : billImport.getMatchedCount());
        summary.setDiscrepancyCount(allDiscrepancies.size());
        summary.setOpenDiscrepancyCount(openCount);
        summary.setBalanced(channelPays.size() == localPays.size()
            && channelRefunds.size() == localRefunds.size()
            && channelPayAmount == localPayAmount
            && channelRefundAmount == localRefundAmount
            && channelNet == localNet
            && openCount == 0);
        return summary;
    }

    @Override
    public BillReconcileDrilldown getDrilldown(Long discrepancyId) {
        if (discrepancyId == null) {
            throw new BizException("差异单ID不能为空");
        }
        BillReconcileDiscrepancy discrepancy = discrepancyRepository.findById(discrepancyId);
        if (discrepancy == null) {
            throw new BizException("差异单不存在或已被删除");
        }

        BillReconcileDrilldown drilldown = new BillReconcileDrilldown();
        drilldown.setDiscrepancy(discrepancy);

        List<BillRecord> channelRecords = billRecordRepository.listByImport(
            discrepancy.getImportId(), discrepancy.getBizType());
        List<BillRecord> matchedChannelRecords = channelRecords.stream()
            .filter(record -> sameText(record.getBizNo(), discrepancy.getBizNo())
                || sameText(record.getChannelSerialNo(), discrepancy.getChannelSerialNo()))
            .collect(Collectors.toList());
        drilldown.setChannelRecords(matchedChannelRecords);

        List<BillLedgerSnapshot> localLedgers = BillRecordType.PAY.getType().equals(discrepancy.getBizType())
            ? loadPaymentDrilldown(discrepancy)
            : loadRefundDrilldown(discrepancy);
        drilldown.setLocalLedgers(localLedgers);

        BillRecord channel = chooseChannelRecord(matchedChannelRecords, discrepancy);
        BillLedgerSnapshot local = chooseLocalLedger(localLedgers, discrepancy);
        drilldown.setVerificationItems(buildVerificationItems(discrepancy.getBizType(), channel, local));
        return drilldown;
    }

    private List<BillLedgerSnapshot> loadPaymentDrilldown(BillReconcileDiscrepancy discrepancy) {
        Map<String, PaymentOrder> candidates = new LinkedHashMap<>();
        if (StringUtils.hasText(discrepancy.getLocalLedgerNo())) {
            addPaymentCandidate(candidates, paymentOrderRepository.findByPaymentNo(discrepancy.getLocalLedgerNo()));
        }
        Set<String> serialNos = new LinkedHashSet<>();
        addText(serialNos, discrepancy.getLocalSerialNo());
        addText(serialNos, discrepancy.getChannelSerialNo());
        if (!serialNos.isEmpty()) {
            for (PaymentOrder payment : paymentOrderRepository.listByChannelAndChannelOrderNos(CHANNEL_WXPAY, serialNos)) {
                addPaymentCandidate(candidates, payment);
            }
        }
        Set<String> orderNos = new LinkedHashSet<>();
        addText(orderNos, discrepancy.getLocalBizNo());
        addText(orderNos, discrepancy.getBizNo());
        if (!orderNos.isEmpty()) {
            for (PaymentOrder payment : paymentOrderRepository.listByChannelAndOrderNos(CHANNEL_WXPAY, orderNos)) {
                addPaymentCandidate(candidates, payment);
            }
        }
        return candidates.values().stream().map(this::toPaymentSnapshot).collect(Collectors.toList());
    }

    private List<BillLedgerSnapshot> loadRefundDrilldown(BillReconcileDiscrepancy discrepancy) {
        Set<String> refundNos = new LinkedHashSet<>();
        addText(refundNos, discrepancy.getLocalLedgerNo());
        addText(refundNos, discrepancy.getLocalBizNo());
        addText(refundNos, discrepancy.getBizNo());
        List<RefundOrder> refunds = refundOrderRepository.listByRefundNos(refundNos);
        Set<String> paymentNos = refunds.stream().map(RefundOrder::getPaymentNo)
            .filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, PaymentOrder> payments = paymentOrderRepository.listByPaymentNos(paymentNos).stream()
            .collect(Collectors.toMap(PaymentOrder::getPaymentNo, Function.identity(), (a, b) -> a));
        List<BillLedgerSnapshot> result = new ArrayList<>();
        for (RefundOrder refund : refunds) {
            BillLedgerSnapshot snapshot = new BillLedgerSnapshot();
            snapshot.setLedgerType(BillRecordType.REFUND.getType());
            snapshot.setLedgerNo(refund.getRefundNo());
            snapshot.setBizNo(refund.getRefundNo());
            snapshot.setOrderNo(refund.getOrderNo());
            snapshot.setAmount(refund.getRefundAmount());
            snapshot.setStatus(refund.getStatus());
            snapshot.setOccurredTime(refund.getSuccessTime());
            snapshot.setSourcePaymentNo(refund.getPaymentNo());
            PaymentOrder sourcePayment = payments.get(refund.getPaymentNo());
            if (sourcePayment != null) {
                snapshot.setChannel(sourcePayment.getChannel());
            }
            result.add(snapshot);
        }
        return result;
    }

    private BillLedgerSnapshot toPaymentSnapshot(PaymentOrder payment) {
        BillLedgerSnapshot snapshot = new BillLedgerSnapshot();
        snapshot.setLedgerType(BillRecordType.PAY.getType());
        snapshot.setLedgerNo(payment.getPaymentNo());
        snapshot.setBizNo(payment.getOrderNo());
        snapshot.setOrderNo(payment.getOrderNo());
        snapshot.setChannel(payment.getChannel());
        snapshot.setChannelSerialNo(payment.getChannelOrderNo());
        snapshot.setAmount(paymentAmount(payment));
        snapshot.setStatus(payment.getStatus());
        snapshot.setOccurredTime(payment.getPaidTime());
        return snapshot;
    }

    private void addPaymentCandidate(Map<String, PaymentOrder> candidates, PaymentOrder payment) {
        if (payment != null && StringUtils.hasText(payment.getPaymentNo())) {
            candidates.putIfAbsent(payment.getPaymentNo(), payment);
        }
    }

    private void addText(Set<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value);
        }
    }

    private BillRecord chooseChannelRecord(List<BillRecord> records, BillReconcileDiscrepancy discrepancy) {
        for (BillRecord record : records) {
            if (sameText(record.getChannelSerialNo(), discrepancy.getChannelSerialNo())) {
                return record;
            }
        }
        return records.isEmpty() ? null : records.get(0);
    }

    private BillLedgerSnapshot chooseLocalLedger(List<BillLedgerSnapshot> ledgers,
                                                  BillReconcileDiscrepancy discrepancy) {
        for (BillLedgerSnapshot ledger : ledgers) {
            if (sameText(ledger.getLedgerNo(), discrepancy.getLocalLedgerNo())) {
                return ledger;
            }
        }
        for (BillLedgerSnapshot ledger : ledgers) {
            if (sameText(ledger.getChannelSerialNo(), discrepancy.getLocalSerialNo())
                || sameText(ledger.getChannelSerialNo(), discrepancy.getChannelSerialNo())) {
                return ledger;
            }
        }
        for (BillLedgerSnapshot ledger : ledgers) {
            if (sameText(ledger.getBizNo(), discrepancy.getLocalBizNo())
                || sameText(ledger.getBizNo(), discrepancy.getBizNo())) {
                return ledger;
            }
        }
        return ledgers.isEmpty() ? null : ledgers.get(0);
    }

    private List<BillVerificationItem> buildVerificationItems(String bizType, BillRecord channel,
                                                               BillLedgerSnapshot local) {
        List<BillVerificationItem> items = new ArrayList<>();
        String channelBizNo = channel == null ? null : channel.getBizNo();
        String channelSerialNo = channel == null ? null : channel.getChannelSerialNo();
        Integer channelAmount = channel == null ? null
            : (BillRecordType.PAY.getType().equals(bizType) ? channel.getTotalAmount() : channel.getRefundAmount());
        String channelStatus = channel == null ? null : channel.getTradeStatus();

        items.add(verification("业务单号", channelBizNo, local == null ? null : local.getBizNo(),
            comparableEquals(channelBizNo, local == null ? null : local.getBizNo())));
        items.add(verification("渠道流水号", channelSerialNo, local == null ? null : local.getChannelSerialNo(),
            comparableEquals(channelSerialNo, local == null ? null : local.getChannelSerialNo())));
        items.add(verification("金额", formatFen(channelAmount), formatFen(local == null ? null : local.getAmount()),
            comparableEquals(channelAmount, local == null ? null : local.getAmount())));

        Boolean statusMatched = null;
        if (channel != null && local != null) {
            if (BillRecordType.PAY.getType().equals(bizType)) {
                statusMatched = WxTradeState.SUCCESS.getType().equalsIgnoreCase(channelStatus)
                    && PaymentOrderStatus.SUCCESS.getType().equals(local.getStatus());
            } else {
                statusMatched = isChannelRefundSettled(channel)
                    && RefundOrderStatus.SUCCESS.getType().equals(local.getStatus());
            }
        }
        items.add(verification("状态", channelStatus, local == null ? null : local.getStatus(), statusMatched));
        return items;
    }

    private BillVerificationItem verification(String field, String channelValue, String localValue, Boolean matched) {
        BillVerificationItem item = new BillVerificationItem();
        item.setField(field);
        item.setChannelValue(channelValue);
        item.setLocalValue(localValue);
        item.setMatched(matched);
        return item;
    }

    private Boolean comparableEquals(Object left, Object right) {
        return left == null || right == null ? null : Objects.equals(left, right);
    }

    private boolean sameText(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && Objects.equals(left, right);
    }

    private String formatFen(Integer amount) {
        if (amount == null) {
            return null;
        }
        return String.format(Locale.ROOT, "%d分 (¥%.2f)", amount, amount / 100.0D);
    }

    private boolean isChannelRefundSettled(BillRecord record) {
        if (record == null || !StringUtils.hasText(record.getTradeStatus())) {
            return false;
        }
        String status = record.getTradeStatus();
        return WxTradeState.SUCCESS.getType().equalsIgnoreCase(status)
            || "REFUND".equalsIgnoreCase(status)
            || "REVOKED".equalsIgnoreCase(status);
    }

    @Override
    public BillReconcileDiscrepancy resolveDiscrepancy(Long id, String resolveRemark) {
        if (id == null) {
            throw new BizException("差异单ID不能为空");
        }
        BillReconcileDiscrepancy discrepancy = discrepancyRepository.findById(id);
        if (discrepancy == null) {
            throw new BizException("差异单不存在或已被删除");
        }
        // 幂等：已处理直接返回
        if (BillDiscrepancyStatus.RESOLVED.getType().equals(discrepancy.getStatus())) {
            return discrepancy;
        }
        if (!StringUtils.hasText(resolveRemark)) {
            throw new BizException("处理备注不能为空");
        }
        discrepancy.setStatus(BillDiscrepancyStatus.RESOLVED.getType());
        discrepancy.setResolveRemark(resolveRemark.trim());
        discrepancy.setResolvedTime(new Date());
        AuthPrincipal principal = AuthContext.get();
        if (principal != null) {
            discrepancy.setResolvedBy(principal.getUsername());
        }
        discrepancyRepository.update(discrepancy);
        return discrepancy;
    }

    // ==================== 辅助方法 ====================

    private BillImport requireByImportNo(String importNo) {
        if (!StringUtils.hasText(importNo)) {
            throw new BizException("导入批次号不能为空");
        }
        BillImport billImport = billImportRepository.findByImportNo(importNo.trim());
        if (billImport == null) {
            throw new BizException("账单导入批次不存在：" + importNo);
        }
        return billImport;
    }

    /**
     * 同账单日账单种类幂等与互斥校验：
     * 同种类已存在 → 拒绝；ALL 与 SUCCESS/REFUND 互斥（ALL 已含全部记录）；
     * SUCCESS 与 REFUND 可并存（分别覆盖支付、退款方向）。
     */
    private void assertBillDateKindNotExists(String billDate, BillKind billKind) {
        List<BillImport> sameDate = billImportRepository.listByChannelDate(
            CHANNEL_WXPAY, BillType.TRADE.getType(), billDate);
        if (sameDate.isEmpty()) {
            return;
        }
        for (BillImport existing : sameDate) {
            BillKind existingKind = BillKind.of(existing.getBillKind());
            if (existingKind == billKind) {
                throw new BizException("账单日 " + billDate + " 的" + billKind.getDescription()
                    + "已上传（批次 " + existing.getImportNo() + "），同一账单日同种类账单不可重复上传");
            }
            if (billKind == BillKind.ALL || existingKind == BillKind.ALL) {
                throw new BizException("账单日 " + billDate + " 已存在" + existingKind.getDescription()
                    + "批次 " + existing.getImportNo() + "：ALL 全部账单包含支付与退款全部记录，"
                    + "与支付成功账单/退款账单不可重复上传同一账单日");
            }
        }
    }

    private List<BillRecord> filterByType(List<BillRecord> records, BillRecordType type) {
        List<BillRecord> result = new ArrayList<>();
        for (BillRecord record : records) {
            if (type.getType().equals(record.getRecordType())) {
                result.add(record);
            }
        }
        return result;
    }

    private void validateBillDate(String billDate) {
        if (!StringUtils.hasText(billDate)) {
            throw new BizException("账单日期不能为空");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(billDate.trim());
        } catch (DateTimeParseException e) {
            throw new BizException("账单日期格式必须为 " + DatePatterns.DATE);
        }
        LocalDate today = LocalDate.now(BILL_ZONE);
        if (!date.isBefore(today)) {
            throw new BizException("账单日期必须为历史日期（不含当天）");
        }
    }

    private Date atStartOfDay(String billDate) {
        LocalDate date = LocalDate.parse(billDate);
        return Date.from(date.atStartOfDay(BILL_ZONE).toInstant());
    }

    private Date atStartOfNextDay(String billDate) {
        LocalDate date = LocalDate.parse(billDate);
        return Date.from(date.plusDays(1).atStartOfDay(BILL_ZONE).toInstant());
    }

    private String generateImportNo() {
        return "BILL" + OrderNoUtils.getNo();
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BizException("计算文件摘要失败", e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
