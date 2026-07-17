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
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalActSPARQLServiceTests {

    private static final String NAMESPACE = "https://data.example/eli/cz/sb/";
    private static final String ELI_PATH =
            "2026/60/2026-05-27/dokument/norma/cast_1/hlava_3/par_16/odst_2/pism_g";

    @Test
    @SuppressWarnings("unchecked")
    void retrievesFullEliFromSparqlAndCachesMappedText() {
        SparqlQueryExecutor queryExecutor = mock(SparqlQueryExecutor.class);
        Environment environment = mock(Environment.class);
        LegalActService legalActService = mock(LegalActService.class);
        LegalActTextService legalActTextService = mock(LegalActTextService.class);
        RepositoryConnection connection = mock(RepositoryConnection.class);
        TupleQueryResult contentResult = mock(TupleQueryResult.class);
        TupleQueryResult nameResult = mock(TupleQueryResult.class);
        BindingSet contentBinding = contentBinding(ELI_PATH, "Text of paragraph", "paragraph", "16.2.7");
        BindingSet nameBinding = mock(BindingSet.class);
        var valueFactory = SimpleValueFactory.getInstance();

        when(environment.getProperty("app.sparql.eli.namespace")).thenReturn(NAMESPACE);
        when(queryExecutor.query(anyString(), any())).thenAnswer(invocation -> {
            Function<RepositoryConnection, Object> operation = invocation.getArgument(1);
            return operation.apply(connection);
        });
        when(queryExecutor.evaluateTupleQuery(any(), anyString())).thenReturn(contentResult);
        when(queryExecutor.evaluateTupleQuery(any(), anyString(), any())).thenReturn(nameResult);
        when(contentResult.hasNext()).thenReturn(true, false);
        when(contentResult.next()).thenReturn(contentBinding);
        when(nameResult.hasNext()).thenReturn(true);
        when(nameResult.next()).thenReturn(nameBinding);
        when(nameBinding.getValue("nazev")).thenReturn(valueFactory.createLiteral("Test legal act"));

        when(legalActTextService.findByPathPrefix(ELI_PATH)).thenReturn(List.of());
        when(legalActTextService.findByPath(ELI_PATH)).thenReturn(Optional.empty());
        when(legalActService.find(60, Year.of(2026), LocalDate.of(2026, 5, 27)))
                .thenReturn(Optional.empty());
        when(legalActService.create(any())).thenReturn(new LegalAct(
                42L, 60, Year.of(2026), LocalDate.of(2026, 5, 27), "Test legal act", null, null
        ));
        when(legalActTextService.create(any())).thenAnswer(invocation -> {
            LegalActText text = invocation.getArgument(0);
            return new LegalActText(
                    100L, text.legalActId(), text.path(), text.legalText(),
                    text.legalHierarchy(), text.legalOrder()
            );
        });

        LegalActSPARQLService service = new LegalActSPARQLService(
                queryExecutor, environment, legalActService, legalActTextService
        );

        assertThat(service.retrieveLegalActTexts(List.of(
                "https://e-sbirka.gov.cz/eli/cz/sb/" + ELI_PATH
        ))).containsExactly(new LegalActText(
                100L, 42L, ELI_PATH, "Text of paragraph", "paragraph", "16.2.7"
        ));

        ArgumentCaptor<String> contentQuery = ArgumentCaptor.forClass(String.class);
        verify(queryExecutor).evaluateTupleQuery(any(), contentQuery.capture());
        assertThat(contentQuery.getValue()).contains("VALUES ?predek {", "<" + NAMESPACE + ELI_PATH + ">");

        ArgumentCaptor<String> nameQuery = ArgumentCaptor.forClass(String.class);
        verify(queryExecutor).evaluateTupleQuery(any(), nameQuery.capture(), any());
        assertThat(nameQuery.getValue())
                .contains(
                        "esel:má-typ-fragmentu ?title_type",
                        "STRSTARTS(",
                        "https://opendata.eselpoint.gov.cz/esel-esb/cis-esb-typ-fragmentu/",
                        "STRENDS(STR(?title_type), \"/Prefix_Title\")"
                )
                .doesNotContain("/položka/");

        ArgumentCaptor<LegalAct> legalAct = ArgumentCaptor.forClass(LegalAct.class);
        verify(legalActService).create(legalAct.capture());
        assertThat(legalAct.getValue()).usingRecursiveComparison().isEqualTo(new LegalAct(
                null, 60, Year.of(2026), LocalDate.of(2026, 5, 27), "Test legal act", null, null
        ));
    }

    @Test
    void returnsCachedTextWithoutCallingSparql() {
        SparqlQueryExecutor queryExecutor = mock(SparqlQueryExecutor.class);
        Environment environment = mock(Environment.class);
        LegalActService legalActService = mock(LegalActService.class);
        LegalActTextService legalActTextService = mock(LegalActTextService.class);
        LegalActText cached = new LegalActText(1L, 2L, ELI_PATH, "Cached", "paragraph", "1");
        when(legalActTextService.findByPathPrefix(ELI_PATH)).thenReturn(List.of(cached));

        LegalActSPARQLService service = new LegalActSPARQLService(
                queryExecutor, environment, legalActService, legalActTextService
        );

        assertThat(service.retrieveLegalActTexts(List.of(
                "https://another-website.test/eli/cz/sb/" + ELI_PATH
        ))).containsExactly(cached);
        verify(queryExecutor, never()).query(anyString(), any());
    }

    @Test
    void parsesEliSuffixAfterAnOpaquePrefix() {
        SparqlQueryExecutor queryExecutor = mock(SparqlQueryExecutor.class);
        Environment environment = mock(Environment.class);
        LegalActService legalActService = mock(LegalActService.class);
        LegalActTextService legalActTextService = mock(LegalActTextService.class);
        LegalActText cached = new LegalActText(1L, 2L, ELI_PATH, "Cached", "paragraph", "1");
        when(legalActTextService.findByPathPrefix(ELI_PATH)).thenReturn(List.of(cached));

        LegalActSPARQLService service = new LegalActSPARQLService(
                queryExecutor, environment, legalActService, legalActTextService
        );

        assertThat(service.retrieveLegalActTexts(List.of(
                "arbitrary prefix with spaces /eli/cz/sb/" + ELI_PATH
        ))).containsExactly(cached);
        verify(queryExecutor, never()).query(anyString(), any());
    }

    private BindingSet contentBinding(String path, String text, String hierarchy, String order) {
        BindingSet bindingSet = mock(BindingSet.class);
        var valueFactory = SimpleValueFactory.getInstance();
        when(bindingSet.getValue("zneni")).thenReturn(valueFactory.createIRI(NAMESPACE + path));
        when(bindingSet.getValue("obsah")).thenReturn(valueFactory.createLiteral(text));
        when(bindingSet.getValue("hierarchie")).thenReturn(valueFactory.createLiteral(hierarchy));
        when(bindingSet.getValue("poradi")).thenReturn(valueFactory.createLiteral(order));
        return bindingSet;
    }
}
