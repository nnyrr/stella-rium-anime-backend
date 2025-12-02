package top.stellarium.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@Accessors(chain = true)
@TableName("character_info")
public class CharacterInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 角色原名
     */
    private String name;

    /**
     * 角色中文名
     * 对应数据库列: name_cn
     */
    private String nameCn;

    /**
     * 角色图片URL
     */
    private String image;

    /**
     * Bangumi平台ID
     * 对应数据库列: bangumi_id
     */
    private Long bangumiId;

    /**
     * 主要标签/最高票Tag
     */
    private String tag;

    /**
     * 角色出处/来源
     * 注意：对应数据库列 `from` (保留字)
     */
    @TableField("`from`")
    private String from;
}