package ca.on.oicr.gsi.shesmu.overture;

import ca.on.gsi.shesm.overture.song.handler.ApiClient;
import ca.on.gsi.shesm.overture.song.handler.ApiException;
import ca.on.gsi.shesm.overture.song.handler.SubmitApi;
import ca.on.gsi.shesm.overture.song.model.SubmitResponse;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SongAction extends Action {

  private static JsonNode jacksonify(
      ApiClient client, ca.on.gsi.shesm.overture.song.model.JsonNode info) {
    try {
      return OverturePlugin.MAPPER.readTree(client.getJSON().serialize(info));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<String> cromwellId;
  @ActionParameter public String donor;
  JsonNode donorInfo;
  private List<String> errors = Collections.emptyList();
  private List<FileInfo> files;
  private List<MatchResult> matches = Collections.emptyList();
  private final String name;
  @ActionParameter public String sample;
  JsonNode sampleInfo;
  private final Supplier<OverturePlugin> server;
  @ActionParameter public String specimen;
  JsonNode specimenInfo;
  @ActionParameter public String study;
  private final Integer version;

  public SongAction(Supplier<OverturePlugin> server, String name, Integer version) {
    super("overture-song");
    this.server = server;
    this.name = name;
    this.version = version;
  }

  @ActionParameter(type = "ao6access$sdata_type$sfile$pinfo$jmd5$ssize$itype$s")
  public void files(Set<Tuple> input) {
    files =
        input.stream().map(FileInfo::new).sorted(FileInfo.COMPARATOR).collect(Collectors.toList());
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    // TODO
  }

  @Override
  public ActionState perform(ActionServices services) {
    return server
        .get()
        .api()
        .map(
            api -> {
              matches =
                  server
                      .get()
                      .study(study)
                      .flatMap(a -> MatchResult.of(api, a, files))
                      .collect(Collectors.toList());
              switch (matches.size()) {
                case 0:
                  return submit(api);
                case 1:
                  return matches.get(0).state();
                default:
                  return ActionState.HALP;
              }
            })
        .orElseGet(
            () -> {
              errors = Collections.singletonList("Unable to access connection.");
              return ActionState.UNKNOWN;
            });
  }

  @Override
  public int priority() {
    return 0;
  }

  @Override
  public long retryMinutes() {
    return 15;
  }

  @Override
  public boolean search(Pattern query) {
    // TODO
    return false;
  }

  private ActionState submit(ApiClient api) {
    try {
      final ObjectNode request = OverturePlugin.MAPPER.createObjectNode();
      final SubmitResponse response =
          new SubmitApi(api)
              .submitUsingPOST(
                  OverturePlugin.MAPPER.writeValueAsString(request),
                  study,
                  server.get().authorization());
      if (response.getAnalysisId() == null) {
        errors = Collections.singletonList(response.getStatus());
        return ActionState.FAILED;
      }
      response.getAnalysisId();
      // TODO: submit workflow
    } catch (ApiException | JsonProcessingException e) {
      errors = Collections.singletonList(e.getMessage());
      e.printStackTrace();
      return ActionState.UNKNOWN;
    }
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
    // TODO
    return node;
  }
}
