package top.stellarium.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 今日选择VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TodaysPickVO implements Serializable {

    private static final long serialVersionUID = 1L;
    private String name;
    private String nameCn;
    private String image;
    private Long bangumiId;
    private String brief;

}
