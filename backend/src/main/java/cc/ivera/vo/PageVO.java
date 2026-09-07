package cc.ivera.vo;

import lombok.Data;

import java.util.List;

/**
 * 通用分页响应 VO。
 *
 * @param <T> 记录行类型
 */
@Data
public class PageVO<T> {

    private Long total;

    private Integer page;

    private Integer size;

    private List<T> records;
}
