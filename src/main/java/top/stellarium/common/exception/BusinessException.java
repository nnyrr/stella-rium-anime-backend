package top.stellarium.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private Integer code;

    // 默认 500 错误
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    // 自定义错误码（比如 40010: 余额不足）
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
