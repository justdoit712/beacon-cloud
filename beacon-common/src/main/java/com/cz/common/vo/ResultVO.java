package com.cz.common.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 响应前端数据的基本结构
 * @author cz
 * @description
 */
@Data
@NoArgsConstructor
public class ResultVO {

    private Integer code;

    private String msg;

    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    private Object data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long total;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object rows;


    public ResultVO(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
