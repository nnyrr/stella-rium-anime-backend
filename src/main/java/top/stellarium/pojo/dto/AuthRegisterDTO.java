package top.stellarium.pojo.dto;

import lombok.Data;

@Data
public class AuthRegisterDTO {
    private String image;
    private String bangumiId;
    private String captchaToken;
    private String email;
    private String nickname;
    private String password;
    private String username;
}
