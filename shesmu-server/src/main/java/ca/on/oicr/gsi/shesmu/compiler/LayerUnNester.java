package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class LayerUnNester implements Consumer<EcmaScriptRenderer> {

  private final Iterator<List<UnboxableExpression>> iterator;
  private final ExpressionNode inner;
  private final String output;

  LayerUnNester(Iterator<List<UnboxableExpression>> iterator, ExpressionNode inner, String output) {
    this.iterator = iterator;
    this.inner = inner;
    this.output = output;
  }

  @Override
  public void accept(EcmaScriptRenderer renderer) {
    if (iterator.hasNext()) {
      renderer.conditional(
          iterator.next().stream()
              .map(
                  capture -> {
                    final var capturedValue = renderer.newConst(capture.renderEcma(renderer));
                    renderer.define(
                        new EcmaLoadableValue() {
                          @Override
                          public String name() {
                            return capture.name();
                          }

                          @Override
                          public String get() {
                            return capturedValue;
                          }
                        });
                    return capturedValue + " !== null";
                  })
              .collect(Collectors.joining(" && ")),
          this);
    } else {
      renderer.statement(String.format("%s = %s", output, inner.renderEcma(renderer)));
    }
  }
}
