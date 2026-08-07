package com.ai.travel.admin.entity.view;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("point_record")
public class PointRecordView {
    private Long id;
    private String userId;
    private Integer changeValue;
    private Integer currentBalance;
    private String type;
    private String source;
    private String relatedId;
    private String description;
    private LocalDateTime createdAt;
}
