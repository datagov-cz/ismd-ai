package cz.dia.ismd.assistant.service;

import cz.dia.ismd.assistant.model.legal.LegalAct;
import cz.dia.ismd.assistant.model.legal.LegalActText;
import org.eclipse.rdf4j.model.Value;

import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class LegalActSPARQLService {


    private static final Logger log = LoggerFactory.getLogger(LegalActSPARQLService.class);
    private final Environment environment;
    //    Loads the content of a legal act from the SPARQL endpoint using the provided query.
    //    Returns An instance of LegalAct containing the fetched data
    private static final String QUERY_LEGAL_ACT_CONTENT = """
            PREFIX esel: <https://slovník.gov.cz/datový/sbírka/pojem/>
            
            SELECT ?fragment ?citace ?hierarchie ?poradi ?obsah
                WHERE {{
                    ?legal_act_id esel:má-fragment-znění ?fragment .
            
                    ?fragment esel:citace-označení-fragmentu-znění-právního-aktu ?citace ;
                      esel:hierarchie-fragmentu-znění-právního-aktu ?hierarchie ;
                      esel:pořadí-fragmentu-znění-právního-aktu ?poradi ;
                      esel:obsahuje-fragment/esel:text-fragmentu ?obsah .
                }}
            ORDER BY ?poradi
            """;

    //    Loads the name of a legal act from the SPARQL endpoint using the provided query.
    //    Returns The name of the legal act
    private static final String QUERY_LEGAL_ACT_NAME = """
              PREFIX esel: <https://slovník.gov.cz/datový/sbírka/pojem/>
            
              SELECT ?nazev
              WHERE {{
                  ?legal_act_id esel:má-fragment-znění ?fragment .
            
                  ?fragment esel:obsahuje-fragment ?fragment_s_nazvem .
            
                  ?fragment_s_nazvem esel:má-typ-fragmentu <https://opendata.eselpoint.gov.cz/esel-esb/cis-esb-typ-fragmentu/položka/Prefix_Title> ;
                    esel:text-fragmentu ?nazev .
              }}
            """;

    private final SparqlQueryExecutor sparqlQueryExecutor;
    private final LegalActService legalActService;
    private final LegalActTextService legalActTextService;

    public LegalActSPARQLService(
            SparqlQueryExecutor sparqlQueryExecutor,
            Environment environment,
            LegalActService legalActService,
            LegalActTextService legalActTextService
    ) {
        this.sparqlQueryExecutor = sparqlQueryExecutor;
        this.environment = environment;
        this.legalActService = legalActService;
        this.legalActTextService = legalActTextService;
    }

    /**
     * Retrieves all fragments of the legal-act version identified by {@code legalActId}
     * and stores them in {@code legal_act_texts}, in the order returned by the SPARQL query.
     * The parent legal act is created first when it is not already present in the database.
     *
     * @return the stored fragments, or an empty list when the ELI or legal act cannot be found
     */
    @Transactional
    public List<LegalActText> retrieveAndStoreLegalActContent(String legalActId) {
        Optional<LegalAct> retrievedLegalAct = retrieveLegalActInfo(legalActId);
        if (retrievedLegalAct.isEmpty()) {
            return List.of();
        }

        LegalAct legalActInfo = retrievedLegalAct.orElseThrow();
        URI actELI = constructELIFromLegalAct(legalActInfo);
        List<LegalActFragment> fragments = retrieveLegalActFragments(actELI);
        if (fragments.isEmpty()) {
            return List.of();
        }

        LegalAct storedLegalAct = legalActService
                .find(legalActInfo.number(), legalActInfo.year(), legalActInfo.date())
                .orElseGet(() -> legalActService.create(legalActInfo));

        List<LegalActText> storedFragments = new ArrayList<>(fragments.size());
        for (LegalActFragment fragment : fragments) {
            storedFragments.add(legalActTextService.create(new LegalActText(
                    null,
                    Objects.requireNonNull(storedLegalAct.id(), "Stored legal act must have an id"),
                    fragment.text(),
                    fragment.officialId(),
                    fragment.officialNumber(),
                    null
            )));
        }
        return List.copyOf(storedFragments);
    }

    private List<LegalActFragment> retrieveLegalActFragments(URI actELI) {
        return sparqlQueryExecutor.query("legal act content query", repositoryConnection -> {
            List<LegalActFragment> fragments = new ArrayList<>();
            try (TupleQueryResult result = sparqlQueryExecutor.evaluateTupleQuery(
                    repositoryConnection,
                    QUERY_LEGAL_ACT_CONTENT,
                    tupleQuery -> tupleQuery.setBinding(
                            "legal_act_id",
                            SimpleValueFactory.getInstance().createIRI(actELI.toString())
                    )
            )) {
                while (result.hasNext()) {
                    BindingSet bindingSet = result.next();
                    fragments.add(new LegalActFragment(
                            requiredStringValue(bindingSet, "obsah"),
                            requiredStringValue(bindingSet, "fragment"),
                            requiredStringValue(bindingSet, "citace")
                    ));
                }
                return fragments;
            } catch (QueryEvaluationException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Optional<LegalAct> retrieveLegalActInfo(String legalActId) {
        Optional<LegalAct> legalAct = retrieveLegalActFromELI(legalActId);
        if (legalAct.isEmpty()) {
            return Optional.empty();
        }
        URI actELI = constructELIFromLegalAct(legalAct.get());
        return sparqlQueryExecutor.query("legal act name query", repositoryConnection -> {
            try (TupleQueryResult result = sparqlQueryExecutor.evaluateTupleQuery(repositoryConnection, QUERY_LEGAL_ACT_NAME, tupleQuery ->
                tupleQuery.setBinding("legal_act_id", SimpleValueFactory.getInstance().createIRI(actELI.toString()))
            )) {
                if (result.hasNext()) {
                    BindingSet bindingSet = result.next();
                    return Optional.of(legalAct.get().withTitle(stringValue(bindingSet.getValue("nazev"))));
                }
                return Optional.empty();
            } catch (QueryEvaluationException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Optional<LegalAct> retrieveLegalActFromELI(String ELI) {
        Pattern pattern = Pattern.compile(
                "https://.+/eli/cz/sb/(\\d{4})/(\\d+)/(\\d{4}-\\d{2}-\\d{2})"
        );
        Matcher matcher = pattern.matcher(ELI);
        if (matcher.matches()) {
            try {
                Year year = Year.parse(matcher.group(1));
                int number = Integer.parseInt(matcher.group(2));
                LocalDate date = LocalDate.parse(matcher.group(3));
                return Optional.of(new LegalAct(number, year, date));
            } catch (DateTimeParseException | NumberFormatException e) {
                throw new IllegalArgumentException("Unable to extract legal act year, number or date: " + ELI);
            }
        } else {
            return Optional.empty();
        }
    }

    private URI constructELIFromLegalAct(LegalAct legalAct) {
        String prefix = environment.getProperty("app.sparql.eli.namespace");
        return URI.create(Objects.requireNonNull(prefix) +
                legalAct.year() +
                "/" + legalAct.number() +
                "/" + legalAct.date()
        );
    }

    private URI requireAbsoluteIri(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        URI iri;
        try {
            iri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid absolute IRI", exception);
        }
        if (!iri.isAbsolute()) {
            throw new IllegalArgumentException(fieldName + " must be an absolute IRI");
        }
        return iri;
    }

    private String stringValue(Value value) {
        return value == null ? null : value.stringValue();
    }

    private String requiredStringValue(BindingSet bindingSet, String bindingName) {
        return Objects.requireNonNull(
                stringValue(bindingSet.getValue(bindingName)),
                "SPARQL result is missing required binding: " + bindingName
        );
    }

    private record LegalActFragment(String text, String officialId, String officialNumber) {
    }
}
