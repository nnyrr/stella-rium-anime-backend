package top.stellarium.pojo.vo;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 列表类同一返回
 * @param <T>
 */
@Data
public class ListVO<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer total;
    private List<T> list;
}
