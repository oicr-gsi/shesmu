package ca.on.oicr.gsi.shesmu.compiler.definitions;

import java.util.function.Function;
import org.objectweb.asm.Type;

public enum SignatureStorage {
  STATIC {
    @Override
    public Type holderType(Type type) {
      return type;
    }

    @Override
    public Class<?> holderClass(Class<?> clazz) {
      return clazz;
    }
  },
  DYNAMIC {
    @Override
    public Type holderType(Type type) {
      return A_FUNCTION_TYPE;
    }

    @Override
    public Class<?> holderClass(Class<?> clazz) {
      return Function.class;
    }
  };
  private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);

  public abstract Class<?> holderClass(Class<?> clazz);

  public abstract Type holderType(Type type);
}
