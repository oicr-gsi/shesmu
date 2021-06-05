import {
  IconName,
  Tab,
  TableCell,
  UIElement,
  blank,
  br,
  buttonDanger,
  dropdownTable,
  group,
  historyState,
  italic,
  link,
  multipaneState,
  preformatted,
  refreshButton,
  setRootDashboard,
  sharedPane,
  singleState,
  synchronizerFields,
  tableFromRows,
  tableRow,
  tabsModel,
  tagList,
  textWithTitle,
  hidden,
  DisplayElement,
  mono,
} from "./html.js";
import {
  SourceLocation,
  StatefulModel,
  combineModels,
  commonPathPrefix,
  computeDuration,
  copyLocation,
  filterModel,
  formatTimeSpan,
  mapModel,
} from "./util.js";
import {
  ActionFilter,
  createSearch,
  BasicQuery,
  Query,
} from "./actionfilters.js";
import {
  MutableServerInfo,
  refreshable,
  refreshableSvg,
  serverStateModel,
} from "./io.js";
import {
  AlertFilter,
  PrometheusAlert,
  ServerAlertFilter,
  alertNavigator,
  loadFilterRegex,
  prometheusAlertHeader,
  prometheusAlertLocation,
} from "./alert.js";
import {
  ExportSearchCommand,
  actionDisplay,
  standardExports,
} from "./action.js";
import { actionStats } from "./stats.js";
import { helpArea } from "./help.js";

/**
 * A description of a single clause in an olive
 */
interface Clause {
  /** The line of the clause in the source file */
  line: number;
  /** The column of the clause in the source file */
  column: number;
  /** The keyword of the clause in the source file */
  syntax: string;
}
/**
 * An olive in a file
 */
interface Olive extends SourceLocation {
  /** The clauses from the source file that makes up this olive */
  clauses: Clause[];
  /** The description provided by the olive author */
  description: string | null;
  /** The keyword associated with this olive */
  syntax: string;
  /** The output format of this olive */
  produces: OliveType;
  /** Whether the olive is paused */
  paused: boolean | null;
  /**
   * Extra information the olive definition wants to show
   */
  supplementaryInformation: { label: DisplayElement; value: DisplayElement }[];
  /** Any tags associated with this olive */
  tags: string[];
  /** Any tags associated with actions generated by this olive no present in the normal tag list */
  tagsDynamic: string[];
}
type OliveReference = {
  script: ScriptFile;
  playPaused: StatefulModel<boolean | null>;
  olive: {
    olive: Olive;
    playPaused: StatefulModel<boolean | null>;
  } | null;
  keywords: string[];
} | null;

/**
 * The output from an olive
 */
export type OliveType = "ACTIONS" | "ALERTS" | "REFILL";
/**
 * A script that matches a single `.shesmu` file
 */
interface ScriptFile extends SourceLocation {
  /** The input format used by this file */
  format: string;
  /** The disassembled bytecode */
  bytecode: string;
  /** The CPU time in milliseconds the olive ran for */
  cpuTime: number | null;
  /**
   * Whether the olive is paused
   */
  isPaused: boolean;
  /**
   * The information about the run status of the script
   */
  status: string;
  /**
   * The number of rows of input ingested by the script when last run
   */
  inputCount: number | null;
  /**The wallclock time, in milliseconds, the olive ran for*/
  runtime: number | null;
  /** The last time the olive was run, in milliseconds since the UNIX epoch. */
  lastRun: number | null;
  /** The olives in this script */
  olives: Olive[];
}

export function infoForProduces(
  produces: OliveType
): { icon: IconName; description: string } {
  switch (produces) {
    case "ACTIONS":
      return { icon: "camera-reels", description: "Produces actions" };
    case "ALERTS":
      return { icon: "bell", description: "Produces alerts" };
    case "REFILL":
      return { icon: "bucket", description: "Refills a database" };
    default:
      return {
        icon: "question-diamond",
        description: "I have no idea what this olive does.",
      };
  }
}

function keywordsForOlive(olive: Olive): string[] {
  return [olive.syntax, olive.description || ""]
    .flatMap((t) => t.trim().split(/\W+/))
    .concat(olive.tags)
    .filter((x) => x)
    .map((x) => x.toLowerCase());
}
function keywordsForScript(script: ScriptFile): string[] {
  return script.file
    .split(/\//)
    .filter((x) => x)
    .map((x) => x.toLowerCase())
    .concat(script.olives.flatMap((olive) => keywordsForOlive(olive)));
}

function makePauseUpdater(): {
  ui: UIElement;
  model: StatefulModel<boolean | null>;
} {
  return singleState((state) => {
    if (typeof state == "boolean") {
      return state
        ? textWithTitle({ type: "icon", icon: "pause-fill" }, "Actions Paused")
        : textWithTitle({ type: "icon", icon: "play-fill" }, "Actions Running");
    } else {
      return blank();
    }
  });
}

function overview(file: ScriptFile, olive: Olive | null) {
  let lastRun: TableCell;
  if (file.lastRun) {
    const { ago, absolute } = computeDuration(file.lastRun);
    lastRun = { contents: ago, title: absolute };
  } else {
    lastRun = { contents: "Never" };
  }

  const info = [
    tableRow(null, { contents: "Status" }, { contents: file.status }),
    tableRow(null, { contents: "Last Run" }, lastRun),
    tableRow(
      null,
      { contents: "Run Time" },
      {
        contents:
          file.runtime == null ? "Unknown" : formatTimeSpan(file.runtime),
      }
    ),
    tableRow(
      null,
      { contents: "CPU Time" },
      {
        contents:
          file.cpuTime == null ? "Unknown" : formatTimeSpan(file.cpuTime),
      }
    ),
    tableRow(
      null,
      { contents: "Input Fomat" },
      { contents: link(`inputdefs#${file.format}`, file.format) }
    ),
    tableRow(null, { contents: "Source Path" }, { contents: mono(file.file) }),
    tableRow(null, { contents: "Source Hash" }, { contents: file.hash || "*" }),
    tableRow(
      null,
      { contents: "Simulation" },
      {
        contents: link(
          `simulatedash?script=${encodeURIComponent(file.file)}`,
          "Edit in Simulation"
        ),
      }
    ),
  ];
  if (olive) {
    for (const { label, value } of olive.supplementaryInformation) {
      info.push(tableRow(null, { contents: label }, { contents: value }));
    }
    if (olive.url) {
      info.push(
        tableRow(
          null,
          { contents: "Source Code" },
          { contents: link(olive.url, "View") }
        )
      );
    }
    if (olive.tags.length) {
      info.push(
        tableRow(
          null,
          { contents: "Tags" },
          { contents: tagList("", olive.tags) }
        )
      );
    }
  }
  return tableFromRows(info);
}

export function initialiseOliveDash(
  oliveFiles: ScriptFile[],
  saved: SourceLocation | null,
  userFilters: Query | null,
  alertFilters: AlertFilter<string>[] | null,
  exportSearches: ExportSearchCommand[]
): void {
  const container = document.getElementById("olives")!;
  const filenameFormatter = commonPathPrefix(oliveFiles.map((f) => f.file));
  const {
    alert: alertState,
    filters: actionFilterState,
    saved: savedState,
  } = synchronizerFields(
    historyState(
      "olivedash",
      {
        alert: loadFilterRegex(alertFilters || []),
        saved: saved,
        filters: userFilters === null ? {} : userFilters,
      },
      () => saved?.file || "Olives"
    )
  );

  const metroModel = singleState(
    (element: UIElement | null) => element || "No olive selected."
  );
  const alertModel = sharedPane(
    "main",
    (alerts: PrometheusAlert[]) => {
      const { toolbar, main } = alertNavigator(
        alerts,
        prometheusAlertHeader,
        alertState,
        prometheusAlertLocation(filenameFormatter)
      );
      return { toolbar: toolbar, main: main };
    },
    "toolbar",
    "main"
  );
  const { ui: statsUi, toolbar: statsToolbar, model: statsModel } = actionStats(
    (...limits) => search.addPropertySearch(...limits),
    (typeName, start, end, ...limits) =>
      search.addRangeSearch(typeName, start, end, ...limits),
    filenameFormatter,
    standardExports.concat(exportSearches)
  );
  const { actions, bulkCommands, model: actionsModel } = actionDisplay(
    exportSearches
  );
  const search = createSearch(
    actionFilterState,
    combineModels(statsModel, actionsModel),
    false,
    filenameFormatter,
    []
  );
  const metroRequest = refreshableSvg(
    "metrodiagram",
    (location: SourceLocation) => ({
      body: JSON.stringify(copyLocation(location)),
      method: "POST",
    }),
    metroModel.model
  );

  const alertRequest = mapModel(
    refreshable(
      "queryalerts",
      mapModel(alertModel.model, (alerts) => alerts || []),
      false
    ),
    (location: SourceLocation): ServerAlertFilter => ({
      type: "sourcelocation",
      locations: [copyLocation(location)],
    })
  );

  const { model: pauseOliveModel, ui: pauseOliveButton } = singleState(
    (state: MutableServerInfo<OliveReference, boolean> | null) => {
      if (state && state.input?.olive?.olive.produces == "ACTIONS") {
        state.input?.olive?.playPaused.statusChanged(state.response);
        return buttonDanger(
          state.response
            ? [{ type: "icon", icon: "play-fill" }, "Resume Olive's Actions"]
            : [{ type: "icon", icon: "pause-fill" }, "Pause Olive's Actions"],
          state.response
            ? "Allow any actions generated by this version of this olive to run instead of being throttled."
            : "Prevent any actions generated by this version of this olive from running and enter a throttled state.",
          () => state.setter(!state.response)
        );
      }
      return blank();
    },
    true
  );

  const { model: pauseFileModel, ui: pauseFileButton } = singleState(
    (state: MutableServerInfo<OliveReference, boolean> | null) => {
      if (state) {
        state.input?.playPaused.statusChanged(state.response);
        return buttonDanger(
          state.response
            ? [{ type: "icon", icon: "play-fill" }, "Resume Script"]
            : [{ type: "icon", icon: "pause-fill" }, "Pause Script"],
          state.response
            ? "Allow any actions generated by this script (or a previous version) to run instead of being throttled and allow the script to run."
            : "Prevent any actions generated by this script (or a previous version) from running and enter a throttled state and prevent the script from running.",
          () => state.setter(!state.response)
        );
      }
      return blank();
    },
    true
  );

  const { model: miscModel, components } = multipaneState<
    OliveReference,
    {
      overview: (selected: OliveReference) => UIElement;
      bytecode: (selected: OliveReference) => UIElement;
    }
  >("overview", {
    overview: (selected: OliveReference): UIElement =>
      selected
        ? overview(selected.script, selected.olive?.olive || null)
        : "No olive selected.",
    bytecode: (selected: OliveReference): UIElement =>
      selected ? preformatted(selected.script.bytecode) : "No olive selected.",
  });

  const { ui: tabsUi, models: tabsModels } = tabsModel(1, {
    name: "Overview",
    contents: [components.overview, statsUi],
  });

  const { ui, model: hiddenModel } = hidden(tabsUi);

  const model = combineModels(
    mapModel(hiddenModel, (reference) => reference !== null),
    serverStateModel(
      "pauseolive",
      pauseOliveModel,
      (reference: OliveReference, desired: boolean | null) =>
        reference?.olive
          ? {
              ...copyLocation(reference.olive.olive),
              pause: desired,
            }
          : null
    ),
    serverStateModel(
      "pausefile",
      pauseFileModel,
      (reference: OliveReference, desired: boolean | null) =>
        reference
          ? {
              file: reference.script.file,
              line: null,
              column: null,
              hash: null,
              pause: desired,
            }
          : null
    ),
    miscModel,
    mapModel(
      filterModel(
        combineModels(metroRequest, alertRequest),
        "Only available for single olives."
      ),
      (selected: OliveReference) =>
        selected?.olive?.olive || selected?.script.olives[0] || null
    ),
    mapModel(
      filterModel(search.model, "No olives selected."),
      (selected: OliveReference): ActionFilter[] | null => {
        if (selected) {
          return [
            {
              type: "sourcelocation",
              locations: [
                copyLocation(selected.olive?.olive || selected.script),
              ],
            },
          ];
        } else {
          return null;
        }
      }
    ),
    mapModel(tabsModels[0], (selected: OliveReference) => {
      if (!selected) return { tabs: [], activate: false };
      const tabList: Tab[] = [];
      if (
        selected.olive
          ? selected.olive?.olive.produces == "ACTIONS"
          : selected.script.olives.some((o) => o.produces == "ACTIONS")
      ) {
        tabList.push({
          name: "Actions",
          contents: [search.buttons, br(), search.entryBar, br(), actions],
        });
      }
      if (
        selected.olive
          ? selected.olive?.olive.produces == "ALERTS"
          : selected.script.olives.some((o) => o.produces == "ALERTS")
      ) {
        tabList.push({
          name: "Alerts",
          contents: [
            alertModel.components.toolbar,
            br(),
            alertModel.components.main,
          ],
        });
      }
      if (selected.olive || selected.script.olives.length == 1) {
        tabList.push({ name: "Dataflow", contents: metroModel.ui });
      }
      tabList.push({ name: "Bytecode", contents: components.bytecode });

      return { tabs: tabList, activate: false };
    })
  );
  const oliveSelector = dropdownTable(
    model,
    {
      synchronzier: savedState,
      extract: (reference) =>
        reference
          ? copyLocation(reference.olive?.olive || reference.script)
          : null,
      predicate: (state, item) => {
        const location: SourceLocation | null =
          item?.olive?.olive || item?.script || null;
        return (
          location?.file == state?.file &&
          location?.line == state?.line &&
          location?.column == state?.column &&
          location?.hash == state?.hash
        );
      },
    },
    (selected: OliveReference) => {
      if (selected) {
        if (selected.olive) {
          return [
            italic(selected.olive.olive.syntax),
            " ― ",
            selected.olive.olive.description || "No description.",
          ];
        } else {
          return filenameFormatter(selected.script.file);
        }
      } else {
        return "Select a file or olive";
      }
    },
    (candidate: OliveReference, keywords: string[]) =>
      candidate
        ? keywords.every((keyword) =>
            candidate.keywords.some((t) => t.indexOf(keyword) != -1)
          )
        : false,
    ...oliveFiles.map((script) => {
      const scriptPlayPause = makePauseUpdater();
      scriptPlayPause.model.statusChanged(script.isPaused);
      return {
        value: {
          script: script,
          playPaused: scriptPlayPause.model,
          olive: null,
          keywords: [...new Set(keywordsForScript(script))],
        } as OliveReference,
        label: [filenameFormatter(script.file), scriptPlayPause.ui],
        children: script.olives.map((olive) => {
          const olivePlayPause = makePauseUpdater();
          olivePlayPause.model.statusChanged(olive.paused);
          const produces = infoForProduces(olive.produces);
          return {
            value: {
              script: script,
              playPaused: scriptPlayPause.model,
              olive: {
                olive: olive,
                playPaused: olivePlayPause.model,
              },
              keywords: [...new Set(keywordsForOlive(olive))],
            },
            label: [
              { contents: olive.syntax },
              { contents: olive.description || "No description." },
              {
                contents: [
                  textWithTitle(
                    { type: "icon", icon: produces.icon },
                    produces.description
                  ),
                  olive.produces == "ACTIONS" ? olivePlayPause.ui : blank(),
                ],
              },
            ],
          };
        }),
      };
    })
  );
  setRootDashboard(container, [
    group(
      oliveSelector,
      refreshButton(model.reload),
      pauseOliveButton,
      pauseFileButton,
      statsToolbar,
      bulkCommands,
      helpArea("olive")
    ),
    br(),
    ui,
  ]);
}
