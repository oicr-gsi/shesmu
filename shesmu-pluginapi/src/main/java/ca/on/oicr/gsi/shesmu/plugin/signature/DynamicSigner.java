package ca.on.oicr.gsi.shesmu.plugin.signature;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

/**
 * Create a signature based on a particular input value from Shesmu
 *
 * @param <T> the type of the signature
 */
public interface DynamicSigner<T> {
  /**
   * Add a new variable to the signature
   *
   * <p>Signers are guaranteed to see values in alphabetical order by name
   *
   * @param name the variable's name
   * @param type the variable's type
   * @param value the variable's value
   */
  void addVariable(String name, Imyhat type, Object value);

  /** Get the output value of the signature */
  T finish();
}
