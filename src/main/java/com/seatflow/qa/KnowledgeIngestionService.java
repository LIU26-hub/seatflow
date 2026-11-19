package com.seatflow.qa;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Loads the curated rules and FAQ into the vector store.
 *
 * <p>中文批注：这里是幂等入口，管理端可以随时重新执行。document id 使用规则编号，
 * 因此重复导入会覆盖同一条规则，而不是制造重复片段。
 */
@Service
public class KnowledgeIngestionService {

    private final KnowledgeDocumentLoader loader;
    private final VectorStore vectorStore;
    private final String knowledgePath;

    public KnowledgeIngestionService(KnowledgeDocumentLoader loader,
                                     VectorStore vectorStore,
                                     @Value("${seatflow.qa.knowledge-path}") String knowledgePath) {
        this.loader = loader;
        this.vectorStore = vectorStore;
        this.knowledgePath = knowledgePath;
    }

    public int ingest() {
        List<Document> documents = loader.load(knowledgePath);
        vectorStore.add(documents);
        return documents.size();
    }
}
