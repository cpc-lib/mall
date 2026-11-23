package cc.ivera.shared.infrastructure.mq;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface LocalMessageMapper extends BaseMapper<LocalMessage> {

    /**
     * 发件箱扫描：指定状态下到达重试时间的消息，按创建时间、id 先进先出。
     *
     * @param bizTypes 为 null 或空时不限制业务类型（单进程统一中继）；否则只扫描指定类型
     */
    List<LocalMessage> selectPendingMessages(@Param("status") String status,
                                             @Param("now") Date now,
                                             @Param("bizTypes") List<String> bizTypes,
                                             @Param("limit") int limit);
}
