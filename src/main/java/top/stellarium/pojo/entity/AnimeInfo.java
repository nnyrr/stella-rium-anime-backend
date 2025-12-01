package top.stellarium.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true) // 开启链式调用，例如 new AnimeInfo().setName("...").setRating(9.0);
@Builder
@TableName("anime_info") // 对应数据库表名
public class AnimeInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Bangumi ID (主键)
     * 注意：这里使用了 IdType.INPUT，表示ID由程序手动传入（即来自Bangumi源），而非数据库自增或雪花算法生成
     */
    @TableId(value = "bangumi_id", type = IdType.INPUT)
    private Long bangumiId;

    /**
     * 原名
     */
    @TableField("name")
    private String name;

    /**
     * 中文名
     */
    @TableField("name_cn")
    private String nameCn;

    /**
     * 评分
     */
    @TableField("rating")
    private Double rating;

    /**
     * 图片链接
     */
    @TableField("image")
    private String image;

    /**
     * 最高票标签
     */
    @TableField("tag")
    private String tag;

    /**
     * 年份 (String类型)
     */
    @TableField("year")
    private String year;

    /**
     * 简介
     */
    @TableField("summary")
    private String summary;
}