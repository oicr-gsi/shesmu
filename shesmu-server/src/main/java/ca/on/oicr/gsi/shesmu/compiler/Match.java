package ca.on.oicr.gsi.shesmu.compiler;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public enum Match {
  ALL("allMatch", "All", false, false),
  ANY("anyMatch", "Any", true, true),
  NONE("noneMatch", "None", true, false);
  final Method method;
  private final boolean shortCircuitResult;
  private final boolean stopOnPredicateMatches;
  private final String syntax;

  Match(
      String methodName,
      String syntax,
      boolean stopOnPredicateMatches,
      boolean shortCircuitResult) {
    this.syntax = syntax;
    this.stopOnPredicateMatches = stopOnPredicateMatches;
    this.shortCircuitResult = shortCircuitResult;
    method =
        new Method(methodName, Type.BOOLEAN_TYPE, new Type[] {JavaStreamBuilder.A_PREDICATE_TYPE});
  }

  public boolean shortCircuitResult() {
    return shortCircuitResult;
  }

  public boolean stopOnPredicateMatches() {
    return stopOnPredicateMatches;
  }

  public String syntax() {
    return syntax;
  }
}
