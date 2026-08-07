package com.ai.travel.service.impl;

import com.ai.travel.dto.*;
import com.ai.travel.entity.Attraction;
import com.ai.travel.entity.ChatConversation;
import com.ai.travel.entity.ChatMessage;
import com.ai.travel.entity.TripDay;
import com.ai.travel.entity.TripSpot;
import com.ai.travel.repository.AttractionRepository;
import com.ai.travel.repository.ChatConversationRepository;
import com.ai.travel.repository.ChatMessageRepository;
import com.ai.travel.repository.TripDayRepository;
import com.ai.travel.repository.TripSpotRepository;
import com.ai.travel.service.ChatService;
import com.ai.travel.service.TripService;
import com.ai.travel.config.ProgressEmitter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 对话服务实现
 * 
 * 使用 DeepSeek API 解析用户自然语言 → 提取结构化行程参数
 * 支持多轮对话上下文管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TripService tripService;
    private final ProgressEmitter progressEmitter;
    private final AttractionRepository attractionRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TripDayRepository tripDayRepository;
    private final TripSpotRepository tripSpotRepository;

    @Qualifier("tripGenerationExecutor")
    private final Executor tripGenerationExecutor;

    /** 对话上下文缓存（生产环境由 DB 兜底，ConcurrentHashMap 仅作热缓存） */
    private final ConcurrentHashMap<String, ChatContext> contexts = new ConcurrentHashMap<>();

    /** 请求去重缓存：同一 conversationId + text 在 5s 内不重复调用 AI */
    private final ConcurrentHashMap<String, Long> dedupCache = new ConcurrentHashMap<>();
    private static final long DEDUP_TTL_MS = 5000;

    /** DeepSeek HTTP 超时（毫秒） */
    private static final int DEEPSEEK_CONNECT_TIMEOUT = 10000;
    private static final int DEEPSEEK_READ_TIMEOUT = 15000;

    /** 景点→城市映射缓存（启动时从数据库加载） */
    private volatile Map<String, String> spotCityCache = null;

    /**
     * 获取景点→城市映射（懒加载 + 缓存）
     */
    private Map<String, String> getSpotCityMap() {
        if (spotCityCache == null) {
            synchronized (this) {
                if (spotCityCache == null) {
                    spotCityCache = loadSpotCityMapFromDb();
                }
            }
        }
        return spotCityCache;
    }

    /**
     * 从数据库加载景点→城市映射
     */
    private Map<String, String> loadSpotCityMapFromDb() {
        Map<String, String> map = new HashMap<>();
        try {
            List<Attraction> attractions = attractionRepository.selectList(null);
            for (Attraction a : attractions) {
                if (a.getName() != null && a.getCity() != null) {
                    map.put(a.getName(), a.getCity());
                }
            }
            log.info("📍 加载景点→城市映射: {} 条", map.size());
        } catch (Exception e) {
            log.warn("⚠️ 加载景点映射失败，使用空映射: {}", e.getMessage());
        }
        return map;
    }

    @Override
    public ChatResponse processMessage(String userId, ChatSendRequest request) {
        String conversationId = Optional.ofNullable(request.getConversationId())
                .orElse(UUID.randomUUID().toString());
        ensureConversation(userId, conversationId, request.getText());

        // 🚀 B4: 请求去重 — 同一 conversationId + text 5s 内不重复调用
        String dedupKey = conversationId + "::" + request.getText();
        Long lastCall = dedupCache.get(dedupKey);
        long now = System.currentTimeMillis();
        if (lastCall != null && (now - lastCall) < DEDUP_TTL_MS) {
            log.info("🔄 检测到重复请求，跳过: conversationId={}, text={}", conversationId, request.getText());
            // 返回上次快照（或空响应让前端等）
            return ChatResponse.builder()
                    .conversationId(conversationId)
                    .messages(Collections.emptyList())
                    .build();
        }
        dedupCache.put(dedupKey, now);

        appendMessage(userId, conversationId, "user", "text", request.getText(), null, null);

        // 🚀 B1: 从 DB 恢复对话上下文（支持多实例/重启后不丢上下文）
        ChatContext ctx = contexts.get(conversationId);
        if (ctx == null) {
            ctx = restoreContextFromDb(conversationId);
            if (ctx == null) {
                ctx = new ChatContext();
            }
            contexts.put(conversationId, ctx);
        }
        ctx.lastActivity = now;

        // 同步前端记录的 tripId 到对话上下文（done 阶段用于加载景点列表、执行修改）
        if (request.getTripId() != null && !request.getTripId().isBlank()) {
            ctx.currentTripId = request.getTripId();
        }

        // done 阶段：若上下文已有 tripId，确保当前行程景点列表已注入（供 LLM 指代消解）
        if (ctx.currentTripId != null) {
            ctx.currentTripSpots = loadCurrentTripSpots(ctx.currentTripId);
        }

        // ═══════════════════════════════════════════════════════════════
        // AI 优先方案：所有用户输入都先调 DeepSeek 解析
        // 规则解析仅作为 DeepSeek 失败时的降级兜底
        // ═══════════════════════════════════════════════════════════════
        Map<String, Object> aiParams = null;
        String aiReplyText = null;
        try {
            log.info("🤖 AI优先解析: conversationId={}, text={}", conversationId, request.getText());
            aiParams = callDeepSeekForIntent(request.getText(), ctx);
            // 如果 AI 返回了 reply 字段，用作助手回复文本
            if (aiParams.containsKey("reply") && aiParams.get("reply") != null) {
                aiReplyText = (String) aiParams.get("reply");
            }
            // 合并 AI 解析的参数
            mergeParams(ctx, aiParams);
            log.info("✅ AI解析结果: destination={}, departure={}, days={}, intent={}",
                    ctx.destination, ctx.departure, ctx.days, aiParams.get("intent"));
        } catch (Exception e) {
            log.warn("⚠️ DeepSeek 调用失败，降级到规则解析: {}", e.getMessage());
            Map<String, Object> fallback = fallbackParse(request.getText());
            mergeParams(ctx, fallback);
        }

        // 检查参数完整性
        boolean allRequired = isAllRequiredPresent(ctx);
        // 优先使用 AI 返回的回复文本，否则用规则生成
        String assistantText = aiReplyText != null ? aiReplyText : buildAssistantText(ctx, allRequired);

        // modify_trip 意图：尝试直接执行结构化 action，回填结果到 reply / Replacement
        String currentIntent = aiParams != null ? (String) aiParams.get("intent") : null;
        boolean isModifyTrip = "modify_trip".equals(currentIntent);
        ChatResponse.ModifyTripInfo modifyInfo = null;
        if (isModifyTrip) {
            modifyInfo = buildModifyTripInfo(aiParams, ctx);
            // 执行 action：能直接做的（replace_spot/swap_another/undo/regenerate_day）就做掉，
            // 用执行结果覆盖 AI 的 reply，让前端拿到"已替换/已撤销"的确定态文案
            if (modifyInfo != null && ctx.currentTripId != null) {
                ActionResult ar = executeModifyAction(ctx, modifyInfo);
                if (ar.reply != null) assistantText = ar.reply;
                if (ar.replacement != null) modifyInfo.setReplacement(ar.replacement);
                if (ar.resolvedActionType != null) modifyInfo.setActionType(ar.resolvedActionType);
                if (ar.resolvedNeedsClarify != null) modifyInfo.setNeedsClarify(ar.resolvedNeedsClarify);
                if (ar.resolvedDayNum != null) modifyInfo.setModifyDayNum(ar.resolvedDayNum);
            }
        }

        // 构建响应
        List<ChatMessageDTO> messages = new ArrayList<>();
        messages.add(ChatMessageDTO.assistantText(assistantText));

        // 非 modify_trip 且参数齐全 → 显示参数卡片
        if (!isModifyTrip && ctx.destination != null && ctx.days != null) {
            ChatResponse.ExtractedParams params = buildExtractedParams(ctx);
            Map<String, Object> paramPayload = buildParamPayload(params);
            paramPayload.put("editing", false);
            paramPayload.put("mustVisit", params.getMustVisit() != null ? params.getMustVisit() : Collections.emptyList());
            messages.add(ChatMessageDTO.paramCard(paramPayload));

            ctx.phase = "params_collected";
        }

        for (ChatMessageDTO message : messages) {
            appendMessage(userId, conversationId, "assistant", message.getType(), message.getContent(), message.getPayload(), null);
        }
        updateConversationMeta(userId, conversationId, request.getText(), messages.size() + 1);

        return ChatResponse.builder()
                .conversationId(conversationId)
                .messages(messages)
                .params(ctx.destination != null ? buildExtractedParams(ctx) : null)
                .intent(aiParams != null ? (String) aiParams.get("intent") : null)
                .modifyTrip(modifyInfo)
                .build();
    }

    /** modify_trip action 执行结果，用于覆盖 AI reply 和回填 Replacement */
    private static class ActionResult {
        String reply;
        String replacement;
        String resolvedActionType;
        Boolean resolvedNeedsClarify;
        Integer resolvedDayNum;
    }

    /**
     * 执行 modify_trip 的结构化 action。能直接做的（replace_spot/swap_another/undo/regenerate_day）就做掉，
     * 结果覆盖 AI 的 reply；做不了（needs_clarify）的留 reply 不变，让前端展示反问。
     * 同时维护 ctx.lastProposedAction 支持多轮指代。
     */
    private ActionResult executeModifyAction(ChatContext ctx, ChatResponse.ModifyTripInfo info) {
        ActionResult ar = new ActionResult();
        String tripId = ctx.currentTripId;
        String actionType = info.getActionType();

        // 撤销：用 lastProposedAction 里记录的版本号回退
        if ("undo".equals(actionType)) {
            return doUndo(ctx);
        }
        // 换一个：基于 lastProposedAction 重新生成替代
        if ("swap_another".equals(actionType) && ctx.lastProposedAction != null) {
            String spotId = (String) ctx.lastProposedAction.get("spotId");
            String hint = (String) ctx.lastProposedAction.getOrDefault("hint", "");
            ActionResult swap = doReplaceSpot(ctx, tripId, spotId, hint, "再帮你换一个别的景点~");
            swap.resolvedActionType = "swap_another_done";
            return swap;
        }
        // 确认上轮提议：清掉 lastProposedAction，回一句确认
        if ("confirm_proposal".equals(actionType)) {
            ctx.lastProposedAction = null;
            ar.reply = "好的，就用这个~";
            return ar;
        }
        // 精准替换单景点
        if ("replace_spot".equals(actionType) && info.getTargetSpotId() != null) {
            return doReplaceSpot(ctx, tripId, info.getTargetSpotId(), info.getModifyTarget(), null);
        }
        // 重生成当天：前端收到 actionType=regenerate_day 后直接调用 regenerateDay 接口，不需要二次确认
        if ("regenerate_day".equals(actionType)) {
            int dayN = info.getModifyDayNum() != null ? info.getModifyDayNum() : 0;
            String targetName = info.getModifyTarget();
            if (targetName != null && !targetName.isBlank()) {
                ar.reply = "好的，帮你重新规划第" + dayN + "天，换掉「" + targetName + "」安排别的景点~";
            } else {
                ar.reply = "好的，帮你重新规划第" + dayN + "天的行程~";
            }
            ar.resolvedActionType = "regenerate_day";
            return ar;
        }
        // needs_clarify 或其它：不执行，保留 AI reply
        return ar;
    }

    private ActionResult doReplaceSpot(ChatContext ctx, String tripId, String spotId, String hint, String fixedReply) {
        ActionResult ar = new ActionResult();
        if (tripId == null || spotId == null) {
            ar.reply = "当前没有可修改的行程，先生成一条路线吧~";
            ar.resolvedActionType = "needs_clarify";
            ar.resolvedNeedsClarify = true;
            return ar;
        }
        // 执行前存一个自动版本，支持后续"撤销"
        String versionId = null;
        try {
            versionId = tripService.saveTripVersionReturningId(tripId, "auto-before-replace-spot");
        } catch (Exception e) {
            log.warn("replaceSpot 前自动存版本失败：{}", e.getMessage());
        }

        com.ai.travel.dto.TripSpotDTO newSpot = null;
        try {
            newSpot = tripService.replaceSpot(tripId, spotId, hint != null ? hint : "");
        } catch (Exception e) {
            log.warn("replaceSpot 执行失败 tripId={}, spotId={}: {}", tripId, spotId, e.getMessage());
        }
        if (newSpot == null) {
            // 单景点替换失败时，自动降级为重新规划该景点所在那天，避免让用户卡住
            int dayNum = findSpotDayNum(ctx, spotId);
            if (dayNum > 0) {
                ar.reply = "这个景点暂时没找到合适的单点替代，帮你重新规划第" + dayNum + "天整体调整~";
                ar.resolvedActionType = "regenerate_day";
                ar.resolvedDayNum = dayNum;
                ar.resolvedNeedsClarify = false;
                return ar;
            }
            ar.reply = "这个景点暂时没找到合适的替代，你可以试试说「重新规划第X天」让我整体调整那天行程~";
            ar.resolvedActionType = "needs_clarify";
            ar.resolvedNeedsClarify = true;
            return ar;
        }
        ar.replacement = newSpot.getName();
        ar.reply = fixedReply != null ? fixedReply : "好的，帮你把「" + (hint != null ? hint : "") + "」换成「" + newSpot.getName() + "」~";
        ar.resolvedActionType = "replace_spot_done";
        // 刷新上下文景点列表，并记录提议供"换一个/撤销"
        ctx.currentTripSpots = loadCurrentTripSpots(tripId);
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("type", "replace_spot");
        proposed.put("tripId", tripId);
        proposed.put("spotId", spotId);
        proposed.put("hint", hint);
        proposed.put("lastReplacement", newSpot.getName());
        proposed.put("versionId", versionId);
        ctx.lastProposedAction = proposed;
        return ar;
    }

    private ActionResult doUndo(ChatContext ctx) {
        ActionResult ar = new ActionResult();
        if (ctx.lastProposedAction == null) {
            ar.reply = "没有可撤销的改动了~";
            return ar;
        }
        String tripId = (String) ctx.lastProposedAction.get("tripId");
        String versionId = (String) ctx.lastProposedAction.get("versionId");
        try {
            if (tripId != null) {
                // 没有精确 versionId 时，restore 最近一个自动版本；这里简化为查列表取最新
                if (versionId != null) {
                    tripService.restoreTripVersion(tripId, versionId);
                } else {
                    // 退回最近版本（列表第一条）
                    List<com.ai.travel.dto.TripPlanDTO> versions = tripService.getTripVersions(tripId);
                    if (versions != null && !versions.isEmpty()) {
                        tripService.restoreTripVersion(tripId, versions.get(0).getId());
                    }
                }
                ctx.currentTripSpots = loadCurrentTripSpots(tripId);
                ar.reply = "好的，已恢复原来的景点~";
            }
        } catch (Exception e) {
            log.warn("撤销失败：{}", e.getMessage());
            ar.reply = "撤销没成功，稍后重试或去详情页手动恢复~";
        }
        ctx.lastProposedAction = null;
        return ar;
    }

    @Override
    public String confirmAndGenerate(String userId, String conversationId,
                                     ChatSendRequest.ConfirmParams params) {
        log.info("User {} confirmed trip: {} {} days", userId, params.getDestination(), params.getDays());
        ensureConversation(userId, conversationId, params.getDestination());
        appendMessage(userId, conversationId, "user", "text", "确认生成", null, null);

        // 构建 CreateTripRequest
        CreateTripRequest createRequest = new CreateTripRequest();
        createRequest.setDestination(params.getDestination());
        createRequest.setDays(params.getDays());
        // 起终点：用于行程生成时"第一天=起点，最后一天=终点"
        createRequest.setStartPoint(params.getStartPoint());
        createRequest.setEndPoint(params.getEndPoint());
        // 日期防御性归一化：兼容 "7月2日" / "2026-07-02" / "2026/7/2" 等格式，统一为 yyyy-MM-dd
        createRequest.setStartDate(normalizeDate(params.getStartDate()));
        createRequest.setEndDate(normalizeDate(params.getEndDate()));
        createRequest.setBudget(params.getBudget());

        // 偏好转为 List
        if (params.getPreference() != null) {
            createRequest.setPreferences(Collections.singletonList(params.getPreference()));
        }
        // 必去景点
        createRequest.setMustVisitPlaces(params.getMustVisit());
        // 出行方式
        createRequest.setTravelMode(params.getTravelMode());
        // 徒步偏好由对话端按需传入，AI 会据此约束难度、里程和路线类型。
        createRequest.setHikingProfile(params.getHikingProfile());

        // 调用现有行程生成服务（异步，立即返回 tripId + GENERATING 状态）
        TripPlanDTO trip = tripService.createTrip(userId, createRequest);

        // 清理对话上下文 + 去重缓存
        contexts.remove(conversationId);
        dedupCache.entrySet().removeIf(e -> e.getKey().startsWith(conversationId + "::"));
        appendMessage(userId, conversationId, "assistant", "text", "已开始生成路线，请稍候。", null, null);
        updateConversationMeta(userId, conversationId, params.getDestination() + "路线生成中", 2);

        log.info("Trip generation started: tripId={}", trip.getId());
        return trip.getId();
    }

    @Override
    public List<ChatConversationDTO> listConversations(String userId) {
        List<ChatConversation> rows = chatConversationRepository.selectList(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getUserId, userId)
                .eq(ChatConversation::getStatus, "active")
                .orderByDesc(ChatConversation::getUpdatedAt)
                .last("LIMIT 50"));
        return rows.stream().map(this::toConversationDTO).toList();
    }

    @Override
    public ChatHistoryDTO getHistory(String userId, String conversationId) {
        ChatConversation conversation = chatConversationRepository.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getUserId, userId)
                .eq(ChatConversation::getStatus, "active"));
        if (conversation == null) {
            return ChatHistoryDTO.builder()
                    .conversationId(conversationId)
                    .messages(Collections.emptyList())
                    .build();
        }

        List<ChatMessage> rows = chatMessageRepository.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getUserId, userId)
                .orderByAsc(ChatMessage::getSortOrder)
                .orderByAsc(ChatMessage::getId));

        return ChatHistoryDTO.builder()
                .conversationId(conversationId)
                .conversation(toConversationDTO(conversation))
                .messages(rows.stream().map(this::toHistoryMessage).toList())
                .build();
    }

    @Override
    public void deleteConversation(String userId, String conversationId) {
        chatConversationRepository.update(null, new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getUserId, userId)
                .set(ChatConversation::getStatus, "deleted"));
        contexts.remove(conversationId);
    }

    @Override
    public void saveSnapshot(String userId, String conversationId, ChatSnapshotRequest request) {
        if (conversationId == null || conversationId.isBlank()) return;
        List<ChatSnapshotRequest.MessageItem> messages = request.getMessages() != null
                ? request.getMessages()
                : Collections.emptyList();

        String title = firstNotBlank(request.getTitle(), buildTitleFromMessages(messages), "新对话");
        ensureConversation(userId, conversationId, title);

        chatMessageRepository.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getUserId, userId));

        int order = 0;
        String lastMessage = "";
        for (ChatSnapshotRequest.MessageItem item : messages) {
            if ("typing".equals(item.getType())) continue;
            if ("system".equals(item.getRole()) && "_welcome".equals(item.getId())) continue;
            appendMessage(userId, conversationId, item.getRole(), item.getType(), item.getContent(), item.getPayload(), item.getId(), order++);
            if (item.getContent() != null && !item.getContent().isBlank()) {
                lastMessage = item.getContent();
            }
        }

        chatConversationRepository.update(null, new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getUserId, userId)
                .set(ChatConversation::getTitle, title)
                .set(ChatConversation::getLastMessage, abbreviate(lastMessage, 255))
                .set(ChatConversation::getMessageCount, order)
                .set(ChatConversation::getStatus, "active"));
    }

    // ═══════════════════════════════════════
    // DeepSeek NLP 意图解析
    // ═══════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> callDeepSeekForIntent(String userText, ChatContext ctx) {
        String systemPrompt = buildNPTPrompt();
        String userPrompt = String.format(
                "用户输入：「%s」\n已有参数：%s\n\n请解析用户意图，严格按照下面的 json 格式输出，不要输出任何其他内容：",
                userText,
                buildContextJson(ctx)
        );

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-v4-flash");
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 800);
            // 🆕 使用 JSON Output 模式，确保返回合法 JSON
            requestBody.put("response_format", Map.of("type", "json_object"));

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));
            requestBody.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            org.springframework.web.client.RestTemplate deepseekRt = new org.springframework.web.client.RestTemplate();
            deepseekRt.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                setConnectTimeout(DEEPSEEK_CONNECT_TIMEOUT);
                setReadTimeout(DEEPSEEK_READ_TIMEOUT);
            }});

            String response = deepseekRt.postForObject(apiUrl, entity, String.class);

            if (response != null) {
                JsonNode jsonResponse = objectMapper.readTree(response);
                JsonNode choices = jsonResponse.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    String content = choices.get(0).get("message").get("content").asText();
                    // JSON Output 模式下不需要清理 ```json 标记
                    return objectMapper.readValue(content, Map.class);
                }
            }
        } catch (Exception e) {
            log.warn("DeepSeek NLP failed (timeout={}ms), using fallback: {}", DEEPSEEK_READ_TIMEOUT, e.getMessage());
        }

        // 降级
        return fallbackParse(userText);
    }

    private String buildNPTPrompt() {
        // 🚀 人格化 v2: "小派" — 本地旅行达人
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return """
                你是「小派」，拾路派的 AI 旅行助手。你的人设：
                - 你是一个热爱旅行的本地达人，性格开朗、说话带点幽默
                - 你对全国各地的景点、美食、天气了如指掌
                - 你说话口语化，会用「~」结尾，偶尔用 emoji
                - 你的核心任务：通过自然聊天了解用户需求，最终生成完美的旅行路线

                ## 你的输出格式
                严格 json，包含：
                - intent: new_plan / modify_param / add_spot / remove_spot / modify_trip / chitchat / unclear
                - destination: 目的地（城市名，null=未提及）
                - departure: 出发城市（null=未提及）
                - endPoint: 返程城市（null=未提及）
                - roundTrip: boolean，是否往返
                - days: 天数 1-15（null=未提及）
                - startDate: 出发日期 yyyy-MM-dd（null=未提及）
                - preference: 轻松休闲/网红打卡/小众探索/文化体验/美食之旅/户外冒险/徒步穿越（null=未提及）
                - budget: 经济实惠/中等预算/高端享受（null=未提及）
                - travelMode: drive/transit/hiking（null=未提及）
                - hikingProfile: 仅徒步需求时输出对象；只填写用户明确提及的字段，未提及字段为 null。
                  可用字段：groupType(solo/parent-child/team)、distanceRange(1-3km/3-8km/8-15km/15km以上)、durationType(half-day/full-day/multi-day)、routeTypes(环线/单程/往返/登山穿越/溯溪/古道数组)、difficulty(1-5)、sceneryPrefs数组、roadCondition、physicalLimit数组、trafficLimit、avoidItems数组、needCampsite、advancedNeeds数组。
                  例如“带孩子走5公里环线，不走野路”应提取 groupType=parent-child、distanceRange=3-8km、routeTypes=[环线]、avoidItems=[避开野路危险路段]。
                - mustVisit: 必去景点数组（仅添加用户明确提到的）
                - removeSpots: 用户要删除的景点数组
                - modifyDayNum: 修改第几天的行程（整数，null=未提及）
                - modifyTarget: 修改目标（景点名/酒店/行程节奏，null=未提及）
                - targetSpotId: 精确景点 ID（从已有参数 currentTripSpots 匹配，null=未匹配到）
                - swapAnother / undo / confirmProposal: 布尔，多轮指代
                - reply: 你给用户的回复文本（自然口语化，重要！）
                - missing: 仍缺少的必填字段数组

                ## 回复风格
                - 用户说"你好""在吗"等问候 → 热情回应 + 主动聊当前季节/天气 + 推荐目的地。
                  比如"今天成都 24°C 阳光特别好☀️ 这个季节去九寨沟看水正是时候！有什么想法吗？"
                - 用户说起目的地 → 确认 + 顺便聊两句当地特色（美食/季节/冷知识）
                  比如"成都啊！这时候去刚好能赶上荷花季，人民公园的鹤鸣茶社坐一下午太舒服了~ 打算玩几天？"
                - 用户修改参数 → 直接确认，不反问
                  比如"好的，改成5天~"
                - 用户说"第X天不想去XX" → 确认要替换，不要问"要继续吗"
                - 参数齐全时 → 自然汇总确认
                  比如"好嘞！帮你总结一下：成都3天，轻松休闲风，自驾，预算中等~ 确认的话我这就开始生成路线！"
                - 永远不要问"要继续吗""要换吗"等二次确认

                ## 关键规则
                1. "周末"=2天，"小长假"=3天
                2. 日期相对今天（${today}）推算，“下周五”按实际日期算
                3. 已有参数里有 weather 字段时，自然融入回复中
                4. 用户只说"嗯""好的" → intent="unclear"
                5. 不要编造景点名，mustVisit 只加用户明确说的
                6. 必须输出合法 JSON + 自然的 reply

                ## 输出示例（仅作格式参考）
                用户说："你好"
                {"intent":"chitchat","reply":"嗨～今天成都 24°C 阳光超好☀️ 这个季节去川西草原正好！有什么想去的地方吗？","missing":["destination","days"]}

                用户说："从成都出发去阿坝自驾五天"
                {"intent":"new_plan","destination":"阿坝","departure":"成都","days":5,"travelMode":"drive","reply":"阿坝州！这时候草原全是花海🌼 从成都出发自驾5天，沿途还能路过汶川吃车厘子~","missing":[]}

                用户说："改成7天"
                {"intent":"modify_param","days":7,"reply":"好的，改成7天，可以玩得更深了~","missing":[]}
                """.replace("${today}", today);
    }

    private String buildContextJson(ChatContext ctx) {
        Map<String, Object> ctxMap = new LinkedHashMap<>();
        ctxMap.put("destination", ctx.destination);
        ctxMap.put("days", ctx.days);
        ctxMap.put("startDate", ctx.startDate);
        ctxMap.put("endDate", ctx.endDate);
        ctxMap.put("preference", ctx.preference);
        ctxMap.put("budget", ctx.budget);
        ctxMap.put("travelMode", ctx.travelMode);
        ctxMap.put("hikingProfile", ctx.hikingProfile);
        ctxMap.put("mustVisit", ctx.mustVisit);
        // 🆕 注入天气/季节信息（让 AI 回复更自然、主动）
        String weatherCtx = buildWeatherContext(ctx.destination);
        if (weatherCtx != null) {
            ctxMap.put("weather", weatherCtx);
        }
        // done 阶段注入当前行程的景点列表，供 LLM 在有界集合内做指代消解
        if (ctx.currentTripSpots != null && !ctx.currentTripSpots.isEmpty()) {
            ctxMap.put("currentTripSpots", ctx.currentTripSpots);
        }
        // 上一轮提议的 action，支持"换一个/撤销/确认"等多轮指代
        if (ctx.lastProposedAction != null) {
            ctxMap.put("lastProposedAction", ctx.lastProposedAction);
        }
        try {
            return objectMapper.writeValueAsString(ctxMap);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 🆕 构建天气/季节上下文（目前用模拟数据，后续可接入真实天气 API）
     * 让 AI 能主动告知用户当地天气、当季推荐，提升对话自然感
     */
    private String buildWeatherContext(String destination) {
        if (destination == null || destination.isBlank()) return null;
        int month = LocalDate.now().getMonthValue();
        // 季节推荐映射
        java.util.Map<String, String> seasonTips = java.util.Map.of(
            "成都", "7月成都25-32°C，湿度较高，建议带伞。荷花季正当时，人民公园鹤鸣茶社赏荷绝佳~",
            "厦门", "7月厦门28-34°C，海边城市注意防晒。环岛路骑行看日落非常美🌅",
            "北京", "7月北京24-35°C，干燥炎热多喝水。故宫、颐和园游客多，建议早上去~",
            "大理", "7月大理18-26°C，避暑胜地！苍山洱海之间非常凉爽，雨季记得带薄外套",
            "西安", "7月西安25-38°C，非常热！建议早晚出行，中午去博物馆吹空调",
            "杭州", "7月杭州27-36°C，闷热。西湖边荷花开了，断桥边晨跑很惬意~",
            "重庆", "7月重庆28-38°C，火炉城市！晚上去南山看夜景很巴适🌃",
            "三亚", "7月三亚28-35°C，下水正好！蜈支洲岛潜水能见度高",
            "丽江", "7月丽江15-24°C，凉爽舒适！雨季偶尔下雨，但雨后的玉龙雪山特别美",
            "阿坝", "7月阿坝10-22°C，避暑天堂！草原花海盛开，九寨沟水量充沛💧"
        );
        String tip = seasonTips.get(destination);
        if (tip == null) {
            tip = destination + " 7月平均气温20-30°C，适合出游~";
        }
        return tip;
    }

    /** 城市/地区别名表（用于规则解析） */
    private static final String[][] CITY_ALIASES = {
            {"厦门"}, {"成都", "蓉城"}, {"北京", "首都"}, {"大理", "洱海"},
            {"西安", "长安"}, {"杭州", "西湖"}, {"重庆", "山城"}, {"三亚", "天涯"},
            {"丽江", "古城"}, {"上海", "魔都"}, {"广州"}, {"深圳"},
            {"武汉"}, {"长沙"}, {"南京"}, {"苏州"}, {"青岛"}, {"大连"},
            {"哈尔滨"}, {"兰州"}, {"银川"}, {"呼和浩特"}, {"桂林"}, {"北海"},
            {"阿坝", "阿坝州", "九寨沟", "黄龙", "若尔盖", "四姑娘山"},
            {"甘孜", "稻城", "亚丁", "康定", "理塘", "色达"},
            {"拉萨", "西藏"}, {"林芝"}, {"日喀则"},
            {"西宁", "青海湖", "青海"}, {"格尔木"},
            {"洛阳"}, {"郑州"}, {"开封"}, {"福州"}, {"泉州"},
            {"敦煌"}, {"张掖"}, {"嘉峪关"}, {"延安"},
            {"黄山"}, {"张家界"}, {"凤凰"}, {"阳朔"},
            {"乌鲁木齐", "新疆"}, {"喀什"}, {"伊犁"},
            {"贵阳", "贵州"}, {"遵义"},
            {"昆明", "云南"}, {"大理"}, {"丽江"}, {"西双版纳"},
            {"海口"}, {"台北"},
            {"平遥"}, {"大同"}, {"承德"}, {"秦皇岛"}
    };

    /**
     * 主要城市的热门出发/到达具体地点（交通枢纽 + 地标商圈）
     * 用于让用户在识别到出发城市后，进一步选择具体起点/终点
     */
    private static final Map<String, List<String>> CITY_LANDMARKS = new LinkedHashMap<>() {{
        put("成都", List.of("春熙路", "天府广场", "成都东站", "双流机场", "天府机场"));
        put("北京", List.of("北京西站", "首都机场", "大兴机场", "天安门", "三里屯"));
        put("上海", List.of("虹桥机场", "浦东机场", "人民广场", "上海站", "陆家嘴"));
        put("广州", List.of("广州南站", "白云机场", "天河城", "北京路"));
        put("深圳", List.of("深圳北站", "宝安机场", "福田口岸", "世界之窗"));
        put("重庆", List.of("重庆北站", "江北机场", "解放碑", "洪崖洞"));
        put("西安", List.of("西安北站", "咸阳机场", "钟楼", "大雁塔"));
        put("昆明", List.of("昆明南站", "长水机场", "翠湖", "金马碧鸡坊"));
        put("大理", List.of("大理古城", "大理站", "洱海公园", "苍山"));
        put("丽江", List.of("丽江古城", "丽江站", "三义机场", "玉龙雪山"));
        put("兰州", List.of("兰州西站", "中川机场", "张掖路", "黄河铁桥"));
        put("西宁", List.of("西宁站", "曹家堡机场", "东关清真大寺", "青海湖"));
        put("拉萨", List.of("拉萨站", "贡嘎机场", "布达拉宫", "大昭寺"));
        put("乌鲁木齐", List.of("乌鲁木齐站", "地窝堡机场", "大巴扎", "红山"));
        put("三亚", List.of("三亚凤凰机场", "三亚站", "亚龙湾", "海棠湾"));
        put("海口", List.of("美兰机场", "海口东站", "骑楼老街", "万绿园"));
        put("武汉", List.of("武汉站", "天河机场", "户部巷", "黄鹤楼"));
        put("长沙", List.of("长沙南站", "黄花机场", "五一广场", "橘子洲"));
        put("南京", List.of("南京南站", "禄口机场", "新街口", "夫子庙"));
        put("苏州", List.of("苏州站", "苏州北站", "观前街", "拙政园"));
        put("杭州", List.of("杭州东站", "萧山机场", "西湖", "武林广场"));
        put("厦门", List.of("厦门站", "高崎机场", "中山路", "鼓浪屿"));
        put("青岛", List.of("青岛站", "胶东机场", "栈桥", "五四广场"));
        put("哈尔滨", List.of("哈尔滨西站", "太平机场", "中央大街", "冰雪大世界"));
        put("桂林", List.of("桂林站", "两江机场", "阳朔", "象鼻山"));
        put("贵阳", List.of("贵阳北站", "龙洞堡机场", "甲秀楼", "花果园"));
        put("阿坝", List.of("九寨沟口", "黄龙景区", "若尔盖", "四姑娘山"));
        put("甘孜", List.of("康定", "稻城亚丁", "理塘", "色达"));
    }};

    /** 从文本中匹配城市名，返回标准城市名 */
    private String matchCity(String text, String[][] cities) {
        for (String[] city : cities) {
            for (String key : city) {
                if (text.contains(key)) return city[0];
            }
        }
        return null;
    }

    /**
     * 提取出发地：识别"从XX出发""XX出发""回XX"等模式
     * @return 出发城市名，未识别返回 null
     */
    private String extractDeparture(String text, String[][] cities) {
        // "从XX出发" / "从XX启程"
        Matcher m1 = Pattern.compile("从([^\\s,，。！？去到前往回]{2,8}?)(?:出发|启程|动身|起)").matcher(text);
        if (m1.find()) {
            String dep = matchCity(m1.group(1), cities);
            if (dep != null) return dep;
        }
        // "XX出发"（前面没有"从"也算）
        Matcher m2 = Pattern.compile("([^\\s,，。！？去到前往从回]{2,6}?)出发").matcher(text);
        if (m2.find()) {
            String dep = matchCity(m2.group(1), cities);
            if (dep != null) return dep;
        }
        // "回XX"（回家/回程的意思）
        Matcher m3 = Pattern.compile("回([^\\s,，。！？去到出发]{2,6}?)(?:[，,。！？\\s]|$)").matcher(text);
        if (m3.find()) {
            String dep = matchCity(m3.group(1), cities);
            if (dep != null) return dep;
        }
        return null;
    }

    /**
     * 提取目的地：优先识别"去XX""到XX""前往XX"等模式，排除出发地
     * @return 目的城市名，未识别返回 null
     */
    private String extractDestination(String text, String[][] cities, String departure) {
        // 优先："去XX" / "到XX" / "前往XX" / "往XX"
        Pattern[] destPatterns = {
            Pattern.compile("去([^\\s,，。！？出发回]{2,10}?)"),
            Pattern.compile("到([^\\s,，。！？出发回]{2,10}?)"),
            Pattern.compile("前往([^\\s,，。！？]{2,10}?)"),
            Pattern.compile("往([^\\s,，。！？出发回]{2,10}?)"),
            Pattern.compile("抵达([^\\s,，。！？]{2,10}?)")
        };
        for (Pattern p : destPatterns) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                String candidate = m.group(1).trim();
                String matched = matchCity(candidate, cities);
                if (matched != null && !matched.equals(departure)) {
                    return matched;
                }
            }
        }

        // 没有明确"去/到"关键词：遍历城市表，跳过出发地
        for (String[] city : cities) {
            for (String key : city) {
                if (text.contains(key)) {
                    if (departure != null && city[0].equals(departure)) continue;
                    return city[0];
                }
            }
        }
        return null;
    }

    /** 统计文本中出现的城市数量 */
    private int countMentionedCities(String text, String[][] cities) {
        Set<String> found = new HashSet<>();
        for (String[] city : cities) {
            for (String key : city) {
                if (text.contains(key)) { found.add(city[0]); break; }
            }
        }
        return found.size();
    }

    /**
     * 规则降级解析
     */
    private Map<String, Object> fallbackParse(String text) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 先提取出发地
        String departure = extractDeparture(text, CITY_ALIASES);
        if (departure != null) {
            result.put("departure", departure);
        }

        // 2. 提取目的地（排除出发地）
        String dest = extractDestination(text, CITY_ALIASES, departure);
        if (dest != null) {
            result.put("destination", dest);
        }

        // 2.5 往返识别：用户明确说"回XX"且 XX=出发地，则标记 roundTrip=true，endPoint=departure
        if (departure != null) {
            Matcher rt = Pattern.compile("回" + Pattern.quote(departure) + "(?:[，,。！？\\s]|$)").matcher(text);
            if (rt.find() || text.contains("然后回" + departure) || text.contains("最后回" + departure)) {
                result.put("roundTrip", true);
                result.put("endPoint", departure);
            }
        }

        // 3. 景点→城市映射（从数据库动态加载）
        if (!result.containsKey("destination")) {
            Map<String, String> spotCityMap = getSpotCityMap();
            for (Map.Entry<String, String> entry : spotCityMap.entrySet()) {
                if (text.contains(entry.getKey())) {
                    result.put("destination", entry.getValue());
                    result.computeIfAbsent("mustVisit", k -> new ArrayList<String>());
                    ((java.util.List<String>) result.get("mustVisit")).add(entry.getKey());
                    break;
                }
            }
        }

        // 天数：支持"一天""两天"等中文数字 + "1天""2日"等阿拉伯数字
        if (text.contains("一日") || text.contains("1天") || text.contains("一天")) result.put("days", 1);
        else if (text.contains("两日") || text.contains("2天") || text.contains("两天") || text.contains("周末")) result.put("days", 2);
        else if (text.contains("三日") || text.contains("3天") || text.contains("三天") || text.contains("小长假")) result.put("days", 3);
        else if (text.contains("四日") || text.contains("4天") || text.contains("四天")) result.put("days", 4);
        else if (text.contains("五日") || text.contains("5天") || text.contains("五天")) result.put("days", 5);
        else if (text.contains("六日") || text.contains("6天") || text.contains("六天")) result.put("days", 6);
        else if (text.contains("一周") || text.contains("七日") || text.contains("7天") || text.contains("七天")) result.put("days", 7);

        // 偏好
        if (text.contains("轻松") || text.contains("休闲")) result.put("preference", "轻松休闲");
        else if (text.contains("网红") || text.contains("打卡")) result.put("preference", "网红打卡");
        else if (text.contains("小众") || text.contains("探索")) result.put("preference", "小众探索");
        else if (text.contains("美食") || text.contains("吃")) result.put("preference", "美食之旅");
        else if (text.contains("文化") || text.contains("历史")) result.put("preference", "文化体验");
        else if (text.contains("户外") || text.contains("冒险")) result.put("preference", "户外冒险");

        // 预算
        if (text.contains("穷游") || text.contains("省钱") || text.contains("经济")) result.put("budget", "经济实惠");
        else if (text.contains("高端") || text.contains("豪华") || text.contains("享受")) result.put("budget", "高端享受");

        // 出行方式
        if (text.contains("徒步")) result.put("travelMode", "hiking");
        else if (text.contains("公交") || text.contains("地铁")) result.put("travelMode", "transit");
        else if (text.contains("自驾") || text.contains("开车") || text.contains("驾车")) result.put("travelMode", "drive");

        return result;
    }

    // ═══════════════════════════════════════
    // 参数合并 & 校验
    // ═══════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void mergeParams(ChatContext ctx, Map<String, Object> extracted) {
        if (extracted.containsKey("destination") && extracted.get("destination") != null)
            ctx.destination = (String) extracted.get("destination");
        if (extracted.containsKey("departure") && extracted.get("departure") != null)
            ctx.departure = (String) extracted.get("departure");
        if (extracted.containsKey("endPoint") && extracted.get("endPoint") != null)
            ctx.endPoint = (String) extracted.get("endPoint");
        if (extracted.containsKey("roundTrip") && extracted.get("roundTrip") != null) {
            Object rt = extracted.get("roundTrip");
            if (rt instanceof Boolean) {
                ctx.roundTrip = (Boolean) rt;
            } else {
                ctx.roundTrip = Boolean.parseBoolean(String.valueOf(rt));
            }
        }
        if (extracted.containsKey("days") && extracted.get("days") != null)
            ctx.days = ((Number) extracted.get("days")).intValue();
        // 日期合并：DeepSeek 返回 yyyy-MM-dd 字符串，归一化后写入上下文
        if (extracted.containsKey("startDate") && extracted.get("startDate") != null) {
            String normalized = normalizeDate((String) extracted.get("startDate"));
            if (normalized != null) ctx.startDate = normalized;
        }
        if (extracted.containsKey("endDate") && extracted.get("endDate") != null) {
            String normalized = normalizeDate((String) extracted.get("endDate"));
            if (normalized != null) ctx.endDate = normalized;
        }
        if (extracted.containsKey("preference") && extracted.get("preference") != null)
            ctx.preference = (String) extracted.get("preference");
        if (extracted.containsKey("budget") && extracted.get("budget") != null)
            ctx.budget = (String) extracted.get("budget");
        if (extracted.containsKey("travelMode") && extracted.get("travelMode") != null)
            ctx.travelMode = (String) extracted.get("travelMode");
        if (extracted.containsKey("hikingProfile") && extracted.get("hikingProfile") != null) {
            Map<String, Object> existing = ctx.hikingProfile == null
                    ? new LinkedHashMap<>()
                    : objectMapper.convertValue(ctx.hikingProfile, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> incoming = objectMapper.convertValue(extracted.get("hikingProfile"),
                    new TypeReference<Map<String, Object>>() {});
            incoming.forEach((key, value) -> { if (value != null) existing.put(key, value); });
            ctx.hikingProfile = objectMapper.convertValue(existing, HikingProfileDTO.class);
        }

        // 合并必去景点
        Object mustVisit = extracted.get("mustVisit");
        if (mustVisit instanceof List) {
            List<String> spots = (List<String>) mustVisit;
            for (String spot : spots) {
                if (!ctx.mustVisit.contains(spot)) ctx.mustVisit.add(spot);
            }
        }

        // 处理删除景点意图
        Object removeSpots = extracted.get("removeSpots");
        if (removeSpots instanceof List) {
            List<String> toRemove = (List<String>) removeSpots;
            for (String spot : toRemove) {
                ctx.mustVisit.removeIf(s -> s.contains(spot) || spot.contains(s));
            }
        }
    }

    private boolean isAllRequiredPresent(ChatContext ctx) {
        return ctx.destination != null && ctx.days != null && ctx.days > 0;
    }

    private String buildAssistantText(ChatContext ctx, boolean allRequired) {
        StringBuilder sb = new StringBuilder();
        if (allRequired) {
            int days = ctx.days != null ? ctx.days : 2;
            String pref = ctx.preference != null ? ctx.preference : "轻松休闲";
            sb.append(String.format("好的！为你规划 %s %d日游，偏%s风格。",
                    ctx.destination, days, pref));
            if (ctx.budget != null) sb.append(String.format(" 预算控制在%s水平。", ctx.budget));
        } else {
            if (ctx.destination == null) {
                sb.append("你想去哪里玩呢？");
            } else if (ctx.days == null) {
                sb.append(String.format("去%s几天呢？", ctx.destination));
            }
        }
        return sb.toString();
    }

    private ChatResponse.ExtractedParams buildExtractedParams(ChatContext ctx) {
        int days = ctx.days != null ? ctx.days : 2;  // 默认 2 天
        // 日期：优先用用户指定的 startDate；未指定则用今天兜底
        DateTimeFormatter isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate start = (ctx.startDate != null) ? parseLooseDate(ctx.startDate) : null;
        if (start == null) start = LocalDate.now();
        LocalDate end = (ctx.endDate != null) ? parseLooseDate(ctx.endDate) : null;
        if (end == null) end = start.plusDays(days - 1);

        // 起终点兜底逻辑：
        // - departure 是城市级（如"成都"），不直接作为 startPoint
        // - 只要识别到出发城市，startPoint 就留空，由前端通过 wx.chooseLocation 让用户发送定位
        // - 如果用户之前已选过具体地点（ctx.startPoint 非空），则沿用
        // - 终点同理；往返路线时 endPoint 应 = startPoint（但 startPoint 未选时也留空）
        String startPoint = ctx.startPoint;   // 用户已选的具体地点（可能为空）
        String departureCity = ctx.departure; // AI 识别的出发城市
        // 出发城市已识别时，startPoint 留空，前端引导用户发送定位

        String endPoint = ctx.endPoint;
        boolean isRoundTrip = ctx.roundTrip != null && ctx.roundTrip;
        if (isRoundTrip && startPoint != null) {
            // 往返：终点 = 起点（仅当起点已确定时）
            endPoint = startPoint;
        }

        // 起点选项：出发城市已识别时返回非空标记，前端据此显示"点击发送定位"
        List<String> startOpts = departureCity != null
                ? Collections.singletonList("发送定位")
                : Collections.emptyList();
        // 终点选项：往返或目的地已识别时返回非空标记
        List<String> endOpts;
        if (isRoundTrip) {
            endOpts = startOpts; // 往返：终点同城
        } else if (ctx.destination != null) {
            endOpts = Collections.singletonList("发送定位");
        } else {
            endOpts = Collections.emptyList();
        }

        return ChatResponse.ExtractedParams.builder()
                .destination(ctx.destination)
                .startPoint(startPoint)
                .endPoint(endPoint)
                .startPointOptions(startOpts)
                .endPointOptions(endOpts)
                .days(ctx.days)
                .startDate(start.format(isoFmt))
                .endDate(end.format(isoFmt))
                .preference(ctx.preference != null ? ctx.preference : "轻松休闲")
                .budget(ctx.budget != null ? ctx.budget : "中等预算")
                .travelMode(ctx.travelMode != null ? ctx.travelMode : "drive")
                .travelModeLabel(modeLabel(ctx.travelMode))
                .mustVisit(new ArrayList<>(ctx.mustVisit))
                .hikingProfile(ctx.hikingProfile)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildParamPayload(ChatResponse.ExtractedParams params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("destination", params.getDestination());
        payload.put("startPoint", params.getStartPoint());
        payload.put("endPoint", params.getEndPoint());
        payload.put("startPointOptions", params.getStartPointOptions() != null ? params.getStartPointOptions() : Collections.emptyList());
        payload.put("endPointOptions", params.getEndPointOptions() != null ? params.getEndPointOptions() : Collections.emptyList());
        payload.put("days", params.getDays());
        payload.put("startDate", params.getStartDate());
        payload.put("endDate", params.getEndDate());
        payload.put("preference", params.getPreference());
        payload.put("budget", params.getBudget());
        payload.put("travelMode", params.getTravelMode());
        payload.put("travelModeLabel", params.getTravelModeLabel());
        payload.put("mustVisit", params.getMustVisit() != null ? params.getMustVisit() : Collections.emptyList());
        payload.put("hikingProfile", params.getHikingProfile());
        return payload;
    }

    /**
     * 构建 modify_trip 信息（intent=modify_trip 时）
     * 推导 actionType：targetSpotId+tripId → replace_spot；仅 modifyDayNum → regenerate_day；
     * targetSpotId 解析不到时，尝试服务端模糊匹配 currentTripSpots 自动推导；
     * 仍无法定位 → needs_clarify。多轮指代 swapAnother/undo/confirmProposal 原样透传。
     */
    private ChatResponse.ModifyTripInfo buildModifyTripInfo(Map<String, Object> aiParams, ChatContext ctx) {
        if (aiParams == null) return null;
        if (!"modify_trip".equals(aiParams.get("intent"))) return null;
        Integer dayNum = null;
        Object rawDay = aiParams.get("modifyDayNum");
        if (rawDay instanceof Number) {
            dayNum = ((Number) rawDay).intValue();
        } else if (rawDay != null) {
            try { dayNum = Integer.parseInt(String.valueOf(rawDay)); } catch (Exception ignored) {}
        }
        String target = aiParams.get("modifyTarget") != null ? String.valueOf(aiParams.get("modifyTarget")) : null;
        String targetSpotId = aiParams.get("targetSpotId") != null ? String.valueOf(aiParams.get("targetSpotId")) : null;
        Boolean swapAnother = boolOrNull(aiParams.get("swapAnother"));
        Boolean undo = boolOrNull(aiParams.get("undo"));
        Boolean confirmProposal = boolOrNull(aiParams.get("confirmProposal"));

        // 服务端兜底：AI 没返回 targetSpotId 但有 modifyTarget（景点名），在 currentTripSpots 中模糊匹配
        if ((targetSpotId == null || "null".equals(targetSpotId) || targetSpotId.isBlank())
                && target != null && !target.isBlank()
                && ctx.currentTripSpots != null && !ctx.currentTripSpots.isEmpty()) {
            Map<String, Object> matched = fuzzyMatchSpot(target, ctx.currentTripSpots);
            if (matched != null) {
                targetSpotId = String.valueOf(matched.get("spotId"));
                // 如果 AI 也没返回 dayNum，从匹配到的景点中取
                if (dayNum == null && matched.get("dayNum") instanceof Number) {
                    dayNum = ((Number) matched.get("dayNum")).intValue();
                }
            }
        }

        // 推导 actionType：多轮指代优先透传
        String actionType;
        boolean needsClarify = false;
        if (swapAnother != null && swapAnother) {
            actionType = "swap_another";
        } else if (undo != null && undo) {
            actionType = "undo";
        } else if (confirmProposal != null && confirmProposal) {
            actionType = "confirm_proposal";
        } else if (targetSpotId != null && !targetSpotId.isBlank() && !"null".equals(targetSpotId)) {
            actionType = "replace_spot";
        } else if (dayNum != null && dayNum > 0) {
            actionType = "regenerate_day";
        } else {
            actionType = "needs_clarify";
            needsClarify = true;
        }
        return ChatResponse.ModifyTripInfo.builder()
                .modifyDayNum(dayNum)
                .modifyTarget(target)
                .actionType(actionType)
                .targetSpotId(targetSpotId)
                .needsClarify(needsClarify)
                .swapAnother(swapAnother)
                .undo(undo)
                .confirmProposal(confirmProposal)
                .build();
    }

    /**
     * 在行程景点列表中模糊匹配景点名
     * 支持包含匹配（用户说的景点名是列表中景点名的子串，或反之）
     * 如"折多山观景台"匹配"折多山观景平台"
     */
    private Map<String, Object> fuzzyMatchSpot(String target, List<Map<String, Object>> spots) {
        if (target == null || target.isBlank() || spots == null) return null;
        String normalizedTarget = target.replaceAll("\\s+", "").toLowerCase();
        // 第一轮：精确匹配（忽略大小写和空格）
        for (Map<String, Object> s : spots) {
            String name = String.valueOf(s.get("name")).replaceAll("\\s+", "").toLowerCase();
            if (name.equals(normalizedTarget)) return s;
        }
        // 第二轮：包含匹配（用户说的包含列表名，或列表名包含用户说的）
        // 优先选更长的匹配（更精确）
        Map<String, Object> best = null;
        int bestLen = 0;
        for (Map<String, Object> s : spots) {
            String name = String.valueOf(s.get("name")).replaceAll("\\s+", "").toLowerCase();
            if (name.contains(normalizedTarget) || normalizedTarget.contains(name)) {
                int matchLen = Math.min(name.length(), normalizedTarget.length());
                if (matchLen > bestLen) {
                    best = s;
                    bestLen = matchLen;
                }
            }
        }
        if (best != null) return best;
        // 第三轮：计算字符重叠率，选重叠度最高的
        double bestScore = 0.0;
        for (Map<String, Object> s : spots) {
            String name = String.valueOf(s.get("name")).replaceAll("\\s+", "").toLowerCase();
            double score = charOverlap(normalizedTarget, name);
            if (score > bestScore && score >= 0.5) {
                best = s;
                bestScore = score;
            }
        }
        return best;
    }

    private double charOverlap(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<Character> setA = new HashSet<>();
        for (char c : a.toCharArray()) setA.add(c);
        int overlap = 0;
        Set<Character> seen = new HashSet<>();
        for (char c : b.toCharArray()) {
            if (setA.contains(c) && !seen.contains(c)) {
                overlap++;
                seen.add(c);
            }
        }
        return (double) overlap / Math.max(setA.size(), b.chars().distinct().count());
    }

    private int findSpotDayNum(ChatContext ctx, String spotId) {
        if (ctx.currentTripSpots == null || spotId == null) return 0;
        for (Map<String, Object> s : ctx.currentTripSpots) {
            if (spotId.equals(String.valueOf(s.get("spotId")))) {
                Object dn = s.get("dayNum");
                if (dn instanceof Number) return ((Number) dn).intValue();
            }
        }
        return 0;
    }

    private Boolean boolOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private String modeLabel(String mode) {
        if (mode == null) return "自驾";
        return switch (mode) {
            case "hiking" -> "徒步";
            case "transit" -> "公交";
            default -> "自驾";
        };
    }

    /**
     * 日期防御性归一化：兼容 yyyy-MM-dd / M月d日 / yyyy年M月d日 / yyyy/M/d / M-d 等，
     * 缺年份补当前年。解析失败回退到今天。供 confirmAndGenerate 落库前兜底。
     */
    private String normalizeDate(String raw) {
        LocalDate parsed = parseLooseDate(raw);
        if (parsed == null) parsed = LocalDate.now();
        return parsed.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /**
     * 宽松日期解析：返回 LocalDate，无法识别返回 null（不抛异常）。
     */
    private LocalDate parseLooseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        int year = LocalDate.now().getYear();

        // 1. yyyy-MM-dd 或 yyyy-M-d
        Matcher m1 = Pattern.compile("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})$").matcher(s);
        if (m1.matches()) {
            try { return LocalDate.of(Integer.parseInt(m1.group(1)), Integer.parseInt(m1.group(2)), Integer.parseInt(m1.group(3))); }
            catch (DateTimeException e) { return null; }
        }
        // 2. yyyy年M月d日
        Matcher m2 = Pattern.compile("^(\\d{4})年(\\d{1,2})月(\\d{1,2})日$").matcher(s);
        if (m2.matches()) {
            try { return LocalDate.of(Integer.parseInt(m2.group(1)), Integer.parseInt(m2.group(2)), Integer.parseInt(m2.group(3))); }
            catch (DateTimeException e) { return null; }
        }
        // 3. M月d日（补当前年）
        Matcher m3 = Pattern.compile("^(\\d{1,2})月(\\d{1,2})日$").matcher(s);
        if (m3.matches()) {
            try { return LocalDate.of(year, Integer.parseInt(m3.group(1)), Integer.parseInt(m3.group(2))); }
            catch (DateTimeException e) { return null; }
        }
        // 4. M-d 或 M/d（补当前年）
        Matcher m4 = Pattern.compile("^(\\d{1,2})[-/](\\d{1,2})$").matcher(s);
        if (m4.matches()) {
            try { return LocalDate.of(year, Integer.parseInt(m4.group(1)), Integer.parseInt(m4.group(2))); }
            catch (DateTimeException e) { return null; }
        }
        // 5. 尝试 ISO 标准解析
        try { return LocalDate.parse(s); } catch (DateTimeException e) { return null; }
    }

    /**
     * 加载当前行程的景点列表（name + id + dayNum + 坐标），供 LLM 在有界集合内做指代消解。
     * done 阶段：用户提到的景点一定来自这个列表，LLM 解析出精确 spotId，后端按 id 定位。
     */
    private List<Map<String, Object>> loadCurrentTripSpots(String tripId) {
        if (tripId == null || tripId.isBlank()) return Collections.emptyList();
        try {
            List<TripDay> days = tripDayRepository.findByTripId(tripId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (TripDay day : days) {
                List<TripSpot> spots = tripSpotRepository.findByTripDayId(day.getId());
                for (TripSpot s : spots) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("spotId", s.getId());
                    item.put("name", s.getName());
                    item.put("dayNum", day.getDay());
                    item.put("category", s.getCategory());
                    result.add(item);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("加载行程景点列表失败 tripId={}: {}", tripId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private void ensureConversation(String userId, String conversationId, String titleSeed) {
        if (conversationId == null || conversationId.isBlank()) return;
        ChatConversation existing = chatConversationRepository.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getUserId, userId));
        if (existing != null) {
            if ("deleted".equals(existing.getStatus())) {
                existing.setStatus("active");
                chatConversationRepository.updateById(existing);
            }
            return;
        }
        ChatConversation conversation = new ChatConversation();
        conversation.setId(conversationId);
        conversation.setUserId(userId);
        conversation.setTitle(abbreviate(firstNotBlank(titleSeed, "新对话"), 40));
        conversation.setSummary("");
        conversation.setLastMessage("");
        conversation.setMessageCount(0);
        conversation.setStatus("active");
        chatConversationRepository.insert(conversation);
    }

    /**
     * 🚀 B1: 从 DB 恢复 ChatContext（支持多实例 / 重启后不丢上下文）
     * 从最近一条 param_card 消息的 payload 反序列化出对话参数，
     * 再从最近一条 user 消息获取 conversation 元数据。
     * 内存缓存没有时才调用，避免每轮都读 DB。
     */
    private ChatContext restoreContextFromDb(String conversationId) {
        try {
            // 1. 找最近一条 param_card 消息
            List<ChatMessage> paramCards = chatMessageRepository.selectList(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, conversationId)
                    .eq(ChatMessage::getType, "param_card")
                    .orderByDesc(ChatMessage::getSortOrder)
                    .last("LIMIT 1"));
            if (paramCards.isEmpty()) return null;

            Map<String, Object> payload = fromJsonObject(paramCards.get(0).getPayloadJson());
            if (payload == null) return null;

            ChatContext ctx = new ChatContext();
            ctx.destination = (String) payload.get("destination");
            ctx.departure = (String) payload.get("departure");
            ctx.startPoint = (String) payload.get("startPoint");
            ctx.endPoint = (String) payload.get("endPoint");

            Object daysObj = payload.get("days");
            if (daysObj instanceof Number) ctx.days = ((Number) daysObj).intValue();

            ctx.startDate = (String) payload.get("startDate");
            ctx.endDate = (String) payload.get("endDate");
            ctx.preference = (String) payload.get("preference");
            ctx.budget = (String) payload.get("budget");
            ctx.travelMode = (String) payload.get("travelMode");
            if (payload.get("hikingProfile") != null) {
                ctx.hikingProfile = objectMapper.convertValue(payload.get("hikingProfile"), HikingProfileDTO.class);
            }

            Object mv = payload.get("mustVisit");
            if (mv instanceof List) {
                ctx.mustVisit = new ArrayList<>((List<String>) mv);
            }

            // 2. 从最近一条 assistant text 推断 phase
            List<ChatMessage> lastMsgs = chatMessageRepository.selectList(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, conversationId)
                    .eq(ChatMessage::getRole, "assistant")
                    .orderByDesc(ChatMessage::getSortOrder)
                    .last("LIMIT 5"));
            boolean hasTripCard = lastMsgs.stream().anyMatch(m -> "trip_card".equals(m.getType()));
            boolean hasProgress = lastMsgs.stream().anyMatch(m -> "progress".equals(m.getType()));
            if (hasTripCard) ctx.phase = "done";
            else if (hasProgress) ctx.phase = "generating";
            else if (paramCards.get(0).getType() != null) ctx.phase = "params_collected";

            // 3. 从 DB 恢复 currentTripId（最近一条携带 tripId 的消息）
            List<ChatMessage> tripMsgs = chatMessageRepository.selectList(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, conversationId)
                    .eq(ChatMessage::getRole, "assistant")
                    .in(ChatMessage::getType, "trip_card", "map_card")
                    .orderByDesc(ChatMessage::getSortOrder)
                    .last("LIMIT 1"));
            if (!tripMsgs.isEmpty()) {
                Map<String, Object> tripPayload = fromJsonObject(tripMsgs.get(0).getPayloadJson());
                if (tripPayload != null && tripPayload.get("tripId") != null) {
                    ctx.currentTripId = (String) tripPayload.get("tripId");
                    ctx.currentTripSpots = loadCurrentTripSpots(ctx.currentTripId);
                }
            }

            ctx.lastActivity = System.currentTimeMillis();
            log.info("✅ 从 DB 恢复对话上下文: conversationId={}, dest={}, days={}, phase={}",
                    conversationId, ctx.destination, ctx.days, ctx.phase);
            return ctx;
        } catch (Exception e) {
            log.warn("从 DB 恢复上下文失败: {}", e.getMessage());
            return null;
        }
    }

    private void appendMessage(String userId, String conversationId, String role, String type, String content, Object payload, String clientMsgId) {
        Integer maxOrder = chatMessageRepository.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .eq(ChatMessage::getUserId, userId)
                        .orderByDesc(ChatMessage::getSortOrder)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .map(ChatMessage::getSortOrder)
                .orElse(-1);
        appendMessage(userId, conversationId, role, type, content, payload, clientMsgId, maxOrder + 1);
    }

    private void appendMessage(String userId, String conversationId, String role, String type, String content, Object payload, String clientMsgId, int sortOrder) {
        if (conversationId == null || conversationId.isBlank()) return;
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setType(type);
        message.setContent(content);
        message.setPayloadJson(toJson(payload));
        message.setClientMsgId(clientMsgId);
        message.setSortOrder(sortOrder);
        chatMessageRepository.insert(message);
    }

    private void updateConversationMeta(String userId, String conversationId, String lastMessage, int addedCount) {
        ChatConversation conversation = chatConversationRepository.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getUserId, userId));
        int count = conversation != null && conversation.getMessageCount() != null ? conversation.getMessageCount() : 0;
        String title = conversation != null ? conversation.getTitle() : "";
        chatConversationRepository.update(null, new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getUserId, userId)
                .set(ChatConversation::getTitle, abbreviate(firstNotBlank(title, lastMessage, "新对话"), 40))
                .set(ChatConversation::getLastMessage, abbreviate(lastMessage, 255))
                .set(ChatConversation::getMessageCount, count + addedCount)
                .set(ChatConversation::getStatus, "active"));
    }

    private ChatConversationDTO toConversationDTO(ChatConversation row) {
        return ChatConversationDTO.builder()
                .id(row.getId())
                .title(row.getTitle())
                .summary(row.getSummary())
                .lastMessage(row.getLastMessage())
                .messageCount(row.getMessageCount())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    private ChatHistoryDTO.MessageItem toHistoryMessage(ChatMessage row) {
        return ChatHistoryDTO.MessageItem.builder()
                .id(firstNotBlank(row.getClientMsgId(), row.getId() != null ? "srv_" + row.getId() : null))
                .role(row.getRole())
                .type(row.getType())
                .content(row.getContent())
                .payload(fromJson(row.getPayloadJson()))
                .timestamp(row.getCreatedAt() != null
                        ? row.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : null)
                .build();
    }

    private String buildTitleFromMessages(List<ChatSnapshotRequest.MessageItem> messages) {
        for (ChatSnapshotRequest.MessageItem item : messages) {
            if ("user".equals(item.getRole()) && item.getContent() != null && !item.getContent().isBlank()) {
                return item.getContent();
            }
        }
        return null;
    }

    private String firstNotBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private String abbreviate(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.length() <= max) return trimmed;
        return trimmed.substring(0, Math.max(0, max - 1)) + "…";
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("chat payload serialize failed: {}", e.getMessage());
            return null;
        }
    }

    private Object fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            log.warn("chat payload parse failed: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> fromJsonObject(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            log.warn("chat payload object parse failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 🚀 B5: 定时清理过期上下文 — 每 10 分钟执行一次
     * 清理超过 30 分钟无活跃的 ChatContext 和 dedupCache，释放内存
     */
    @Scheduled(fixedRate = 600000)
    public void cleanupStaleContexts() {
        long now = System.currentTimeMillis();
        long maxIdleMs = 30 * 60 * 1000; // 30 分钟

        int ctxBefore = contexts.size();
        contexts.entrySet().removeIf(e -> (now - e.getValue().lastActivity) > maxIdleMs);
        int ctxAfter = contexts.size();

        int dedupBefore = dedupCache.size();
        dedupCache.entrySet().removeIf(e -> (now - e.getValue()) > 60000); // 去重缓存保留 60s
        int dedupAfter = dedupCache.size();

        if (ctxBefore != ctxAfter || dedupBefore != dedupAfter) {
            log.info("🧹 清理过期上下文: contexts {}→{}, dedupCache {}→{}",
                    ctxBefore, ctxAfter, dedupBefore, dedupAfter);
        }
    }

    // ═══════════════════════════════════════
    // 对话上下文
    // ═══════════════════════════════════════

    /**
     * 🚀 通过 WebSocket 推送 AI 优化后的参数
     */
    private void pushOptimizedParams(String conversationId, ChatContext ctx) {
        try {
            // 构建优化后的参数
            ChatResponse.ExtractedParams params = buildExtractedParams(ctx);
            Map<String, Object> paramPayload = buildParamPayload(params);
            paramPayload.put("editing", false);
            paramPayload.put("mustVisit", params.getMustVisit() != null ? params.getMustVisit() : Collections.emptyList());

            // 构建推送消息
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "param_optimized");
            message.put("data", Map.of(
                    "text", "AI 已优化你的行程参数 ✨",
                    "params", paramPayload
            ));

            // 🚀 通过聊天 WebSocket 推送
            progressEmitter.sendToChat(conversationId, "param_optimized", message);

            log.info("📨 WebSocket 推送成功: conversationId={}", conversationId);
        } catch (Exception e) {
            log.warn("⚠️ WebSocket 推送失败: {}", e.getMessage());
        }
    }

    static class ChatContext {
        String destination;
        String departure;   // 出发城市（AI识别，城市级，如"成都"）
        String startPoint;  // 起点具体地点（用户选择，如"春熙路"；未选时为null）
        String endPoint;    // 终点（返程地；往返时 = startPoint）
        Boolean roundTrip;  // 是否往返路线
        Integer days;
        String startDate;   // yyyy-MM-dd，用户可指定出发日期
        String endDate;     // yyyy-MM-dd
        String preference;
        String budget;
        String travelMode;
        HikingProfileDTO hikingProfile;
        List<String> mustVisit = new ArrayList<>();
        String phase = "welcome";
        long lastActivity = System.currentTimeMillis();
        /** 已生成行程的景点列表（done 阶段注入，供 LLM 指代消解） */
        List<Map<String, Object>> currentTripSpots;
        /** 上一轮 AI 提议的动作（支持"换一个/撤销/确认"多轮指代） */
        Map<String, Object> lastProposedAction;
        /** 当前对话关联的 tripId（done 阶段用于加载景点列表） */
        String currentTripId;
    }
}
