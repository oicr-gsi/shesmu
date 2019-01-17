package ca.on.oicr.gsi.shesmu.compiler;

import org.objectweb.asm.Type;

class LoadParameter extends LoadableValue {
  private final int index;
  private final String name;
  private final Type type;

  public LoadParameter(int index, Target source) {
    super();
    this.index = index;
    name = source.name();
    type = source.type().asmType();
  }

  @Override
  public void accept(Renderer t) {
    t.methodGen().loadArg(index);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Type type() {
    return type;
  }
}
