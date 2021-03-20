package ca.on.oicr.gsi.shesmu.plugin.input;

import java.util.TreeMap;

/**
 * JSON format for JSON-defined input formats
 *
 * @param variables the variables defined expected in this format
 * @param timeFormat the format for date storage
 */
public record Definition(TreeMap<String, VariableDefinition> variables, TimeFormat timeFormat) {}
