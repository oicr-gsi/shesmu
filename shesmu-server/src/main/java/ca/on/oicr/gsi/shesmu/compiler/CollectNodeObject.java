package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CollectNodeObject extends CollectNode {

  private List<String> definedNames;
  private final List<CollectFieldNode> fields;

  public CollectNodeObject(int line, int column, List<CollectFieldNode> fields) {
    super(line, column);
    this.fields = fields;
    fields.sort(Comparator.comparing(CollectFieldNode::fieldName));
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final var remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toList());
    for (final var field : fields) {
      field.collectFreeVariables(names, predicate);
    }
    names.removeAll(remove);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    for (final var field : fields) {
      field.collectPlugins(pluginFileNames);
    }
  }

  @Override
  public boolean orderingCheck(Ordering ordering, Consumer<String> errorHandler) {
    return fields.stream().filter(f -> f.orderingCheck(ordering, errorHandler)).count()
        == fields.size();
  }

  @Override
  public void render(JavaStreamBuilder builder, LoadableConstructor name) {
    final var builders =
        builder.collectObject(
            line(),
            column(),
            fields.stream()
                .map(
                    f -> {
                      final Set<String> freeVariables = new HashSet<>();
                      f.collectFreeVariables(freeVariables, Flavour::needsCapture);
                      return new Pair<>(
                          f.fieldName(),
                          builder
                              .renderer()
                              .allValues()
                              .filter(l -> freeVariables.contains(l.name()))
                              .toArray(LoadableValue[]::new));
                    })
                .collect(Collectors.toList()));
    for (var i = 0; i < fields.size(); i++) {
      fields.get(i).render(builders[i], name);
    }
  }

  @Override
  public String render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    final var start = builder.renderer().newConst(builder.finish());
    return fields.stream()
        .map(
            f ->
                f.fieldName()
                    + ": "
                    + f.render(builder.renderer().buildStream(builder.currentType(), start), name))
        .sorted()
        .collect(Collectors.joining(", ", "{", "}"));
  }

  @Override
  public boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    final var ok =
        fields.stream().filter(f -> f.resolve(defs, name, errorHandler)).count() == fields.size();
    definedNames = name.targets().map(Target::name).collect(Collectors.toList());
    return ok;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    var ok = true;
    for (final var nameCount :
        fields.stream()
            .collect(Collectors.groupingBy(CollectFieldNode::fieldName, Collectors.counting()))
            .entrySet()) {
      if (nameCount.getValue() > 1) {
        errorHandler.accept(
            String.format(
                "%d:%d: Field “%s” repeats %d.",
                line(), column(), nameCount.getKey(), nameCount.getValue()));
        ok = false;
      }
    }
    return ok
        & fields.stream()
                .filter(f -> f.resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == fields.size();
  }

  @Override
  public Imyhat type() {
    return new ObjectImyhat(fields.stream().map(CollectFieldNode::field));
  }

  @Override
  public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    return fields.stream().filter(f -> f.typeCheck(incoming, errorHandler)).count()
        == fields.size();
  }
}
