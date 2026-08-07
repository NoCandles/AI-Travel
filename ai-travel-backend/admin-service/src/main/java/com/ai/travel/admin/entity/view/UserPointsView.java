package com.ai.travel.admin.entity.view;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_points")
public class UserPointsView {
    private Long id;
    private String userId;
    private Integer totalPoints;
    private Integer availablePoints;
    private Integer usedPoints;
    private String level;
    private Integer signInStreak;
    private LocalDate lastSignInDate;
    private LocalDateTime createdAt;
}
