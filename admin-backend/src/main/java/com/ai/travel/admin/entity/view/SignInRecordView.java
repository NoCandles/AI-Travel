package com.ai.travel.admin.entity.view;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sign_in_record")
public class SignInRecordView {
    private Long id;
    private String userId;
    private LocalDateTime signInDate;
    private Integer pointsEarned;
    private LocalDateTime createdAt;
}
