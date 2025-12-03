package top.stellarium.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateRoomVO {
    private Long id;
    private Long animeId;
}
