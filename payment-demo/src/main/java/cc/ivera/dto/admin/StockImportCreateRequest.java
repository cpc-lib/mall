package cc.ivera.dto.admin;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Excel 批量库存导入记录创建请求：导入的 Excel 文件（base64，后端存盘）+
 * 前端解析后的明细（仅暂存，不直接改库存）。
 */
@Data
public class StockImportCreateRequest {

    private String fileName;//Excel 文件名

    @NotBlank(message = "导入文件不能为空")
    private String fileBase64;//Excel 文件内容（xlsx, base64），保存到后端目录

    @NotNull(message = "导入明细不能为空")
    @Valid
    private List<ProductStockBatchAdjustRequest.Item> items;
}
