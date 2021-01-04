package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** A multi-keyed map that functions a value based on rules/tables */
public interface FunctionDefinition {
  static FunctionDefinition cast(
      String name,
      String ecmaDefinition,
      Imyhat returnType,
      Imyhat argumentType,
      String description) {
    return new FunctionDefinition() {

      @Override
      public String description() {
        return description;
      }

      @Override
      public Path filename() {
        return null;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Stream<FunctionParameter> parameters() {
        return Stream.of(new FunctionParameter("argument", argumentType));
      }

      @Override
      public void render(GeneratorAdapter methodGen) {
        methodGen.cast(argumentType.apply(TypeUtils.TO_ASM), returnType.apply(TypeUtils.TO_ASM));
      }

      @Override
      public String renderEcma(Object[] args) {
        return String.format(ecmaDefinition, args);
      }

      @Override
      public void renderStart(GeneratorAdapter methodGen) {
        // None required.
      }

      @Override
      public Imyhat returnType() {
        return returnType;
      }
    };
  }
  /**
   * Define a function that binds to a static method
   *
   * @param owner the class containing the static method
   * @param methodName the name of the static method
   * @param description the help text for the method
   * @param returnType the return type of the method (the appropriate Java type will be matched)
   * @param parameters the types of the arguments to the method (the appropriate Java types will be
   *     matched)
   */
  public static FunctionDefinition staticMethod(
      String name,
      Class<?> owner,
      String methodName,
      String description,
      String ecmaDefinition,
      Imyhat returnType,
      FunctionParameter... parameters) {
    return new FunctionDefinition() {

      @Override
      public String description() {
        return description;
      }

      @Override
      public Path filename() {
        return null;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Stream<FunctionParameter> parameters() {
        return Stream.of(parameters);
      }

      @Override
      public void render(GeneratorAdapter methodGen) {
        methodGen.invokeStatic(
            Type.getType(owner),
            new Method(
                methodName,
                returnType.apply(TypeUtils.TO_ASM),
                Stream.of(parameters)
                    .map(p -> p.type().apply(TypeUtils.TO_ASM))
                    .toArray(Type[]::new)));
      }

      @Override
      public String renderEcma(Object[] args) {
        return String.format(ecmaDefinition, args);
      }

      @Override
      public void renderStart(GeneratorAdapter methodGen) {
        // None required.
      }

      @Override
      public Imyhat returnType() {
        return returnType;
      }
    };
  }

  public static FunctionDefinition virtualIntegerFunction(
      String name,
      String methodName,
      String description,
      String ecmaDefinition,
      FunctionParameter firstParameter,
      FunctionParameter... parameters) {
    return new FunctionDefinition() {

      @Override
      public String description() {
        return description;
      }

      @Override
      public Path filename() {
        return null;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Stream<FunctionParameter> parameters() {
        return Stream.concat(Stream.of(firstParameter), Stream.of(parameters));
      }

      @Override
      public void render(GeneratorAdapter methodGen) {
        Method method =
            new Method(
                methodName,
                Type.INT_TYPE,
                Stream.of(parameters)
                    .map(p -> p.type().apply(TypeUtils.TO_ASM))
                    .toArray(Type[]::new));
        Imyhat owner = firstParameter.type();
        if (owner.javaType().isInterface()) {
          methodGen.invokeInterface(owner.apply(TypeUtils.TO_ASM), method);
        } else {
          methodGen.invokeVirtual(owner.apply(TypeUtils.TO_ASM), method);
        }
        methodGen.cast(Type.INT_TYPE, Type.LONG_TYPE);
      }

      @Override
      public String renderEcma(Object[] args) {
        return String.format(ecmaDefinition, args);
      }

      @Override
      public void renderStart(GeneratorAdapter methodGen) {
        // None required.
      }

      @Override
      public Imyhat returnType() {
        return Imyhat.INTEGER;
      }
    };
  }

  public static FunctionDefinition virtualMethod(
      String name,
      String methodName,
      String description,
      String ecmaDefinition,
      Imyhat returnType,
      FunctionParameter firstParameter,
      FunctionParameter... parameters) {
    return new FunctionDefinition() {

      @Override
      public String description() {
        return description;
      }

      @Override
      public Path filename() {
        return null;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Stream<FunctionParameter> parameters() {
        return Stream.concat(Stream.of(firstParameter), Stream.of(parameters));
      }

      @Override
      public void render(GeneratorAdapter methodGen) {
        Method method =
            new Method(
                methodName,
                returnType.apply(TypeUtils.TO_ASM),
                Stream.of(parameters)
                    .map(p -> p.type().apply(TypeUtils.TO_ASM))
                    .toArray(Type[]::new));
        Imyhat owner = firstParameter.type();
        if (owner.javaType().isInterface()) {
          methodGen.invokeInterface(owner.apply(TypeUtils.TO_ASM), method);
        } else {
          methodGen.invokeVirtual(owner.apply(TypeUtils.TO_ASM), method);
        }
      }

      @Override
      public String renderEcma(Object[] args) {
        return String.format(ecmaDefinition, args);
      }

      @Override
      public void renderStart(GeneratorAdapter methodGen) {
        // None required.
      }

      @Override
      public Imyhat returnType() {
        return returnType;
      }
    };
  }

  /** Documentation about how this function works */
  String description();

  Path filename();

  /** The name of the function. */
  String name();

  /** The parameters of the function, in order */
  Stream<FunctionParameter> parameters();

  /** The function was actually used in a program. */
  default void read() {}

  /** Create bytecode for this function. */
  void render(GeneratorAdapter methodGen);

  /** Create ECMAScript for this function */
  String renderEcma(Object[] args);
  /**
   * Create bytecode for anything that should be on the stack before the arguments of this function.
   */
  void renderStart(GeneratorAdapter methodGen);

  /** The return type of the map */
  Imyhat returnType();
}
