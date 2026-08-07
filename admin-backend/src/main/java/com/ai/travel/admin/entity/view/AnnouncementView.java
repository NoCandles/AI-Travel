package com.ai.travel.admin.entity.view;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("system_announcements")
public class AnnouncementView {
    private String id;
    private String title;
    private String content;
    private String level;
    private Integer status;
    private String publishBy;
    private LocalDateTime publishAt;
    private LocalDateTime createdAt;
}
