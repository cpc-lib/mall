package cc.ivera.product.infrastructure.storage;

import cc.ivera.product.domain.gateway.StockFileStorage;
import cc.ivera.shared.domain.exception.BizException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 导入文件存储路由门面（V6）：应用层唯一依赖（实现领域端口 {@link StockFileStorage}）。
 * 写入走 stock.import.storage 配置的后端（writeType）；编辑保存与读取按记录自身
 * storage_type 路由（空按 LOCAL 兼容 V5 存量），保证文件跟随记录原件、切换配置
 * 不影响历史记录可读性。MinIO 后端按需装配，未启用时访问 MINIO 记录报业务异常。
 */
@Component
public class StockImportFileStorage implements StockFileStorage {

    private final LocalStockImportFileStore localStore;
    private final ObjectProvider<MinioStockImportFileStore> minioStoreProvider;
    private final String writeType;

    public StockImportFileStorage(LocalStockImportFileStore localStore,
                                  ObjectProvider<MinioStockImportFileStore> minioStoreProvider,
                                  StockImportStorageProperties properties) {
        this.localStore = localStore;
        this.minioStoreProvider = minioStoreProvider;
        this.writeType = "minio".equalsIgnoreCase(properties.getStorage())
            ? StockImportFileStore.TYPE_MINIO
            : StockImportFileStore.TYPE_LOCAL;
    }

    /**
     * 配置的写入后端：LOCAL / MINIO。
     */
    public String writeType() {
        return writeType;
    }

    /**
     * 按指定后端保存文件，返回可持久化的文件地址（storageType 空按 LOCAL）。
     */
    public String save(String storageType, Long id, byte[] bytes) {
        return store(storageType).save(id, bytes);
    }

    /**
     * 按指定后端读取文件（storageType 空按 LOCAL），文件缺失返回 null。
     */
    public byte[] read(String storageType, Long id, String location) {
        return store(storageType).read(id, location);
    }

    private StockImportFileStore store(String storageType) {
        String type = storageType == null || storageType.trim().isEmpty()
            ? StockImportFileStore.TYPE_LOCAL
            : storageType.trim().toUpperCase();
        if (StockImportFileStore.TYPE_MINIO.equals(type)) {
            MinioStockImportFileStore minioStore = minioStoreProvider.getIfAvailable();
            if (minioStore == null) {
                throw new BizException("MinIO 存储未启用，无法访问该导入文件");
            }
            return minioStore;
        }
        if (StockImportFileStore.TYPE_LOCAL.equals(type)) {
            return localStore;
        }
        throw new BizException("未知的导入文件存储类型：" + storageType);
    }
}
