package com.seatflow.qa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Turns the curated knowledge file into one document per section.
 *
 * <p>中文批注：每条规则、每条 FAQ 天然就是一个语义完整的片段，按二级标题切分比
 * 固定长度切分更可靠。document id 用 sourceId，重复入库时可以覆盖而不是堆积。
 */
@Component
public class KnowledgeDocumentLoader {

    private final ResourceLoader resourceLoader;

    public KnowledgeDocumentLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public List<Document> load(String location) {
        Resource resource = resourceLoader.getResource(location);
        String content;
        try {
            content = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read knowledge file " + location, ex);
        }

        String title = null;
        String sourceType = null;
        String sourceId = null;
        StringBuilder body = new StringBuilder();
        List<Document> documents = new ArrayList<>();

        for (String line : content.split("\\R")) {
            if (line.startsWith("## ")) {
                if (title != null) {
                    documents.add(toDocument(title, sourceType, sourceId, body.toString()));
                }
                String heading = line.substring(3).trim();
                int closing = heading.indexOf(']');
                sourceType = closing > 1 && heading.startsWith("[")
                        ? heading.substring(1, closing) : "NOTE";
                String rest = closing > 1 ? heading.substring(closing + 1).trim() : heading;
                int space = rest.indexOf(' ');
                sourceId = space > 0 ? rest.substring(0, space) : rest;
                title = rest;
                body = new StringBuilder();
            } else if (title != null) {
                body.append(line).append('\n');
            }
        }
        if (title != null) {
            documents.add(toDocument(title, sourceType, sourceId, body.toString()));
        }
        return documents;
    }

    private Document toDocument(String title, String sourceType, String sourceId, String body) {
        return new Document(
                sourceId,
                title + "\n" + body.trim(),
                Map.of(
                        "title", title,
                        "sourceType", sourceType,
                        "sourceId", sourceId));
    }
}
