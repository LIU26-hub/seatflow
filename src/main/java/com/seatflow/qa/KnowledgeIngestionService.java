package com.seatflow.qa;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.seatflow.common.BusinessException;
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

    public KnowledgeIngestionService(KnowledgeDocumentLoader loader,
                                     VectorStore vectorStore,
                                     @Value("${seatflow.qa.knowledge-path}") String knowledgePath,
                                     CurrentUserProvider currentUserProvider) {
        this.loader = loader;
        this.vectorStore = vectorStore;
        this.knowledgePath = knowledgePath;
        this.currentUserProvider = currentUserProvider;
    }

    public int ingest() {
        CurrentUser actor = currentUserProvider.require();
        if (!actor.isAdmin()) {
            throw new BusinessException(403, "only admin can rebuild the knowledge base");
        }
        List<Document> documents = loader.load(knowledgePath);
        vectorStore.add(documents);
        return documents.size();
    }
}