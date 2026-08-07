package com.ai.travel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("point_record")
public class PointRecord {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String userId;
    
    private Integer changeValue;
    
    private Integer currentBalance;
    
    private String type; // earn/spend
    
    private String source;
    
    private String relatedId;
    
    private String description;
    
    private LocalDateTime createdAt;
}
