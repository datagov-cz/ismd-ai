from typing import List, Optional
from domain.models.suggestion_model import LegalAct, LegalStructuralElement

def _select_structural_elements(
          legal_act: LegalAct,
          structural_element_ids: Optional[List[str]]) -> List[LegalStructuralElement]:
  selected_structural_elements = []
  if structural_element_ids:
      # Preprocess input IDs for fast lookup
      id_set = set(structural_element_ids)
      # Extract the number from el.id and match with input IDs
      def extract_number(el_id: str) -> Optional[str]:
          # Assumes the number is after 'par_' and before any trailing chars
          import re
          match = re.search(r'par_(\d+[a-z]*)', el_id)
          return match.group(1) if match else None

      selected_structural_elements = [
          el for el in (legal_act.consistsOf or [])
          if (num := extract_number(el.id)) and num in id_set
      ]
      if not selected_structural_elements:
          raise ValueError("No matching structural elements found for the provided IDs.")
      
  return selected_structural_elements
