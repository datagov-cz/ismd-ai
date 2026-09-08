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
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
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
        assertThat(contentQuery.getValue()).contains("VALUES ?source {", "<" + NAMESPACE + ELI_PATH + ">");

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

    @Test
    @SuppressWarnings("unchecked")
    void loadsWholeVersionDespitePartiallyCachedParagraphAndPreservesSubtreeSelection() {
        String actPath = "2026/60/2026-05-27";
        String paragraph = actPath + "/dokument/norma/par_1";
        String child = paragraph + "/odst_1:2";
        String sibling = actPath + "/dokument/norma/par_2";
        String ns = "https://slovník.gov.cz/datový/sbírka/pojem/";
        var vf = SimpleValueFactory.getInstance();
        var repository = new SailRepository(new MemoryStore());
        repository.init();
        try (var connection = repository.getConnection()) {
            // Version membership is separate from the fragment parent hierarchy.
            for (String path : List.of(paragraph, child, sibling)) {
                var fragment = vf.createIRI(NAMESPACE + path);
                var content = vf.createIRI(NAMESPACE + path + "/content");
                connection.add(vf.createIRI(NAMESPACE + actPath), vf.createIRI(ns + "má-fragment-znění"), fragment);
                connection.add(fragment, vf.createIRI(ns + "obsahuje-fragment"), content);
                connection.add(content, vf.createIRI(ns + "text-fragmentu"), vf.createLiteral(path));
                connection.add(fragment, vf.createIRI(ns + "hierarchie-fragmentu-znění-právního-aktu"), vf.createLiteral("paragraph"));
                connection.add(fragment, vf.createIRI(ns + "pořadí-fragmentu-znění-právního-aktu"), vf.createLiteral(path));
            }
            connection.add(vf.createIRI(NAMESPACE + child), vf.createIRI(ns + "má-předka"), vf.createIRI(NAMESPACE + paragraph));

            var executor = mock(SparqlQueryExecutor.class);
            when(executor.query(anyString(), any())).thenAnswer(invocation ->
                    ((Function<RepositoryConnection, Object>) invocation.getArgument(1)).apply(connection));
            when(executor.evaluateTupleQuery(any(), anyString())).thenAnswer(invocation ->
                    connection.prepareTupleQuery(invocation.getArgument(1, String.class)).evaluate());
            var environment = mock(Environment.class);
            when(environment.getProperty("app.sparql.eli.namespace")).thenReturn(NAMESPACE);
            var acts = mock(LegalActService.class);
            when(acts.find(60, Year.of(2026), LocalDate.of(2026, 5, 27)))
                    .thenReturn(Optional.of(new LegalAct(42L, 60, Year.of(2026), LocalDate.of(2026, 5, 27), "Act", null, null)));
            var texts = mock(LegalActTextService.class);
            var cachedChild = new LegalActText(7L, 42L, child, child, "paragraph", child);
            when(texts.findByPathPrefix(actPath)).thenReturn(List.of(cachedChild));
            when(texts.findByPath(child)).thenReturn(Optional.of(cachedChild));
            when(texts.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
            var service = new LegalActSPARQLService(executor, environment, acts, texts);

            assertThat(service.retrieveLegalActTexts(List.of(NAMESPACE + actPath)))
                    .extracting(LegalActText::path).containsExactly(paragraph, child, sibling);
            assertThat(service.retrieveLegalActTexts(List.of(NAMESPACE + paragraph)))
                    .extracting(LegalActText::path).containsExactly(paragraph, child);
            assertThat(service.retrieveLegalActTexts(List.of(NAMESPACE + child)))
                    .containsExactly(cachedChild);
            assertThat(service.retrieveLegalActTexts(List.of(NAMESPACE + actPath, NAMESPACE + paragraph)))
                    .extracting(LegalActText::path).containsExactly(paragraph, child, sibling);
        } finally {
            repository.shutDown();
        }
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
