package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("level_config")
public class LevelConfig {
    
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    private Integer level;
    
    private Integer minPoints;
    
    private Integer maxPoints;
    
    private String title;
    
    private String privilege;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
