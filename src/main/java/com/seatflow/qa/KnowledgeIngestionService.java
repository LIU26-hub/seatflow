package com.seatflow.qa;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.seatflow.common.BusinessException;
import com.seatflow.activity.Activity;
import com.seatflow.activity.ActivityIndexingService;
import com.seatflow.activity.ActivityRepository;
import com.seatflow.activity.ActivityStatus;
import com.seatflow.qa.dto.ReindexResult;
import com.seatflow.user.CurrentUser;
import com.seatflow.user.CurrentUserProvider;

/**
 * Loads the curated rules and FAQ into the vector store.
 *
 * <p>中文批注：这里是幂等入口，只允许管理员执行。document id 使用规则编号，
 * 因此重复导入会覆盖同一条规则，而不是制造重复片段。
 */
@Service
public class KnowledgeIngestionService {

    private final KnowledgeDocumentLoader loader;
    private final VectorStore vectorStore;
    private final String knowledgePath;
    private final CurrentUserProvider currentUserProvider;
    private final ActivityRepository activityRepository;
    private final ActivityIndexingService activityIndexingService;

    public KnowledgeIngestionService(KnowledgeDocumentLoader loader,
                                     VectorStore vectorStore,
                                     @Value("${seatflow.qa.knowledge-path}") String knowledgePath,
                                     CurrentUserProvider currentUserProvider,
                                     ActivityRepository activityRepository,
                                     ActivityIndexingService activityIndexingService) {
        this.loader = loader;
        this.vectorStore = vectorStore;
        this.knowledgePath = knowledgePath;
        this.currentUserProvider = currentUserProvider;
        this.activityRepository = activityRepository;
        this.activityIndexingService = activityIndexingService;
    }

    /**
     * Rebuilds both halves of the knowledge base: curated rules/FAQ and the
     * published activities. Activity indexing uses a deterministic id, so this
     * is safe to run repeatedly.
     */
    public ReindexResult ingest() {
        CurrentUser actor = currentUserProvider.require();
        if (!actor.isAdmin()) {
            throw new BusinessException(403, "only admin can rebuild the knowledge base");
        }
        List<Document> documents = loader.load(knowledgePath);
        vectorStore.add(documents);

        List<Activity> activities = activityRepository
                .findByStatusOrderByRegistrationStartAsc(ActivityStatus.PUBLISHED);
        activities.forEach(activityIndexingService::index);

        return new ReindexResult(documents.size(), activities.size());
    }
}
