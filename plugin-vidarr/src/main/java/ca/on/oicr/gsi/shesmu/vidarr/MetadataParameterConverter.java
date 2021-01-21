package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.vidarr.OutputProvisionFormat;
import ca.on.oicr.gsi.vidarr.OutputType;
import ca.on.oicr.gsi.vidarr.OutputType.IdentifierKey;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowRequest;
import ca.on.oicr.gsi.vidarr.api.TargetDeclaration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

final class MetadataParameterConverter implements OutputType.Visitor<Imyhat> {

  private static Imyhat convert(IdentifierKey keyType) {
    switch (keyType) {
      case INTEGER:
        return Imyhat.INTEGER;
      case STRING:
        return Imyhat.STRING;
      default:
        return Imyhat.BAD;
    }
  }

  static Optional<CustomActionParameter<SubmitAction>> create(
      Map<String, OutputType> parameters, TargetDeclaration target) {
    final var visitor = new MetadataParameterConverter(target);
    return ParameterGroup.create(
        "metadata", SubmitWorkflowRequest::setMetadata, parameters, p -> p.apply(visitor));
  }

  private final TargetDeclaration target;

  public MetadataParameterConverter(TargetDeclaration target) {
    this.target = target;
  }

  @Override
  public Imyhat file() {
    return handle(OutputProvisionFormat.FILES);
  }

  @Override
  public Imyhat fileWithLabels() {
    return handle(OutputProvisionFormat.FILES);
  }

  @Override
  public Imyhat files() {
    return handle(OutputProvisionFormat.FILES);
  }

  @Override
  public Imyhat filesWithLabels() {
    return handle(OutputProvisionFormat.FILES);
  }

  private Imyhat handle(OutputProvisionFormat format) {
    final var provisioner = target.getOutputProvisioners().get(format);
    if (provisioner == null) {
      return Imyhat.BAD;
    } else {
      final var customType = provisioner.apply(VidarrPlugin.SIMPLE_TO_IMYHAT);
      return Imyhat.algebraicTuple("ALL", customType)
          .unify(Imyhat.algebraicTuple("REMAINING", customType))
          .unify(Imyhat.algebraicTuple("MANUAL", customType, SubmitAction.EXTERNAL_IDS));
    }
  }

  @Override
  public Imyhat list(Map<String, IdentifierKey> keys, Map<String, OutputType> outputs) {
    return new ObjectImyhat(
            Stream.concat(
                keys.entrySet().stream().map(e -> new Pair<>(e.getKey(), convert(e.getValue()))),
                outputs.entrySet().stream()
                    .map(e -> new Pair<>(e.getKey(), e.getValue().apply(this)))))
        .asList();
  }

  @Override
  public Imyhat logs() {
    return handle(OutputProvisionFormat.LOGS);
  }

  @Override
  public Imyhat qualityControl() {
    return handle(OutputProvisionFormat.QUALITY_CONTROL);
  }

  @Override
  public Imyhat unknown() {
    return Imyhat.BAD;
  }

  @Override
  public Imyhat warehouseRecords() {
    return handle(OutputProvisionFormat.DATAWAREHOUSE_RECORDS);
  }
}
