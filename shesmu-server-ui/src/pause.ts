import { actionDisplay, butterForPurgeCount } from "./action.js";
import { fetchJsonWithBusyDialog, refreshable } from "./io.js";
import {
  br,
  dialog,
  radioSelector,
  refreshButton,
  setRootDashboard,
  singleState,
  tableFromRows,
  tableRow,
  buttonDanger,
  temporaryState,
  tabs,
  hidden,
} from "./html.js";
import {
  SourceLocation,
  commonPathPrefix,
  copyLocation,
  mapModel,
  filterModel,
  combineModels,
} from "./util.js";
import { helpArea } from "./help.js";
import { ActionFilter, createSearch } from "./actionfilters.js";
import { actionStats } from "./stats.js";

export interface PauseRequest extends SourceLocation {
  pause: boolean | null;
}

/**
 * The current pause information
 */
export interface Pauses {
  liveFiles: string[];
  deadFiles: string[];
  liveOlives: SourceLocation[];
  deadOlives: SourceLocation[];
}
interface PauseLocation extends SourceLocation {
  live: boolean;
}
function convert(pauses: Pauses): PauseLocation[] {
  return [
    pauses.liveFiles.map((f) => ({
      file: f,
      line: null,
      column: null,
      hash: null,
      live: true,
    })),
    pauses.deadFiles.map((f) => ({
      file: f,
      line: null,
      column: null,
      hash: null,
      live: false,
    })),
    pauses.liveOlives.map((l) => ({ ...l, live: true })),
    pauses.deadOlives.map((l) => ({ ...l, live: false })),
  ].flat();
}
export function initialisePauseDashboard(pauses: Pauses) {
  const filenameFormatter = commonPathPrefix(
    [
      pauses.deadFiles,
      pauses.liveFiles,
      pauses.liveOlives.map((s) => s.file),
      pauses.deadOlives.map((s) => s.file),
    ].flat()
  );
  const actions = actionDisplay([]);
  const stats = actionStats(
    (...limits) => search.addPropertySearch(...limits),
    (typeName, start, end) => search.addRangeSearch(typeName, start, end),
    []
  );
  const search = createSearch(
    temporaryState({}),
    combineModels(stats.model, actions.model),
    false,
    filenameFormatter,
    []
  );

  const hiddenPane = hidden(
    tabs(
      { contents: stats.ui, name: "Overview" },
      {
        contents: [actions.bulkCommands, br(), actions.actions],
        name: "Actions",
      }
    )
  );
  const actionsModel = combineModels(
    filterModel(
      mapModel(search.model, (location: PauseLocation): ActionFilter[] => [
        { type: "sourcelocation", locations: [copyLocation(location)] },
      ]),
      "No pause is selected."
    ),
    mapModel(hiddenPane.model, (input) => input !== null)
  );
  const selectorTable = singleState((pauseInfo: Pauses | null) => {
    const pauses = pauseInfo ? convert(pauseInfo) : [];
    const active = radioSelector(
      "Show Actions",
      "Hide Actions",
      actionsModel,
      ...pauses
    );

    return pauses.length
      ? tableFromRows(
          [
            tableRow(
              null,
              {
                contents: "File",
                header: true,
              },
              {
                contents: "Line",
                header: true,
              },
              {
                contents: "Column",
                header: true,
              },
              {
                contents: "Source Hash",
                header: true,
              },
              {
                contents: "On Active Script?",
                header: true,
              },
              {
                contents: "",
                header: true,
              }
            ),
          ].concat(
            pauses.map((pause, index) =>
              tableRow(
                null,
                {
                  contents: filenameFormatter(pause.file),
                },
                {
                  contents: pause.line || "*",
                },
                {
                  contents: pause.column || "*",
                },
                {
                  contents: pause.hash || "*",
                },
                {
                  contents: pause.live ? "🍅 Fresh" : "💀 Dead",
                },
                {
                  contents: [
                    active[index],

                    buttonDanger(
                      "▶ Restart",
                      "Remove this pause and allow actions to continue.",
                      () =>
                        fetchJsonWithBusyDialog(
                          pause.line ? "pauseolive" : "pausefile",
                          {
                            method: "POST",
                            body: JSON.stringify({ ...pause, pause: false }),
                          },
                          (isPause) => {
                            if (!isPause) {
                              refresher.statusChanged(null);
                            } else {
                              dialog((_c) => "Could not clear pause.");
                            }
                          }
                        )
                    ),
                    buttonDanger(
                      "️☠️ Purge",
                      "Purge related actions and remove this pause.",
                      () =>
                        fetchJsonWithBusyDialog(
                          "purge",
                          {
                            method: "POST",
                            body: JSON.stringify([
                              {
                                type: "sourcelocation",
                                locations: [copyLocation(pause)],
                              },
                            ]),
                          },
                          (count: number) =>
                            fetchJsonWithBusyDialog(
                              pause.line ? "pauseolive" : "pausefile",
                              {
                                method: "POST",
                                body: JSON.stringify({
                                  ...pause,
                                  pause: false,
                                }),
                              },
                              (isPause) => {
                                if (!isPause) {
                                  butterForPurgeCount(count);
                                  refresher.statusChanged(null);
                                } else {
                                  dialog((_c) => "Could not clear pause.");
                                }
                              }
                            )
                        )
                    ),
                  ],
                }
              )
            )
          )
        )
      : "No pauses set right now.";
  });

  const refresher = refreshable(
    "pauses",
    (input: null) => ({ method: "GET" }),
    selectorTable.model,
    true
  );

  setRootDashboard(
    document.getElementById("pausedash")!,
    refreshButton(refresher.reload),
    helpArea("pauses"),
    br(),
    selectorTable.ui,
    hiddenPane.ui
  );
  selectorTable.model.statusChanged(pauses);
}
