package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.SupplementaryInformation;
import ca.on.oicr.gsi.shesmu.plugin.SupplementaryInformation.DisplayElement;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.cache.SimpleRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.json.AsJsonNode;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.gsi.vidarr.BasicType;
import ca.on.oicr.gsi.vidarr.BasicType.Visitor;
import ca.on.oicr.gsi.vidarr.JsonBodyHandler;
import ca.on.oicr.gsi.vidarr.api.MaxInFlightDeclaration;
import ca.on.oicr.gsi.vidarr.api.TargetDeclaration;
import ca.on.oicr.gsi.vidarr.api.WorkflowDeclaration;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class VidarrPlugin extends JsonPluginFile<Configuration> {
  static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final Pattern INVALID = Pattern.compile("[^A-Za-z0-9_]");
  static final ObjectMapper MAPPER = new ObjectMapper();
  static final BasicType.Visitor<Imyhat> SIMPLE_TO_IMYHAT =
      new Visitor<>() {
        @Override
        public Imyhat bool() {
          return Imyhat.BOOLEAN;
        }

        @Override
        public Imyhat date() {
          return Imyhat.DATE;
        }

        @Override
        public Imyhat dictionary(BasicType key, BasicType value) {
          return Imyhat.dictionary(key.apply(this), value.apply(this));
        }

        @Override
        public Imyhat floating() {
          return Imyhat.FLOAT;
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
        public Imyhat list(BasicType inner) {
          return inner.apply(this).asList();
        }

        @Override
        public Imyhat object(Stream<Pair<String, BasicType>> fields) {
          return new ObjectImyhat(fields.map(f -> new Pair<>(f.first(), f.second().apply(this))));
        }

        @Override
        public Imyhat optional(BasicType inner) {
          return inner.apply(this).asOptional();
        }

        @Override
        public Imyhat pair(BasicType left, BasicType right) {
          return Imyhat.tuple(left.apply(this), right.apply(this));
        }

        @Override
        public Imyhat string() {
          return Imyhat.STRING;
        }

        @Override
        public Imyhat taggedUnion(Stream<Entry<String, BasicType>> union) {
          return union
              .map(e -> e.getValue().apply(new TaggedUnionType(e.getKey())))
              .reduce(Imyhat::unify)
              .orElse(Imyhat.BAD);
        }

        @Override
        public Imyhat tuple(Stream<BasicType> elements) {
          return Imyhat.tuple(elements.map(e -> e.apply(this)).toArray(Imyhat[]::new));
        }
      };

  static {
    MAPPER.registerModule(new JavaTimeModule());
  }

  private class MaxInFlightCache
      extends ValueCache<Optional<MaxInFlightDeclaration>, Optional<MaxInFlightDeclaration>> {

    private Optional<URI> url;
    private HttpClient client;

    public MaxInFlightCache(String name, HttpClient client) {
      super("max-in-flight " + name, 10, SimpleRecord::new);
      this.client = client;
      url = Optional.empty();
    }

    public void setURL(Optional<URI> url) {
      this.url = url;
    }

    public Optional<URI> getURL() {
      return url;
    }

    @Override
    protected Optional<MaxInFlightDeclaration> fetch(Instant lastUpdated) {
      Optional<MaxInFlightDeclaration> maxInFlight = Optional.empty();
      try {
        if (url.isPresent()) {
          final var mifResult =
              client.send(
                  HttpRequest.newBuilder(url.get().resolve("/api/max-in-flight")).GET().build(),
                  new JsonBodyHandler<>(MAPPER, MaxInFlightDeclaration.class));
          if (mifResult.statusCode() == 200) {
            maxInFlight = Optional.of(mifResult.body().get());
          }
        }
      } catch (IOException | InterruptedException e) {
        e.printStackTrace();
      }
      return maxInFlight;
    }
  }

  static String sanitise(String raw) {
    final var clean = INVALID.matcher(raw).replaceAll("_");
    return Character.isLowerCase(clean.charAt(0)) ? clean : ("v" + clean);
  }

  private final Definer<VidarrPlugin> definer;
  private Optional<URI> url = Optional.empty();
  private MaxInFlightCache mifCache;

  public VidarrPlugin(Path fileName, String instanceName, Definer<VidarrPlugin> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
    mifCache = new MaxInFlightCache(instanceName, CLIENT);
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    final var u = url;
    u.ifPresent(uri -> renderer.link("URL", uri.toString(), uri.toString()));
  }

  @ShesmuAction(name = "unload_by_external_ids")
  public UnloadExternalIdentifiersAction unloadByExternalIds() {
    return new UnloadExternalIdentifiersAction(definer);
  }

  @ShesmuAction(name = "unload_by_workflow_runs")
  public UnloadWorkflowRunsAction unloadByWorkflowRuns() {
    return new UnloadWorkflowRunsAction(definer);
  }

  private String getMaxInFlightMessage(Optional<URI> url, String workflow) {
    String message;
    mifCache.setURL(url);
    Optional<MaxInFlightDeclaration> maxInFlight = mifCache.get();
    if (maxInFlight.isPresent()) {
      var result = maxInFlight.get().getWorkflows().get(workflow);
      if (result == null) {
        message = "unknown workflow";
      } else {
        message =
            String.format("%d of max %d", result.getCurrentInFlight(), result.getMaxInFlight());
      }
    } else {
      message = "could not retrieve status";
    }
    return message;
  }

  @Override
  protected Optional<Integer> update(Configuration value) {
    try {
      url = Optional.of(URI.create(value.getUrl()));
      final var workflowsResult =
          CLIENT.send(
              HttpRequest.newBuilder(url.get().resolve("/api/workflows")).GET().build(),
              new JsonBodyHandler<>(MAPPER, WorkflowDeclaration[].class));
      final var targetsResult =
          CLIENT.send(
              HttpRequest.newBuilder(url.get().resolve("/api/targets")).GET().build(),
              new JsonBodyHandler<>(
                  MAPPER, new TypeReference<Map<String, TargetDeclaration>>() {}));
      if (workflowsResult.statusCode() == 200 && targetsResult.statusCode() == 200) {
        definer.clearActions();
        final var workflows = workflowsResult.body().get();
        for (final var target : targetsResult.body().get().entrySet()) {
          final var targetParameters = new ArrayList<CustomActionParameter<SubmitAction>>();
          if (target.getValue().getEngineParameters() != null) {
            targetParameters.add(
                new CustomActionParameter<>(
                    "engine_parameters",
                    true,
                    target.getValue().getEngineParameters().apply(SIMPLE_TO_IMYHAT)) {
                  @Override
                  public void store(SubmitAction action, Object value) {
                    action.request.setEngineParameters(AsJsonNode.convert(type(), value));
                  }
                });
          }
          if (target.getValue().getConsumableResources() != null
              && !target.getValue().getConsumableResources().isEmpty()) {
            for (final var resource : target.getValue().getConsumableResources().entrySet()) {
              targetParameters.add(
                  new CustomActionParameter<>(
                      sanitise("resource_" + resource.getKey()),
                      true,
                      resource.getValue().apply(SIMPLE_TO_IMYHAT)) {
                    private final String name = resource.getKey();

                    @Override
                    public void store(SubmitAction action, Object value) {
                      action
                          .request
                          .getConsumableResources()
                          .put(name, AsJsonNode.convert(type(), value));
                    }
                  });
            }
          }
          for (final var workflow : workflows) {
            if (target.getValue().getLanguage().contains(workflow.getLanguage())) {
              InputParameterConverter.create(workflow.getParameters(), target.getValue())
                  .ifPresent(
                      inputParameters ->
                          MetadataParameterConverter.create(
                                  workflow.getMetadata(), target.getValue())
                              .ifPresent(
                                  metadataParameters ->
                                      definer.defineAction(
                                          sanitise(target.getKey())
                                              + Parser.NAMESPACE_SEPARATOR
                                              + sanitise(
                                                  workflow.getName()
                                                      + "_v"
                                                      + workflow.getVersion()),
                                          String.format(
                                              "Workflow %s version %s from Vidarr instance %s on target %s.",
                                              workflow.getName(),
                                              workflow.getVersion(),
                                              value.getUrl(),
                                              target.getKey()),
                                          SubmitAction.class,
                                          new Supplier<>() {
                                            private final Supplier<VidarrPlugin> supplier = definer;
                                            private final String targetName = target.getKey();
                                            private final String workflowName = workflow.getName();
                                            private final String workflowVersion =
                                                workflow.getVersion();

                                            @Override
                                            public SubmitAction get() {
                                              return new SubmitAction(
                                                  supplier,
                                                  targetName,
                                                  workflowName,
                                                  workflowVersion);
                                            }
                                          },
                                          Stream.of(
                                                  targetParameters.stream(),
                                                  Stream.of(inputParameters, metadataParameters),
                                                  workflow.getLabels() == null
                                                      ? Stream
                                                          .<CustomActionParameter<SubmitAction>>
                                                              empty()
                                                      : workflow.getLabels().entrySet().stream()
                                                          .<CustomActionParameter<SubmitAction>>map(
                                                              entry ->
                                                                  new CustomActionParameter<>(
                                                                      sanitise(
                                                                          "label_"
                                                                              + entry.getKey()),
                                                                      true,
                                                                      entry
                                                                          .getValue()
                                                                          .apply(
                                                                              SIMPLE_TO_IMYHAT)) {
                                                                    private final String label =
                                                                        entry.getKey();

                                                                    @Override
                                                                    public void store(
                                                                        SubmitAction action,
                                                                        Object value) {
                                                                      type()
                                                                          .accept(
                                                                              new PackJsonObject(
                                                                                  action.request
                                                                                      .getLabels(),
                                                                                  label),
                                                                              value);
                                                                    }
                                                                  }))
                                              .flatMap(Function.identity()),
                                          new SupplementaryInformation() {
                                            @Override
                                            public Stream<Pair<DisplayElement, DisplayElement>>
                                                generate() {
                                              return Stream.of(
                                                  new Pair<>(
                                                      SupplementaryInformation.text("In-flight"),
                                                      SupplementaryInformation.text(
                                                          definer
                                                              .get()
                                                              .getMaxInFlightMessage(
                                                                  url, workflow.getName()))));
                                            }
                                          })));
            }
          }
        }
        return Optional.of(10);
      } else {
        return Optional.of(2);
      }
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      return Optional.of(2);
    }
  }

  public Optional<URI> url() {
    return url;
  }
}
