package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Optional;

public interface UndefinedVariableProvider {
  UndefinedVariableProvider NONE = n -> Optional.empty();

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
