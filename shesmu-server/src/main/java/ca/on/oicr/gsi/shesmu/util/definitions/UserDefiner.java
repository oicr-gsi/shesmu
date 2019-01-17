package ca.on.oicr.gsi.shesmu.util.definitions;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.Imyhat;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Interface to define a function */
public interface UserDefiner {
  /** Remove all defined actions */
  void clearActions();

  /** Remove all defined constants */
  public void clearConstants();

  /** Remove all defined functions */
  void clearFunctions();

  /**
   * Define a new action
   *
   * @param name the Shesmu name of the action
   * @param description the help text for the action
   * @param clazz the class which implements the actions
   * @param supplier a generator of new instances of this action
   * @param parameters the parameters that cannot be inferred by annotations
   */
  public <A extends Action> void defineAction(
      String name,
      String description,
      Class<A> clazz,
      Supplier<A> supplier,
      Stream<ActionParameterDefinition> parameters);

  /**
   * Define a constant using a particular value
   *
   * @param name the name for the constant
   * @param description the help text for the constant
   * @param type the Shesmu type for the object
   * @param value the current value of the object
   */
  public void defineConstant(String name, String description, Imyhat type, Object value);

  /**
   * Define a constant using a particular value
   *
   * @param name the name for the constant
   * @param description the help text for the constant
   * @param type the Shesmu type for the constant
   * @param value a callback to compute the current value of the constant
   */
  public <R> void defineConstant(
      String name, String description, TypeGlue<R> returnType, Supplier<R> constant);

  /**
   * Define a function taking many parameters
   *
   * @param name the name of the function
   * @param description the help text of the function
   * @param returnType the returned Shesmu type of the function
   * @param function the implementation of the function
   * @param parameters the Shesmu types and help text of the parameters of the function
   */
  public void defineFunction(
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
  public <A, R> void defineFunction(
      String name,
      String description,
      TypeGlue<R> returnType,
      String parameterName,
      TypeGlue<A> parameterType,
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
  public <A, B, R> void defineFunction(
      String name,
      String description,
      TypeGlue<R> returnType,
      String parameter1Name,
      TypeGlue<A> parameter1Type,
      String parameter2Name,
      TypeGlue<B> parameter2Type,
      BiFunction<A, B, R> function);
}
