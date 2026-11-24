package com.seatflow.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.seatflow.activity.Activity;
import com.seatflow.activity.ActivityRepository;
import com.seatflow.qa.dto.QaRequest;
import com.seatflow.qa.dto.QaResponse;

@ExtendWith(MockitoExtension.class)
class QaServiceTest {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ChatModel chatModel;

    private QaService qaService;

    @BeforeEach
    void setUp() {
        qaService = new QaService(vectorStore, activityRepository, chatModel, 5, 0.35);
    }

    @Test
    void refusesWithoutCallingTheModelWhenNothingIsRetrieved() {
        given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of());

        QaResponse response = qaService.answer(new QaRequest("今天食堂有什么菜", null));

        assertThat(response.answer()).contains("无法从现有资料");
        assertThat(response.citations()).isEmpty();
        then(chatModel).should(never()).call(any(Prompt.class));
    }

    @Test
    void citesKnowledgeAndLiveActivityFacts() {
        Document rule = new Document("BR-08", "确认时限为 15 分钟", Map.of(
                "title", "BR-08 确认时限",
                "sourceType", "RULE",
                "sourceId", "BR-08"));
        given(vectorStore.similaritySearch(any(SearchRequest.class))).willReturn(List.of(rule));
        Activity activity = new Activity();
        activity.setId(5L);
        activity.setTitle("AI meetup");
        activity.setLocation("Main hall");
        given(activityRepository.findById(5L)).willReturn(java.util.Optional.of(activity));
        given(chatModel.call(any(Prompt.class)))
                .willReturn(new ChatResponse(List.of(new Generation(
                        new AssistantMessage("请在 15 分钟内确认 [1]，地点见 [A]。")))));

        QaResponse response = qaService.answer(new QaRequest("多久确认？在哪办？", 5L));

        assertThat(response.answer()).contains("15 分钟");
        assertThat(response.citations()).hasSize(2);
        assertThat(response.citations().get(0).label()).isEqualTo("1");
        assertThat(response.citations().get(0).sourceId()).isEqualTo("BR-08");
        assertThat(response.citations().get(1).label()).isEqualTo("A");
        assertThat(response.citations().get(1).sourceType()).isEqualTo("ACTIVITY");
        assertThat(response.citations().get(1).sourceId()).isEqualTo("activity-5");
    }
}
