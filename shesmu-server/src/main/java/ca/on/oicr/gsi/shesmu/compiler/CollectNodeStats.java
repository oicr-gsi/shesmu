package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class CollectNodeStats extends CollectNode {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method METHOD_SUMMARY_STATS__GET_AVERAGE =
      new Method("getAverage", Type.DOUBLE_TYPE, new Type[0]);
  private static final Method METHOD_SUMMARY_STATS__GET_COUNT =
      new Method("getCount", Type.LONG_TYPE, new Type[0]);
  private static final Method METHOD_TUPLE__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {Type.getType(Object[].class)});
  private List<String> definedNames;
  private final ExpressionNode expression;

  public CollectNodeStats(int line, int column, ExpressionNode expression) {
    super(line, column);
    this.expression = expression;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final var remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toList());
    expression.collectFreeVariables(names, predicate);
    names.removeAll(remove);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(JavaStreamBuilder builder, LoadableConstructor name) {
    final Set<String> freeVariables = new HashSet<>();
    expression.collectFreeVariables(freeVariables, Flavour::needsCapture);
    final var primitive =
        expression.type().isSame(Imyhat.INTEGER) ? PrimitiveStream.LONG : PrimitiveStream.DOUBLE;
    final var renderer =
        builder.mapToPrimitive(
            line(),
            column(),
            name,
            primitive,
            builder
                .renderer()
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));
    renderer.methodGen().visitCode();
    expression.render(renderer);
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();

    builder.summaryStatistics(primitive);

    final var local = builder.renderer().methodGen().newLocal(primitive.summaryStatisticsType());
    builder.renderer().methodGen().storeLocal(local);
    builder.renderer().methodGen().newInstance(A_TUPLE_TYPE);
    builder.renderer().methodGen().dup();
    builder.renderer().methodGen().push(5);
    builder.renderer().methodGen().newArray(A_OBJECT_TYPE);

    builder.renderer().methodGen().dup();
    builder.renderer().methodGen().push(0);
    builder.renderer().methodGen().loadLocal(local);
    builder
        .renderer()
        .methodGen()
        .invokeVirtual(primitive.summaryStatisticsType(), METHOD_SUMMARY_STATS__GET_AVERAGE);
    builder.renderer().methodGen().valueOf(Type.DOUBLE_TYPE);
    builder.renderer().methodGen().arrayStore(A_OBJECT_TYPE);

    builder.renderer().methodGen().dup();
    builder.renderer().methodGen().push(1);
    builder.renderer().methodGen().loadLocal(local);
    builder
        .renderer()
        .methodGen()
        .invokeVirtual(primitive.summaryStatisticsType(), METHOD_SUMMARY_STATS__GET_COUNT);
    builder.renderer().methodGen().valueOf(Type.LONG_TYPE);
    builder.renderer().methodGen().arrayStore(A_OBJECT_TYPE);

    builder.renderer().methodGen().dup();
    builder.renderer().methodGen().push(2);
    builder.renderer().methodGen().loadLocal(local);
    builder
        .renderer()
        .methodGen()
        .invokeVirtual(
            primitive.summaryStatisticsType(),
            new Method("getMax", primitive.resultType(), new Type[0]));
    builder.renderer().methodGen().valueOf(primitive.resultType());
    builder.renderer().methodGen().arrayStore(A_OBJECT_TYPE);

    builder.renderer().methodGen().dup();
    builder.renderer().methodGen().push(3);
    builder.renderer().methodGen().loadLocal(local);
    builder
        .renderer()
        .methodGen()
        .invokeVirtual(
            primitive.summaryStatisticsType(),
            new Method("getMin", primitive.resultType(), new Type[0]));
    builder.renderer().methodGen().valueOf(primitive.resultType());
    builder.renderer().methodGen().arrayStore(A_OBJECT_TYPE);

    builder.renderer().methodGen().dup();
    builder.renderer().methodGen().push(4);
    builder.renderer().methodGen().loadLocal(local);
    builder
        .renderer()
        .methodGen()
        .invokeVirtual(
            primitive.summaryStatisticsType(),
            new Method("getSum", primitive.resultType(), new Type[0]));
    builder.renderer().methodGen().valueOf(primitive.resultType());
    builder.renderer().methodGen().arrayStore(A_OBJECT_TYPE);

    builder.renderer().methodGen().invokeConstructor(A_TUPLE_TYPE, METHOD_TUPLE__CTOR);
  }

  @Override
  public String render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    builder.map(name, expression.type(), expression::renderEcma);
    return String.format("$runtime.summaryStats(%s)", builder.finish());
  }

  @Override
  public boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    final var ok = expression.resolve(defs.bind(name), errorHandler);
    definedNames = name.targets().map(Target::name).collect(Collectors.toList());
    return ok;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return new ObjectImyhat(
        Stream.of(
            new Pair<>("average", Imyhat.FLOAT),
            new Pair<>("count", Imyhat.INTEGER),
            new Pair<>("maximum", expression.type()),
            new Pair<>("minimum", expression.type()),
            new Pair<>("sum", expression.type())));
  }

  @Override
  public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    if (expression.typeCheck(errorHandler)) {
      if (expression.type().isSame(Imyhat.INTEGER) || expression.type().isSame(Imyhat.FLOAT)) {
        return true;
      }
      expression.typeError("integer or float", expression.type(), errorHandler);
    }
    return false;
  }
}
