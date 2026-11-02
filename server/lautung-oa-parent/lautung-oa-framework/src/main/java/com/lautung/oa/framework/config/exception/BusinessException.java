package com.lautung.oa.framework.config.exception;

import com.lautung.oa.common.result.ResultCodeEnum;
import lombok.Data;

@Data
public class BusinessException extends RuntimeException {

    private Integer code;//状态码
    private String msg;//描述信息

    public BusinessException(Integer code,String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    /**
     * 接收枚举类型对象
     * @param resultCodeEnum
     */
    public BusinessException(ResultCodeEnum resultCodeEnum) {
        super(resultCodeEnum.getMessage());
        this.code = resultCodeEnum.getCode();
        this.msg = resultCodeEnum.getMessage();
    }

    @Override
    public String toString() {
        return "BusinessException{" +
                "code=" + code +
                ", message=" + this.getMessage() +
                '}';
    }
}
