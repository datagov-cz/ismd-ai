from model.domain.suggestion_model import LegalAct, LegalStructuralElement
from rdflib import Graph
import re
import os
import json
from datetime import datetime

class LegalActRepositoryESEL:
  def __init__(self):
    """
    Initializes the repository with the given SPARQL endpoint.
    
    :param sparql_endpoint: URL of the SPARQL endpoint
    """
    self.sparql_endpoint = "https://opendata.eselpoint.cz/sparql"

  def load_legal_act(self, legal_act_id: str) -> LegalAct:
    """
    Loads a legal act either from a local JSON dump or from the SPARQL endpoint.
    If the JSON dump does not exist, it fetches the data from the endpoint and saves it.

    :param legal_act_id: The ID of the legal act to load
    :return: An instance of LegalAct
    """
    # Extract year, number, and date from the legal_act_id
    match = re.match(r"https://opendata\.eselpoint\.cz/esel-esb/eli/cz/sb/(\d{4})/(\d+)/(\d{4}-\d{2}-\d{2})", legal_act_id)
    if not match:
      raise ValueError(f"Invalid legal_act_id format: {legal_act_id}")
    year, number, date = match.groups()

    # Construct the file path
    file_name = f"{number}-{year}-{date}.json"
    file_path = os.path.join("data", "legal_acts", file_name)

    # Check if the JSON file exists
    if os.path.exists(file_path):
      try:
        with open(file_path, "r", encoding="utf-8") as file:
          data = json.load(file)
          return LegalAct(**data)
      except Exception as e:
        raise RuntimeError(f"Failed to load legal act from file: {e}")

    # If the file does not exist, fetch the data from the SPARQL endpoint
    legal_act = self._load_legal_act_from_esel(legal_act_id)

    # Save the fetched data to a JSON file
    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    try:
      with open(file_path, "w", encoding="utf-8") as file:
        file.write(legal_act.model_dump_json(indent=2))
    except Exception as e:
      raise RuntimeError(f"Failed to save legal act to file: {e}")

    return legal_act

  def _load_legal_act_from_esel(self, legal_act_id: str) -> LegalAct:
    """
    Fetches a legal act by its ID from the SPARQL endpoint.

    :param legal_act_id: The ID of the legal act to fetch
    :return: An instance of LegalAct containing the fetched data
    """
    officialTitle = self._load_legal_act_name_from_esel(legal_act_id)

    # Extract year and number from the legal_act_id
    match = re.match(r"https://opendata\.eselpoint\.cz/esel-esb/eli/cz/sb/(\d{4})/(\d+)/.*", legal_act_id)
    if not match:
      raise ValueError(f"Invalid legal_act_id format: {legal_act_id}")
    year, number = match.groups()
    officialNumber = f"{number}/{year}"

    legal_structural_elements = self._load_legal_act_content_from_esel(legal_act_id)
    
    return LegalAct(
      id=legal_act_id,
      officialTitle=officialTitle,
      officialNumber=officialNumber,
      consistsOf=legal_structural_elements
    )

  def _load_legal_act_name_from_esel(self, legal_act_id: str) -> str:
    """
    Loads the name of a legal act from the SPARQL endpoint using the provided query.

    :return: The name of the legal act
    """
    g = Graph()
    sparql_str = f"""
        PREFIX esel: <https://slovník.gov.cz/datový/sbírka/pojem/>

        SELECT ?nazev
        WHERE {{
          SERVICE <{self.sparql_endpoint}> {{
            <{legal_act_id}> esel:má-fragment-znění ?fragment .

            ?fragment esel:obsahuje-fragment ?fragment_s_nazvem .

            ?fragment_s_nazvem esel:má-typ-fragmentu <https://opendata.eselpoint.cz/esel-esb/cis-esb-typ-fragmentu/položka/Prefix_Title> ;
              esel:text-fragmentu ?nazev .
          }}
        }}
      """
    try:
      results = g.query(sparql_str)
      name = ""
      for row in results:
        name = str(row.nazev)
      return name
    except Exception as e:
      raise RuntimeError(f"Failed to load legal act name: {e}")

  def _load_legal_act_content_from_esel(self, legal_act_id: str) -> LegalAct:
    """
    Loads the content of a legal act from the SPARQL endpoint using the provided query.

    :return: An instance of LegalAct containing the fetched data
    """
    g = Graph()
    sparql_str = f"""
        PREFIX esel: <https://slovník.gov.cz/datový/sbírka/pojem/>

        SELECT ?fragment ?citace ?hierarchie ?poradi ?obsah
        WHERE {{
          SERVICE <{self.sparql_endpoint}> {{
            <{legal_act_id}> esel:má-fragment-znění ?fragment .

            ?fragment esel:citace-označení-fragmentu-znění-právního-aktu ?citace ;
              esel:hierarchie-fragmentu-znění-právního-aktu ?hierarchie ;
              esel:pořadí-fragmentu-znění-právního-aktu ?poradi ;
              esel:obsahuje-fragment/esel:text-fragmentu ?obsah .
          }}
        }}
        ORDER BY ?poradi
      """

    try:
      results = g.query(sparql_str)

      act_paragraphs = {}

      for row in results:
        citace = str(row.citace)
        match = re.match(r"§ (\d+[a-z]*)( .*|$)", citace)
        if match:
          paragraph_number = match.group(1)
          if paragraph_number in act_paragraphs:
            act_paragraphs[paragraph_number] += "\n" + str(row.obsah)
          else:
            act_paragraphs[paragraph_number] = str(row.obsah)


      legal_structural_elements = []

      for paragraph_number, content in act_paragraphs.items():
        # Remove HTML tags from the content
        plain_text_content = re.sub(r"<[^>]*>", "", content)

        element = LegalStructuralElement(
          id=f"{legal_act_id}/par_{paragraph_number}",
          officialIdentifier=f"§ {paragraph_number}",
          textContent=plain_text_content,
        )
        legal_structural_elements.append(element)

      return legal_structural_elements
    except Exception as e:
      raise RuntimeError(f"Failed to load legal act: {e}")