package ca.on.oicr.gsi.shesmu.plugin.functions;

import ca.on.oicr.gsi.shesmu.plugin.Definer;

/** Interface to export functions with arbitrarily many parameters to Shesmu olives */
public interface VariadicFunction {
  /**
   * Call the function
   *
   * @param arguments the arguments, of the type specified when the function was exported via {@link
   *     Definer#defineFunction(String, String, ca.on.oicr.gsi.shesmu.plugin.types.Imyhat,
   *     VariadicFunction, FunctionParameter...)}
   * @return the return value, of the type specified when the function was exported
   */
  Object apply(Object... arguments);
}
