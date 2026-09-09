package cc.ivera.product.infrastructure.storage;

import cc.ivera.shared.domain.exception.BizException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地磁盘存储实现（V5 行为迁入）：每个导入记录一个文件 {dir}/{id}.xlsx。
 * 目录由 stock.import.dir 配置（默认 ./upload/stock-import，相对后端工作目录）；
 * save 返回文件绝对路径作为记录地址；read 的 location 为空时按记录 id 回退
 * 默认文件名，兼容 V5 存量记录（该批记录未持久化 file_path）。
 */
public class LocalStockImportFileStore implements StockImportFileStore {

    private final Path baseDir;

    public LocalStockImportFileStore(String dir) {
        this.baseDir = Paths.get(dir).toAbsolutePath().normalize();
    }

    @Override
    public String type() {
        return TYPE_LOCAL;
    }

    @Override
    public String save(Long id, byte[] bytes) {
        try {
            Files.createDirectories(baseDir);
            Path file = baseDir.resolve(id + ".xlsx");
            Files.write(file, bytes);
            return file.toString();
        } catch (IOException e) {
            throw new BizException("导入文件保存失败", e);
        }
    }

    @Override
    public byte[] read(Long id, String location) {
        Path file = (location == null || location.trim().isEmpty())
            ? baseDir.resolve(id + ".xlsx")
            : Paths.get(location).toAbsolutePath().normalize();
        if (!Files.exists(file)) return null;
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new BizException("导入文件读取失败", e);
        }
    }
}
