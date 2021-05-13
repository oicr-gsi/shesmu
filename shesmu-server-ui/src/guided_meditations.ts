import {
  ExportSearchCommand,
  actionDisplay,
  standardExports,
} from "./action.js";
import { ActionFilter, createSearch } from "./actionfilters.js";
import {
  AlertFilter,
  PrometheusAlert,
  ServerAlertFilter,
  alertNavigator,
  prometheusAlertHeader,
  prometheusAlertLocation,
} from "./alert.js";
import { helpArea } from "./help.js";
import {
  DisplayElement,
  InputField,
  TableCell,
  UIElement,
  blank,
  br,
  button,
  buttonAccessory,
  dialog,
  dragOrder,
  dropdown,
  hr,
  inputCheckbox,
  inputNumber,
  inputText,
  link,
  mono,
  pane,
  pickFromSet,
  radioSelector,
  setRootDashboard,
  sharedPane,
  singleState,
  table,
  tableFromRows,
  tableRow,
  tabs,
  temporaryState,
} from "./html.js";
import { fetchAsPromise, loadFile, refreshable, saveFile } from "./io.js";
import { actionStats } from "./stats.js";
import {
  FakeConstant,
  FakeRefillerParameters,
  SimulationResponse,
  renderResponse,
} from "./simulation.js";
import {
  FilenameFormatter,
  StatefulModel,
  combineModels,
  commonPathPrefix,
  individualPropertyModel,
  mapModel,
  reducingModel,
} from "./util.js";
import { dictNew, setNew } from "./runtime.js";

interface GuidedMeditation {
  name: string;
  start: (
    status: StatefulModel<StatusInidcator>,
    filenameFormatter: FilenameFormatter,
    exportSearches: ExportSearchCommand[]
  ) => UIElement;
}

type Information =
  | { type: "actions"; filter: ActionFilter }
  | { type: "alerts"; filter: ServerAlertFilter }
  | {
      type: "download";
      file: string;
      mimetype: string;
      contents: string;
      isJson: false;
    }
  | {
      type: "download";
      file: string;
      mimetype: string;
      contents: any;
      isJson: true;
    }
  | {
      type: "simulation";
      script: string;
      parameters: { [name: string]: { type: string; value: any } };
      fakeRefillers: { [name: string]: FakeRefillerParameters };
    }
  | {
      type: "simulation-existing";
      fileName: string;
      parameters: { [name: string]: { type: string; value: any } };
    }
  | { type: "table"; data: DisplayElement[][]; headers: string[] }
  | { type: "display"; contents: DisplayElement };

type FormElement<T> =
  | (T extends string
      ? {
          label: DisplayElement;
          type: "text";
        }
      : never)
  | (T extends number
      ? {
          label: DisplayElement;
          type: "number" | "offset";
        }
      : never)
  | (T extends boolean
      ? {
          label: DisplayElement;
          type: "boolean";
        }
      : never)
  | (T extends string[]
      ? {
          label: DisplayElement;
          type: "subset";
          values: string[];
        }
      : never)
  | (T extends { [name: string]: string }[]
      ? {
          label: DisplayElement;
          type: "upload-table";
          columns: string[];
        }
      : never)
  | {
      items: [DisplayElement, T][];
      label: DisplayElement;
      type: "select";
    }
  | {
      label: DisplayElement;
      type: "upload-json";
    }
  | {
      label: DisplayElement;
      labelMaker: (input: T) => DisplayElement;
      type: "select-dynamic";
      values: T[];
    };

type FetchOperation<T> =
  | (T extends number ? { type: "count"; filter: ActionFilter } : never)
  | (T extends string[]
      ?
          | { type: "action-ids"; filter: ActionFilter }
          | { type: "action-tags"; filter: ActionFilter }
      : never)
  | (T extends unknown[] ? FetchOperationList<T[0]> : never)
  | (T extends unknown[] ? FetchOperationFlatten<T[0]> : never)
  | (T extends [unknown, unknown][]
      ? FetchOperationDictionary<T[0][0], T[0][1]>
      : never)
  | { type: "constant"; name: string }
  | { type: "function"; name: string; args: any[] }
  | {
      type: "refiller";
      script: string;
      compare: (a: any, b: any) => number;
      fakeRefiller: { export_to_meditation: FakeRefillerParameters };
      fakeConstants: { [name: string]: FakeConstant };
    };

type FetchOperationList<T> = {
  type: "list";
  operations: FetchOperation<T>[];
  compare: (a: T, b: T) => number;
};
type FetchOperationFlatten<T> = {
  type: "flatten";
  operations: FetchOperation<T[]>[];
  compare: (a: T, b: T) => number;
};
type FetchOperationDictionary<K, V> = {
  type: "dictionary";
  operations: { key: K; value: FetchOperation<V> }[];
  compare: (a: K, b: K) => number;
};
type InformationNested = Information[] | InformationNested[];

type StatusInidcator = "GOOD" | "FAILED" | "WAITING" | "UNKNOWN";

type Wizard =
  | WizardFetch<unknown>
  | WizardForm<unknown>
  | WizardChoice
  | WizardFork<unknown>
  | WizardStatus;

interface WizardChoice {
  type: "choice";
  choices: { [name: string]: WizardStep };
}

interface WizardFetch<T> {
  type: "fetch";
  parameters: { [P in keyof T]: FetchOperation<T[P]> };
  processor: WizardDataStep<T>;
}

interface WizardForm<T> {
  type: "form";
  parameters: { [P in keyof T]: FormElement<T[P]> };
  processor: WizardDataStep<T>;
}

interface WizardFork<T> {
  type: "fork";
  processor: WizardDataStep<T>;
  items: { title: string; extra: T }[];
}

interface WizardStatus {
  type: "status";
  status: boolean;
}

export type WizardNext = {
  information: InformationNested;
  then: Wizard | null;
};

export type WizardStep = () => WizardNext;
export type WizardDataStep<T> = (input: T) => WizardNext;

const endings = [
  "Thank you for coming on this journey.",
  "Hopefully, these are the answers you need.",
  "The answers you need are not always the ones you seek.",
  "Have you tried rebooting?",
];

const meditations: GuidedMeditation[] = [];

export function register(name: string, wizard: () => WizardNext) {
  meditations.push({
    name: name,
    start: (status, filenameFormatter, exportSearches) => {
      const { information, then } = wizard();
      status.statusChanged("UNKNOWN");
      return [
        information
          .flat(Number.MAX_VALUE)
          .map((i) => renderInformation(i, filenameFormatter, exportSearches)),
        then == null
          ? "Well, that was fast."
          : renderWizard(then, status, filenameFormatter, exportSearches),
      ];
    },
  });
}
export function iconForStatus(input: StatusInidcator): UIElement {
  switch (input) {
    case "FAILED":
      return { type: "icon", icon: "x-circle-fill" };
    case "GOOD":
      return { type: "icon", icon: "check-circle-fill" };
    case "WAITING":
      return { type: "icon", icon: "hourglass" };
    default:
      return { type: "icon", icon: "question-circle-fill" };
  }
}
export function initialiseMeditationDash(
  oliveFiles: string[],
  exportSearches: ExportSearchCommand[]
): void {
  if (meditations.length) {
    const status = singleState(iconForStatus);
    const filenameFormatter = commonPathPrefix(oliveFiles);
    const { model, ui } = singleState((input: GuidedMeditation | null) =>
      input == null
        ? blank()
        : input.start(status.model, filenameFormatter, exportSearches)
    );
    const selectors = radioSelector(
      { type: "icon", icon: "signpost" },
      { type: "icon", icon: "signpost-fill" },
      model,
      ...meditations
    );
    setRootDashboard("meditationdash", [
      helpArea("meditation"),
      "Start a journey:",
      br(),
      tableFromRows(
        meditations.map((meditation, index) =>
          tableRow(
            null,
            { contents: meditation.name },
            { contents: selectors[index] }
          )
        )
      ),
      br(),
      "Meditation status: ",
      status.ui,
      ui,
    ]);
  } else {
    setRootDashboard("meditationdash", [
      { type: "icon", icon: "flower3" },
      "No meditations found. ",
      helpArea("meditation"),
      br(),
      "To add new meditations, consult the ",
      link(
        "https://oicr-gsi.github.io/shesmu/guided-meditations.html",
        "Guided Meditations Guide"
      ),
      ".",
    ]);
  }
}
function buildFetch<T>(
  wizard: WizardFetch<T>,
  status: StatefulModel<StatusInidcator>,
  model: StatefulModel<WizardNext | null>
): UIElement {
  const { ui: fetchUi, model: fetchModel } = pane("medium");
  fetchModel.statusWaiting();
  const fetchOutput = {} as T;
  const promises: Promise<void>[] = [];
  const refresh = () => {
    status.statusChanged("WAITING");
    for (const k of Object.keys(wizard.parameters)) {
      const key = k as keyof T;
      const parameter = wizard.parameters[key];
      if (parameter === undefined) {
        continue;
      }
      promises.push(makeFetchEntry(key, parameter, fetchOutput));
    }
    Promise.all(promises)
      .then(() => {
        status.statusChanged("UNKNOWN");
        fetchModel.statusChanged([
          button(
            [{ type: "icon", icon: "arrow-repeat" }, "Refresh"],
            "Reload this data and start again from this point.",
            refresh
          ),
          "Data fetched from:",
          br(),
          tableFromRows(
            Object.values(wizard.parameters)
              .flatMap((parameter) =>
                makeFetchDescription(parameter as FetchOperation<unknown>)
              )
              .map((c) => tableRow(null, c))
          ),
        ]);
        model.statusChanged(wizard.processor(fetchOutput));
      })
      .catch((e) => fetchModel.statusFailed(`${e}`, refresh));
  };
  refresh();
  return [fetchUi, br()];
}
function buildForm<T>(
  wizard: WizardForm<T>,
  model: StatefulModel<WizardNext | null>
): UIElement {
  const output = {} as T;
  try {
    const fields = Object.keys(wizard.parameters).map((k) => {
      const key = k as keyof T;
      return makeFormEntry(key, wizard.parameters[key], output);
    });

    return [
      tableFromRows(
        fields.map((f) =>
          tableRow(null, { contents: f.label }, { contents: f.field.ui })
        )
      ),
      button(
        { type: "icon", icon: "arrow-right-circle" },
        "Continue to next step",
        () => {
          for (const field of fields) {
            field.update();
          }

          model.statusChanged(wizard.processor(output));
        }
      ),
      br(),
    ];
  } catch (e) {
    return e.toString();
  }
}
function makeFetchEntry<T, K extends keyof T>(
  key: K,
  parameter: FetchOperation<T[K]>,
  fetchOutput: T
): Promise<void> {
  return makeFetchPromise(parameter).then((result) => {
    fetchOutput[key] = result;
  });
}

function makeFetchPromise<T>(parameter: FetchOperation<T>): Promise<T> {
  switch (parameter.type) {
    case "action-ids":
      return fetchAsPromise("action-ids", [parameter.filter]).then(
        (ids) => (ids.sort() as unknown) as T
      );
    case "action-tags":
      return fetchAsPromise("tags", [parameter.filter]).then(
        (ids) => (ids.sort() as unknown) as T
      );
    case "constant":
      return fetchAsPromise("constant", parameter.name).then((constant) => {
        if (constant.error) {
          throw new Error(constant.error);
        }
        return constant.value;
      });
    case "count":
      return fetchAsPromise("count", [parameter.filter]).then(
        (count) => (count as unknown) as T
      );
    case "function":
      return fetchAsPromise("function", {
        name: parameter.name,
        args: parameter.args,
      }).then((value) => {
        if (value.error) {
          throw new Error(value.error);
        }
        return value.value;
      });
    case "refiller":
      return fetchAsPromise("simulate", {
        allowUnused: true,
        fakeActions: {},
        fakeRefillers: parameter.fakeRefiller,
        fakeConstants: parameter.fakeConstants,
        script: parameter.script,
        dryRun: false,
        readStale: false,
      }).then((result) => {
        const items = result.refillers?.["export_to_meditation"];
        if (items) {
          return (setNew(items, parameter.compare) as unknown) as T;
        } else {
          throw new Error(result.errors.join("\n") || "Unknown error.");
        }
      });
    case "list":
      return (makeFetchPromiseList(parameter) as unknown) as Promise<T>;
    case "flatten":
      return (makeFetchPromiseFlatten(parameter) as unknown) as Promise<T>;
    case "dictionary":
      return makeFetchPromiseDict(parameter).then((r) => (r as unknown) as T);
  }
}

function makeFetchDescription<T>(parameter: FetchOperation<T>): TableCell[] {
  switch (parameter.type) {
    case "action-ids":
      return [{ contents: "Action Identifiers" }];
    case "action-tags":
      return [{ contents: "Action Tags" }];
    case "constant":
      return [{ contents: ["Constant: ", mono(parameter.name)] }];
    case "count":
      return [{ contents: "Action Count" }];
    case "function":
      return [{ contents: ["Function: ", mono(parameter.name)] }];
    case "refiller":
      return [{ contents: "Olive Data" }];
    case "list":
      return parameter.operations.flatMap((op) => makeFetchDescription(op));
    case "flatten":
      return parameter.operations.flatMap((op) => makeFetchDescription(op));
    case "dictionary":
      return parameter.operations.flatMap(({ key, value }) =>
        makeFetchDescription(value)
      );
  }
}

function makeFetchPromiseDict<K, V>(
  parameter: FetchOperationDictionary<K, V>
): Promise<[K, V][]> {
  return Promise.all(
    parameter.operations.map(({ key, value }) =>
      makeFetchPromise(value).then((result) => [key, result] as [K, V])
    )
  ).then((items) => dictNew(items, parameter.compare));
}

function makeFetchPromiseList<T>(parameter: FetchOperationList<T>) {
  return Promise.all(parameter.operations.map(makeFetchPromise)).then((items) =>
    setNew(items, parameter.compare)
  );
}
function makeFetchPromiseFlatten<T>(parameter: FetchOperationFlatten<T>) {
  return Promise.all(parameter.operations.map(makeFetchPromise)).then((items) =>
    setNew(items.flat(1), parameter.compare)
  );
}
function makeFormEntry<T, K extends keyof T>(
  key: K,
  definition: FormElement<T[K]>,
  output: T
) {
  let field: InputField<T[K]>;
  if (definition === undefined) {
    throw new Error(`Illegal configuration. Key ${key} is not found.`);
  }
  if (definition.type == "text") {
    field = (inputText() as unknown) as InputField<T[K]>;
  } else if (definition.type == "number") {
    field = (inputNumber(0, 0, null) as unknown) as InputField<T[K]>;
  } else if (definition.type == "offset") {
    const units = temporaryState([3600_000, "hours"] as [number, string]);
    const offset = inputNumber(0, 0, null);
    field = {
      ui: [
        offset.ui,
        dropdown(
          ([, n]) => n,
          (unit) => unit == units.get(),
          units,
          null,
          [1, "milliseconds"],
          [1000, "seconds"],
          [60000, "minutes"],
          [3600000, "hours"],
          [86400000, "days"]
        ),
      ],
      get value(): T[K] {
        return ((offset.value * units.get()[0]) as unknown) as T[K];
      },
      enabled: true,
    };
  } else if (definition.type == "boolean") {
    field = (inputCheckbox("", false) as unknown) as InputField<T[K]>;
  } else if (definition.type == "select") {
    const value = temporaryState<[DisplayElement, T[K]]>(definition.items[0]);
    field = {
      set enabled(_: boolean) {
        // Do nothing.
      },
      ui: dropdown(
        ([label]) => label,
        (d) => d == definition.items[0],
        value,
        null,
        ...definition.items
      ),
      get value(): T[K] {
        return value.get()[1];
      },
    };
  } else if (definition.type == "select-dynamic") {
    if (definition.values.length == 0) {
      throw new Error("Drop down with no items to select.");
    }
    const value = temporaryState<T[K]>(definition.values[0]);
    field = {
      set enabled(_: boolean) {
        // Do nothing.
      },
      ui: dropdown(
        definition.labelMaker,
        null,
        value,
        null,
        ...definition.values
      ),
      get value(): T[K] {
        return value.get();
      },
    };
  } else if (definition.type == "subset") {
    const selected = new Set<string>();
    const selectedDisplay = singleState((input: string[]) =>
      input.length == 0
        ? "No items selected."
        : input.map((item) =>
            buttonAccessory(item, "Remove this item.", () => {
              selected.delete(item);
              selectedDisplay.model.statusChanged(
                Array.from(selected.values())
              );
            })
          )
    );
    selectedDisplay.model.statusChanged([]);
    field = {
      set enabled(_: boolean) {
        // Do nothing.
      },
      ui: [
        button({ type: "icon", icon: "plus-circle" }, "Select items", () =>
          pickFromSet(
            (definition.values || []).filter((i) => !selected.has(i)),
            (result) => {
              result.forEach((v) => selected.add(v));
              selectedDisplay.model.statusChanged(
                Array.from(selected.values())
              );
            },
            (item) => ({ label: item, title: "" }),
            (item, keywords) =>
              keywords.every((k) => item.toLowerCase().indexOf(k) != -1),
            false
          )
        ),
        selectedDisplay.ui,
      ],
      get value(): T[K] {
        return (Array.from(selected.values()) as unknown) as T[K];
      },
    };
  } else if (definition.type == "upload-json") {
    let value: any = null;
    field = {
      set enabled(_: boolean) {
        // Do nothing.
      },
      ui: button(
        { type: "icon", icon: "file-earmark-arrow-up" },
        "Upload JSON data",
        () =>
          loadFile((_, data) => {
            try {
              value = JSON.parse(data);
            } catch (e) {
              dialog((c) => ["Failed to parse JSON:", br(), e.toString()]);
            }
          })
      ),
      get value(): T[K] {
        return (value as unknown) as T[K];
      },
    };
  } else if (definition.type == "upload-table") {
    const value: { [name: string]: string }[] = [];
    const columns = definition.columns;
    const tsvDisplay = singleState(
      (input: {
        columns: string[];
        data: string;
        delimiter: string;
        header: boolean;
      }) => {
        const raw = input.data.split(/\r?\n/).filter((x) => !x.match(/^\s*$/));
        value.length = 0;
        for (let i = input.header ? 1 : 0; i < raw.length; i++) {
          const row = Object.fromEntries(input.columns.map((c) => [c, ""]));
          let state = 0;
          let index = 0;
          let last = 0;
          while (index < raw[i].length && last < input.columns.length) {
            switch (state) {
              case 0: // Try to detect space-padded start of data
                if (raw[i].charAt(index).match(/\s/)) {
                  // Keep eating spaces
                } else if (raw[i].charAt(index) == input.delimiter) {
                  last++;
                } else if (raw[i].charAt(index) == '"') {
                  state = 2;
                } else {
                  state = 1;
                  row[input.columns[last]] += raw[i].charAt(index);
                }
                break;
              case 1: // Copy unquoted data
                if (raw[i].charAt(index) == input.delimiter) {
                  last++;
                  state = 0;
                } else {
                  row[input.columns[last]] += raw[i].charAt(index);
                }
                break;

              case 2: // Copy quoted data
                if (raw[i].charAt(index) == '"') {
                  state = 3;
                } else {
                  row[input.columns[last]] += raw[i].charAt(index);
                }
                break;
              case 3: // Absorb lead out
                if (raw[i].charAt(index) == input.delimiter) {
                  last++;
                  state = 0;
                } else if (raw[i].charAt(index).match(/\s/)) {
                  // Eat spaces
                } else {
                  return `Invalid data detected in ${
                    input.columns[last]
                  } on line ${i + 1}`;
                }
            }
            index++;
          }
          value.push(row);
        }

        return value.length == 0
          ? "No rows provided."
          : [
              "Preview:",
              br(),
              table(
                value,
                ...columns.map(
                  (k) =>
                    [k, (r: { [name: string]: string }) => r[k]] as [
                      string,
                      (row: object) => string
                    ]
                )
              ),
            ];
      }
    );
    const tsvKnobs = individualPropertyModel(tsvDisplay.model, {
      columns,
      data: "",
      delimiter: ",",
      header: true,
    });
    type Delimiter = { delimiter: string; name: string; description: string };
    field = {
      set enabled(_: boolean) {
        // Do nothing.
      },
      ui: [
        button(
          { type: "icon", icon: "file-earmark-arrow-up" },
          "Upload tabular data",
          () => loadFile((_, data) => tsvKnobs.data.statusChanged(data))
        ),
        dropdown(
          (input, selected) => (selected ? input.name : input.description),
          null,
          mapModel(tsvKnobs.delimiter, (input: Delimiter) => input.delimiter),
          null,
          { delimiter: ",", name: "CSV", description: "Comma-delimited (,)" },
          { delimiter: "\t", name: "TSV", description: "Tab-delimited (\\t)" },
          {
            delimiter: ";",
            name: "SSV",
            description: "Semicolon-delimited (;)",
          }
        ),

        dropdown(
          (input, selected) => {
            if (selected) {
              return input
                ? { type: "icon", icon: "grid-3x2" }
                : { type: "icon", icon: "grid-3x3" };
            } else {
              return input
                ? [{ type: "icon", icon: "grid-3x2" }, "First Row is Header"]
                : [{ type: "icon", icon: "grid-3x3" }, "First Row is Data"];
            }
          },
          null,
          tsvKnobs.header,
          null,
          true,
          false
        ),
        br(),
        dragOrder(tsvKnobs.columns, (x) => x, ...columns),
        br(),
        tsvDisplay.ui,
      ],
      get value(): T[K] {
        return (value as unknown) as T[K];
      },
    };
  } else {
    throw new Error(`Unsupported definition type ${definition.type}.`);
  }
  return {
    field: field,
    label: definition.label,
    update: () => {
      output[key] = field.value;
    },
  };
}

export function renderInformation(
  info: Information,
  filenameFormatter: FilenameFormatter,
  exportSearches: ExportSearchCommand[]
): UIElement {
  switch (info.type) {
    case "display":
      return info.contents;
    case "actions":
      const { ui: statsUi, model: statsModel } = actionStats(
        (...limits) => search.addPropertySearch(...limits),
        (typeName, start, end) => search.addRangeSearch(typeName, start, end),
        filenameFormatter,
        standardExports.concat(exportSearches)
      );
      const { actions, bulkCommands, model: actionsModel } = actionDisplay(
        exportSearches
      );
      const search = createSearch(
        temporaryState({}),
        combineModels(statsModel, actionsModel),
        false,
        filenameFormatter,
        []
      );
      search.model.statusChanged([info.filter]);
      return [
        br(),
        search.buttons,
        bulkCommands,
        br(),
        search.entryBar,
        tabs(
          { name: "Overview", contents: statsUi },
          { name: "Actions", contents: actions }
        ),
      ];
    case "alerts":
      const alertModel = sharedPane(
        "main",
        (alerts: PrometheusAlert[]) => {
          const { toolbar, main } = alertNavigator(
            alerts,
            prometheusAlertHeader,
            temporaryState<AlertFilter<RegExp>[]>([]),
            prometheusAlertLocation(filenameFormatter)
          );
          return { toolbar: toolbar, main: main };
        },
        "toolbar",
        "main"
      );
      const alertRefresh = refreshable(
        "queryalerts",
        mapModel(alertModel.model, (alerts) => alerts || []),
        false
      );
      alertRefresh.statusChanged(info.filter);
      return [
        br(),
        button(
          [{ type: "icon", icon: "arrow-repeat" }, "Refresh"],
          "Reload alerts.",
          () => alertRefresh.reload()
        ),
        alertModel.components.toolbar,
        br(),
        alertModel.components.main,
      ];
    case "download":
      return button(
        [
          { type: "icon", icon: "file-earmark-arrow-down" },
          "Download ",
          info.file,
        ],
        "Download this file.",
        () =>
          saveFile(
            info.isJson
              ? JSON.stringify(info.contents, null, 2)
              : info.contents,
            info.mimetype,
            info.file
          )
      );
    case "simulation":
      const simulationPane = singleState(
        (response: SimulationResponse | null) =>
          tabs(...renderResponse(response)),
        false
      );
      const simulationRefresh = refreshable(
        "simulate",
        simulationPane.model,
        false
      );
      simulationRefresh.statusChanged({
        allowUnused: true,
        fakeActions: {},
        fakeConstants: info.parameters,
        fakeRefillers: info.fakeRefillers,
        dryRun: false,
        readStale: false,
        script: info.script,
      });
      return [
        br(),
        button(
          [{ type: "icon", icon: "arrow-repeat" }, "Refresh"],
          "Rerun simulation.",
          () => simulationRefresh.reload()
        ),
        br(),
        simulationPane.ui,
      ];
    case "simulation-existing":
      const simulationExistingPane = singleState(
        (response: SimulationResponse | null) =>
          tabs(...renderResponse(response)),
        false
      );
      const simulationExistingRefresh = refreshable(
        "simulate-existing",
        simulationExistingPane.model,
        false
      );
      simulationExistingRefresh.statusChanged({
        fakeConstants: info.parameters,
        fileName: info.fileName,
        readStale: false,
      });
      return [
        br(),
        button(
          [{ type: "icon", icon: "arrow-repeat" }, "Refresh"],
          "Rerun simulation.",
          () => simulationExistingRefresh.reload()
        ),
        br(),
        simulationExistingPane.ui,
      ];
    case "table":
      return table(
        info.data,
        ...info.headers.map(
          (name, index) =>
            [name, (row) => row[index]] as [
              UIElement,
              (row: DisplayElement[]) => DisplayElement
            ]
        )
      );
    default:
      return "Unknown information.";
  }
}

export function renderWizard(
  wizard: Wizard,
  status: StatefulModel<StatusInidcator>,
  filenameFormatter: FilenameFormatter,
  exportSearches: ExportSearchCommand[]
): UIElement {
  const childDisplay = singleState((next: WizardNext | null) => {
    if (next == null) {
      return blank();
    }
    let final: UIElement = null;
    if (next.then == null) {
      final = [
        br(),
        { type: "icon", icon: "flower2" },
        endings[Math.floor(Math.random() * endings.length)],
      ];
    } else {
      final = renderWizard(
        next.then,
        status,
        filenameFormatter,
        exportSearches
      );
    }

    return [
      next.information
        .flat(Number.MAX_VALUE)
        .map((i) => renderInformation(i, filenameFormatter, exportSearches)),
      final,
    ];
  });
  let inner: UIElement = "Unknown Step";
  switch (wizard.type) {
    case "choice":
      const selectors = radioSelector(
        { type: "icon", icon: "arrow-down-circle" },
        { type: "icon", icon: "arrow-down-circle-fill" },
        mapModel(childDisplay.model, (step: WizardStep | null) => {
          if (step == null) {
            return null;
          } else {
            try {
              return step();
            } catch (e) {
              dialog((_) => e.toString());
              return null;
            }
          }
        }),
        ...Object.values(wizard.choices)
      );
      inner = tableFromRows(
        Object.keys(wizard.choices).map((choice, index) =>
          tableRow(null, { contents: choice }, { contents: selectors[index] })
        )
      );
      break;
    case "form":
      inner = buildForm(wizard, childDisplay.model);
      break;
    case "fetch":
      inner = buildFetch(wizard, status, childDisplay.model);
      break;
    case "status":
      status.statusChanged(wizard.status ? "GOOD" : "FAILED");
      inner = wizard.status
        ? [{ type: "icon", icon: "check-circle-fill" }, "This seems good."]
        : [{ type: "icon", icon: "x-circle-fill" }, "This seems bad."];
      break;
    case "fork":
      switch (wizard.items.length) {
        case 0:
          inner = [
            br(),
            { type: "icon", icon: "cone-striped" },
            "Normally the road forks here, but it seems this is a cul-de-sac.",
          ];
          break;
        case 1:
          const { information, then } = wizard.processor(wizard.items[0].extra);
          inner = [
            information
              .flat(Number.MAX_VALUE)
              .map((i) =>
                renderInformation(i, filenameFormatter, exportSearches)
              ),
            then == null
              ? [
                  br(),
                  { type: "icon", icon: "flower2" },
                  endings[Math.floor(Math.random() * endings.length)],
                ]
              : renderWizard(then, status, filenameFormatter, exportSearches),
          ];
          break;
        default:
          const combinedStatuses = reducingModel(
            status,
            (value: StatusInidcator, accumulator: StatusInidcator | null) => {
              if (accumulator == null) {
                return value;
              }
              if (value == "FAILED" || accumulator == "FAILED") {
                return "FAILED";
              }
              if (value == "WAITING" || accumulator == "WAITING") {
                return "WAITING";
              }
              if (value == "UNKNOWN" || accumulator == "UNKNOWN") {
                return "UNKNOWN";
              }
              return "GOOD";
            },
            "GOOD",
            wizard.items.length
          );
          inner = [
            tabs(
              ...wizard.items.map(({ title, extra }, index) => {
                const { information, then } = wizard.processor(extra);
                const childStatus = singleState(iconForStatus);
                return {
                  name: [title, " ", childStatus.ui],
                  contents: [
                    information
                      .flat(Number.MAX_VALUE)
                      .map((i) =>
                        renderInformation(i, filenameFormatter, exportSearches)
                      ),
                    then == null
                      ? [
                          br(),
                          { type: "icon", icon: "flower2" } as UIElement,
                          "This branch bears no more fruit.",
                        ]
                      : renderWizard(
                          then,
                          combineModels(
                            childStatus.model,
                            combinedStatuses[index]
                          ),
                          filenameFormatter,
                          exportSearches
                        ),
                  ],
                };
              })
            ),
            br(),
          ];
      }
      break;
  }

  return [hr(), inner, childDisplay.ui];
}
