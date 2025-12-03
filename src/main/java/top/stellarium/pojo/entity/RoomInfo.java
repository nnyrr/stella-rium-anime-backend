package top.stellarium.pojo.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomInfo {
    private Long id;
    private String title;
    private Long animeId;
    private Integer currentEpisode;
    private Long ownerId;
    private Integer onlineCount;
    private Integer status;
    private String cover;
}
