package cz.dia.ismd.assistant.controller.legalrepository;

import cz.dia.ismd.assistant.dto.legalrepository.LegalActRepositoryResponse;
import cz.dia.ismd.assistant.records.legalrepository.LegalActRepositoryRecord;
import cz.dia.ismd.assistant.service.legalrepository.LegalActRepositoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegalActRepositoryController {

    private final LegalActRepositoryService legalActRepositoryService;

    public LegalActRepositoryController(LegalActRepositoryService legalActRepositoryService) {
        this.legalActRepositoryService = legalActRepositoryService;
    }

    @PostMapping("/legal-acts/repository/import")
    public LegalActRepositoryResponse importLegalActs() {
        return toResponse(legalActRepositoryService.importLegalActs());
    }

    @GetMapping("/legal-acts/repository")
    public LegalActRepositoryResponse getLegalActs() {
        return toResponse(legalActRepositoryService.currentRecord());
    }

    private LegalActRepositoryResponse toResponse(LegalActRepositoryRecord record) {
        return new LegalActRepositoryResponse(
                record.savedAt(),
                record.query(),
                record.legalActIris().size(),
                record.legalActIris()
        );
    }
}
