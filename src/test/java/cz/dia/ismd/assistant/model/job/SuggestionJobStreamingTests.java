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
    void pollingDrainsOnlyNewSuggestionsWhileKeepingCompleteHistory() {
        SuggestionJob job = new SuggestionJob(UUID.randomUUID(), JobKind.CLASS, null);
        ClassSuggestion first = suggestion("first");
        ClassSuggestion second = suggestion("second");

        job.addClassSuggestion(first);
        assertThat(job.status()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(job.drainClassSuggestions()).containsExactly(first);
        assertThat(job.drainClassSuggestions()).isEmpty();

        job.addClassSuggestion(second);
        job.completeClasses(List.of(first, second));
        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.drainClassSuggestions()).containsExactly(second);
        assertThat(job.classSuggestions()).containsExactly(first, second);
    }

    private ClassSuggestion suggestion(String id) {
        return new ClassSuggestion(
                id, LangString.cs(id), null, null, TermType.CLASS, List.of(), "/eli/cz/sb/2024/1");
    }
}
