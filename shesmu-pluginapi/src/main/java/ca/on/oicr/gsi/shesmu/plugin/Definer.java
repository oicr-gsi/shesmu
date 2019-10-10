package ca.on.oicr.gsi.shesmu.plugin;

import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.functions.VariadicFunction;
import ca.on.oicr.gsi.shesmu.plugin.refill.CustomRefillerParameter;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.plugin.signature.DynamicSigner;
import ca.on.oicr.gsi.shesmu.plugin.signature.StaticSigner;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ReturnTypeGuarantee;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Interface to define a actions, constants, functions, and signatures that can be used by olives
 */
public interface Definer<T> extends Supplier<T> {
  /**
   * Create a new refiller description
   *
   * <p>This indirection enforces correct generic type usage
   */
  interface RefillDefiner {
    /**
     * Get information about a new refiller
     *
     * @param <I> the type of the input rows
     */
    <I> RefillInfo<I, ? extends Refiller<I>> info(Class<I> rowType);
  }

  /**
   * Provide information about a refiller
   *
   * @param <I> the type of the input rows
   * @param <F> the type of the refiller
   */
  interface RefillInfo<I, F extends Refiller<I>> {
    /** Create new instance of a refiller */
    F create();

    /** Add any non-annotated parameters */
    Stream<CustomRefillerParameter<F, I>> parameters();

    /** Get the class of this refiller for discovery of annotations */
    Class<? extends Refiller> type();
  }

  /** Remove all defined actions */
  void clearActions();

  /** Remove all defined constants */
  void clearConstants();

  /** Remove all defined functions */
  void clearFunctions();

  /** Remove all defined refillers */
  void clearRefillers();

  /**
   * Define a new action
   *
   * @param name the Shesmu name of the action
   * @param description the help text for the action
   * @param clazz the class which implements the actions
   * @param supplier a generator of new instances of this action
   * @param parameters the parameters that cannot be inferred by annotations
   */
  <A extends Action> void defineAction(
      String name,
      String description,
      Class<A> clazz,
      Supplier<A> supplier,
      Stream<CustomActionParameter<A>> parameters);

  /**
   * Define a constant using a particular value
   *
   * @param name the name for the constant
   * @param description the help text for the constant
   * @param type the Shesmu type for the object
   * @param value the current value of the object
   */
  void defineConstant(String name, String description, Imyhat type, Object value);

  /**
   * Define a constant using a particular value
   *
   * @param name the name for the constant
   * @param description the help text for the constant
   * @param returnType the Shesmu type for the constant
   * @param value the current value of the object
   */
  <R> void defineConstant(
      String name, String description, ReturnTypeGuarantee<R> returnType, R value);

  /**
   * Define a constant using a particular value
   *
   * @param name the name for the constant
   * @param description the help text for the constant
   * @param returnType the Shesmu type for the constant
   * @param constant a callback to compute the current value of the constant
   */
  <R> void defineConstant(
      String name, String description, ReturnTypeGuarantee<R> returnType, Supplier<R> constant);

  /**
   * Define a new signature format that looks at input objects
   *
   * @param name the name of the signature
   * @param returnType the return type of the signature
   * @param signer a function to construct new signers
   */
  <R> void defineDynamicSigner(
      String name, ReturnTypeGuarantee<R> returnType, Supplier<? extends DynamicSigner<R>> signer);

  /**
   * Define a function taking many parameters
   *
   * @param name the name of the function
   * @param description the help text of the function
   * @param returnType the returned Shesmu type of the function
   * @param function the implementation of the function
   * @param parameters the Shesmu types and help text of the parameters of the function
   */
  void defineFunction(
      String name,
      String description,
      Imyhat returnType,
      VariadicFunction function,
      FunctionParameter... parameters);

  /**
   * Define a function taking one argument
   *
   * @param name the name of the function
   * @param description the help text of the function
   * @param returnType the returned Shesmu type of the function
   * @param parameterName the help text of the parameter
   * @param parameterType the type of the parameter
   * @param function the implementation of the function
   */
  <A, R> void defineFunction(
      String name,
      String description,
      ReturnTypeGuarantee<R> returnType,
      String parameterName,
      TypeGuarantee<A> parameterType,
      Function<A, R> function);

  /**
   * Define a function taking two arguments
   *
   * @param name the name of the function
   * @param description the help text of the function
   * @param returnType the returned Shesmu type of the function
   * @param parameter1Name the help text of the first parameter
   * @param parameter1Type the type of the first parameter
   * @param parameter2Name the help text of the second parameter
   * @param parameter2Type the type of the second parameter
   * @param function the implementation of the function
   */
  <A, B, R> void defineFunction(
      String name,
      String description,
      ReturnTypeGuarantee<R> returnType,
      String parameter1Name,
      TypeGuarantee<A> parameter1Type,
      String parameter2Name,
      TypeGuarantee<B> parameter2Type,
      BiFunction<A, B, R> function);

  /**
   * Define a new refiller
   *
   * @param name the name of the refiller
   * @param description the help text for the refiller
   */
  void defineRefiller(String name, String description, RefillDefiner definer);

  /**
   * Define a new signature format that is the same for all input objects; it depends only on the
   * olive
   *
   * @param name the name of the signature
   * @param returnType the return type of the signature
   * @param signer a function to construct new signers
   */
  <R> void defineStaticSigner(
      String name, ReturnTypeGuarantee<R> returnType, Supplier<? extends StaticSigner<R>> signer);
}
