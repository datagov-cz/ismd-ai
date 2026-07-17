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

import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


@Service
public class LegalActSPARQLService {

    private static final Logger log = LoggerFactory.getLogger(LegalActSPARQLService.class);
    private static final String ELI_PATH_PREFIX = "/eli/cz/sb/";
    private final Environment environment;

    private static final String QUERY_LEGAL_ACT_NAME = """
              PREFIX esel: <https://slovník.gov.cz/datový/sbírka/pojem/>

              SELECT ?nazev
              WHERE {
                  ?legal_act_id esel:má-fragment-znění ?fragment .

                  ?fragment esel:obsahuje-fragment ?fragment_s_nazvem .

                  ?fragment_s_nazvem esel:má-typ-fragmentu ?title_type ;
                    esel:text-fragmentu ?nazev .

                  FILTER(
                    STRSTARTS(
                      STR(?title_type),
                      "https://opendata.eselpoint.gov.cz/esel-esb/cis-esb-typ-fragmentu/"
                    )
                    && STRENDS(STR(?title_type), "/Prefix_Title")
                  )
              }
            """;

    private static final String QUERY_LEGAL_ACT_CONTENT = """
            PREFIX esel: <https://slovník.gov.cz/datový/sbírka/pojem/>
            SELECT ?zneni ?hierarchie ?poradi ?obsah
            WHERE {
              {
                SELECT ?predek
                WHERE {
                  VALUES ?predek {
                    %s
                  }
                }
              }

              {
                SELECT ?predek ?zneni
                WHERE {
                  ?zneni esel:má-předka ?predek .
                }
              }
              OPTION (
                TRANSITIVE,
                t_in(?predek),
                t_out(?zneni),
                t_min(0),
                t_distinct,
                t_no_cycles
              )

              ?zneni
                esel:hierarchie-fragmentu-znění-právního-aktu ?hierarchie ;
                esel:pořadí-fragmentu-znění-právního-aktu ?poradi ;
                esel:obsahuje-fragment ?fragment .

              ?fragment esel:text-fragmentu ?obsah .
            }
            ORDER BY ?poradi
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

    /** Retrieves the requested ELI texts from the database, fetching and caching cache misses. */
    @Transactional
    public List<LegalActText> retrieveLegalActTexts(List<String> eliIdentifiers) {
        Objects.requireNonNull(eliIdentifiers, "eliIdentifiers must not be null");
        if (eliIdentifiers.isEmpty()) {
            return List.of();
        }

        Map<String, ParsedEli> identifiersByPath = new LinkedHashMap<>();
        for (String identifier : eliIdentifiers) {
            ParsedEli parsed = parseEli(identifier);
            identifiersByPath.putIfAbsent(parsed.path(), parsed);
        }

        Map<String, LegalActText> textsByPath = new LinkedHashMap<>();
        List<ParsedEli> cacheMisses = new ArrayList<>();
        for (ParsedEli identifier : identifiersByPath.values()) {
            List<LegalActText> cachedTexts = legalActTextService.findByPathPrefix(identifier.path());
            if (cachedTexts.isEmpty()) {
                cacheMisses.add(identifier);
            } else {
                cachedTexts.forEach(text -> textsByPath.putIfAbsent(text.path(), text));
            }
        }
        if (cacheMisses.isEmpty()) {
            return List.copyOf(textsByPath.values());
        }

        List<LegalActFragment> fragments = retrieveLegalActFragments(cacheMisses);
        Map<String, Optional<LegalAct>> actsByPath = new LinkedHashMap<>();
        for (LegalActFragment fragment : fragments) {
            ParsedEli fragmentEli = parseShortEli(fragment.path());
            Optional<LegalAct> storedAct = actsByPath.computeIfAbsent(
                    fragmentEli.legalActPath(),
                    ignored -> findOrCreateLegalAct(fragmentEli)
            );
            if (storedAct.isEmpty()) {
                log.warn("Ignoring legal text {} because its legal act name was not found", fragment.path());
                continue;
            }

            LegalActText storedText = legalActTextService.findByPath(fragment.path())
                    .orElseGet(() -> legalActTextService.create(new LegalActText(
                            null,
                            Objects.requireNonNull(storedAct.orElseThrow().id(), "Stored legal act must have an id"),
                            fragment.path(),
                            fragment.text(),
                            fragment.hierarchy(),
                            fragment.order()
                    )));
            textsByPath.putIfAbsent(storedText.path(), storedText);
        }
        return List.copyOf(textsByPath.values());
    }

    /** Backwards-compatible convenience overload for one identifier. */
    @Transactional
    public List<LegalActText> retrieveAndStoreLegalActContent(String eliIdentifier) {
        return retrieveLegalActTexts(List.of(eliIdentifier));
    }

    /** Backwards-compatible overload for callers that use the original method name. */
    @Transactional
    public List<LegalActText> retrieveAndStoreLegalActContent(List<String> eliIdentifiers) {
        return retrieveLegalActTexts(eliIdentifiers);
    }

    private List<LegalActFragment> retrieveLegalActFragments(List<ParsedEli> identifiers) {
        String namespace = eliNamespace();
        String values = identifiers.stream()
                .map(identifier -> "<" + namespace + identifier.path() + ">")
                .reduce((left, right) -> left + "\n                    " + right)
                .orElseThrow();
        String query = QUERY_LEGAL_ACT_CONTENT.formatted(values);

        return sparqlQueryExecutor.query("legal act content query", repositoryConnection -> {
            List<LegalActFragment> fragments = new ArrayList<>();
            try (TupleQueryResult result = sparqlQueryExecutor.evaluateTupleQuery(
                    repositoryConnection,
                    query
            )) {
                while (result.hasNext()) {
                    BindingSet bindingSet = result.next();
                    String zneni = requiredStringValue(bindingSet, "zneni");
                    if (!zneni.startsWith(namespace)) {
                        throw new IllegalArgumentException("SPARQL result is outside the configured ELI namespace: " + zneni);
                    }
                    fragments.add(new LegalActFragment(
                            zneni.substring(namespace.length()),
                            requiredStringValue(bindingSet, "obsah"),
                            requiredStringValue(bindingSet, "hierarchie"),
                            requiredStringValue(bindingSet, "poradi")
                    ));
                }
                return fragments;
            } catch (QueryEvaluationException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Optional<LegalAct> retrieveLegalActInfo(String legalActId) {
        ParsedEli parsed = parseEli(legalActId);
        LegalAct legalAct = parsed.toLegalAct();
        String actELI = eliNamespace() + parsed.legalActPath();
        return sparqlQueryExecutor.query("legal act name query", repositoryConnection -> {
            try (TupleQueryResult result = sparqlQueryExecutor.evaluateTupleQuery(repositoryConnection, QUERY_LEGAL_ACT_NAME, tupleQuery ->
                tupleQuery.setBinding("legal_act_id", SimpleValueFactory.getInstance().createIRI(actELI))
            )) {
                if (result.hasNext()) {
                    BindingSet bindingSet = result.next();
                    return Optional.of(legalAct.withTitle(requiredStringValue(bindingSet, "nazev")));
                }
                return Optional.empty();
            } catch (QueryEvaluationException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Optional<LegalAct> findOrCreateLegalAct(ParsedEli parsed) {
        Optional<LegalAct> cached = legalActService.find(parsed.number(), parsed.year(), parsed.date());
        if (cached.isPresent()) {
            return cached;
        }
        return retrieveLegalActInfo(ELI_PATH_PREFIX + parsed.path())
                .map(legalActService::create);
    }

    private ParsedEli parseEli(String identifier) {
        Objects.requireNonNull(identifier, "ELI identifier must not be null");
        int prefixIndex = identifier.lastIndexOf(ELI_PATH_PREFIX);
        if (prefixIndex < 0) {
            throw new IllegalArgumentException("ELI identifier must contain " + ELI_PATH_PREFIX + ": " + identifier);
        }
        return parseShortEli(identifier.substring(prefixIndex + ELI_PATH_PREFIX.length()));
    }

    private ParsedEli parseShortEli(String path) {
        if (path == null || path.isBlank() || !path.matches("[A-Za-z0-9_./-]+")) {
            throw new IllegalArgumentException("ELI path contains unsupported characters: " + path);
        }
        String[] segments = path.split("/", -1);
        if (segments.length < 3 || segments[0].length() != 4 || !segments[0].chars().allMatch(Character::isDigit)
                || segments[1].isEmpty() || !segments[1].chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("ELI path must start with {year}/{number}/{date}: " + path);
        }
        try {
            Year year = Year.parse(segments[0]);
            int number = Integer.parseInt(segments[1]);
            LocalDate date = LocalDate.parse(segments[2]);
            return new ParsedEli(path, year, number, date);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unable to extract legal act year, number or date: " + path, exception);
        }
    }

    private String eliNamespace() {
        String namespace = Objects.requireNonNull(
                environment.getProperty("app.sparql.eli.namespace"),
                "app.sparql.eli.namespace must be configured"
        );
        if (!namespace.endsWith("/")) {
            namespace += "/";
        }
        return namespace;
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

    private record ParsedEli(String path, Year year, int number, LocalDate date) {
        String legalActPath() {
            return year + "/" + number + "/" + date;
        }

        LegalAct toLegalAct() {
            return new LegalAct(number, year, date);
        }
    }

    private record LegalActFragment(String path, String text, String hierarchy, String order) {
    }
}
