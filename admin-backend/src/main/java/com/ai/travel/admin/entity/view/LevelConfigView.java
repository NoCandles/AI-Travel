package com.ai.travel.admin.entity.view;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("level_config")
public class LevelConfigView {
    private Long id;
    private String level;
    private Integer minPoints;
    private Integer maxPoints;
    private String title;
    private String privilege;
}
