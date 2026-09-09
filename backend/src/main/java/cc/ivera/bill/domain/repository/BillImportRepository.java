package cc.ivera.bill.domain.repository;

import cc.ivera.bill.domain.model.BillImport;

import java.util.List;

/**
 * 账单导入批次仓储端口。
 */
public interface BillImportRepository {

    /**
     * 新建批次（id/审计时间回填）。
     */
    void save(BillImport billImport);

    /**
     * 按主键更新（MP NOT_NULL 策略）。
     */
    void update(BillImport billImport);

    BillImport findById(Long id);

    /**
     * 按导入批次业务单号查询，不存在返回 null。
     */
    BillImport findByImportNo(String importNo);

    /**
     * 按账单文件 SHA-256 查询（同文件幂等）。
     */
    BillImport findByFileHash(String fileHash);

    /**
     * 同渠道 + 账单大类 + 账单日的全部批次，按 id 升序（账单种类幂等/互斥校验）。
     */
    List<BillImport> listByChannelDate(String channelCode, String billType, String billDate);

    /**
     * 导入批次列表：billDate 非空时按账单日期过滤，按 id 倒序。
     */
    List<BillImport> listByDate(String billDate);
}
