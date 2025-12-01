package top.stellarium.pojo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CollectionDTO {
    private String bangumiId;
    private Long userId;
    private Integer limit;
    private Integer page;
    private Integer status;
}
