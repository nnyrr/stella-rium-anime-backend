package top.stellarium.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadVO {
    private String image;
    private String uuid;
}
