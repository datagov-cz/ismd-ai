package cz.dia.ismd.assistant.service.legalact;

import cz.dia.ismd.assistant.data.legalact.LegalAct;
import cz.dia.ismd.assistant.service.sparql.SparqlQueryExecutor;
import org.eclipse.rdf4j.model.Value;

import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeParseException;
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

    public LegalActSPARQLService(SparqlQueryExecutor sparqlQueryExecutor, Environment environment) {
        this.sparqlQueryExecutor = sparqlQueryExecutor;
        this.environment = environment;
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
}
