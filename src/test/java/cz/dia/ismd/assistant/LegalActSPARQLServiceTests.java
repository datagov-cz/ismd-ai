package cz.dia.ismd.assistant;

import cz.dia.ismd.assistant.model.legal.LegalAct;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import cz.dia.ismd.assistant.service.LegalActSPARQLService;
import cz.dia.ismd.assistant.service.LegalActService;
import cz.dia.ismd.assistant.service.LegalActTextService;
import cz.dia.ismd.assistant.service.SparqlQueryExecutor;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.LocalDate;
import java.time.Year;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalActSPARQLServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void retrievesLegalActFragmentsAndStoresThem() {
        SparqlQueryExecutor queryExecutor = mock(SparqlQueryExecutor.class);
        Environment environment = mock(Environment.class);
        LegalActService legalActService = mock(LegalActService.class);
        LegalActTextService legalActTextService = mock(LegalActTextService.class);
        RepositoryConnection connection = mock(RepositoryConnection.class);
        TupleQueryResult nameResult = mock(TupleQueryResult.class);
        TupleQueryResult contentResult = mock(TupleQueryResult.class);
        BindingSet nameBinding = mock(BindingSet.class);
        BindingSet firstFragment = fragmentBinding(
                "https://example.test/fragment/1", "§ 1", "First fragment"
        );
        BindingSet secondFragment = fragmentBinding(
                "https://example.test/fragment/2", "§ 2", "Second fragment"
        );
        var valueFactory = SimpleValueFactory.getInstance();

        when(environment.getProperty("app.sparql.eli.namespace"))
                .thenReturn("https://example.test/eli/cz/sb/");
        when(queryExecutor.query(anyString(), any())).thenAnswer(invocation -> {
            Function<RepositoryConnection, Object> operation = invocation.getArgument(1);
            return operation.apply(connection);
        });
        when(queryExecutor.evaluateTupleQuery(any(), anyString(), any()))
                .thenReturn(nameResult, contentResult);

        when(nameResult.hasNext()).thenReturn(true);
        when(nameResult.next()).thenReturn(nameBinding);
        when(nameBinding.getValue("nazev")).thenReturn(valueFactory.createLiteral("Test act"));

        when(contentResult.hasNext()).thenReturn(true, true, false);
        when(contentResult.next()).thenReturn(firstFragment, secondFragment);

        when(legalActService.find(1, Year.of(2024), LocalDate.of(2024, 1, 1)))
                .thenReturn(Optional.empty());
        when(legalActService.create(any())).thenReturn(new LegalAct(
                42L, 1, Year.of(2024), LocalDate.of(2024, 1, 1), "Test act", null, null
        ));
        AtomicLong textId = new AtomicLong(100);
        when(legalActTextService.create(any())).thenAnswer(invocation -> {
            LegalActText text = invocation.getArgument(0);
            return new LegalActText(
                    textId.getAndIncrement(), text.legalActId(), text.legalText(),
                    text.officialId(), text.officialNumber(), text.successorId()
            );
        });

        LegalActSPARQLService service = new LegalActSPARQLService(
                queryExecutor, environment, legalActService, legalActTextService
        );

        assertThat(service.retrieveAndStoreLegalActContent(
                "https://example.test/eli/cz/sb/2024/1/2024-01-01"
        )).satisfiesExactly(
                text -> assertThat(text).usingRecursiveComparison().isEqualTo(new LegalActText(
                        100L, 42L, "First fragment", "https://example.test/fragment/1", "§ 1", null
                )),
                text -> assertThat(text).usingRecursiveComparison().isEqualTo(new LegalActText(
                        101L, 42L, "Second fragment", "https://example.test/fragment/2", "§ 2", null
                ))
        );
        verify(legalActService).create(any(LegalAct.class));
    }

    private BindingSet fragmentBinding(String id, String number, String text) {
        BindingSet bindingSet = mock(BindingSet.class);
        var valueFactory = SimpleValueFactory.getInstance();
        when(bindingSet.getValue("fragment")).thenReturn(valueFactory.createIRI(id));
        when(bindingSet.getValue("citace")).thenReturn(valueFactory.createLiteral(number));
        when(bindingSet.getValue("obsah")).thenReturn(valueFactory.createLiteral(text));
        return bindingSet;
    }
}
