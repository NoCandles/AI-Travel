package com.ai.travel.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 对话意图解析响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 对话会话ID */
    private String conversationId;

    /** 本次响应消息列表 */
    private List<ChatMessageDTO> messages;

    /** AI解析出的结构化参数 */
    private ExtractedParams params;

    /** AI识别的意图分类：new_plan/modify_param/add_spot/remove_spot/modify_trip/chitchat/unclear */
    private String intent;

    /** 修改行程信息（intent=modify_trip 时有值） */
    private ModifyTripInfo modifyTrip;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedParams {
        private String destination;
        private String startPoint;      // 出发地（起点，具体地点）
        private String endPoint;        // 终点（返程地，往返时=起点）
        private List<String> startPointOptions;  // 起点具体地点选项（基于出发城市）
        private List<String> endPointOptions;    // 终点具体地点选项
        private String startDate;
        private String endDate;
        private Integer days;
        private String preference;
        private String budget;
        private String travelMode;
        private String travelModeLabel;
        private List<String> mustVisit;
        /** 徒步场景的结构化约束；非徒步时为空。 */
        private HikingProfileDTO hikingProfile;
    }

    /** 修改已生成行程的信息 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModifyTripInfo {
        private Integer modifyDayNum;   // 用户想修改第几天（1-based），null=未明确
        private String modifyTarget;    // 用户想修改的内容（如"盖聂之眼""酒店""行程节奏"）
        /** 结构化动作：replace_spot / regenerate_day / needs_clarify。前端按此分发执行 */
        private String actionType;
        /** 目标景点 ID（从当前行程景点列表解析，replace_spot 时必填） */
        private String targetSpotId;
        /** 替代景点名（后端 replace_spot 执行后回填；首轮未执行时为空，前端展示用） */
        private String replacement;
        /** 是否需要澄清（targetSpotId 解析不到 / 多个匹配） */
        private Boolean needsClarify;
        /** 多轮指代：换一个 / 撤销 / 确认上轮提议 */
        private Boolean swapAnother;
        private Boolean undo;
        private Boolean confirmProposal;
    }
}
