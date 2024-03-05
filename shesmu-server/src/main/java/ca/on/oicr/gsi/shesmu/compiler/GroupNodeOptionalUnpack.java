package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A only-if action in a “Group” clause
 *
 * <p>Also usable as the variable definition for the result
 */
public final class GroupNodeOptionalUnpack extends GroupNode {

  public static final String INNER_SUFFIX = ":inner";

  static String innerName(String name) {
    return name + INNER_SUFFIX;
  }

  private final String name;
  private final GroupNode inner;
  private final OptionalGroupUnpack unpack;
  private final Map<Integer, List<UnboxableExpression>> captures = new TreeMap<>();

  public GroupNodeOptionalUnpack(
      int line, int column, String name, GroupNode inner, OptionalGroupUnpack unpack) {
    super(line, column);
    this.name = name;
    this.inner = inner;
    this.unpack = unpack;
  }

  @Override
  public void collectFreeVariables(Set<String> freeVariables, Predicate<Flavour> predicate) {
    for (final var layer : captures.values()) {
      for (final var capture : layer) {
        capture.collectFreeVariables(freeVariables, predicate);
      }
    }
    inner.collectFreeVariables(freeVariables, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    for (final var layer : captures.values()) {
      for (final var capture : layer) {
        capture.collectPlugins(pluginFileNames);
      }
    }
    inner.collectPlugins(pluginFileNames);
  }

  @Override
  public boolean isRead() {
    return inner.isRead();
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void read() {
    inner.read();
  }

  @Override
  public void render(Regrouper regroup, RootBuilder rootBuilder) {
    inner.render(regroup.addOnlyIf(name, unpack.consumer(inner.type(), captures)), rootBuilder);
  }

  @Override
  public boolean resolve(
      NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler) {
    return inner.resolve(defs, outerDefs, errorHandler)
        && captures.values().stream()
            .allMatch(
                layer ->
                    layer.stream().filter(capture -> capture.resolve(defs, errorHandler)).count()
                        == layer.size());
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return inner.resolveDefinitions(
        new OptionalCaptureCompilerServices(expressionCompilerServices, errorHandler, captures),
        errorHandler);
  }

  @Override
  public Imyhat type() {
    return unpack.type(inner.type());
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (captures.isEmpty()) {
      errorHandler.accept(
          String.format("%d:%d: No optional values are used in this collector.", line(), column()));
      return false;
    } else {
      var ok =
          captures.values().stream()
                  .allMatch(
                      layer ->
                          layer.stream().filter(capture -> capture.typeCheck(errorHandler)).count()
                              == layer.size())
              && inner.typeCheck(errorHandler);
      return ok;
    }
  }
}
