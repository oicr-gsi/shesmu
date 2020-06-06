package ca.on.oicr.gsi.shesmu.overture;

import ca.on.gsi.shesm.overture.song.handler.AnalysisApi;
import ca.on.gsi.shesm.overture.song.handler.ApiClient;
import ca.on.gsi.shesm.overture.song.handler.SchemaApi;
import ca.on.gsi.shesm.overture.song.model.Analysis;
import ca.on.gsi.shesm.overture.song.model.AnalysisType;
import ca.on.gsi.shesm.overture.song.model.PageDTOAnalysisType;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;

public final class OverturePlugin extends JsonPluginFile<Configuration> {
  private final class StudyDataCache
      extends KeyValueCache<String, Stream<Analysis>, Stream<Analysis>> {

    public StudyDataCache(Path fileName) {
      super("study " + fileName, 30, ReplacingRecord::new);
    }

    @Override
    protected Stream<Analysis> fetch(String key, Instant lastUpdated) throws Exception {
      final ApiClient client = api.orElse(null);
      if (client == null) {
        return Stream.empty();
      }
      final List<Analysis> analyses = new AnalysisApi(client).getAnalysisUsingGET(key, "PUBLISHED");
      for (final Analysis analysis : analyses) {
        analysis.getFiles().sort(FileInfo.COMPARATOR_ENTITY);
      }
      return analyses.stream();
    }
  }

  static final ObjectMapper MAPPER = new ObjectMapper();
  private Optional<ApiClient> api = Optional.empty();
  private String authorization = "";
  private final Definer<OverturePlugin> definer;
  private final StudyDataCache studies;

  public OverturePlugin(Path fileName, String instanceName, Definer<OverturePlugin> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
    studies = new StudyDataCache(fileName);
  }

  public Optional<ApiClient> api() {
    return api;
  }

  @Override
  public void configuration(SectionRenderer renderer) throws XMLStreamException {}

  public void invalidate(String study) {
    studies.invalidate(study);
  }

  public String authorization() {
    return authorization;
  }

  private Stream<CustomActionParameter<SongAction>> processSchema(String schema) {
    // TODO

  }

  public Stream<Analysis> study(String studyId) {
    return studies.get(studyId);
  }

  @Override
  protected Optional<Integer> update(Configuration configuration) {
    final ApiClient api = new ApiClient().setBasePath(configuration.getSongUrl());
    this.api = Optional.of(api);
    int offset = 0;
    authorization = configuration.getAuthorization();
    definer.clearActions();
    try {
      while (true) {
        final PageDTOAnalysisType response =
            new SchemaApi(api)
                .listAnalysisTypesUsingGET(
                    false, 1000, null, offset, null, null, null, null, null, null, null, null, null,
                    null);
        for (final AnalysisType type : response.getResultSet()) {
          definer.defineAction(
              String.format("%s_%s_%d", this.name(), type.getName(), type.getVersion()),
              String.format(
                  "Submission to SONG server defined in %s for schema %s (version %d)",
                  fileName(), type.getName(), type.getVersion()),
              SongAction.class,
              () -> new SongAction(definer, type.getName(), type.getVersion()),
              processSchema(
                  api.getJSON()
                      .serialize(type.getSchema())) // The format given to us by the API client is a
              // nightmare, so we're just going to convert it back to
              // a string and let Jackson decode it
              );
        }
        offset = response.getOffset() + response.getResultSet().size();
        if (response.getCount() <= offset) break;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return Optional.of(10);
    }
    return Optional.empty();
  }
}
