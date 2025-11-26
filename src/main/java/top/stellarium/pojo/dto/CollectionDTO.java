package top.stellarium.pojo.dto;

import lombok.Data;

@Data
public class CollectionDTO {
    private String bangumiId;
    private Long userId;
    private Integer limit;
    private Integer page;
    private Integer status;
}
