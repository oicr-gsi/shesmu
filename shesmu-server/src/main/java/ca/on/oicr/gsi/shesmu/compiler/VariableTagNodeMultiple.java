package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class VariableTagNodeMultiple extends VariableTagNode {

  private static final Type A_RUNTIME_SUPPORT = Type.getType(RuntimeSupport.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Method METHOD_RUNTIME_SUPPORT__POPULATE_ARRAY =
      new Method(
          "populateArray",
          Type.INT_TYPE,
          new Type[] {Type.getType(String[].class), A_SET_TYPE, Type.INT_TYPE});
  private static final Method METHOD_SET__SIZE = new Method("size", Type.INT_TYPE, new Type[] {});

  protected VariableTagNodeMultiple(ExpressionNode expression) {
    super(expression);
  }

  @Override
  public Optional<IntConsumer> renderDynamicSize(Renderer renderer) {
    render(renderer);
    renderer.methodGen().dup();
    final int listLocal = renderer.methodGen().newLocal(A_SET_TYPE);
    renderer.methodGen().storeLocal(listLocal);
    renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__SIZE);
    renderer.methodGen().math(GeneratorAdapter.ADD, Type.INT_TYPE);
    return Optional.of(
        indexLocal -> {
          renderer.methodGen().dup();
          renderer.methodGen().loadLocal(listLocal);
          renderer.methodGen().loadLocal(indexLocal);
          renderer
              .methodGen()
              .invokeStatic(A_RUNTIME_SUPPORT, METHOD_RUNTIME_SUPPORT__POPULATE_ARRAY);
          renderer.methodGen().storeLocal(indexLocal);
        });
  }

  @Override
  public int renderStaticTag(Renderer renderer, int tagIndex) {
    return 0;
  }

  @Override
  protected Imyhat requiredType() {
    return Imyhat.STRING.asList();
  }

  @Override
  public int staticSize() {
    return 0;
  }
}
