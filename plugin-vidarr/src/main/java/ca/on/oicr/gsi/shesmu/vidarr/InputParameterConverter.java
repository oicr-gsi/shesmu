package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.vidarr.InputProvisionFormat;
import ca.on.oicr.gsi.vidarr.InputType;
import ca.on.oicr.gsi.vidarr.InputType.Visitor;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowRequest;
import ca.on.oicr.gsi.vidarr.api.TargetDeclaration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;

final class InputParameterConverter implements InputType.Visitor<Imyhat> {

  private static final Pair<String, Imyhat> EXTERNAL_IDS_FIELD =
      new Pair<>("externalIds", SubmitAction.EXTERNAL_IDS);
  public static final Imyhat INTERNAL_ALGEBRAIC_TYPE =
      Imyhat.algebraicTuple("INTERNAL", Imyhat.STRING.asList());

  static Optional<CustomActionParameter<SubmitAction>> create(
      Map<String, InputType> parameters, TargetDeclaration target) {
    final var visitor = new InputParameterConverter(target);
    return ParameterGroup.create(
        "arguments", SubmitWorkflowRequest::setArguments, parameters, p -> p.apply(visitor));
  }

  private final TargetDeclaration target;

  public InputParameterConverter(TargetDeclaration target) {
    this.target = target;
  }

  @Override
  public Imyhat bool() {
    return Imyhat.BOOLEAN;
  }

  @Override
  public Imyhat date() {
    return Imyhat.DATE;
  }

  @Override
  public Imyhat dictionary(InputType key, InputType value) {
    return Imyhat.dictionary(key.apply(this), value.apply(this));
  }

  @Override
  public Imyhat directory() {
    return handle(InputProvisionFormat.DIRECTORY);
  }

  @Override
  public Imyhat file() {
    return handle(InputProvisionFormat.FILE);
  }

  @Override
  public Imyhat floating() {
    return Imyhat.FLOAT;
  }

  private Imyhat handle(InputProvisionFormat format) {
    final var type = target.getInputProvisioners().get(format);
    if (type == null) {
      return Imyhat.BAD;
    } else {
      return INTERNAL_ALGEBRAIC_TYPE.unify(
          Imyhat.algebraicObject(
              "EXTERNAL",
              Stream.of(
                  EXTERNAL_IDS_FIELD,
                  new Pair<>("configuration", type.apply(VidarrPlugin.SIMPLE_TO_IMYHAT)))));
    }
  }

  @Override
  public Imyhat integer() {
    return Imyhat.INTEGER;
  }

  @Override
  public Imyhat json() {
    return Imyhat.JSON;
  }

  @Override
  public Imyhat list(InputType inner) {
    return inner.apply(this).asList();
  }

  @Override
  public Imyhat object(Stream<Pair<String, InputType>> fields) {
    return new ObjectImyhat(fields.map(f -> new Pair<>(f.first(), f.second().apply(this))));
  }

  @Override
  public Imyhat optional(InputType inner) {
    return inner.apply(this).asOptional();
  }

  @Override
  public Imyhat pair(InputType left, InputType right) {
    return new ObjectImyhat(
        Stream.of(new Pair<>("left", left.apply(this)), new Pair<>("right", right.apply(this))));
  }

  @Override
  public Imyhat string() {
    return Imyhat.STRING;
  }

  @Override
  public Imyhat taggedUnion(Stream<Entry<String, InputType>> union) {
    return union
        .map(
            entry ->
                entry
                    .getValue()
                    .apply(
                        new Visitor<Imyhat>() {
                          @Override
                          public Imyhat bool() {
                            return Imyhat.BAD;
                          }

                          @Override
                          public Imyhat date() {
                            return Imyhat.BAD;
                          }

                          @Override
                          public Imyhat dictionary(InputType inputType, InputType inputType1) {
                            return Imyhat.BAD;
                          }

                          @Override
                          public Imyhat directory() {
                            return Imyhat.BAD;
                          }

                          @Override
                          public Imyhat file() {
                            return Imyhat.BAD;
                          }

                          @Override
                          public Imyhat floating() {
                            return Imyhat.BAD;
                          }

                          @Override
                          public Imyhat integer() {
                            return Imyhat.BAD;
                          }

                          @Override
                          public Imyhat json() {
                            return Imyhat.BAD;
                          }

                          @Override
                          public Imyhat list(InputType inputType) {
                            return Imyhat.BAD;
                          }

                          @Override
                          public Imyhat object(Stream<Pair<String, InputType>> fields) {
                            return Imyhat.algebraicObject(
                                entry.getKey(),
                                fields.map(
                                    entry ->
                                        new Pair<>(
                                            entry.first(),
                                            entry.second().apply(InputParameterConverter.this))));
                          }

                          @Override
                          public Imyhat optional(InputType inputType) {
                            return Imyhat.BAD;
                          }

                          @Override
                          public Imyhat pair(InputType left, InputType right) {
                            return Imyhat.algebraicObject(
                                entry.getKey(),
                                Stream.of(
                                    new Pair<>("left", left.apply(InputParameterConverter.this)),
                                    new Pair<>(
                                        "right", right.apply(InputParameterConverter.this))));
                          }

                          @Override
                          public Imyhat string() {
                            return Imyhat.BAD;
                          }

                          @Override
                          public Imyhat taggedUnion(Stream<Entry<String, InputType>> stream) {
                            return Imyhat.BAD;
                          }

                          @Override
                          public Imyhat tuple(Stream<InputType> elements) {
                            return Imyhat.algebraicTuple(
                                entry.getKey(),
                                elements
                                    .map(element -> element.apply(InputParameterConverter.this))
                                    .toArray(Imyhat[]::new));
                          }
                        }))
        .reduce(Imyhat::unify)
        .orElse(Imyhat.BAD);
  }

  @Override
  public Imyhat tuple(Stream<InputType> elements) {
    return Imyhat.tuple(elements.map(e -> e.apply(this)).toArray(Imyhat[]::new));
  }
}
