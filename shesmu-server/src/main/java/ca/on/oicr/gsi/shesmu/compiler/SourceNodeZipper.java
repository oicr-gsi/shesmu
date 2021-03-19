package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.CopySemantics;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
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
  private Imyhat keyType;
  private final List<Pair<Integer, Character>> copySemantics = new ArrayList<>();

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
  public EcmaStreamBuilder render(EcmaScriptRenderer renderer) {

    return renderer.buildStream(
        type,
        String.format(
            "$runtime.zip(%s, %s, %s)",
            left.renderEcma(renderer),
            right.renderEcma(renderer),
            renderer.lambda(
                2,
                (r, arg) -> {
                  final var result = r.newLet("[]");
                  r.conditional(
                      keyType.apply(EcmaScriptRenderer.isEqual(arg.apply(0), arg.apply(1))),
                      whenTrue -> {
                        final var output =
                            whenTrue.newLet(String.format("new Array(%d)", copySemantics.size()));
                        for (var index = 0; index < copySemantics.size(); index++) {
                          whenTrue.statement(
                              String.format(
                                  "%s[%d] = $runtime.nullifyUndefined(%s?.[%d])",
                                  output,
                                  index,
                                  arg.apply(
                                      Character.toLowerCase(copySemantics.get(index).second())
                                              == 'a'
                                          ? 0
                                          : 1),
                                  copySemantics.get(index).first()));
                        }

                        whenTrue.statement(String.format("%s.push(%s)", result, output));
                      });
                  return result;
                })));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return left.resolve(defs, errorHandler) & right.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return left.resolveDefinitions(expressionCompilerServices, errorHandler)
        & right.resolveDefinitions(expressionCompilerServices, errorHandler);
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
    for (final var expression : List.of(left, right)) {
      final var type = expression.type();
      if (type == Imyhat.EMPTY) {
        errorHandler.accept(
            String.format(
                "%d:%d: Cannot iterate over empty list. No type to check subsequent operations.",
                line(), column()));
        return false;
      }
      if (type instanceof Imyhat.ListImyhat) {
        var inner = ((Imyhat.ListImyhat) type).inner();
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
    final var leftIndex = types.get(0).get(0);
    final var rightIndex = types.get(1).get(0);
    if (!leftIndex.isSame(rightIndex)) {
      errorHandler.accept(
          String.format(
              "%d:%d: The first element of both tuples must match. Got %s and %s.",
              line(), column(), leftIndex.name(), rightIndex.name()));
      return false;
    }
    final List<Imyhat> output = new ArrayList<>();
    keyType = leftIndex.unify(rightIndex);
    output.add(keyType);
    for (var position = 0; position < 2; position++) {
      for (var index = 1; index < types.get(position).count(); index++) {
        final var outputType = types.get(position).get(index).asOptional();
        output.add(outputType);
        copySemantics.add(
            new Pair<>(
                index,
                (char)
                    ((outputType.isSame(types.get(position).get(index)) ? 'A' : 'a') + position)));
      }
    }
    type = Imyhat.tuple(output.toArray(Imyhat[]::new));
    return true;
  }
}
