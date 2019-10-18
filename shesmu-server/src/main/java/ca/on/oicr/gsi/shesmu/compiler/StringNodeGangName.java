package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.GangDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class StringNodeGangName extends StringNode {
  private class BadElement extends Element {

    private BadElement(Target source, boolean dropIfDefault) {
      super(source, dropIfDefault);
    }

    @Override
    void check(GeneratorAdapter methodGen, Label skip) {
      throw new UnsupportedOperationException();
    }

    @Override
    boolean isGood(Consumer<String> errorHandler) {
      errorHandler.accept(
          String.format(
              "%d:%d: Variable %s in gang %s cannot be converted to string.",
              line, column, source.name(), definition.name()));
      return false;
    }

    @Override
    void render(GeneratorAdapter methodGen) {
      throw new UnsupportedOperationException();
    }
  }

  private abstract static class Element {
    protected final boolean dropIfDefault;
    protected final Target source;

    private Element(Target source, boolean dropIfDefault) {
      this.source = source;
      this.dropIfDefault = dropIfDefault;
    }

    abstract void check(GeneratorAdapter methodGen, Label skip);

    abstract boolean isGood(Consumer<String> errorHandler);

    abstract void render(GeneratorAdapter methodGen);
  }

  private class IntElement extends Element {

    private IntElement(Target source, boolean dropIfDefault) {
      super(source, dropIfDefault);
    }

    @Override
    void check(GeneratorAdapter methodGen, Label skip) {
      methodGen.ifCmp(Type.LONG_TYPE, GeneratorAdapter.EQ, skip);
    }

    @Override
    boolean isGood(Consumer<String> errorHandler) {
      return true;
    }

    @Override
    void render(GeneratorAdapter methodGen) {
      methodGen.invokeVirtual(A_STRING_BUILDER_TYPE, STRING_BUILDER__APPEND_LONG);
    }
  }

  private class StringElement extends Element {

    private StringElement(Target source, boolean dropIfDefault) {
      super(source, dropIfDefault);
    }

    @Override
    void check(GeneratorAdapter methodGen, Label skip) {
      methodGen.invokeVirtual(A_STRING_TYPE, STRING__IS_EMPTY);
      methodGen.ifZCmp(GeneratorAdapter.NE, skip);
    }

    @Override
    boolean isGood(Consumer<String> errorHandler) {
      return true;
    }

    @Override
    void render(GeneratorAdapter methodGen) {
      methodGen.invokeVirtual(A_STRING_BUILDER_TYPE, STRING_BUILDER__APPEND_STRING);
    }
  }

  private static final Type A_STRING_BUILDER_TYPE = Type.getType(StringBuilder.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method STRING_BUILDER__APPEND_LONG =
      new Method("append", A_STRING_BUILDER_TYPE, new Type[] {Type.LONG_TYPE});
  private static final Method STRING_BUILDER__APPEND_STRING =
      new Method("append", A_STRING_BUILDER_TYPE, new Type[] {A_STRING_TYPE});
  private static final Method STRING__IS_EMPTY =
      new Method("isEmpty", Type.BOOLEAN_TYPE, new Type[0]);
  private final int column;
  private GangDefinition definition;
  private List<Element> elements;
  private final int line;
  private final String name;

  public StringNodeGangName(int line, int column, String name) {
    this.line = line;
    this.column = column;
    this.name = name;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    for (final Element element : elements) {
      if (predicate.test(element.source.flavour())) {
        names.add(element.source.name());
      }
    }
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing.
  }

  @Override
  public boolean isPassive() {
    return false;
  }

  @Override
  public void render(Renderer renderer) {
    boolean underscore = false;
    for (final Element element : elements) {
      if (element.dropIfDefault) {
        final Label skip = renderer.methodGen().newLabel();
        renderer.loadTarget(element.source);
        element.check(renderer.methodGen(), skip);
        if (underscore) {
          renderer.methodGen().push("_");
          renderer.methodGen().invokeVirtual(A_STRING_BUILDER_TYPE, STRING_BUILDER__APPEND_STRING);
        }
        renderer.loadTarget(element.source);
        element.render(renderer.methodGen());
        renderer.methodGen().mark(skip);
      } else {
        if (underscore) {
          renderer.methodGen().push("_");
          renderer.methodGen().invokeVirtual(A_STRING_BUILDER_TYPE, STRING_BUILDER__APPEND_STRING);
        }
        renderer.loadTarget(element.source);
        element.render(renderer.methodGen());
      }
      underscore = true;
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final Optional<List<Element>> result =
        TypeUtils.matchGang(
            line,
            column,
            defs,
            definition,
            (target, dropIfDefault) -> {
              if (target.type().isSame(Imyhat.STRING)) {
                return new StringElement(target, dropIfDefault);
              }
              if (target.type().isSame(Imyhat.INTEGER)) {
                return new IntElement(target, dropIfDefault);
              }
              return new BadElement(target, dropIfDefault);
            },
            errorHandler);
    result.ifPresent(x -> elements = x);
    return result.isPresent();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final Optional<? extends GangDefinition> gangDefinition =
        expressionCompilerServices
            .inputFormat()
            .gangs()
            .filter(g -> g.name().equals(name))
            .findAny();
    if (gangDefinition.isPresent()) {
      definition = gangDefinition.get();
      return true;
    }
    errorHandler.accept(String.format("%d:%d: Unknown gang “%s”.", line, column, name));
    return false;
  }

  @Override
  public String text() {
    return null;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = true;
    for (final Element element : elements) {
      ok &= element.isGood(errorHandler);
    }
    return ok;
  }
}
