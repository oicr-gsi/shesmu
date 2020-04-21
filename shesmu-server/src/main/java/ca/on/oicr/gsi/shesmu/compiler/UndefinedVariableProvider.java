package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Something that can "create" undefined variables out of thin air.
 *
 * <p>This is used by <tt>*</tt> to force an undefined variable to be created
 */
public interface UndefinedVariableProvider {
  UndefinedVariableProvider NONE = n -> Optional.empty();

  /**
   * Create a provider that delegates to another and invokes a callback with any results that are
   * produced
   */
  static UndefinedVariableProvider listen(
      UndefinedVariableProvider input, Consumer<Target> consumer) {
    return name -> {
      final Optional<Target> result = input.handleUndefinedVariable(name);
      result.ifPresent(consumer);
      return result;
    };
  }

  /** Combine two providers in sequence */
  static UndefinedVariableProvider combine(
      UndefinedVariableProvider first, UndefinedVariableProvider second) {
    return name -> {
      final Optional<Target> result = first.handleUndefinedVariable(name);
      if (!result.isPresent()) {
        return second.handleUndefinedVariable(name);
      }
      return result;
    };
  }

  Optional<Target> handleUndefinedVariable(String name);
}
