package top.stellarium.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 主页动漫VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IndexAnimeVO implements Serializable {

    private static final long serialVersionUID = 1L;
    private String name;
    private String nameCn;
    private Double rating;
    private String image;
    private Long bangumiId;
    private String tag; // main tag most upvoted
    private String year;

}
