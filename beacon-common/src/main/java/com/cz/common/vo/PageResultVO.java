package com.cz.common.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页接口响应对象。
 *
 * @param <T> 列表元素类型
 */
@Data
@NoArgsConstructor
public class PageResultVO<T> {

    private Integer code;

    private String msg;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long total;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<T> rows;

    public PageResultVO(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
