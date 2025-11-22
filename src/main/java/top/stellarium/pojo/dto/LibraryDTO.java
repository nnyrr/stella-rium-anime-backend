package top.stellarium.pojo.dto;

import jakarta.annotation.Nullable;
import lombok.Data;

@Data
public class LibraryDTO {
    private Integer limit;
    private Integer page;
    @Nullable
    private String season;
    @Nullable
    private String sort;
    @Nullable
    private String year;

}
