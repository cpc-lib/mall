package cc.ivera.mapper;

import cc.ivera.entity.InventoryTransaction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface InventoryTransactionMapper extends BaseMapper<InventoryTransaction> {

    /**
     * 库存流水总数：商品/业务类型/操作状态均为可选过滤条件。
     */
    Long countTransactions(@Param("productId") Long productId,
                           @Param("bizType") String bizType,
                           @Param("status") String status);

    /**
     * 库存流水分页：条件可选，按创建时间、id 倒序，LIMIT/OFFSET 手动分页。
     */
    List<InventoryTransaction> selectTransactionPage(@Param("productId") Long productId,
                                                     @Param("bizType") String bizType,
                                                     @Param("status") String status,
                                                     @Param("limit") int limit,
                                                     @Param("offset") long offset);
}
