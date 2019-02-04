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
      Imyhat returnType,
      FunctionParameter... parameters) {
    return new FunctionDefinition() {

      @Override
      public String description() {
        return description;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Path filename() {
        return null;
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
      public void renderStart(GeneratorAdapter methodGen) {
        // None required.
      }

      @Override
      public Imyhat returnType() {
        return returnType;
      }
    };
  }

  public static FunctionDefinition virtualMethod(
      String name,
      String methodName,
      String description,
      Imyhat returnType,
      FunctionParameter... parameters) {
    return new FunctionDefinition() {

      @Override
      public String description() {
        return description;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Path filename() {
        return null;
      }

      @Override
      public Stream<FunctionParameter> parameters() {
        return Stream.of(parameters);
      }

      @Override
      public void render(GeneratorAdapter methodGen) {
        Method method =
            new Method(
                methodName,
                returnType.apply(TypeUtils.TO_ASM),
                Stream.of(parameters)
                    .skip(1)
                    .map(p -> p.type().apply(TypeUtils.TO_ASM))
                    .toArray(Type[]::new));
        Imyhat owner = parameters[0].type();
        if (owner.javaType().isInterface()) {
          methodGen.invokeInterface(owner.apply(TypeUtils.TO_ASM), method);
        } else {
          methodGen.invokeVirtual(owner.apply(TypeUtils.TO_ASM), method);
        }
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

  /** The name of the function. */
  String name();

  Path filename();

  /** The parameters of the function, in order */
  Stream<FunctionParameter> parameters();

  /** Create bytecode for this function. */
  void render(GeneratorAdapter methodGen);

  /**
   * Create bytecode for anything that should be on the stack before the arguments of this function.
   */
  void renderStart(GeneratorAdapter methodGen);

  /** The return type of the map */
  Imyhat returnType();
}
