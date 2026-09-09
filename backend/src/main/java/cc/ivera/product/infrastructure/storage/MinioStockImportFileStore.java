package cc.ivera.product.infrastructure.storage;

import cc.ivera.shared.domain.exception.BizException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * MinIO 对象存储实现（V6）：每个导入记录一个对象，键固定 stock-import/{id}.xlsx。
 * save 前确保 bucket 存在（不存在自动创建），返回对象键作为记录地址；
 * read 的 location 为空时按记录 id 回退默认对象键，兼容 V5 存量记录；
 * 对象不存在（NoSuchKey/NoSuchBucket）视为文件缺失返回 null。
 */
public class MinioStockImportFileStore implements StockImportFileStore {

    private static final String OBJECT_PREFIX = "stock-import/";

    private final MinioClient client;
    private final String bucket;

    public MinioStockImportFileStore(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public String type() {
        return TYPE_MINIO;
    }

    /**
     * 导入记录对应的对象键。
     */
    public String objectKey(Long id) {
        return OBJECT_PREFIX + id + ".xlsx";
    }

    @Override
    public String save(Long id, byte[] bytes) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey(id))
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .build());
            return objectKey(id);
        } catch (Exception e) {
            throw new BizException("导入文件保存到 MinIO 失败", e);
        }
    }

    @Override
    public byte[] read(Long id, String location) {
        String key = (location == null || location.trim().isEmpty()) ? objectKey(id) : location;
        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf, 0, buf.length)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() == null ? null : e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchBucket".equals(code)) return null;
            throw new BizException("导入文件读取失败：" + code, e);
        } catch (Exception e) {
            throw new BizException("导入文件读取失败", e);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
