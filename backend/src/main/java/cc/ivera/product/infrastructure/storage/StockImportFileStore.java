package cc.ivera.product.infrastructure.storage;

/**
 * Excel 导入文件存储契约（V6）：支持本地磁盘与 MinIO 对象存储两种后端。
 * 每个导入记录一个文件，地址（本地绝对路径 / MinIO 对象键）持久化在
 * t_stock_import.file_path，读取按记录自身地址路由。
 */
public interface StockImportFileStore {

    String TYPE_LOCAL = "LOCAL";
    String TYPE_MINIO = "MINIO";

    /**
     * 存储后端类型：LOCAL / MINIO。
     */
    String type();

    /**
     * 保存（覆盖）导入记录对应的 Excel 文件，返回可持久化的文件地址。
     */
    String save(Long id, byte[] bytes);

    /**
     * 按地址读取导入文件；location 为空回退默认地址（兼容 V5 存量记录）；文件缺失返回 null。
     */
    byte[] read(Long id, String location);
}
