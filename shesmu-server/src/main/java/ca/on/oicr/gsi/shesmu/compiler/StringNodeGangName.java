package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.GangDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class StringNodeGangName extends StringNode {
  private enum ElementType {
    STRING {
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
    },
    INT {

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
    };

    abstract void check(GeneratorAdapter methodGen, Label skip);

    abstract boolean isGood(Consumer<String> errorHandler);

    abstract void render(GeneratorAdapter methodGen);
  }

  private final class Element {
    final boolean dropIfDefault;
    private final Imyhat expectedType;
    final Target source;
    ElementType type;

    private Element(Target source, Imyhat expectedType, boolean dropIfDefault) {
      this.source = source;
      this.expectedType = expectedType;
      this.dropIfDefault = dropIfDefault;
    }

    public boolean typeCheck(Consumer<String> errorHandler) {
      if (!expectedType.isSame(source.type())) {
        errorHandler.accept(
            String.format(
                "%d:%d: Gang variable %s should have type %s but got %s.",
                line, column, source.name(), expectedType.name(), source.type().name()));
      }
      if (source.type().isSame(Imyhat.STRING)) {
        type = ElementType.STRING;
        return true;
      } else if (source.type().isSame(Imyhat.INTEGER)) {
        type = ElementType.INT;
        return true;
      } else {
        errorHandler.accept(
            String.format(
                "%d:%d: Variable %s in gang %s has type %s which cannot be converted to string.",
                line, column, source.name(), definition.name(), source.type().name()));
        return false;
      }
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
        element.type.check(renderer.methodGen(), skip);
        if (underscore) {
          renderer.methodGen().push("_");
          renderer.methodGen().invokeVirtual(A_STRING_BUILDER_TYPE, STRING_BUILDER__APPEND_STRING);
        }
        renderer.loadTarget(element.source);
        element.type.render(renderer.methodGen());
        renderer.methodGen().mark(skip);
      } else {
        if (underscore) {
          renderer.methodGen().push("_");
          renderer.methodGen().invokeVirtual(A_STRING_BUILDER_TYPE, STRING_BUILDER__APPEND_STRING);
        }
        renderer.loadTarget(element.source);
        element.type.render(renderer.methodGen());
      }
      underscore = true;
    }
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    throw new UnsupportedOperationException("It should be impossible to have a gang in ECMAScript");
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final Optional<List<Element>> result =
        TypeUtils.matchGang(line, column, defs, definition, Element::new, errorHandler);
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
      ok &= element.typeCheck(errorHandler);
    }
    return ok;
  }
}
