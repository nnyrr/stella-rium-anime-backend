package top.stellarium.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthLoginVO {
    private Long userId;
    private String avatar;
    private String nickname;
    private String token;
}
