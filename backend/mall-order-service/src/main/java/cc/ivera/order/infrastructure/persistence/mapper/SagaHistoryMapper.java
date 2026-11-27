package cc.ivera.order.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
 * Seata Saga 历史表直连 Mapper。
 * <p>Seata 表由引擎维护，不属于订单领域模型，这里只用注解 SQL 取 ID 与删除行数，
 * 不建 PO、不继承 BaseMapper。is_running 为 SMALLINT，0 表示未运行。</p>
 */
@Mapper
public interface SagaHistoryMapper {

    @Select("SELECT id FROM seata_state_machine_inst "
        + "WHERE status = 'SU' AND compensation_status IS NULL AND is_running = 0 "
        + "AND parent_id IS NULL AND gmt_end < #{cutoff} "
        + "AND NOT EXISTS (SELECT 1 FROM seata_state_machine_inst c WHERE c.parent_id = seata_state_machine_inst.id) "
        + "ORDER BY gmt_end ASC, id ASC LIMIT #{limit}")
    List<String> findCleanableInstanceIds(@Param("cutoff") Date cutoff, @Param("limit") int limit);

    @Delete("<script>"
        + "DELETE FROM seata_state_inst WHERE machine_inst_id IN "
        + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
        + "</script>")
    int deleteStateInstancesByMachineInstIds(@Param("ids") List<String> ids);

    @Delete("<script>"
        + "DELETE FROM seata_state_machine_inst WHERE id IN "
        + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
        + "AND status = 'SU' AND compensation_status IS NULL AND is_running = 0 AND gmt_end < #{cutoff}"
        + "</script>")
    int deleteMachineInstancesTerminalBefore(@Param("cutoff") Date cutoff, @Param("ids") List<String> ids);
}
