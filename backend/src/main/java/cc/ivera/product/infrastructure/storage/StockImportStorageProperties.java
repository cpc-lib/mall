package cc.ivera.product.infrastructure.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Excel 导入文件存储配置（V6）。
 * storage=local 时文件落本地 dir 目录；storage=minio 时写入 MinIO 对象存储，
 * 对象键固定 stock-import/{id}.xlsx，bucket 不存在自动创建。
 */
@Data
@ConfigurationProperties(prefix = "stock.import")
public class StockImportStorageProperties {

    /**
     * 存储后端：local-本地磁盘（默认），minio-对象存储
     */
    private String storage = "local";

    /**
     * local 后端保存目录（相对后端进程工作目录或绝对路径）
     */
    private String dir = "./upload/stock-import";

    /**
     * MinIO 连接配置（storage=minio 时生效）
     */
    private Minio minio = new Minio();

    @Data
    public static class Minio {

        /**
         * MinIO 服务地址，如 http://192.168.1.200:9000（storage=minio 时必填）
         */
        private String endpoint;

        /**
         * 访问键
         */
        private String accessKey = "minioadmin";

        /**
         * 密钥
         */
        private String secretKey = "minioadmin";

        /**
         * 存储桶（不存在时自动创建）
         */
        private String bucket = "payment-demo";
    }
}
