package cc.ivera.dto.admin;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 导入记录 Excel 编辑保存请求：前端在电子表格组件中编辑后，
 * 生成新的 xlsx（base64）连同整理后的明细一起提交；仅 PENDING 记录可保存。
 */
@Data
public class StockImportFileSaveRequest {

    @NotBlank(message = "导入文件不能为空")
    private String fileBase64;//编辑后的 Excel 文件内容（xlsx, base64）

    @NotNull(message = "导入明细不能为空")
    @Valid
    private List<ProductStockBatchAdjustRequest.Item> items;
}
