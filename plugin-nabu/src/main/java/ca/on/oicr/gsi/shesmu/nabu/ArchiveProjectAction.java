package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.*;
import ca.on.oicr.gsi.shesmu.plugin.action.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public class ArchiveProjectAction extends ArchiveAction<NabuProjectArchiveDto> {

  public ArchiveProjectAction(Definer<NabuPlugin> owner) {
    super(owner, "archive-project-action");
  }

  @ActionParameter(name = "project_identifier")
  public void projectId(String projectId) {
    this.identifier = projectId;
  }

  @ActionParameter(name = "project_total_size")
  public void projectTotalSize(Long projectTotalSize) {
    this.totalSize = projectTotalSize;
  }

  @Override
  protected String actionType() {
    return "archive-project-action";
  }

  @Override
  protected String identifierJsonFieldName() {
    return "projectIdentifier";
  }

  @Override
  protected String totalSizeJsonFieldName() {
    return "projectTotalSize";
  }

  @Override
  protected String entityLabel() {
    return "project";
  }

  @Override
  protected Class<NabuProjectArchiveDto[]> dtoArrayClass() {
    return NabuProjectArchiveDto[].class;
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    try {
      digest.accept(MAPPER.writeValueAsBytes(owner));
      digest.accept(new byte[] {0});
      digest.accept(identifier.getBytes(StandardCharsets.UTF_8));
      digest.accept(new byte[] {0});
      digest.accept(MAPPER.writeValueAsBytes(limsIds));
      digest.accept(MAPPER.writeValueAsBytes(parameters));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }
}
