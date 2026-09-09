package cc.ivera.product.domain.gateway;

/**
 * 导入文件存储出站端口：支持本地磁盘与 MinIO 对象存储两种后端。
 * 写入走配置的后端（writeType）；编辑保存与读取按记录自身 storage_type 路由，
 * 保证文件跟随记录原件、切换配置不影响历史记录可读性。
 */
public interface StockFileStorage {

    /**
     * 配置的写入后端：LOCAL / MINIO。
     */
    String writeType();

    /**
     * 按指定后端保存文件，返回可持久化的文件地址（storageType 空按 LOCAL）。
     */
    String save(String storageType, Long id, byte[] bytes);

    /**
     * 按指定后端读取文件（storageType 空按 LOCAL），文件缺失返回 null。
     */
    byte[] read(String storageType, Long id, String location);
}
