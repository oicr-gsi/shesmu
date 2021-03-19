package ca.on.oicr.gsi.shesmu.guanyin;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class GuanyinReportValue {
  private final String category;
  private final Set<Path> filesIn;
  private final boolean finished;
  private final Optional<Instant> freshestInputDate;
  private final Optional<Instant> generated;
  private final long id;
  private final boolean notificationDone;
  private final JsonNode parameters;
  private final long reportId;
  private final String reportName;
  private final Optional<Path> reportPath;
  private final String version;

  public GuanyinReportValue(ReportDto reportDto, RecordDto recordDto) {
    category = reportDto.getCategory();
    filesIn =
        recordDto.getFilesIn() == null
            ? Set.of()
            : recordDto.getFilesIn().stream().map(Paths::get).collect(Collectors.toSet());
    finished = recordDto.isFinished();
    freshestInputDate = Optional.ofNullable(recordDto.getFreshestInputDate()).map(Instant::parse);
    generated = Optional.ofNullable(recordDto.getGenerated()).map(Instant::parse);
    id = recordDto.getId();
    notificationDone = recordDto.isNotificationDone();
    parameters = recordDto.getParameters();
    reportId = reportDto.getId();
    reportName = reportDto.getName();
    reportPath = Optional.ofNullable(recordDto.getReportPath()).map(Paths::get);
    version = reportDto.getVersion();
  }

  @ShesmuVariable
  public String category() {
    return category;
  }

  @ShesmuVariable
  public Set<Path> files_in() {
    return filesIn;
  }

  @ShesmuVariable
  public Optional<Instant> freshest_input_timestamp() {
    return freshestInputDate;
  }

  @ShesmuVariable
  public Optional<Instant> generated() {
    return generated;
  }

  @ShesmuVariable
  public boolean is_finished() {
    return finished;
  }

  @ShesmuVariable
  public boolean notification_done() {
    return notificationDone;
  }

  @ShesmuVariable
  public Optional<Path> output_path() {
    return reportPath;
  }

  @ShesmuVariable
  public JsonNode parameters() {
    return parameters;
  }

  @ShesmuVariable
  public long record_id() {
    return id;
  }

  @ShesmuVariable
  public long report_id() {
    return reportId;
  }

  @ShesmuVariable
  public String report_name() {
    return reportName;
  }

  @ShesmuVariable
  public String version() {
    return version;
  }
}
