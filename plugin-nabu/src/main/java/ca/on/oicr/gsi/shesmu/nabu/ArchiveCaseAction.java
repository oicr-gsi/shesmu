package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.*;
import ca.on.oicr.gsi.shesmu.plugin.action.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public class ArchiveCaseAction extends ArchiveAction<NabuCaseArchiveDto> {

  public String assayName;
  public String assayVersion;
  public Long requisitionId;

  public ArchiveCaseAction(Definer<NabuPlugin> owner) {
    super(owner, "archive-case-action");
  }

  @ActionParameter(name = "assay_name")
  public void assayName(String assayName) {
    this.assayName = assayName;
  }

  @ActionParameter(name = "assay_version")
  public void assayVersion(String assayVersion) {
    this.assayVersion = assayVersion;
  }

  @ActionParameter(name = "case_identifier")
  public void caseId(String caseId) {
    this.identifier = caseId;
  }

  @ActionParameter(name = "case_total_size")
  public void caseTotalSize(Long caseTotalSize) {
    this.totalSize = caseTotalSize;
  }

  @ActionParameter(name = "requisition_id")
  public void requisitionId(Long requisitionId) {
    this.requisitionId = requisitionId;
  }

  @Override
  protected String actionType() {
    return "archive-case-action";
  }

  @Override
  protected String identifierJsonFieldName() {
    return "caseIdentifier";
  }

  @Override
  protected String totalSizeJsonFieldName() {
    return "caseTotalSize";
  }

  @Override
  protected String entityLabel() {
    return "case";
  }

  @Override
  protected Class<NabuCaseArchiveDto[]> dtoArrayClass() {
    return NabuCaseArchiveDto[].class;
  }

  @Override
  protected ObjectNode createRequestJson(ObjectMapper mapper) {
    final ObjectNode node = super.createRequestJson(mapper);
    node.put("requisitionId", this.requisitionId);
    return node;
  }

  @Override
  protected JsonNode metadataToJson(
      Optional<String> archiveNote,
      Long totalSize,
      Long offsiteArchiveSize,
      Long onsiteArchiveSize) {
    ObjectNode node =
        (ObjectNode)
            super.metadataToJson(archiveNote, totalSize, offsiteArchiveSize, onsiteArchiveSize);
    node.put("assayName", this.assayName);
    node.put("assayVersion", this.assayVersion);
    return node;
  }

  @Override
  public boolean equals(Object obj) {

    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ArchiveCaseAction other = (ArchiveCaseAction) obj;
    return super.equals(obj) && Objects.equals(this.requisitionId, other.requisitionId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), requisitionId);
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    try {
      digest.accept(MAPPER.writeValueAsBytes(owner));
      digest.accept(new byte[] {0});
      digest.accept(identifier.getBytes(StandardCharsets.UTF_8));
      digest.accept(new byte[] {0});
      digest.accept(MAPPER.writeValueAsBytes(requisitionId));
      digest.accept(MAPPER.writeValueAsBytes(limsIds));
      digest.accept(MAPPER.writeValueAsBytes(parameters));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }
}
