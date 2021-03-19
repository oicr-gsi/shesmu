package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public abstract class JoinSourceNode {

  protected static final Type A_INPUT_PROVIDER_TYPE = Type.getType(InputProvider.class);
  protected static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  protected static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final ParseDispatch<JoinSourceNode> DISPATCH = new ParseDispatch<>();
  protected static final Method METHOD_INPUT_PROVIDER__FETCH =
      new Method("fetch", A_STREAM_TYPE, new Type[] {A_STRING_TYPE});

  static {
    DISPATCH.addKeyword(
        "Call",
        (p, o) -> {
          final var name = new AtomicReference<String>();
          final var arguments = new AtomicReference<List<ExpressionNode>>();
          final var result =
              p.qualifiedIdentifier(name::set)
                  .whitespace()
                  .symbol("(")
                  .whitespace()
                  .listEmpty(arguments::set, ExpressionNode::parse, ',')
                  .symbol(")")
                  .whitespace();
          if (result.isGood()) {
            o.accept(new JoinSourceNodeCall(p.line(), p.column(), name.get(), arguments.get()));
          }
          return result;
        });
    DISPATCH.addRaw(
        "input format",
        (p, o) -> {
          final var name = new AtomicReference<String>();
          final var result = p.identifier(name::set).whitespace();
          if (result.isGood()) {
            o.accept(new JoinSourceNodeInput(p.line(), p.column(), name.get()));
          }
          return result;
        });
  }

  public static Parser parse(Parser input, Consumer<JoinSourceNode> output) {
    return input.dispatch(DISPATCH, output);
  }

  public abstract boolean canSign();

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public abstract JoinInputSource render(
      BaseOliveBuilder oliveBuilder,
      Function<String, CallableDefinitionRenderer> definitions,
      String prefix,
      String variablePrefix,
      Predicate<String> signatureUsed,
      Predicate<String> singableUsed);

  public abstract Stream<? extends Target> resolve(
      String syntax,
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
