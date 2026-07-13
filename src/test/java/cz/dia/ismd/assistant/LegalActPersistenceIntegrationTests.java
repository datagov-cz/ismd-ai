package cz.dia.ismd.assistant;

import cz.dia.ismd.assistant.model.legal.LegalAct;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.service.LegalActService;
import cz.dia.ismd.assistant.service.LegalActTextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;

class LegalActPersistenceIntegrationTests extends AssistantIntegrationTest {

    @Autowired
    private LegalActService legalActService;

    @Autowired
    private LegalActTextService legalActTextService;

    @Test
    void createsReadsUpdatesAndDeletesLegalActsAndTheirTexts() {
        LegalAct createdAct = legalActService.create(new LegalAct(
                null,
                12,
                Year.of(2025),
                LocalDate.of(2025, 2, 3),
                "Original title",
                null,
                null
        ));

        assertThat(createdAct.id()).isNotNull();
        assertThat(createdAct.createdAt()).isNotNull();
        assertThat(legalActService.find(12, Year.of(2025), LocalDate.of(2025, 2, 3)))
                .contains(createdAct);

        LegalActText firstText = legalActTextService.create(new LegalActText(
                null, createdAct.id(), "First version", "official-1", "12/2025", null
        ));
        LegalActText secondText = legalActTextService.create(new LegalActText(
                null, createdAct.id(), "Second version", "official-2", "12/2025", null
        ));

        LegalActText linkedFirstText = legalActTextService.update(firstText.id(), new LegalActText(
                firstText.id(), createdAct.id(), "First version", "official-1", "12/2025", secondText.id()
        )).orElseThrow();
        assertThat(linkedFirstText.successorId()).isEqualTo(secondText.id());
        assertThat(legalActTextService.findByLegalActId(createdAct.id()))
                .extracting(LegalActText::id)
                .containsExactly(firstText.id(), secondText.id());

        LegalAct updatedAct = legalActService.update(createdAct.id(), new LegalAct(
                createdAct.id(), 12, Year.of(2025), LocalDate.of(2025, 2, 3),
                "Updated title", createdAct.createdAt(), createdAct.updatedAt()
        )).orElseThrow();
        assertThat(updatedAct.title()).isEqualTo("Updated title");

        legalActTextService.update(firstText.id(), new LegalActText(
                firstText.id(), createdAct.id(), "First version", "official-1", "12/2025", null
        ));
        assertThat(legalActTextService.delete(secondText.id())).isTrue();
        assertThat(legalActTextService.delete(firstText.id())).isTrue();
        assertThat(legalActService.delete(createdAct.id())).isTrue();
        assertThat(legalActService.findById(createdAct.id())).isEmpty();
    }
}
