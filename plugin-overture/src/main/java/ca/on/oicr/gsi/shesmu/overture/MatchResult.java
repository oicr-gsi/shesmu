package ca.on.oicr.gsi.shesmu.overture;

import ca.on.gsi.shesm.overture.song.handler.ApiClient;
import ca.on.gsi.shesm.overture.song.model.Analysis;
import ca.on.gsi.shesm.overture.song.model.FileEntity;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class MatchResult {
  public static Stream<MatchResult> of(ApiClient client, Analysis analysis, List<FileInfo> files) {
    try {
      final List<InfoDiff> infoDiffs = new ArrayList<>();
      int oliveUnmatched = files.size();
      int serverUnmatched = analysis.getFiles().size();
      int oliveIndex = 0;
      int serverIndex = 0;
      int filesStale = 0;
      while (oliveIndex < files.size() && serverIndex < analysis.getFiles().size()) {
        final FileInfo currentOlive = files.get(oliveIndex);
        final FileEntity currentServer = analysis.getFiles().get(serverIndex);
        int comparison = currentOlive.access().compareTo(currentServer.getFileAccess());
        if (comparison == 0) {
          comparison = currentOlive.dataType().compareTo(currentServer.getDataType());
        }
        if (comparison == 0) {
          comparison = currentOlive.type().compareTo(currentServer.getFileType());
        }
        if (comparison == 0) {
          comparison =
              currentOlive.file().getFileName().toString().compareTo(currentServer.getFileName());
        }
        if (comparison == 0) {
          oliveUnmatched--;
          serverUnmatched--;
          if (currentOlive.md5().equals(currentServer.getFileMd5sum())
              && currentOlive.size() == currentServer.getFileSize()) {
          } else {
            filesStale++;
          }
          final JsonNode serverInfo =
              OverturePlugin.MAPPER.readTree(client.getJSON().serialize(currentServer.getInfo()));
          if (serverInfo.equals(currentOlive.info())) {
            infoDiffs.add(
                new InfoDiff(currentServer.getObjectId(), serverInfo, currentOlive.info()));
          }

        } else if (comparison < 0) {
          oliveIndex++;
        } else {
          serverIndex++;
        }
      }
      if (oliveUnmatched == files.size() && serverUnmatched == analysis.getFiles().size()) {
        return Stream.empty();
      } else {
        return Stream.of(
            new MatchResult(
                analysis, infoDiffs, oliveUnmatched > 0, serverUnmatched > 0, filesStale > 0));
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private final Analysis analysis;
  private final List<InfoDiff> infoDiffs;
  private final boolean staleFiles;
  private final boolean unmatchedOliveFiles;
  private final boolean unmatchedServerFiles;

  public MatchResult(
      Analysis analysis,
      List<InfoDiff> infoDiffs,
      boolean unmatchedOliveFiles,
      boolean unmatchedServerFiles,
      boolean staleFiles) {
    this.analysis = analysis;
    this.infoDiffs = infoDiffs;
    this.unmatchedOliveFiles = unmatchedOliveFiles;
    this.unmatchedServerFiles = unmatchedServerFiles;
    this.staleFiles = staleFiles;
  }

  public Analysis analysis() {
    return analysis;
  }

  public ActionState state() {
    return infoDiffs.isEmpty() && !unmatchedOliveFiles && !unmatchedServerFiles && !staleFiles
        ? ActionState.SUCCEEDED
        : ActionState.HALP;
  }
}
