package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

public final class JoinSourceNodeInput extends JoinSourceNode {

  protected final int column;
  private final String format;
  private InputFormatDefinition inputFormat;
  protected final int line;

  public JoinSourceNodeInput(int line, int column, String format) {
    this.line = line;
    this.column = column;
    this.format = format;
  }

  @Override
  public boolean canSign() {
    return true;
  }

  @Override
  public final void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing
  }

  @Override
  public final JoinInputSource render(
      BaseOliveBuilder oliveBuilder,
      Function<String, CallableDefinitionRenderer> definitions,
      String prefix,
      String variablePrefix,
      Predicate<String> signatureUsed,
      Predicate<String> signableUsed) {
    oliveBuilder
        .owner
        .signatureVariables()
        .filter(signature -> signatureUsed.test(variablePrefix + signature.name()))
        .forEach(
            signatureDefinition ->
                oliveBuilder.createSignature(
                    prefix,
                    inputFormat,
                    inputFormat
                        .baseStreamVariables()
                        .filter(input -> signableUsed.test(variablePrefix + input.name()))
                        .map(SignableRenderer::always),
                    signatureDefinition));

    return new JoinInputSource() {
      @Override
      public Optional<CallableDefinitionRenderer> additionalFormatCollector() {
        return Optional.empty();
      }

      @Override
      public InputFormatDefinition format() {
        return inputFormat;
      }

      @Override
      public String name() {
        return inputFormat.name();
      }

      @Override
      public void render(Renderer renderer) {
        renderer.methodGen().push(inputFormat.name());
        renderer.methodGen().invokeInterface(A_INPUT_PROVIDER_TYPE, METHOD_INPUT_PROVIDER__FETCH);
      }

      @Override
      public Type type() {
        return inputFormat.type();
      }
    };
  }

  @Override
  public final Stream<? extends Target> resolve(
      String syntax,
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    inputFormat = oliveCompilerServices.inputFormat(format);
    if (inputFormat == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown input format “%s” in %s.", line, column, format, syntax));
      return null;
    }

    return Stream.concat(inputFormat.baseStreamVariables(), oliveCompilerServices.signatures());
  }

  @Override
  public final boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
