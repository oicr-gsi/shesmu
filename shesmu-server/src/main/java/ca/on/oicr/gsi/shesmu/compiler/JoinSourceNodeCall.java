package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.*;
import static org.objectweb.asm.Type.VOID_TYPE;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.server.BaseHotloadingCompiler;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class JoinSourceNodeCall extends JoinSourceNode {

  private final List<ExpressionNode> arguments;
  private final int column;
  private final int line;
  private final String name;
  private CallableDefinition target;
  private InputFormatDefinition format;

  public JoinSourceNodeCall(int line, int column, String name, List<ExpressionNode> arguments) {
    this.line = line;
    this.column = column;
    this.name = name;
    this.arguments = arguments;
  }

  @Override
  public boolean canSign() {
    return target.isRoot();
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    arguments.forEach(arg -> arg.collectPlugins(pluginFileNames));
  }

  private static final Method CTOR_DEFAULT = new Method("<init>", VOID_TYPE, new Type[] {});

  @Override
  public JoinInputSource render(
      BaseOliveBuilder oliveBuilder,
      Function<String, CallableDefinitionRenderer> definitions,
      String prefix,
      String variablePrefix,
      Predicate<String> signatureUsed,
      Predicate<String> signableUsed) {
    final var accessorType =
        Type.getObjectType(
            String.format(
                "%s/Join Signer Accessor %d:%d",
                BaseHotloadingCompiler.PACKAGE_INTERNAL, line, column));
    final var signablesAlwaysUsed =
        format
            .baseStreamVariables()
            .filter(v -> v.flavour() == Target.Flavour.STREAM_SIGNABLE)
            .map(InputVariable::name)
            .filter(signableUsed)
            .collect(Collectors.toSet());
    final List<SignableVariableCheck> checks = new ArrayList<>();
    target.collectSignables(signablesAlwaysUsed, checks::add);
    final var signables =
        format
            .baseStreamVariables()
            .filter(v -> v.flavour() == Target.Flavour.STREAM_SIGNABLE)
            .map(
                v ->
                    signablesAlwaysUsed.contains(v.name())
                        ? SignableRenderer.always(v)
                        : SignableRenderer.conditional(
                            v,
                            checks.stream()
                                .filter(c -> c.name().equals(v.name()))
                                .collect(Collectors.toList())))
            .collect(Collectors.toList());
    oliveBuilder
        .owner
        .signatureVariables()
        .forEach(
            signer ->
                OliveBuilder.createSignatureInfrastructure(
                    oliveBuilder.owner, prefix, format, signables, signer));
    OliveBuilder.buildSignerAccessor(oliveBuilder.owner, accessorType, prefix, format);
    return new JoinInputSource() {
      private final CallableDefinitionRenderer defineOlive = definitions.apply(name);

      @Override
      public InputFormatDefinition format() {
        return format;
      }

      @Override
      public String name() {
        return target.name();
      }

      @Override
      public void render(Renderer renderer) {
        renderer.methodGen().push(target.format());
        renderer.methodGen().invokeInterface(A_INPUT_PROVIDER_TYPE, METHOD_INPUT_PROVIDER__FETCH);
        defineOlive.generatePreamble(renderer.methodGen());
        oliveBuilder.loadOliveServices(renderer.methodGen());
        oliveBuilder.loadInputProvider(renderer.methodGen());
        renderer.emitNamed(ACTION_NAME);
        renderer.emitNamed(SOURCE_LOCATION_FILE);
        renderer.emitNamed(SOURCE_LOCATION_LINE);
        renderer.emitNamed(SOURCE_LOCATION_COLUMN);
        renderer.emitNamed(SOURCE_LOCATION_HASH);
        renderer.methodGen().newInstance(accessorType);
        renderer.methodGen().dup();
        renderer.methodGen().invokeConstructor(accessorType, CTOR_DEFAULT);
        for (final var rendererConsumer : arguments) {
          rendererConsumer.render(renderer);
        }
        defineOlive.generateCall(renderer.methodGen());
      }

      @Override
      public Type type() {
        return defineOlive.currentType();
      }
    };
  }

  @Override
  public Stream<? extends Target> resolve(
      String syntax,
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    final var limitedDefs = defs.replaceStream(Stream.empty(), true);
    if (arguments.stream().filter(argument -> argument.resolve(limitedDefs, errorHandler)).count()
        != arguments.size()) {
      return null;
    }
    return target.outputStreamVariables(oliveCompilerServices, errorHandler).orElse(null);
  }

  @Override
  public boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    final var ok =
        arguments.stream()
                .filter(
                    argument -> argument.resolveDefinitions(oliveCompilerServices, errorHandler))
                .count()
            == arguments.size();
    target = oliveCompilerServices.olive(name);
    if (target != null) {
      if (target.parameterCount() != arguments.size()) {
        errorHandler.accept(
            String.format(
                "%d:%d: “Define %s” specifies %d parameters, but only %d arguments provided.",
                line, column, name, target.parameterCount(), arguments.size()));
        return false;
      }
      format = oliveCompilerServices.inputFormat(target.format());
      return ok;
    }
    errorHandler.accept(
        String.format("%d:%d: Cannot find matching “Define %s” for call.", line, column, name));
    return false;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return IntStream.range(0, arguments.size())
            .filter(
                index -> {
                  if (!arguments.get(index).typeCheck(errorHandler)) {
                    return false;
                  }
                  final var isSame =
                      arguments.get(index).type().isSame(target.parameterType(index));
                  if (!isSame) {
                    errorHandler.accept(
                        String.format(
                            "%d:%d: Parameter %d to “%s” expects %s, but got %s.",
                            line,
                            column,
                            index,
                            name,
                            target.parameterType(index).name(),
                            arguments.get(index).type().name()));
                  }
                  return isSame;
                })
            .count()
        == arguments.size();
  }
}
