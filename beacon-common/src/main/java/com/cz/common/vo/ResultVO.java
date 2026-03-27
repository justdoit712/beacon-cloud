package com.cz.common.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用接口响应对象。
 *
 * @author cz
 */
@Data
@NoArgsConstructor
public class ResultVO<T> {

    private Integer code;

    private String msg;

    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    private T data;


    public ResultVO(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
