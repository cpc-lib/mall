package cc.ivera.mapper;

import cc.ivera.entity.LocalMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface LocalMessageMapper extends BaseMapper<LocalMessage> {

    /**
     * 发件箱扫描：指定状态下到达重试时间的消息，按创建时间、id 先进先出。
     */
    List<LocalMessage> selectPendingMessages(@Param("status") String status,
                                             @Param("now") Date now,
                                             @Param("limit") int limit);
}
