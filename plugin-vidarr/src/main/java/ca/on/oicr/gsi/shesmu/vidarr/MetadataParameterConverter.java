package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.vidarr.BasicType;
import ca.on.oicr.gsi.vidarr.OutputProvisionFormat;
import ca.on.oicr.gsi.vidarr.OutputType;
import ca.on.oicr.gsi.vidarr.OutputType.IdentifierKey;
import ca.on.oicr.gsi.vidarr.api.TargetDeclaration;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
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

  static Optional<CustomActionParameter<SubmitAction>> createSubmitParam(
      Map<String, OutputType> parameters, TargetDeclaration target) {
    final List<ParameterGroup> handlers =
        parameters.entrySet().stream()
            .map(
                entry ->
                    new ParameterGroup(
                        entry.getKey(),
                        entry.getValue().apply(new MetadataParameterConverter(target))))
            .sorted()
            .toList();

    if (handlers.stream().anyMatch(h -> h.type.isBad())) {
      return Optional.empty();
    }

    Imyhat type =
        Imyhat.algebraicObject("INDIVIDUAL", handlers.stream().map(ParameterGroup::objectField));

    boolean canHaveGlobal = true;
    for (int index = 1; index < handlers.size(); index++) {
      if (!handlers.get(index - 1).type.isSame(handlers.get(index).type)) {
        canHaveGlobal = false;
        break;
      }
    }
    if (canHaveGlobal && !handlers.isEmpty()) {
      type = type.unify(Imyhat.algebraicTuple("GLOBAL", handlers.get(0).type));
    }

    return Optional.of(
        new CustomActionParameter<>("metadata", true, type) {
          @Override
          public void store(SubmitAction action, Object value) {
            final AlgebraicValue tuple = (AlgebraicValue) value;
            final ObjectNode object = VidarrPlugin.MAPPER.createObjectNode();
            action.request.setMetadata(object);
            switch (tuple.name()) {
              case "INDIVIDUAL":
                for (int index = 0; index < handlers.size(); index++) {
                  handlers.get(index).store(object, tuple.get(index));
                }
                break;
              case "GLOBAL":
                for (final ParameterGroup handler : handlers) {
                  handler.store(object, tuple.get(0));
                }
                break;
            }
          }
        });
  }

  // Don't know that all of this is necessary. also refactor me into sharing code with my brother
  // Tried to make one generic method that just returned CustomActionParameter<VidarrAction> but it
  // blew up
  static Optional<CustomActionParameter<ImportAction>> createImportParam(
      Map<String, OutputType> parameters, TargetDeclaration target) {
    final List<ParameterGroup> handlers =
        parameters.entrySet().stream()
            .map(
                entry ->
                    new ParameterGroup(
                        entry.getKey(),
                        entry.getValue().apply(new MetadataParameterConverter(target))))
            .sorted()
            .toList();

    if (handlers.stream().anyMatch(h -> h.type.isBad())) {
      return Optional.empty();
    }

    Imyhat type =
        Imyhat.algebraicObject("INDIVIDUAL", handlers.stream().map(ParameterGroup::objectField));

    boolean canHaveGlobal = true;
    for (int index = 1; index < handlers.size(); index++) {
      if (!handlers.get(index - 1).type.isSame(handlers.get(index).type)) {
        canHaveGlobal = false;
        break;
      }
    }
    if (canHaveGlobal && !handlers.isEmpty()) {
      type = type.unify(Imyhat.algebraicTuple("GLOBAL", handlers.get(0).type));
    }

    return Optional.of(
        new CustomActionParameter<>("metadata", true, type) {
          @Override
          public void store(ImportAction action, Object value) {
            final AlgebraicValue tuple = (AlgebraicValue) value;
            final ObjectNode object = VidarrPlugin.MAPPER.createObjectNode();
            action.request.getWorkflowRun().setMetadata(object);
            switch (tuple.name()) {
              case "INDIVIDUAL":
                for (int index = 0; index < handlers.size(); index++) {
                  handlers.get(index).store(object, tuple.get(index));
                }
                break;
              case "GLOBAL":
                for (final ParameterGroup handler : handlers) {
                  handler.store(object, tuple.get(0));
                }
                break;
            }
          }
        });
  }

  private final TargetDeclaration target;

  public MetadataParameterConverter(TargetDeclaration target) {
    this.target = target;
  }

  @Override
  public Imyhat file(boolean optional) {
    return handle(OutputProvisionFormat.FILES);
  }

  @Override
  public Imyhat fileWithLabels(boolean optional) {
    return handle(OutputProvisionFormat.FILES);
  }

  @Override
  public Imyhat files(boolean optional) {
    return handle(OutputProvisionFormat.FILES);
  }

  @Override
  public Imyhat filesWithLabels(boolean optional) {
    return handle(OutputProvisionFormat.FILES);
  }

  private Imyhat handle(OutputProvisionFormat format) {
    final BasicType provisioner = target.getOutputProvisioners().get(format);
    if (provisioner == null) {
      return Imyhat.BAD;
    } else {
      final Imyhat customType = provisioner.apply(VidarrPlugin.SIMPLE_TO_IMYHAT);
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
  public Imyhat logs(boolean optional) {
    return handle(OutputProvisionFormat.LOGS);
  }

  @Override
  public Imyhat qualityControl(boolean optional) {
    return handle(OutputProvisionFormat.QUALITY_CONTROL);
  }

  @Override
  public Imyhat unknown() {
    return Imyhat.BAD;
  }

  @Override
  public Imyhat warehouseRecords(boolean optional) {
    return handle(OutputProvisionFormat.DATAWAREHOUSE_RECORDS);
  }
}
