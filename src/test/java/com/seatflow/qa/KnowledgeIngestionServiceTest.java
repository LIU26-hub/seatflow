package com.seatflow.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import com.seatflow.activity.Activity;
import com.seatflow.activity.ActivityIndexingService;
import com.seatflow.activity.ActivityRepository;
import com.seatflow.activity.ActivityStatus;
import com.seatflow.common.BusinessException;
import com.seatflow.qa.dto.ReindexResult;
import com.seatflow.user.CurrentUser;
import com.seatflow.user.CurrentUserProvider;
import com.seatflow.user.UserRole;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceTest {

    @Mock
    private KnowledgeDocumentLoader loader;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityIndexingService activityIndexingService;

    private KnowledgeIngestionService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeIngestionService(
                loader, vectorStore, "classpath:knowledge/seatflow-knowledge.md",
                currentUserProvider, activityRepository, activityIndexingService);
    }

    @Test
    void rejectsNonAdmin() {
        given(currentUserProvider.require())
                .willReturn(new CurrentUser(9L, "participant", UserRole.PARTICIPANT));

        assertThatThrownBy(service::ingest)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(403));
        then(vectorStore).should(never()).add(anyList());
    }

    @Test
    void rebuildsRulesAndPublishedActivities() {
        given(currentUserProvider.require())
                .willReturn(new CurrentUser(1L, "admin", UserRole.ADMIN));
        given(loader.load("classpath:knowledge/seatflow-knowledge.md"))
                .willReturn(List.of(
                        new Document("BR-01", "rule", Map.of("sourceId", "BR-01")),
                        new Document("FAQ-01", "faq", Map.of("sourceId", "FAQ-01"))));
        Activity activity = new Activity();
        activity.setId(5L);
        activity.setStatus(ActivityStatus.PUBLISHED);
        given(activityRepository.findByStatusOrderByRegistrationStartAsc(ActivityStatus.PUBLISHED))
                .willReturn(List.of(activity));

        ReindexResult result = service.ingest();

        assertThat(result.knowledgeChunks()).isEqualTo(2);
        assertThat(result.activities()).isEqualTo(1);
        then(vectorStore).should().add(anyList());
        then(activityIndexingService).should().index(activity);
    }
}
