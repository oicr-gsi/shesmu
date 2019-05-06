package ca.on.oicr.gsi.shesmu.niassa;

/**
 * Description of the relationship between an action analysis provenance information extracted from
 * Niassa.
 */
public enum AnalysisComparison {
  /** Everything about this item matches. */
  EXACT,
  /**
   * The workflow ID and input file SWIDs match. The LIMS keys have some in common (by ID and
   * provider), but have different Pinery data versions or unmatched LIMS keys.
   */
  PARTIAL,
  /**
   * The workflow ID or input file SWIDs are different or there are no LIMS keys in common (by ID
   * and provider).
   */
  DIFFERENT
}
