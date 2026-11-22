package com.seatflow.activity;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Indexes activities into the vector store so QA can recall them by meaning.
 *
 * <p>中文批注：活动创建/更新时，把标题、介绍、场地拼成一个文档写入向量库，
 * 元数据带 activityId，供"最近有什么 AI 相关活动"这类模糊搜索召回。
 * document id 固定为 "activity-" 加活动 id：重复入库会覆盖同一条而不是越积越多，
 * 同时避免和规则/FAQ 的 id（R-01、FAQ-01 之类）撞名。
 */
@Slf4j
@Service
public class ActivityIndexingService {

    private static final String ID_PREFIX = "activity-";
    private static final String SOURCE_TYPE = "ACTIVITY";

    private final VectorStore vectorStore;

    public ActivityIndexingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 写入或覆盖一条活动文档。 */
    public void index(Activity activity) {
        try {
            vectorStore.add(List.of(toDocument(activity)));
        } catch (RuntimeException ex) {
            // 向量库不可用不应让活动创建/更新失败：Redis 不在正确性关键路径上（R-02）。
            log.warn("failed to index activity {}: {}", activity.getId(), ex.getMessage());
        }
    }

    /** 活动下线/删除时把对应文档移出向量库。 */
    public void remove(Long activityId) {
        try {
            vectorStore.delete(List.of(ID_PREFIX + activityId));
        } catch (RuntimeException ex) {
            log.warn("failed to remove activity {} from vector store: {}", activityId, ex.getMessage());
        }
    }

    private Document toDocument(Activity activity) {
        return new Document(
                ID_PREFIX + activity.getId(),
                buildText(activity),
                Map.of(
                        "activityId", activity.getId(),
                        "title", activity.getTitle(),
                        "location", activity.getLocation(),
                        "sourceType", SOURCE_TYPE,
                        "sourceId", ID_PREFIX + activity.getId()));
    }

    private String buildText(Activity activity) {
        StringBuilder text = new StringBuilder(activity.getTitle());
        if (activity.getDescription() != null && !activity.getDescription().isBlank()) {
            text.append('\n').append(activity.getDescription());
        }
        if (activity.getLocation() != null && !activity.getLocation().isBlank()) {
            text.append('\n').append("地点：").append(activity.getLocation());
        }
        text.append('\n').append("报名时间：")
                .append(activity.getRegistrationStart())
                .append(" 至 ")
                .append(activity.getRegistrationEnd());
        return text.toString();
    }
}
