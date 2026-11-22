package com.seatflow.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Structural guard for the evaluation set.
 *
 * <p>中文批注：这里不调用真实模型，只校验评测集本身是否可用：题目非空、
 * 标注的标准来源在知识库里真实存在、且没有重复题。真正的检索召回率评测
 * 需要 SiliconFlow Key 与 Redis Stack，放在手工执行步骤里。
 */
class QaEvaluationDatasetTest {

    record EvaluationCase(String question, List<String> expectedSourceIds) {
    }

    @Test
    void datasetIsWellFormedAndPointsAtRealSources() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = new ClassPathResource("eval/qa-eval.json").getInputStream()) {
            List<EvaluationCase> cases = mapper.readValue(
                    in, new TypeReference<List<EvaluationCase>>() {
                    });

            assertThat(cases).hasSizeGreaterThanOrEqualTo(15);

            Set<String> questions = new HashSet<>();
            for (EvaluationCase testCase : cases) {
                assertThat(testCase.question()).isNotBlank();
                assertThat(testCase.expectedSourceIds()).isNotEmpty();
                assertThat(questions.add(testCase.question()))
                        .as("duplicate question: %s", testCase.question())
                        .isTrue();
            }

            List<org.springframework.ai.document.Document> documents =
                    new KnowledgeDocumentLoader(new org.springframework.core.io.DefaultResourceLoader())
                            .load("classpath:knowledge/seatflow-knowledge.md");
            Set<String> knownIds = new HashSet<>();
            documents.forEach(document ->
                    knownIds.add(String.valueOf(document.getMetadata().get("sourceId"))));

            assertThat(cases).allSatisfy(testCase -> testCase.expectedSourceIds().forEach(id ->
                    assertThat(knownIds).as("unknown source id %s", id).contains(id)));
        }
    }
}
