package ca.on.oicr.gsi.shesmu.runtime;

/**
 * An interface for getting signature values
 *
 * <p>This is used by <tt>Define</tt> olives to access signatures which their callers must provide.
 */
public interface SignatureAccessor {

  /**
   * Get a signature
   *
   * @param name the name of the signature
   * @param value the stream value; the type of the object is not really know and should only be
   *     access via method handles
   * @return the signature result; the type is known to the compiler and the callee will unbox it
   *     appropriately
   */
  Object dynamicSignature(String name, Object value);

  /**
   * Get a signature
   *
   * @param name the name of the signature
   * @return the signature result; the type is known to the compiler and the callee will unbox it
   *     appropriately
   */
  Object staticSignature(String name);
}
