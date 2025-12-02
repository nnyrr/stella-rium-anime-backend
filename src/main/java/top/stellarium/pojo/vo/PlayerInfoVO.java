package top.stellarium.pojo.vo;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class PlayerInfoVO {
    private String title;
    private String synopsis;
    private List<String> tags;
    private String year;
    private String cover;
    private Long bangumiId;
    private List<episode> episodes;

    @Data
    public static class episode{
        Integer sort;
        String title;
        String url;
    }
}
