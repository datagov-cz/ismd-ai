package cz.dia.ismd.assistant.controller;

public final class OpenApiExamples {

    public static final String JOB_ID = "00000000-0000-0000-0000-000000000101";
    public static final String PROPERTY_JOB_ID = "00000000-0000-0000-0000-000000000201";
    public static final String RELATIONSHIP_JOB_ID = "00000000-0000-0000-0000-000000000301";

    public static final String CLASS_SUGGESTION_REQUEST = """
            {
              "k": 3,
              "structuralElementIds": [
                "/eli/cz/sb/2024/1/par_2",
                "/eli/cz/sb/2024/1/par_3"
              ],
              "contextText": "Zákon popisuje žádosti o povolení a příslušný orgán.",
              "knownConceptualModel": {
                "classes": [
                  {
                    "suggestionID": "existing-class-applicant",
                    "name": {
                      "cs": "Žadatel"
                    },
                    "definition": {
                      "cs": "Osoba podávající žádost."
                    },
                    "explanation": {
                      "cs": "Třída již známá z konceptuálního modelu."
                    },
                    "type": "CLASS",
                    "specializes": [],
                    "legalAct": "/eli/cz/sb/2024/1"
                  }
                ],
                "attributes": [],
                "relationships": []
              }
            }
            """;

    public static final String CLASS_SUGGESTION_START_RESPONSE = """
            {
              "jobId": "00000000-0000-0000-0000-000000000101",
              "status": "IN_PROGRESS"
            }
            """;

    public static final String CLASS_SUGGESTIONS_RESPONSE = """
            [
              {
                "jobId": "00000000-0000-0000-0000-000000000101",
                "status": "COMPLETED",
                "newSuggestions": [
                  {
                    "suggestionID": "class-permit-application",
                    "name": {
                      "cs": "Žádost o povolení"
                    },
                    "definition": {
                      "cs": "Podání, kterým žadatel žádá o vydání povolení."
                    },
                    "explanation": {
                      "cs": "Právní text opakovaně odkazuje na žádosti o povolení jako na klíčový objekt domény."
                    },
                    "type": "CLASS",
                    "specializes": [],
                    "legalAct": "/eli/cz/sb/2024/1"
                  }
                ]
              }
            ]
            """;

    public static final String PROPERTY_SUGGESTION_REQUEST = """
            {
              "k": 3,
              "selectedClassId": "class-permit-application",
              "structuralElementIds": [
                "/eli/cz/sb/2024/1/par_2"
              ],
              "contextText": "Žádost musí obsahovat identifikaci žadatele a doručovací adresu.",
              "knownConceptualModel": {
                "classes": [
                  {
                    "suggestionID": "class-permit-application",
                    "name": {
                      "cs": "Žádost o povolení"
                    },
                    "definition": {
                      "cs": "Podání, kterým žadatel žádá o vydání povolení."
                    },
                    "explanation": {
                      "cs": "Vybraná třída pro extrakci vlastností."
                    },
                    "type": "CLASS",
                    "specializes": [],
                    "legalAct": "/eli/cz/sb/2024/1"
                  }
                ],
                "attributes": [],
                "relationships": []
              }
            }
            """;

    public static final String SELECTED_CLASS_JOB_START_RESPONSE = """
            {
              "jobId": "00000000-0000-0000-0000-000000000201",
              "selectedClassId": "class-permit-application",
              "status": "IN_PROGRESS"
            }
            """;

    public static final String PROPERTY_SUGGESTIONS_RESPONSE = """
            [
              {
                "jobId": "00000000-0000-0000-0000-000000000201",
                "selectedClassId": "00000000-0000-0000-0000-000000000203",
                "status": "COMPLETED",
                "newAttributeSuggestions": [
                  {
                    "suggestionID": "00000000-0000-0000-0000-000000000204",
                    "associatedClass": {
                      "id": "00000000-0000-0000-0000-000000000203"
                    },
                    "name": {
                      "cs": "Doručovací adresa"
                    },
                    "definition": {
                      "cs": "Adresa určená pro doručování písemností v řízení."
                    },
                    "explanation": {
                      "cs": "Právní text vyžaduje uvedení doručovací adresy v žádosti."
                    },
                    "legalAct": "/eli/cz/sb/2024/1"
                  }
                ]
              }
            ]
            """;

    public static final String RELATIONSHIP_SUGGESTION_REQUEST = """
            {
              "k": 3,
              "selectedClassId": "class-permit-application",
              "structuralElementIds": [
                "/eli/cz/sb/2024/1/par_4"
              ],
              "contextText": "Příslušný orgán rozhoduje o žádosti.",
              "knownConceptualModel": {
                "classes": [
                  {
                    "suggestionID": "class-permit-application",
                    "name": {
                      "cs": "Žádost o povolení"
                    },
                    "definition": {
                      "cs": "Podání, kterým žadatel žádá o vydání povolení."
                    },
                    "explanation": {
                      "cs": "Vybraná třída pro extrakci vztahů."
                    },
                    "type": "CLASS",
                    "specializes": [],
                    "legalAct": "/eli/cz/sb/2024/1"
                  },
                  {
                    "suggestionID": "class-competent-authority",
                    "name": {
                      "cs": "Příslušný orgán"
                    },
                    "definition": {
                      "cs": "Orgán veřejné moci příslušný k rozhodnutí."
                    },
                    "explanation": {
                      "cs": "Cílová třída dostupná v konceptuálním modelu."
                    },
                    "type": "CLASS",
                    "specializes": [],
                    "legalAct": "/eli/cz/sb/2024/1"
                  }
                ],
                "attributes": [],
                "relationships": []
              }
            }
            """;

    public static final String RELATIONSHIP_JOB_START_RESPONSE = """
            {
              "jobId": "00000000-0000-0000-0000-000000000301",
              "selectedClassId": "class-permit-application",
              "status": "IN_PROGRESS"
            }
            """;

    public static final String RELATIONSHIP_SUGGESTIONS_RESPONSE = """
            [
              {
                "jobId": "00000000-0000-0000-0000-000000000301",
                "selectedClassId": "class-permit-application",
                "status": "COMPLETED",
                "newRelationshipSuggestions": [
                  {
                    "suggestionID": "relationship-decided-by",
                    "sourceClass": {
                      "id": "class-permit-application"
                    },
                    "targetClass": {
                      "id": "class-competent-authority"
                    },
                    "name": {
                      "cs": "rozhoduje"
                    },
                    "definition": {
                      "cs": "Vztah mezi žádostí a orgánem, který o ní rozhoduje."
                    },
                    "explanation": {
                      "cs": "Vybraný paragraf stanoví, že o žádosti rozhoduje příslušný orgán."
                    },
                    "legalAct": "/eli/cz/sb/2024/1"
                  }
                ]
              }
            ]
            """;

    public static final String FEEDBACK_REQUEST = """
            [
              {
                "jobID": "00000000-0000-0000-0000-000000000101",
                "suggestionID": [
                  "00000000-0000-0000-0000-000000000101", "00000000-0000-0000-0000-000000000102"
                ]
              }
            ]
            """;

    public static final String FEEDBACK_ACCEPT_RESPONSE = """
            {
              "status": "accepted",
              "type": "accepted"
            }
            """;

    public static final String FEEDBACK_LIKE_RESPONSE = """
            {
              "status": "accepted",
              "type": "liked"
            }
            """;

    public static final String FEEDBACK_DISLIKE_RESPONSE = """
            {
              "status": "accepted",
              "type": "disliked"
            }
            """;

    public static final String VALIDATION_ERROR_RESPONSE = """
            {
              "detail": "k musí být větší nebo rovno 1"
            }
            """;

    public static final String JOB_NOT_FOUND_ERROR_RESPONSE = """
            {
              "detail": "Úloha nebyla nalezena: 00000000-0000-0000-0000-000000000101"
            }
            """;

    public static final String FEEDBACK_JOB_NOT_FOUND_ERROR_RESPONSE = """
            {
              "detail": "Úloha nebyla nalezena: 00000000-0000-0000-0000-000000009999"
            }
            """;

    public static final String FEEDBACK_SUGGESTION_NOT_FOUND_ERROR_RESPONSE = """
            {
              "detail": "Návrh nebyl nalezen pro úlohu 00000000-0000-0000-0000-000000000101: missing-suggestion-id"
            }
            """;

    public static final String TOKEN_LIMIT_ERROR_RESPONSE = """
            {
              "detail": "Nelze spustit úlohu 00000000-0000-0000-0000-000000000101 pro uživatele user-123, protože byl dosažen jeho denní limit tokenů."
            }
            """;

    public static final String LLM_ERROR_RESPONSE = """
            {
              "detail": "Poskytovatel LLM vrátil prázdnou odpověď"
            }
            """;

    private OpenApiExamples() {
    }
}
