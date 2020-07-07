import {
  initialise,
  tableRow,
  TableCell,
  link,
  tableFromRows,
  tagList,
  multipaneState,
  preformatted,
  UIElement,
  findProxy,
  tabs,
  addElements,
  setFindHandler,
  br,
  group,
  dropdownTable,
  italic,
  pane,
  blank,
  singleState,
  sharedPane,
  synchronizerFields,
  historyState,
  buttonDanger,
  textInline,
  refreshButton,
} from "./html.js";
import {
  SourceLocation,
  computeDuration,
  commonPathPrefix,
  copyLocation,
  combineModels,
  mapModel,
  filterModel,
  formatTimeSpan,
} from "./util.js";
import { ActionFilter, createSearch, BasicQuery } from "./actionfilters.js";
import {
  refreshableSvg,
  refreshable,
  serverStateModel,
  MutableServerInfo,
} from "./io.js";
import {
  PrometheusAlert,
  alertNavigator,
  prometheusAlertHeader,
  prometheusAlertLocation,
  AlertFilter,
  loadFilterRegex,
} from "./alert.js";
import { actionDisplay, ExportSearchCommand } from "./action.js";
import { actionStats } from "./stats.js";

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
  /** Any tags assoicated with this olive */
  tags: string[];
}
type OliveReference = {
  script: ScriptFile;
  playPause: UIElement;
  updatePaused: (state: boolean | null) => void;
  olive: {
    olive: Olive;
    playPause: UIElement;
    updatePaused: (state: boolean | null) => void;
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
  /**The time, in nanoseconds, the olive ran for*/
  runtime: number | null;
  /** The last time the olive was run, in milliseconds since the UNIX epoch. */
  lastRun: number | null;
  /** The olives in this script */
  olives: Olive[];
}

export function infoForProduces(
  produces: OliveType
): { icon: string; description: string } {
  switch (produces) {
    case "ACTIONS":
      return { icon: "🎬 ", description: "Produces actions" };
    case "ALERTS":
      return { icon: "🔔", description: "Produces alerts" };
    case "REFILL":
      return { icon: "🗑", description: "Refills a database" };
    default:
      return {
        icon: "🤷",
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

function makePauseUpdater(
  updater: (...elements: UIElement[]) => void
): (isPaused: boolean | null) => void {
  return (state) => {
    if (typeof state == "boolean") {
      updater(
        state
          ? textInline(" ⏸", "Actions Paused")
          : textInline(" ▶", "Actions Running")
      );
    } else {
      updater(blank());
    }
  };
}

function overview(file: ScriptFile, olive: Olive | null) {
  let lastRun: TableCell;
  if (file.lastRun) {
    const { ago, absolute } = computeDuration(file.lastRun);
    lastRun = { contents: ago, title: absolute };
  } else {
    lastRun = { contents: "Never" };
  }

  const info: HTMLTableRowElement[] = [
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
      { contents: "Input Fomat" },
      { contents: link(`inputdefs#${file.format}`, file.format) }
    ),
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
  userFilters: string | BasicQuery | null,
  alertFilters: AlertFilter<string>[] | null,
  exportSearches: ExportSearchCommand[]
): void {
  initialise();
  const container = document.getElementById("olives")!;
  const filenameFormatter = commonPathPrefix(oliveFiles.map((f) => f.file));
  const dashboardState = historyState(
    {
      alert: loadFilterRegex(alertFilters || []),
      saved: saved,
      filters: userFilters || {},
    },
    () => saved?.file || "Olives"
  );
  const { alert: alertState, filters: actionFilterState } = synchronizerFields(
    dashboardState
  );

  const alertFindProxy = findProxy();
  const actionFindProxy = findProxy();
  const metroModel = singleState(
    (element: UIElement | null) => element || "No olive selected."
  );
  const alertModel = sharedPane(
    "main",
    (alerts: PrometheusAlert[]) => {
      const { toolbar, main, find } = alertNavigator(
        alerts,
        prometheusAlertHeader,
        alertState,
        prometheusAlertLocation(filenameFormatter)
      );
      alertFindProxy.updateHandle(find);
      return { toolbar: toolbar, main: main };
    },
    "toolbar",
    "main"
  );
  const { ui: statsUi, model: statsModel } = actionStats(
    (...limits) => search.addPropertySearch(...limits),
    (typeName, start, end) => search.addRangeSearch(typeName, start, end)
  );
  const { actions, bulkCommands, model: actionsModel } = actionDisplay(
    exportSearches
  );
  const search = createSearch(
    actionFilterState,
    combineModels(statsModel, actionsModel),
    false,
    filenameFormatter,
    [],
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

  const alertRequest = refreshable(
    "queryalerts",
    (location: SourceLocation) => ({
      body: JSON.stringify({
        type: "sourcelocation",
        locations: [copyLocation(location)],
      } as AlertFilter<RegExp>),
      method: "POST",
    }),
    alertModel.model
  );

  const { model: pauseOliveModel, ui: pauseOliveButton } = singleState(
    (state: MutableServerInfo<OliveReference, boolean> | null) => {
      if (state && state.input?.olive?.olive.produces == "ACTIONS") {
        state.input?.olive?.updatePaused(state.response);
        return buttonDanger(
          state.response
            ? "▶ Resume Olive's Actions"
            : "⏸ Pause Olive's Actions",
          state.response
            ? "Allow any actions generated by this version of this olive to run instead of being throttled."
            : "Prevent any actions generated by this version of this olive from running and enter a throttled state.",
          () => state.setter(!state.response)
        );
      }
      return blank();
    }
  );

  const { model: pauseFileModel, ui: pauseFileButton } = singleState(
    (state: MutableServerInfo<OliveReference, boolean> | null) => {
      if (state) {
        state.input?.updatePaused(state.response);
        return buttonDanger(
          state.response
            ? "▶ Resume Script's Actions"
            : "⏸ Pause Script's Actions",
          state.response
            ? "Allow any actions generated by this script (or a previous version) to run instead of being throttled."
            : "Prevent any actions generated by this script (or a previous version) from running and enter a throttled state.",
          () => state.setter(!state.response)
        );
      }
      return blank();
    }
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

  const model = combineModels(
    serverStateModel(
      "pauseolive",
      pauseOliveModel,
      (reference: OliveReference, desired: boolean | null) =>
        reference?.olive
          ? {
              method: "POST",
              body: JSON.stringify({
                ...copyLocation(reference.olive.olive),
                pause: desired,
              }),
            }
          : null
    ),
    serverStateModel(
      "pausefile",
      pauseFileModel,
      (reference: OliveReference, desired: boolean | null) =>
        reference
          ? {
              method: "POST",
              body: JSON.stringify({
                file: reference.script.file,
                pause: desired,
              }),
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
      filterModel(
        combineModels(statsModel, actionsModel),
        "No olives selected."
      ),
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
    )
  );

  const { ui, find } = tabs(
    { name: "Overview", contents: [components.overview, statsUi] },
    { name: "Actions", contents: actions, find: actionFindProxy.find },
    {
      name: "Alerts",
      contents: [
        alertModel.components.toolbar,
        br(),
        alertModel.components.main,
      ],
      find: alertFindProxy.find,
    },
    { name: "Dataflow", contents: metroModel.ui },
    { name: "Bytecode", contents: components.bytecode }
  );
  const oliveSelector = dropdownTable(
    model,
    {
      synchronzier: dashboardState,
      extract: (reference) => ({
        saved: reference
          ? copyLocation(reference.olive?.olive || reference.script)
          : null,
        alert: [] as AlertFilter<RegExp>[],
        filters: {} as BasicQuery,
      }),
      predicate: (state, item) => {
        const location: SourceLocation | null =
          item?.olive?.olive || item?.script || null;
        return (
          location?.file == state.saved?.file &&
          location?.line == state.saved?.line &&
          location?.column == state.saved?.column &&
          location?.hash == state.saved?.hash
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
      const { ui: scriptPlayPause, update: update } = pane();
      const scriptUpdatePaused = makePauseUpdater(update);
      scriptUpdatePaused(script.isPaused);
      return {
        value: {
          script: script,
          playPause: scriptPlayPause,
          updatePaused: scriptUpdatePaused,
          olive: null,
          keywords: [...new Set(keywordsForScript(script))],
        } as OliveReference,
        label: [filenameFormatter(script.file), scriptPlayPause],
        children: script.olives.map((olive) => {
          const { ui: olivePlayPause, update } = pane();
          const updatePaused = makePauseUpdater(update);
          updatePaused(olive.paused);
          const produces = infoForProduces(olive.produces);
          return {
            value: {
              script: script,
              playPause: scriptPlayPause,
              updatePaused: scriptUpdatePaused,
              olive: {
                olive: olive,
                playPause: olivePlayPause,
                updatePaused: updatePaused,
              },
              keywords: [...new Set(keywordsForOlive(olive))],
            },
            label: [
              { contents: olive.syntax },
              { contents: olive.description || "No description." },
              {
                contents: [
                  textInline(produces.icon, produces.description),
                  olive.produces == "ACTIONS" ? olivePlayPause : blank(),
                ],
              },
            ],
          };
        }),
      };
    })
  );
  addElements(container, [
    group(
      oliveSelector,
      refreshButton(model.reload),
      pauseOliveButton,
      pauseFileButton,
      bulkCommands
    ),
    br(),
    ui,
  ]);
  setFindHandler(find);
}
