package cc.ivera.service.impl;

import cc.ivera.dto.admin.ProductStockBatchAdjustRequest;
import cc.ivera.entity.Product;
import cc.ivera.entity.StockImport;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.ProductMapper;
import cc.ivera.mapper.StockImportMapper;
import cc.ivera.service.ProductStockService;
import cc.ivera.service.StockImportService;
import cc.ivera.storage.StockImportFileStorage;
import cc.ivera.vo.BatchAdjustItemVO;
import cc.ivera.vo.BatchAdjustResultVO;
import cc.ivera.vo.StockImportVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Excel 批量库存导入记录实现（V5/V6）。
 * 导入：Excel 文件按配置后端（本地磁盘/MinIO）保存，文件地址（file_path）与
 * 存储后端（storage_type）回写记录，明细校验后暂存 PENDING，不改库存；
 * 编辑：仅 PENDING 可改，文件跟随记录原件按记录自身存储后端覆盖，并同步刷新
 * 明细快照（item_count/items_json），保证文件与明细一致，确认即以此为准；
 * 确认入库：先 CAS 抢占记录（PENDING→CONFIRMED，防并发/重复执行），再按已保存明细
 * 逐条执行库存调整：成功的生效并落流水，失败的返回原因并记录 FAILED 流水，
 * 单条失败不影响其他条目——与批量调整接口同一契约。
 */
@Service
public class StockImportServiceImpl implements StockImportService {

    private static final int MAX_ITEMS_JSON_LENGTH = 4000;
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int LIST_LIMIT = 50;

    private final StockImportMapper stockImportMapper;
    private final ProductMapper productMapper;
    private final ProductStockService productStockService;
    private final StockImportFileStorage fileStorage;
    private final ObjectMapper objectMapper;

    public StockImportServiceImpl(StockImportMapper stockImportMapper, ProductMapper productMapper,
                                  ProductStockService productStockService, StockImportFileStorage fileStorage,
                                  ObjectMapper objectMapper) {
        this.stockImportMapper = stockImportMapper;
        this.productMapper = productMapper;
        this.productStockService = productStockService;
        this.fileStorage = fileStorage;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Long createImport(String fileName, String fileBase64, List<ProductStockBatchAdjustRequest.Item> items) {
        byte[] fileBytes = decodeFile(fileBase64);
        validateItems(items);
        StockImport record = new StockImport();
        record.setFileName(fileName == null || fileName.trim().isEmpty() ? "批量库存调整.xlsx" : fileName.trim());
        record.setItemCount(items.size());
        record.setStatus("PENDING");
        record.setItemsJson(toJson(items));
        stockImportMapper.insert(record);
        String writeType = fileStorage.writeType();
        record.setFilePath(fileStorage.save(writeType, record.getId(), fileBytes));
        record.setStorageType(writeType);
        stockImportMapper.updateById(record);
        return record.getId();
    }

    @Override
    public List<StockImportVO> listImports() {
        List<StockImport> rows = stockImportMapper.selectList(
                new QueryWrapper<StockImport>().orderByDesc("create_time").orderByDesc("id").last("LIMIT " + LIST_LIMIT));
        List<StockImportVO> result = new ArrayList<>();
        for (StockImport row : rows) result.add(toRow(row));
        return result;
    }

    @Override
    public StockImportVO getImport(Long id) {
        StockImport record = stockImportMapper.selectById(id);
        if (record == null) throw new BizException("导入记录不存在");
        return toRow(record);
    }

    @Override
    public String getFile(Long id) {
        StockImport record = stockImportMapper.selectById(id);
        if (record == null) throw new BizException("导入记录不存在");
        // 按记录自身存储后端读取（空按 LOCAL 兼容 V5 存量记录）
        byte[] bytes = fileStorage.read(record.getStorageType(), id, record.getFilePath());
        if (bytes == null) throw new BizException("导入文件不存在，请重新导入");
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Override
    @Transactional
    public void saveImportFile(Long id, String fileBase64, List<ProductStockBatchAdjustRequest.Item> items) {
        StockImport record = stockImportMapper.selectById(id);
        if (record == null) throw new BizException("导入记录不存在");
        if (!"PENDING".equals(record.getStatus())) throw new BizException("该导入记录已确认入库，明细不可再修改");
        byte[] fileBytes = decodeFile(fileBase64);
        validateItems(items);
        UpdateWrapper<StockImport> uw = new UpdateWrapper<>();
        uw.eq("id", id).eq("status", "PENDING")
                .set("item_count", items.size())
                .set("items_json", toJson(items))
                .set("update_time", new Date());
        if (stockImportMapper.update(null, uw) == 0) throw new BizException("该导入记录已确认入库，明细不可再修改");
        // 文件跟随记录原件：按记录自身存储后端覆盖，不迁移
        fileStorage.save(record.getStorageType(), id, fileBytes);
    }

    @Override
    public BatchAdjustResultVO confirmImport(Long id) {
        StockImport record = stockImportMapper.selectById(id);
        if (record == null) throw new BizException("导入记录不存在");
        List<ProductStockBatchAdjustRequest.Item> items = fromItemsJson(record.getItemsJson());
        if (items.isEmpty()) throw new BizException("导入明细为空，请先在编辑中保存明细后再确认入库");
        markConfirmed(id);
        List<BatchAdjustItemVO> results = new ArrayList<>();
        int successCount = 0;
        for (ProductStockBatchAdjustRequest.Item item : items) {
            BatchAdjustItemVO row = new BatchAdjustItemVO();
            row.setProductId(item.getProductId());
            row.setDelta(item.getDelta());
            try {
                Product product = productStockService.adjustOne(item.getProductId(), item.getDelta());
                row.setSuccess(true);
                row.setStock(product.getStock());
                row.setMessage(item.getDelta() > 0 ? "补货成功" : "库存已扣减");
                successCount++;
            } catch (BizException e) {
                productStockService.recordFailedAdjustment(item.getProductId(), item.getDelta(), e.getMessage());
                row.setSuccess(false);
                row.setMessage(e.getMessage());
            }
            results.add(row);
        }
        BatchAdjustResultVO body = new BatchAdjustResultVO();
        body.setTotal(items.size());
        body.setSuccessCount(successCount);
        body.setFailCount(items.size() - successCount);
        body.setResults(results);
        return body;
    }

    private void markConfirmed(Long id) {
        UpdateWrapper<StockImport> uw = new UpdateWrapper<>();
        uw.eq("id", id).eq("status", "PENDING")
                .set("status", "CONFIRMED")
                .set("confirm_time", new Date());
        if (stockImportMapper.update(null, uw) == 0) throw new BizException("该导入记录已确认入库，不能重复执行");
    }

    private void validateItems(List<ProductStockBatchAdjustRequest.Item> items) {
        if (items == null || items.isEmpty()) throw new BizException("导入明细不能为空");
        for (ProductStockBatchAdjustRequest.Item item : items) {
            if (item.getProductId() == null) throw new BizException("导入明细缺少商品ID");
            if (item.getDelta() == null || item.getDelta() == 0) {
                throw new BizException("导入明细调整量需为非 0 整数（商品ID " + item.getProductId() + "）");
            }
            if (productMapper.selectById(item.getProductId()) == null) {
                throw new BizException("商品不存在：商品ID " + item.getProductId());
            }
        }
    }

    private byte[] decodeFile(String fileBase64) {
        if (fileBase64 == null || fileBase64.trim().isEmpty()) throw new BizException("导入文件不能为空");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(fileBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new BizException("导入文件内容必须是合法的 base64 编码");
        }
        if (bytes.length == 0) throw new BizException("导入文件不能为空");
        if (bytes.length > MAX_FILE_BYTES) throw new BizException("导入文件大小不能超过 2MB");
        return bytes;
    }

    private StockImportVO toRow(StockImport record) {
        StockImportVO vo = new StockImportVO();
        vo.setId(record.getId());
        vo.setFileName(record.getFileName());
        vo.setItemCount(record.getItemCount());
        vo.setStatus(record.getStatus());
        vo.setItems(fromItemsJson(record.getItemsJson()));
        vo.setCreateTime(record.getCreateTime());
        vo.setConfirmTime(record.getConfirmTime());
        return vo;
    }

    private String toJson(List<ProductStockBatchAdjustRequest.Item> items) {
        try {
            String json = objectMapper.writeValueAsString(items);
            if (json.length() > MAX_ITEMS_JSON_LENGTH) throw new BizException("导入明细过多，请分批导入");
            return json;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("导入明细序列化失败");
        }
    }

    private List<ProductStockBatchAdjustRequest.Item> fromItemsJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProductStockBatchAdjustRequest.Item>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
