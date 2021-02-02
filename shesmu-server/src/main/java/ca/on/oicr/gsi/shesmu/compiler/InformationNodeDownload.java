package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;
import java.util.function.Consumer;

public class InformationNodeDownload extends InformationNode {
  private final ExpressionNode contents;
  private final ExpressionNode fileName;
  private final Optional<ExpressionNode> mimeType;

  public InformationNodeDownload(
      ExpressionNode fileName, Optional<ExpressionNode> mimeType, ExpressionNode contents) {
    this.contents = contents;
    this.fileName = fileName;
    this.mimeType = mimeType;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    final boolean isJson = contents.type().isSame(Imyhat.JSON);
    return String.format(
        "{ type: \"download\", isJson: %s, file: %s, mimetype: %s, contents: %s }",
        isJson,
        fileName.renderEcma(renderer),
        mimeType
            .map(m -> m.renderEcma(renderer))
            .orElse(isJson ? "application/json" : "text/plain"),
        contents.renderEcma(renderer));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return fileName.resolve(defs, errorHandler)
        & mimeType.map(m -> m.resolve(defs, errorHandler)).orElse(true)
        & contents.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return fileName.resolveDefinitions(expressionCompilerServices, errorHandler)
        & mimeType
            .map(m -> m.resolveDefinitions(expressionCompilerServices, errorHandler))
            .orElse(true)
        & contents.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = true;
    if (contents.typeCheck(errorHandler)) {
      if (!contents.type().isSame(Imyhat.STRING) && !contents.type().isSame(Imyhat.JSON)) {
        ok = false;
        contents.typeError("json or string", contents.type(), errorHandler);
      }
    } else {
      ok = false;
    }

    ok &=
        mimeType
            .map(
                m -> {
                  if (m.typeCheck(errorHandler)) {
                    if (m.type().isSame(Imyhat.STRING)) {
                      return true;
                    } else {
                      m.typeError(Imyhat.STRING, m.type(), errorHandler);
                      return false;
                    }
                  } else {
                    return false;
                  }
                })
            .orElse(true);

    if (fileName.typeCheck(errorHandler)) {
      if (!fileName.type().isSame(Imyhat.STRING)) {
        ok = false;
        fileName.typeError(Imyhat.STRING, fileName.type(), errorHandler);
      }
    } else {
      ok = false;
    }

    return ok;
  }
}
