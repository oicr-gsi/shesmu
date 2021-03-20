package ca.on.oicr.gsi.shesmu.plugin.input;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;

/**
 * The description of a variable in a JSON-defined input format
 *
 * @param type the Shesmu type for this variable
 * @param signable whether this variable should be included in signatures
 * @param gangs the gangs in this variables is part of
 */
public record VariableDefinition(Imyhat type, boolean signable, List<GangDefinition> gangs) {}
