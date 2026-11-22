package com.seatflow.qa;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.seatflow.activity.Activity;
import com.seatflow.activity.ActivityRepository;
import com.seatflow.qa.dto.QaCitation;
import com.seatflow.qa.dto.QaRequest;
import com.seatflow.qa.dto.QaResponse;

/**
 * Retrieval augmented question answering.
 *
 * <p>中文批注：规则与 FAQ 走向量召回；活动时间、场地与介绍是高频变化的事实，
 * 当请求带 activityId 时直接从数据库读取最新值作为结构化上下文，避免向量库里的
 * 旧快照误导用户。生成模型只允许依据这两部分上下文作答，并输出引用编号。
 */
@Service
public class QaService {

    private static final String SYSTEM_PROMPT = """
            你是 SeatFlow 校园活动报名系统的客服助手。
            只能依据给定的【知识片段】与【活动信息】回答，不得编造。
            每条结论后用 [编号] 标注依据；知识片段不足以回答时，直接说明无法从现有资料确认。
            使用简体中文，回答简洁、可执行。
            """;

    private final VectorStore vectorStore;
    private final ActivityRepository activityRepository;
    private final ChatClient chatClient;
    private final int topK;
    private final double similarityThreshold;

    public QaService(VectorStore vectorStore,
                     ActivityRepository activityRepository,
                     ChatModel chatModel,
                     @Value("${seatflow.qa.top-k:5}") int topK,
                     @Value("${seatflow.qa.similarity-threshold:0.35}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.activityRepository = activityRepository;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public QaResponse answer(QaRequest request) {
        long start = System.currentTimeMillis();
        List<Document> matches = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.question())
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build());

        if (matches == null || matches.isEmpty()) {
            return new QaResponse("我无法从现有资料中确认这个问题，请联系活动组织者。",
                    List.of(), System.currentTimeMillis() - start);
        }

        List<QaCitation> citations = new ArrayList<>();
        StringBuilder knowledge = new StringBuilder();
        int index = 1;
        for (Document document : matches) {
            String title = String.valueOf(document.getMetadata().getOrDefault("title", "knowledge"));
            String label = String.valueOf(index);
            knowledge.append('[').append(label).append("] ").append(title)
                    .append('\n').append(document.getText()).append("\n\n");
            citations.add(new QaCitation(
                    label,
                    title,
                    String.valueOf(document.getMetadata().getOrDefault("sourceType", "NOTE")),
                    String.valueOf(document.getMetadata().getOrDefault("sourceId", ""))));
            index++;
        }

        if (request.activityId() != null) {
            activityRepository.findById(request.activityId()).ifPresent(activity -> {
                knowledge.append("[A] 活动信息\n").append(activityFacts(activity)).append('\n');
                citations.add(new QaCitation(
                        "A",
                        activity.getTitle(),
                        "ACTIVITY",
                        "activity-" + activity.getId()));
            });
        }

        String userPrompt = "【知识片段】\n" + knowledge
                + "\n【用户问题】\n" + request.question();
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
        return new QaResponse(answer, citations, System.currentTimeMillis() - start);
    }

    private String activityFacts(Activity activity) {
        return "活动名称：" + activity.getTitle()
                + "；地点：" + activity.getLocation()
                + "；报名开始：" + activity.getRegistrationStart()
                + "；报名结束：" + activity.getRegistrationEnd()
                + "；状态：" + activity.getStatus()
                + "；容量：" + activity.getCapacity()
                + "；已报名：" + activity.getRegisteredCount()
                + "；介绍：" + (activity.getDescription() == null ? "无" : activity.getDescription());
    }
}
