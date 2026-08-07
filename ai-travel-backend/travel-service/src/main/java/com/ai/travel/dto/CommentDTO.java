package com.ai.travel.dto;

import lombok.Data;
import java.util.List;

/**
 * 评论 DTO
 */
@Data
public class CommentDTO {
    private String id;
    private String publishId;
    private String userId;
    private String nickname;
    private String avatar;
    private String content;
    private Integer likeCount;
    private Boolean isLiked;
    private String createTime;
    private String parentId;
    private String replyToNickname;
    /** 回复列表（第二层评论） */
    private List<CommentDTO> replies;
}