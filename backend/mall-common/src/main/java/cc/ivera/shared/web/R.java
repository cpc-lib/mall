package cc.ivera.shared.web;

import cc.ivera.shared.domain.exception.ErrorCode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
public class R<T> {

    private Integer code; //响应码
    private String message; //响应消息
    private T data; //响应数据

    public static R<Map<String, Object>> ok() {
        R<Map<String, Object>> r = new R<>();
        r.setCode(0);
        r.setMessage("成功");
        r.setData(new HashMap<String, Object>());
        return r;
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(0);
        r.setMessage("成功");
        r.setData(data);
        return r;
    }

    public static <T> R<T> error(ErrorCode errorCode, String message) {
        R<T> r = new R<>();
        r.setCode(errorCode.getCode());
        r.setMessage(message);
        return r;
    }
}
