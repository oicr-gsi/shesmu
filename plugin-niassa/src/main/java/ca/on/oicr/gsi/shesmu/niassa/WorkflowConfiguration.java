package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatFunction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class WorkflowConfiguration {

  private static class UserAnnotationParameter extends CustomActionParameter<WorkflowAction> {

    public UserAnnotationParameter(Entry<String, Imyhat> e) {
      super(e.getKey(), true, e.getValue());
    }

    @Override
    public void store(WorkflowAction action, Object value) {
      action.setAnnotation(
          name(),
          type()
              .apply(
                  new ImyhatFunction<String>() {
                    @Override
                    public String apply(String name, AccessContents accessor) {
                      return name + ":" + accessor.apply(this);
                    }

                    @Override
                    public String apply(boolean value) {
                      return Boolean.toString(value);
                    }

                    @Override
                    public String apply(double value) {
                      return Double.toString(value);
                    }

                    @Override
                    public String apply(Instant value) {
                      return value.toString();
                    }

                    @Override
                    public String apply(long value) {
                      return Long.toString(value);
                    }

                    @Override
                    public String apply(Stream<Object> values, Imyhat inner) {
                      return values
                          .map(v -> inner.apply(this, v))
                          .collect(Collectors.joining(",", "[", "]"));
                    }

                    @Override
                    public String apply(String value) {
                      return value;
                    }

                    @Override
                    public String apply(Path value) {
                      return value.toString();
                    }

                    @Override
                    public String apply(Imyhat inner, Optional<?> value) {
                      return value.map(v -> inner.apply(this, v)).orElse("null");
                    }

                    @Override
                    public String apply(JsonNode value) {
                      try {
                        return NiassaServer.MAPPER.writeValueAsString(value);
                      } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                      }
                    }

                    @Override
                    public String applyMap(Map<?, ?> map, Imyhat key, Imyhat value) {
                      return map.entrySet().stream()
                          .map(
                              e ->
                                  key.apply(this, e.getKey())
                                      + ":"
                                      + value.apply(this, e.getValue()))
                          .collect(Collectors.joining(","));
                    }

                    @Override
                    public String applyObject(Stream<Field<String>> contents) {
                      return contents
                          .sorted(Comparator.comparing(Field::index))
                          .map(f -> f.type().apply(this, f.value()))
                          .collect(Collectors.joining("|", "{", "}"));
                    }

                    @Override
                    public String applyTuple(Stream<Field<Integer>> contents) {
                      return contents
                          .sorted(Comparator.comparing(Field::index))
                          .map(f -> f.type().apply(this, f.value()))
                          .collect(Collectors.joining("|", "(", ")"));
                    }
                  },
                  value));
    }
  }

  // CustomActionParameter is already paremeterized so copypasta it is
  private static class MigrationUserAnnotationParameter
      extends CustomActionParameter<MigrationAction> {

    public MigrationUserAnnotationParameter(Entry<String, Imyhat> e) {
      super(e.getKey(), true, e.getValue());
    }

    @Override
    public void store(MigrationAction action, Object value) {
      action.setAnnotation(
          name(),
          type()
              .apply(
                  new ImyhatFunction<String>() {
                    @Override
                    public String apply(String name, AccessContents accessor) {
                      return name + ":" + accessor.apply(this);
                    }

                    @Override
                    public String apply(boolean value) {
                      return Boolean.toString(value);
                    }

                    @Override
                    public String apply(double value) {
                      return Double.toString(value);
                    }

                    @Override
                    public String apply(Instant value) {
                      return value.toString();
                    }

                    @Override
                    public String apply(long value) {
                      return Long.toString(value);
                    }

                    @Override
                    public String apply(Stream<Object> values, Imyhat inner) {
                      return values
                          .map(v -> inner.apply(this, v))
                          .collect(Collectors.joining(",", "[", "]"));
                    }

                    @Override
                    public String apply(String value) {
                      return value;
                    }

                    @Override
                    public String apply(Path value) {
                      return value.toString();
                    }

                    @Override
                    public String apply(Imyhat inner, Optional<?> value) {
                      return value.map(v -> inner.apply(this, v)).orElse("null");
                    }

                    @Override
                    public String apply(JsonNode value) {
                      try {
                        return NiassaServer.MAPPER.writeValueAsString(value);
                      } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                      }
                    }

                    @Override
                    public String applyMap(Map<?, ?> map, Imyhat key, Imyhat value) {
                      return map.entrySet().stream()
                          .map(
                              e ->
                                  key.apply(this, e.getKey())
                                      + ":"
                                      + value.apply(this, e.getValue()))
                          .collect(Collectors.joining(","));
                    }

                    @Override
                    public String applyObject(Stream<Field<String>> contents) {
                      return contents
                          .sorted(Comparator.comparing(Field::index))
                          .map(f -> f.type().apply(this, f.value()))
                          .collect(Collectors.joining("|", "{", "}"));
                    }

                    @Override
                    public String applyTuple(Stream<Field<Integer>> contents) {
                      return contents
                          .sorted(Comparator.comparing(Field::index))
                          .map(f -> f.type().apply(this, f.value()))
                          .collect(Collectors.joining("|", "(", ")"));
                    }
                  },
                  value));
    }
  }

  private long accession;
  private Map<String, String> annotations = Collections.emptyMap();
  private FileMatchingPolicy fileMatchingPolicy = FileMatchingPolicy.SUPERSET;
  private int maxFailed = Integer.MAX_VALUE;
  private int maxInFlight;
  private IniParam<?>[] parameters;
  private long[] previousAccessions;
  private boolean relaunchFailedOnUpgrade;
  private List<String> services = Collections.emptyList();
  private InputLimsKeyProvider type;
  private Map<String, Imyhat> userAnnotations = Collections.emptyMap();
  private String kind = "UNKNOWN";
  private String vidarrName;

  public void define(
      String name, Definer<NiassaServer> definer, Map<String, AlgebraicValue> workflowKind) {
    final String description =
        String.format(
                "Runs SeqWare/Niassa workflow %d with settings in %s.",
                accession, definer.get().fileName())
            + (previousAccessions.length == 0
                ? ""
                : LongStream.of(getPreviousAccessions())
                    .sorted()
                    .mapToObj(Long::toString)
                    .collect(
                        Collectors.joining(", ", " Considered equivalent to workflows: ", "")));
    workflowKind.put(
        definer.defineAction(
            name,
            description,
            WorkflowAction.class,
            () -> {
              final WorkflowAction action =
                  new WorkflowAction(
                      definer,
                      name,
                      accession,
                      previousAccessions,
                      fileMatchingPolicy,
                      services,
                      annotations,
                      relaunchFailedOnUpgrade);
              for (final IniParam<?> param : getParameters()) {
                param.writeDefault(action);
              }
              return action;
            },
            Stream.of(
                    Stream.of(getType().parameter()),
                    Stream.of(getParameters()).map(IniParam::parameter),
                    userAnnotations.entrySet().stream().map(UserAnnotationParameter::new))
                .flatMap(Function.identity()),
            () -> definer.get().displayMaxInfo(accession, name)),
        new AlgebraicValue(kind.toUpperCase()));

    if (vidarrName != null) {
      workflowKind.put(
          definer.defineAction(
              "migration::" + name,
              description,
              MigrationAction.class,
              () -> {
                final MigrationAction migrationAction =
                    new MigrationAction(
                        definer,
                        vidarrName,
                        accession,
                        previousAccessions,
                        fileMatchingPolicy,
                        services,
                        annotations,
                        relaunchFailedOnUpgrade,
                        userAnnotations.keySet());
                for (final IniParam<?> param : getParameters()) {
                  param.writeDefaultMigration(migrationAction);
                }
                return migrationAction;
              },
              Stream.of(
                      Stream.of(getType().parameterMigration()),
                      Stream.of(getParameters()).map(IniParam::parameterMigration),
                      userAnnotations.entrySet().stream()
                          .map(MigrationUserAnnotationParameter::new))
                  .flatMap(Function.identity())),
          new AlgebraicValue(kind.toUpperCase()));
    }
  }

  public long getAccession() {
    return accession;
  }

  public Map<String, String> getAnnotations() {
    return annotations;
  }

  public FileMatchingPolicy getFileMatchingPolicy() {
    return fileMatchingPolicy;
  }

  public String getKind() {
    return kind;
  }

  public int getMaxFailed() {
    return maxFailed;
  }

  public int getMaxInFlight() {
    return maxInFlight;
  }

  public IniParam<?>[] getParameters() {
    return parameters;
  }

  public long[] getPreviousAccessions() {
    return previousAccessions;
  }

  public List<String> getServices() {
    return services;
  }

  public InputLimsKeyProvider getType() {
    return type;
  }

  public Map<String, Imyhat> getUserAnnotations() {
    return userAnnotations;
  }

  public String getVidarrName() {
    return vidarrName;
  }

  public boolean isRelaunchFailedOnUpgrade() {
    return relaunchFailedOnUpgrade;
  }

  public void setAccession(long accession) {
    this.accession = accession;
  }

  public void setAnnotations(Map<String, String> annotations) {
    this.annotations = annotations;
  }

  public void setFileMatchingPolicy(FileMatchingPolicy fileMatchingPolicy) {
    this.fileMatchingPolicy = fileMatchingPolicy;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public void setMaxFailed(int maxFailed) {
    this.maxFailed = maxFailed;
  }

  public void setMaxInFlight(int maxInFlight) {
    this.maxInFlight = maxInFlight;
  }

  public void setParameters(IniParam<?>[] parameters) {
    this.parameters = parameters;
  }

  public void setPreviousAccessions(long[] previousAccessions) {
    this.previousAccessions = previousAccessions;
  }

  public void setRelaunchFailedOnUpgrade(boolean relaunchFailedOnUpgrade) {
    this.relaunchFailedOnUpgrade = relaunchFailedOnUpgrade;
  }

  public void setServices(List<String> services) {
    this.services = services;
  }

  public void setType(InputLimsKeyProvider type) {
    this.type = type;
  }

  public void setUserAnnotations(Map<String, Imyhat> userAnnotations) {
    this.userAnnotations = userAnnotations;
  }

  public void setVidarrName(String name) {
    this.vidarrName = name;
  }
}
