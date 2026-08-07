package com.ai.travel.dto;

import lombok.Data;

import java.util.List;

/**
 * 聊天请求 - 用户发送对话
 */
@Data
public class ChatSendRequest {

    /** 用户输入的自然语言 */
    private String text;

    /** 对话会话ID（首次为空） */
    private String conversationId;

    /** 当前已提取的参数（多轮对话上下文） */
    private ChatParamsDTO currentParams;

    /** 当前对话关联的 tripId（done 阶段用于行程修改） */
    private String tripId;

    /**
     * 已提取的行程参数
     */
    @Data
    public static class ChatParamsDTO {
        private String destination;
        private Integer days;
        private String preference;
        private String budget;
        private String travelMode;
        private List<String> mustVisit;
    }

    /**
     * 确认参数请求
     */
    @Data
    public static class ConfirmParams {
        private String conversationId;
        private String destination;
        private String startPoint;      // 出发地（起点）
        private String endPoint;        // 终点（返程地）
        private Integer days;
        private String startDate;
        private String endDate;
        private String preference;
        private String budget;
        private String travelMode;
        private List<String> mustVisit;
        /** 徒步时附带的体力、里程、路线类型等约束（可选） */
        private HikingProfileDTO hikingProfile;
    }
}
