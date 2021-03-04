package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.EcmaScriptRenderer.LambdaRender;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Function;

/** Helper to build bytecode for “olives” (decision-action stanzas) */
public final class EcmaStreamBuilder {
  public interface RenderSubsampler {
    String render(
        EcmaScriptRenderer renderer,
        String previous,
        Imyhat streamType,
        EcmaLoadableConstructor name);
  }

  private Imyhat currentType;

  private final StringBuilder stream = new StringBuilder();

  private final EcmaScriptRenderer renderer;

  public EcmaStreamBuilder(EcmaScriptRenderer renderer, Imyhat initialType, String root) {
    this.renderer = renderer;
    currentType = initialType;
    stream.append(root);
  }

  public Imyhat currentType() {
    return currentType;
  }

  public void distinct() {
    stream.insert(0, "$runtime.distinct(");
    stream
        .append(", ")
        .append(
            renderer.lambda(
                2,
                (r, args) ->
                    currentType.apply(EcmaScriptRenderer.isEqual(args.apply(0), args.apply(1)))))
        .append(")");
  }

  public final void filter(
      EcmaLoadableConstructor name, Function<EcmaScriptRenderer, String> render) {
    stream
        .append(".filter(")
        .append(
            renderer.lambda(
                1,
                (r, arg) -> {
                  name.create(arg.apply(0)).forEach(r::define);
                  return render.apply(r);
                }))
        .append(")");
  }

  public String finish() {
    return stream.toString();
  }

  public final void flatten(Imyhat newType, LambdaRender render) {
    currentType = newType;
    stream.append(".flatMap(").append(renderer.lambda(1, render)).append(")");
  }

  public void limit(String limit) {
    stream.append(".filter((_, $i) => $i <= ").append(limit).append(")");
  }

  public final void map(
      EcmaLoadableConstructor name, Imyhat newType, Function<EcmaScriptRenderer, String> render) {
    currentType = newType;

    stream
        .append(".map(")
        .append(
            renderer.lambda(
                1,
                (r, arg) -> {
                  name.create(arg.apply(0)).forEach(r::define);
                  return render.apply(r);
                }))
        .append(")");
  }

  public EcmaScriptRenderer renderer() {
    return renderer;
  }

  public void reverse() {
    stream.append(".reverse()");
  }

  public void skip(String limit) {
    stream.append(".filter((_, $i) => $i > ").append(limit).append(")");
  }

  public final void sort(
      EcmaLoadableConstructor name, boolean isString, Function<EcmaScriptRenderer, String> render) {
    stream
        .append(".sort($runtime.")
        .append(isString ? "comparatorString" : "comparatorNumeric")
        .append("(")
        .append(
            renderer.lambda(
                1,
                (r, arg) -> {
                  name.create(arg.apply(0)).forEach(r::define);
                  return render.apply(r);
                }))
        .append("))");
  }

  public final void subsample(
      List<? extends RenderSubsampler> renderers, EcmaLoadableConstructor name) {
    String sampleChain = "$runtime.subsampleStart()";
    for (final RenderSubsampler subsample : renderers) {
      sampleChain = subsample.render(renderer, sampleChain, currentType, name);
    }
    stream.insert(0, "$runtime.subsample(");
    stream.append(", ").append(sampleChain).append(")");
  }
}
