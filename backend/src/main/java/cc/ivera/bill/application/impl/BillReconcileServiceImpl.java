package cc.ivera.bill.application.impl;

import cc.ivera.bill.application.BillReconcileService;
import cc.ivera.bill.application.parser.ParsedBill;
import cc.ivera.bill.application.parser.WxTradeBillParser;
import cc.ivera.bill.domain.enums.*;
import cc.ivera.bill.domain.model.BillImport;
import cc.ivera.bill.domain.model.BillReconcileDiscrepancy;
import cc.ivera.bill.domain.model.BillRecord;
import cc.ivera.bill.domain.repository.BillImportRepository;
import cc.ivera.bill.domain.repository.BillReconcileDiscrepancyRepository;
import cc.ivera.bill.domain.repository.BillRecordRepository;
import cc.ivera.payment.domain.enums.wxpay.WxTradeState;
import cc.ivera.payment.domain.model.PaymentInfo;
import cc.ivera.payment.domain.repository.PaymentInfoRepository;
import cc.ivera.refund.domain.enums.RefundStatus;
import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.refund.domain.repository.RefundInfoRepository;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 微信交易账单上传式对账服务实现。
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
 * <p>对账范围按微信账单种类收窄（依据 v3《交易账单详细说明》）：ALL 账单核对支付与退款；
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

    private final PaymentInfoRepository paymentInfoRepository;

    private final RefundInfoRepository refundInfoRepository;

    private final DistributedLockTemplate lockTemplate;

    private final TransactionTemplate transactionTemplate;

    private final WxTradeBillParser billParser = new WxTradeBillParser();

    public BillReconcileServiceImpl(
        BillImportRepository billImportRepository,
        BillRecordRepository billRecordRepository,
        BillReconcileDiscrepancyRepository discrepancyRepository,
        PaymentInfoRepository paymentInfoRepository,
        RefundInfoRepository refundInfoRepository,
        DistributedLockTemplate lockTemplate,
        TransactionTemplate transactionTemplate
    ) {
        this.billImportRepository = billImportRepository;
        this.billRecordRepository = billRecordRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.paymentInfoRepository = paymentInfoRepository;
        this.refundInfoRepository = refundInfoRepository;
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
        String content = new String(fileBytes, StandardCharsets.UTF_8);

        // 快速幂等路径：同文件直接返回原批次
        BillImport existingByHash = billImportRepository.findByFileHash(fileHash);
        if (existingByHash != null) {
            log.info("账单文件已导入过，幂等返回原批次 importNo={}, billDate={}", existingByHash.getImportNo(), normalizedDate);
            return existingByHash;
        }

        // 锁外先做解析校验（解析失败/空账单不入库）；账单种类由表头识别
        ParsedBill parsed = billParser.parse(content, CHANNEL_WXPAY, normalizedDate);
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

            for (BillReconcileDiscrepancy discrepancy : discrepancies) {
                discrepancy.setImportId(importId);
                discrepancy.setBillDate(billImport.getBillDate());
                discrepancy.setStatus(BillDiscrepancyStatus.OPEN.getType());
                discrepancyRepository.save(discrepancy);
            }

            billImport.setMatchedCount(matchedCount);
            billImport.setDiscrepancyCount(discrepancies.size());
            billImport.setStatus(BillImportStatus.RECONCILED.getType());
            billImport.setReconcileTime(new Date());
            billImport.setErrorMessage(null);
            billImportRepository.update(billImport);
        });
    }

    /**
     * 支付核对：账单 PAY 行 vs t_payment_info(order_no + WXPAY)。
     * 返回匹配成功笔数。
     */
    private int reconcilePayRecords(BillImport billImport, List<BillRecord> payRecords,
                                    List<BillReconcileDiscrepancy> discrepancies) {
        int matched = 0;
        Set<String> billOrderNos = payRecords.stream()
            .map(BillRecord::getBizNo)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());

        Map<String, PaymentInfo> localPaymentMap = loadLocalPayments(billOrderNos);

        for (BillRecord record : payRecords) {
            PaymentInfo local = localPaymentMap.get(record.getBizNo());
            boolean discrepancyFound = false;
            if (local == null) {
                discrepancies.add(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_CHANNEL_ONLY,
                    BillRecordType.PAY, record.getBizNo(), record.getChannelSerialNo(),
                    record.getTotalAmount(), null, record.getTradeStatus(), null));
                discrepancyFound = true;
            } else {
                if (!Objects.equals(record.getTotalAmount(), local.getPayerTotal())) {
                    discrepancies.add(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_AMOUNT_MISMATCH,
                        BillRecordType.PAY, record.getBizNo(), record.getChannelSerialNo(),
                        record.getTotalAmount(), local.getPayerTotal(),
                        record.getTradeStatus(), local.getTradeState()));
                    discrepancyFound = true;
                }
                // 账单支付行均为 SUCCESS；本地非成功终态即为状态不一致
                if (!WxTradeState.SUCCESS.getType().equals(local.getTradeState())) {
                    discrepancies.add(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_STATUS_MISMATCH,
                        BillRecordType.PAY, record.getBizNo(), record.getChannelSerialNo(),
                        record.getTotalAmount(), local.getPayerTotal(),
                        record.getTradeStatus(), local.getTradeState()));
                    discrepancyFound = true;
                }
            }
            if (!discrepancyFound) {
                matched++;
            }
        }

        // 反向扫描：账单日本地 SUCCESS 支付但账单无记录 → 本地有渠道无
        Date dayStart = atStartOfDay(billImport.getBillDate());
        Date dayEnd = atStartOfNextDay(billImport.getBillDate());
        List<PaymentInfo> localDayPayments = paymentInfoRepository.listByChannelAndStateAndCreateTimeRange(
            CHANNEL_WXPAY, WxTradeState.SUCCESS.getType(), dayStart, dayEnd);
        for (PaymentInfo payment : localDayPayments) {
            if (!billOrderNos.contains(payment.getOrderNo())) {
                discrepancies.add(buildDiscrepancy(billImport, BillDiscrepancyType.PAY_LOCAL_ONLY,
                    BillRecordType.PAY, payment.getOrderNo(), payment.getTransactionId(),
                    null, payment.getPayerTotal(), null, payment.getTradeState()));
            }
        }
        return matched;
    }

    /**
     * 退款核对：账单 REFUND 行 vs t_refund_info(refund_no)。
     * 返回匹配成功笔数。
     */
    private int reconcileRefundRecords(BillImport billImport, List<BillRecord> refundRecords,
                                       List<BillReconcileDiscrepancy> discrepancies) {
        int matched = 0;
        Set<String> billRefundNos = refundRecords.stream()
            .map(BillRecord::getBizNo)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());

        Map<String, RefundInfo> localRefundMap = loadLocalRefunds(billRefundNos);

        for (BillRecord record : refundRecords) {
            RefundInfo local = localRefundMap.get(record.getBizNo());
            boolean discrepancyFound = false;
            if (local == null) {
                discrepancies.add(buildDiscrepancy(billImport, BillDiscrepancyType.REFUND_CHANNEL_ONLY,
                    BillRecordType.REFUND, record.getBizNo(), record.getChannelSerialNo(),
                    record.getRefundAmount(), null, record.getTradeStatus(), null));
                discrepancyFound = true;
            } else {
                if (!Objects.equals(record.getRefundAmount(), local.getRefund())) {
                    discrepancies.add(buildDiscrepancy(billImport, BillDiscrepancyType.REFUND_AMOUNT_MISMATCH,
                        BillRecordType.REFUND, record.getBizNo(), record.getChannelSerialNo(),
                        record.getRefundAmount(), local.getRefund(),
                        record.getTradeStatus(), local.getRefundStatus()));
                    discrepancyFound = true;
                }
                // 账单退款状态为 SUCCESS 而本地退款非 SUCCESS → 状态不一致；
                // 账单为 PROCESSING 等中间态时不判差异（账单出账后状态不更新，以本地为准）
                if (WxTradeState.SUCCESS.getType().equals(record.getTradeStatus())
                    && !RefundStatus.SUCCESS.getType().equals(local.getRefundStatus())) {
                    discrepancies.add(buildDiscrepancy(billImport, BillDiscrepancyType.REFUND_STATUS_MISMATCH,
                        BillRecordType.REFUND, record.getBizNo(), record.getChannelSerialNo(),
                        record.getRefundAmount(), local.getRefund(),
                        record.getTradeStatus(), local.getRefundStatus()));
                    discrepancyFound = true;
                }
            }
            if (!discrepancyFound) {
                matched++;
            }
        }

        // 反向扫描：账单日本地 SUCCESS 退款但账单无记录 → 本地有渠道无
        Date dayStart = atStartOfDay(billImport.getBillDate());
        Date dayEnd = atStartOfNextDay(billImport.getBillDate());
        List<RefundInfo> localDayRefunds = refundInfoRepository.listByStatusAndCreateTimeRange(
            RefundStatus.SUCCESS.getType(), dayStart, dayEnd);
        for (RefundInfo refund : localDayRefunds) {
            if (!billRefundNos.contains(refund.getRefundNo())) {
                discrepancies.add(buildDiscrepancy(billImport, BillDiscrepancyType.REFUND_LOCAL_ONLY,
                    BillRecordType.REFUND, refund.getRefundNo(), refund.getRefundId(),
                    null, refund.getRefund(), null, refund.getRefundStatus()));
            }
        }
        return matched;
    }

    private Map<String, PaymentInfo> loadLocalPayments(Set<String> orderNos) {
        return paymentInfoRepository.listByChannelAndOrderNos(CHANNEL_WXPAY, orderNos).stream()
            .collect(Collectors.toMap(PaymentInfo::getOrderNo, Function.identity(), (a, b) -> a));
    }

    private Map<String, RefundInfo> loadLocalRefunds(Set<String> refundNos) {
        return refundInfoRepository.listByRefundNos(refundNos).stream()
            .collect(Collectors.toMap(RefundInfo::getRefundNo, Function.identity(), (a, b) -> a));
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
