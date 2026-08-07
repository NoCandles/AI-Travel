package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_unlocked_template")
public class UserUnlockedTemplate {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String userId;
    
    private String templateId;
    
    private LocalDateTime unlockedAt;
}
