package ca.on.oicr.gsi.shesmu.plugin.signature;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

/**
 * Create a signature value based on which input variables are in the signature
 *
 * @param <T> the type of the signature
 */
public interface StaticSigner<T> {

  /**
   * Add a new variable to the signature
   *
   * <p>Signers are guaranteed to see values in alphabetical order by name
   *
   * @param name the variable's name
   * @param type the variable's type
   */
  void addVariable(String name, Imyhat type);

  /** Get the output value of the signature */
  T finish();
}
