package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.gsicommon.IUSUtils;
import ca.on.oicr.gsi.shesmu.plugin.*;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.SimpleRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.json.AsJsonNode;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.OptionalImyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.gsi.vidarr.BasicType;
import ca.on.oicr.gsi.vidarr.BasicType.Visitor;
import ca.on.oicr.gsi.vidarr.JsonBodyHandler;
import ca.on.oicr.gsi.vidarr.api.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VidarrPlugin extends JsonPluginFile<Configuration> {

  private class AnalysisCache extends ValueCache<Stream<VidarrAnalysisValue>> {
    public AnalysisCache(Path fileName) {
      super("vidarr_analysis " + fileName.toString(), 30, ReplacingRecord::new);
    }

    private String createVidarrProvenanceRequestBody(
        List<AnalysisOutputType> analysisTypes, List<String> versionTypes) {
      return "{"
          + "\"analysisTypes\": ["
          + analysisTypes.stream()
              .map(name -> ("\"" + name.toString() + "\""))
              .collect(Collectors.joining(","))
          + "],"
          + "\"epoch\": 0,"
          + "\"includeParameters\": false,"
          + "\"timestamp\": 0,"
          + "\"versionPolicy\": \"LATEST\","
          + "\"versionTypes\": ["
          + versionTypes.stream().map(name -> ("\"" + name + "\"")).collect(Collectors.joining(","))
          + "]}";
    }

    protected Stream<VidarrAnalysisValue> analysisArchive(String baseUrl) throws Exception {

      final var results =
          HTTP_CLIENT.send(
              HttpRequest.newBuilder(URI.create(baseUrl + "/api/provenance"))
                  .header("Content-type", "application/json")
                  .timeout(Duration.ofMinutes(configuration.get().getTimeout()))
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          createVidarrProvenanceRequestBody(
                              configuration.get().getAnalysisTypes(),
                              configuration.get().getVersionTypes())))
                  .build(),
              new JsonBodyHandler<>(
                  MAPPER, new TypeReference<AnalysisProvenanceResponse<ExternalKey>>() {}));

      if (results.statusCode() != 200) {
        System.err.printf(
            "Request to %s to build vidarr_analysis input format returned bad HTTP code %d. The input format is now unusable.%n",
            baseUrl, results.statusCode());
        return new ErrorableStream<>(Stream.empty(), false);
      }

      final var body = results.body().get();
      return body.getResults().stream()
          .map(
              ca ->
                  new VidarrAnalysisValue(
                      ca.getCompleted().toInstant(),
                      ca.getCreated().toInstant(),
                      ca.getLastAccessed() == null
                          ? Optional.empty()
                          : Optional.of(ca.getLastAccessed().toInstant()),
                      ca.getAnalysis() == null
                          ? new HashSet<>()
                          : ca.getAnalysis().stream()
                              .map(
                                  analysisRecord ->
                                      new Tuple(
                                          analysisRecord.getChecksum(),
                                          analysisRecord.getChecksumType(),
                                          analysisRecord.getExternalKeys().stream()
                                              .map(
                                                  externalId ->
                                                      new Tuple(
                                                          externalId.getId(),
                                                          externalId.getProvider()))
                                              .collect(Collectors.toSet()),
                                          analysisRecord.getLabels(),
                                          analysisRecord.getSize(),
                                          String.format(
                                              "vidarr:%s/file/%s",
                                              ca.getInstanceName(), analysisRecord.getId()),
                                          analysisRecord.getMetatype(),
                                          analysisRecord.getPath()))
                              .collect(Collectors.toSet()),
                      ca.getExternalKeys().stream()
                          .map(
                              eKey ->
                                  new Tuple(
                                      eKey.getId(),
                                      eKey.getProvider(),
                                      eKey.getVersions() == null ? Map.of() : eKey.getVersions()))
                          .collect(Collectors.toSet()),
                      new HashSet<>(ca.getInputFiles()),
                      ca.getWorkflowName(),
                      ca.getWorkflowName() + "/" + ca.getWorkflowVersion(),
                      String.format("vidarr:%s/run/%s", ca.getInstanceName(), ca.getId()),
                      MAPPER.convertValue(
                          ca.getLabels(), new TypeReference<Map<String, JsonNode>>() {}),
                      IUSUtils.parseWorkflowVersion(ca.getWorkflowVersion())
                          .orElse(IUSUtils.UNKNOWN_VERSION)));
    }

    @Override
    protected Stream<VidarrAnalysisValue> fetch(Instant lastUpdated) throws Exception {
      if (configuration.isEmpty()) {
        System.err.println(
            "The vidarr_analysis input format is unusable because Vidarr config is empty.");
        return new ErrorableStream<>(Stream.empty(), false);
      }
      if (configuration.get().getAnalysisTypes() == null
          || configuration.get().getAnalysisTypes().isEmpty()) {
        return new ErrorableStream<>(Stream.empty(), true);
      }
      return analysisArchive(configuration.get().getUrl());
    }
  }

  private class MaxInFlightCache extends ValueCache<Optional<MaxInFlightDeclaration>> {

    public MaxInFlightCache(String name) {
      super("max-in-flight " + name, 10, SimpleRecord::new);
    }

    @Override
    protected Optional<MaxInFlightDeclaration> fetch(Instant lastUpdated) {
      Optional<MaxInFlightDeclaration> maxInFlight = Optional.empty();
      try {
        if (url.isPresent()) {
          final var mifResult =
              CLIENT.send(
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

  private class WorkflowRunInformationCache extends KeyValueCache<String, Optional<Tuple>> {

    public WorkflowRunInformationCache(String instanceName) {
      super("workflow-info " + instanceName, 10, SimpleRecord::new);
    }

    @Override
    protected Optional<Tuple> fetch(String id, Instant lastUpdated) throws Exception {
      final var result =
          CLIENT.send(
              HttpRequest.newBuilder(url.orElseThrow().resolve("/api/run/" + id)).GET().build(),
              new JsonBodyHandler<>(
                  MAPPER, new TypeReference<ProvenanceWorkflowRun<ExternalMultiVersionKey>>() {}));
      if (result.statusCode() == 200) {
        final var run = result.body().get();
        final var externalKeys = EXTERNAL_KEY_TYPE.newSet();
        for (final var externalKey : run.getExternalKeys()) {
          externalKeys.add(
              new Tuple(
                  externalKey.getId(),
                  externalKey.getProvider(),
                  externalKey.getVersions().entrySet().stream()
                      .collect(
                          Collectors.toMap(
                              Entry::getKey,
                              e -> new TreeSet<>(e.getValue()),
                              (a, b) -> a,
                              TreeMap::new))));
        }
        return Optional.of(
            new Tuple(
                Optional.ofNullable(run.getCompleted()).map(ZonedDateTime::toInstant),
                run.getCreated().toInstant(),
                externalKeys,
                run.getId(),
                new TreeSet<>(run.getInputFiles()),
                run.getInstanceName(),
                run.getModified().toInstant(),
                Optional.ofNullable(run.getStarted()).map(ZonedDateTime::toInstant),
                run.getWorkflowName(),
                run.getWorkflowVersion()));
      }
      return Optional.empty();
    }
  }

  private static final Imyhat EXTERNAL_KEY_TYPE =
      new ObjectImyhat(
          Stream.of(
              new Pair<>("id", Imyhat.STRING),
              new Pair<>("provider", Imyhat.STRING),
              new Pair<>("versions", Imyhat.dictionary(Imyhat.STRING, Imyhat.STRING.asList()))));
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

  static String sanitise(String raw) {
    final var clean = INVALID.matcher(raw).replaceAll("_");
    return Character.isLowerCase(clean.charAt(0)) ? clean : ("v" + clean);
  }

  private final Definer<VidarrPlugin> definer;
  private final MaxInFlightCache mifCache;
  private Optional<URI> url = Optional.empty();
  private boolean canSubmit = true;
  private SubmissionPolicy submissionPolicy = SubmissionPolicy.ALWAYS;
  private final WorkflowRunInformationCache workflowRunInfo;
  private Optional<Configuration> configuration = Optional.empty();
  private AnalysisCache analysisCache;

  public VidarrPlugin(Path fileName, String instanceName, Definer<VidarrPlugin> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
    mifCache = new MaxInFlightCache(instanceName);
    workflowRunInfo = new WorkflowRunInformationCache(instanceName);
    analysisCache = new AnalysisCache(fileName);
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    final var u = url;
    u.ifPresent(uri -> renderer.link("URL", uri.toString(), uri.toString()));
    renderer.line("Can submit?", String.valueOf(canSubmit));
  }

  public SubmissionPolicy defaultSubmissionPolicy() {
    return submissionPolicy;
  }

  static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @ShesmuMethod(
      type =
          "qo9completed$qdcreated$dexternalKeys$ao3id$sprovider$sversions$msasinput_files$asinstance_name$smodified$dstarted$qdversion$sworkflow$s",
      name = "workflow_run_info",
      description = "Gets information about a workflow run")
  public Optional<Tuple> fetchRun(String id) {
    return workflowRunInfo.get(id);
  }

  private String getMaxInFlightMessage(String workflow) {
    String message;
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

  @ShesmuAction(name = "unload_by_external_ids")
  public UnloadExternalIdentifiersAction unloadByExternalIds() {
    return new UnloadExternalIdentifiersAction(definer);
  }

  @ShesmuAction(name = "unload_by_workflow_runs")
  public UnloadWorkflowRunsAction unloadByWorkflowRuns() {
    return new UnloadWorkflowRunsAction(definer);
  }

  @ShesmuInputSource
  public Stream<VidarrAnalysisValue> streamVidarrProvenance(boolean readStale) {
    return readStale ? analysisCache.getStale() : analysisCache.get();
  }

  @Override
  protected Optional<Integer> update(Configuration value) {
    configuration = Optional.of(value);
    try {
      url = Optional.of(URI.create(value.getUrl()));
      if (!value.isCanSubmit()) {
        definer.clearActions();
        return Optional.of(10);
      } else {
        if (value.getDefaultMaxSubmissionDelay() == null) {
          submissionPolicy = SubmissionPolicy.ALWAYS;
        } else {
          submissionPolicy = SubmissionPolicy.maxDelay(value.getDefaultMaxSubmissionDelay());
        }
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
                final var type = resource.getValue().apply(SIMPLE_TO_IMYHAT);
                targetParameters.add(
                    new CustomActionParameter<>(
                        sanitise("resource_" + resource.getKey()),
                        // Optional resources can be absent from the request and Vidarr will
                        // behave properly, so we can safely make them not required
                        !(type instanceof OptionalImyhat),
                        type) {
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
                                              private final Supplier<VidarrPlugin> supplier =
                                                  definer;
                                              private final String targetName = target.getKey();
                                              private final String workflowName =
                                                  workflow.getName();
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
                                                            .<CustomActionParameter<SubmitAction>>
                                                                map(
                                                                    entry ->
                                                                        new CustomActionParameter<>(
                                                                            sanitise(
                                                                                "label_"
                                                                                    + entry
                                                                                        .getKey()),
                                                                            true,
                                                                            entry
                                                                                .getValue()
                                                                                .apply(
                                                                                    SIMPLE_TO_IMYHAT)) {
                                                                          private final String
                                                                              label =
                                                                                  entry.getKey();

                                                                          @Override
                                                                          public void store(
                                                                              SubmitAction action,
                                                                              Object value) {
                                                                            type()
                                                                                .accept(
                                                                                    new PackJsonObject(
                                                                                        action
                                                                                            .request
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
                                                                    workflow.getName()))));
                                              }
                                            })));
              }
            }
          }
          return Optional.of(10);
        } else if (workflowsResult.statusCode() == 301) {
          value.setUrl(Utils.get301LocationUrl(workflowsResult, definer));
          return update(value);
        } else if (targetsResult.statusCode() == 301) {
          value.setUrl(Utils.get301LocationUrl(targetsResult, definer));
          return update(value);
        } else {
          return Optional.of(2);
        }
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
