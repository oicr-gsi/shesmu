package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.CopySemantics;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class SourceNodeZipper extends SourceNode {

  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  private static final Type A_COPY_SEMANTICS_ARRAY_TYPE = Type.getType(CopySemantics[].class);
  private static final Method METHOD_RUNTIME_SUPPORT__ZIP =
      new Method(
          "zip", A_STREAM_TYPE, new Type[] {A_SET_TYPE, A_SET_TYPE, A_COPY_SEMANTICS_ARRAY_TYPE});
  private static final Handle SEMANTICS_BSM =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getType(CopySemantics.class).getInternalName(),
          "bootstrap",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(MethodHandles.Lookup.class),
              Type.getType(String.class),
              Type.getType(MethodType.class),
              Type.getType(int[].class)),
          false);

  private final ExpressionNode left;
  private final ExpressionNode right;
  private Imyhat type;
  private List<Pair<Integer, Character>> copySemantics = new ArrayList<>();

  public SourceNodeZipper(int line, int column, ExpressionNode left, ExpressionNode right) {
    super(line, column);
    this.left = left;
    this.right = right;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    left.collectFreeVariables(names, predicate);
    right.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    left.collectPlugins(pluginFileNames);
    right.collectPlugins(pluginFileNames);
  }

  @Override
  public Ordering ordering() {
    return Ordering.RANDOM;
  }

  @Override
  public JavaStreamBuilder render(Renderer renderer) {
    left.render(renderer);
    right.render(renderer);
    renderer
        .methodGen()
        .invokeDynamic(
            copySemantics.stream().map(p -> p.second().toString()).collect(Collectors.joining()),
            Type.getMethodDescriptor(A_COPY_SEMANTICS_ARRAY_TYPE),
            SEMANTICS_BSM,
            copySemantics.stream().map(Pair::first).toArray());
    renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__ZIP);

    return renderer.buildStream(type);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return left.resolve(defs, errorHandler) & right.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return left.resolveFunctions(definedFunctions, errorHandler)
        & right.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public Imyhat streamType() {
    return type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (!left.typeCheck(errorHandler) | !right.typeCheck(errorHandler)) {
      return false;
    }
    final List<Imyhat.TupleImyhat> types = new ArrayList<>();
    for (final ExpressionNode expression : Arrays.asList(left, right)) {
      final Imyhat type = expression.type();
      if (type == Imyhat.EMPTY) {
        errorHandler.accept(
            String.format(
                "%d:%d: Cannot iterate over empty list. No type to check subsequent operations.",
                line(), column()));
        return false;
      }
      if (type instanceof Imyhat.ListImyhat) {
        Imyhat inner = ((Imyhat.ListImyhat) type).inner();
        if (inner instanceof Imyhat.TupleImyhat) {
          types.add((Imyhat.TupleImyhat) inner);
        } else {
          errorHandler.accept(
              String.format("%d:%d: Unzipping must be over tuples.", line(), column()));
          return false;
        }
      } else {
        expression.typeError("list", type, errorHandler);
        return false;
      }
    }
    final Imyhat leftIndex = types.get(0).get(0);
    final Imyhat rightIndex = types.get(1).get(0);
    if (!leftIndex.isSame(rightIndex)) {
      errorHandler.accept(
          String.format(
              "%d:%d: The first element of both tuples must match. Got %s and %s.",
              line(), column(), leftIndex.name(), rightIndex.name()));
      return false;
    }
    final List<Imyhat> output = new ArrayList<>();
    output.add(leftIndex.unify(rightIndex));
    for (int position = 0; position < 2; position++) {
      for (int index = 1; index < types.get(position).count(); index++) {
        final Imyhat outputType = types.get(position).get(index).asOptional();
        output.add(outputType);
        copySemantics.add(
            new Pair<>(
                index,
                (char)
                    ((outputType.isSame(types.get(position).get(index)) ? 'A' : 'a') + position)));
      }
    }
    type = Imyhat.tuple(output.stream().toArray(Imyhat[]::new));
    return true;
  }
}
