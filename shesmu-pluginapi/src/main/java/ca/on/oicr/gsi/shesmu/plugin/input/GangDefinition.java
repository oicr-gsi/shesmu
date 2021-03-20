package ca.on.oicr.gsi.shesmu.plugin.input;

/**
 * The definition for ganges in a JSON-defined input format
 *
 * @param order the relative order of this variable when composing the gang
 * @param gang the gang name
 * @param dropIfDefault if true, exclude the variable from string generation when it has a "default"
 *     value (zero, empty string).
 */
public record GangDefinition(int order, String gang, boolean dropIfDefault) {}
