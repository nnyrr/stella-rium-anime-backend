package top.stellarium.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class LibraryAnimeVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String bangumiId;
    private String date;
    private String director;
    private Integer episodes;
    private String image;
    private String name;
    private String nameCn;
    private String productionCompany;
    private long rank;
    private Double rating;
    private long ratingCount;
    private String[] tags;
}
