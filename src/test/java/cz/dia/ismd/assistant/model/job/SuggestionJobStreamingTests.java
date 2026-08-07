package cz.dia.ismd.assistant.model.job;

import cz.dia.ismd.assistant.model.suggestion.LangString;
import cz.dia.ismd.assistant.model.suggestion.TermType;
import cz.dia.ismd.assistant.model.suggestion.classsuggestion.ClassSuggestion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionJobStreamingTests {

    @Test
    void pollingReturnsTheCompleteSuggestionSnapshotEveryTime() {
        SuggestionJob job = new SuggestionJob(UUID.randomUUID(), JobKind.CLASS, null);
        ClassSuggestion first = suggestion("first");
        ClassSuggestion second = suggestion("second");

        job.addClassSuggestion(first);
        assertThat(job.status()).isEqualTo(JobStatus.IN_PROGRESS);
        List<ClassSuggestion> firstPoll = job.classSuggestions();
        List<ClassSuggestion> repeatedFirstPoll = job.classSuggestions();
        assertThat(firstPoll).containsExactly(first);
        assertThat(repeatedFirstPoll).containsExactly(first);

        job.addClassSuggestion(second);
        job.completeClasses(List.of(first, second));
        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        List<ClassSuggestion> completedPoll = job.classSuggestions();
        List<ClassSuggestion> repeatedCompletedPoll = job.classSuggestions();
        assertThat(completedPoll).containsExactly(first, second);
        assertThat(repeatedCompletedPoll).containsExactly(first, second);
    }

    private ClassSuggestion suggestion(String id) {
        return new ClassSuggestion(
                id, LangString.cs(id), null, null, TermType.CLASS, List.of(), "/eli/cz/sb/2024/1");
    }
}
