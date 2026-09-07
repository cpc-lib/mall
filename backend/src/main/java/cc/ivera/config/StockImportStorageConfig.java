package cc.ivera.config;

import cc.ivera.storage.LocalStockImportFileStore;
import cc.ivera.storage.MinioStockImportFileStore;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 导入文件存储装配（V6）：
 * local 后端无条件装配——既是默认写入后端，也承接历史 LOCAL 记录（含 V5 存量）
 * 的回退读取，切换到 minio 后仍必须存在；
 * minio 后端仅在 stock.import.storage=minio 时装配，endpoint 缺失启动即失败。
 */
@Configuration
@EnableConfigurationProperties(StockImportStorageProperties.class)
public class StockImportStorageConfig {

    @Bean
    public LocalStockImportFileStore localStockImportFileStore(StockImportStorageProperties properties) {
        return new LocalStockImportFileStore(properties.getDir());
    }

    @Bean
    @ConditionalOnProperty(name = "stock.import.storage", havingValue = "minio")
    public MinioClient stockImportMinioClient(StockImportStorageProperties properties) {
        StockImportStorageProperties.Minio minio = properties.getMinio();
        if (minio.getEndpoint() == null || minio.getEndpoint().trim().isEmpty()) {
            throw new IllegalStateException("stock.import.storage=minio 时必须配置 stock.import.minio.endpoint");
        }
        if (minio.getBucket() == null || minio.getBucket().trim().isEmpty()) {
            throw new IllegalStateException("stock.import.minio.bucket 不能为空");
        }
        return MinioClient.builder()
                .endpoint(minio.getEndpoint().trim())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "stock.import.storage", havingValue = "minio")
    public MinioStockImportFileStore minioStockImportFileStore(MinioClient stockImportMinioClient,
                                                               StockImportStorageProperties properties) {
        return new MinioStockImportFileStore(stockImportMinioClient, properties.getMinio().getBucket());
    }
}
