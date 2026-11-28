package cc.ivera.product.application;

import cc.ivera.product.application.dto.SpuSaveRequest;
import cc.ivera.product.application.vo.SpuDetailVO;
import cc.ivera.product.application.vo.SpuSummaryVO;
import cc.ivera.product.domain.model.Product;
import cc.ivera.shared.web.PageVO;

/**
 * 商品应用服务：SPU（含属性/规格）与 SKU（库存单元）管理。
 */
public interface ProductService {

    /**
     * 商品 SKU 列表服务端分页（按 id 升序）。
     */
    PageVO<Product> page(int page, int size);

    /**
     * 商品 SKU 详情，不存在抛 BizException。
     */
    Product getById(Long id);

    /**
     * 兼容快捷新增单规格商品：自动创建 1 个单规格 SPU + 1 条 DEFAULT 默认 SKU。
     */
    Product create(String title, Integer price, Integer stock);

    /**
     * SKU 上架/下架：状态仅支持 ENABLED / DISABLED，同步重算所属 SPU 价格区间与默认 SKU。
     */
    Product changeStatus(Long id, String productStatus);

    /**
     * SPU 分页列表（支持关键字搜索与仅查上架过滤）。
     */
    PageVO<SpuSummaryVO> pageSpu(int page, int size, String keyword, boolean onlyEnabled);

    /**
     * 获取 SPU 完整详情（含基础展示属性、销售规格组与 SKU 列表）。
     */
    SpuDetailVO getSpuDetail(Long spuId);

    /**
     * 创建 SPU（支持单规格 / 多规格，含基础属性、销售规格与初始 SKU 矩阵）。
     */
    SpuDetailVO createSpu(SpuSaveRequest request);

    /**
     * 编辑 SPU（更新基本信息、基础属性、销售规格与 SKU 价格/状态；不物理删除已有 SKU）。
     */
    SpuDetailVO updateSpu(Long spuId, SpuSaveRequest request);

    /**
     * SPU 上架/下架：上架时校验至少存在 1 个 ENABLED SKU。
     */
    SpuDetailVO changeSpuStatus(Long spuId, String spuStatus);
}
