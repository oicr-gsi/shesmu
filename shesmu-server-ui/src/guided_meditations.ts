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
  UIElement,
  blank,
  br,
  button,
  buttonAccessory,
  dialog,
  dropdown,
  hr,
  inputCheckbox,
  inputNumber,
  inputText,
  link,
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
import { fetchAsPromise, refreshable, saveFile } from "./io.js";
import { actionStats } from "./stats.js";
import {
  FakeRefillerParameters,
  SimulationResponse,
  renderResponse,
  FakeConstant,
} from "./simulation.js";
import {
  FilenameFormatter,
  combineModels,
  commonPathPrefix,
  mapModel,
} from "./util.js";
import { setNew } from "./runtime.js";

interface GuidedMeditation {
  name: string;
  start: (
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

type Parameters<T> = {
  [P in keyof T]?:
    | (T[P] extends string
        ? {
            label: DisplayElement;
            type: "text";
          }
        : never)
    | (T[P] extends number
        ? {
            label: DisplayElement;
            type: "number" | "offset";
          }
        : never)
    | (T[P] extends boolean
        ? {
            label: DisplayElement;
            type: "boolean";
          }
        : never)
    | (T[P] extends string[]
        ? {
            label: DisplayElement;
            type: "subset";
            values: string[];
          }
        : never)
    | {
        items: [DisplayElement, T[P]][];
        label: DisplayElement;
        type: "select";
      };
};

type FetchOperation<T> = {
  [P in keyof T]?:
    | { type: "count"; filter: ActionFilter }
    | { type: "action-ids"; filter: ActionFilter }
    | { type: "action-tags"; filter: ActionFilter }
    | {
        type: "refiller";
        script: string;
        compare: (a: any, b: any) => number;
        fakeRefiller: { export_to_meditation: FakeRefillerParameters };
        fakeConstants: { [name: string]: FakeConstant };
      };
};
type InformationNested = Information[] | InformationNested[];

type Wizard<T> =
  | WizardFetch<T>
  | WizardForm<T>
  | WizardChoice<T>
  | WizardFork<T>;

interface WizardChoice<T> {
  type: "choice";
  choices: { [name: string]: WizardStep<T> };
}

interface WizardFetch<T> {
  type: "fetch";
  parameters: FetchOperation<T>;
  processor: WizardStep<T>;
}

interface WizardForm<T> {
  type: "form";
  parameters: Parameters<T>;
  processor: WizardStep<T>;
}

interface WizardFork<T> {
  type: "fork";
  processor: WizardStep<T>;
  items: { title: string; extra: Partial<T> }[];
}

export type WizardNext<T> = {
  information: InformationNested;
  then: Wizard<T> | null;
};

export type WizardStep<T> = (input: Partial<T>) => WizardNext<T>;

const endings = [
  "Thank you for coming on this journey.",
  "Hopefully, these are the answers you need.",
  "The answers you need are not always the ones you seek.",
  "Have you tried rebooting?",
];

const meditations: GuidedMeditation[] = [];

export function register<T>(name: string, wizard: WizardStep<T>) {
  meditations.push({
    name: name,
    start: (filenameFormatter, exportSearches) => {
      const { information, then } = wizard({});
      return [
        information
          .flat(Number.MAX_VALUE)
          .map((i) => renderInformation(i, filenameFormatter, exportSearches)),
        then == null
          ? "Well, that was fast."
          : renderWizard({}, then, filenameFormatter, exportSearches),
      ];
    },
  });
}
export function initialiseMeditationDash(
  oliveFiles: string[],
  exportSearches: ExportSearchCommand[]
): void {
  if (meditations.length) {
    const filenameFormatter = commonPathPrefix(oliveFiles);
    const { model, ui } = singleState((input: GuidedMeditation | null) =>
      input == null ? blank() : input.start(filenameFormatter, exportSearches)
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
        "Guided Medidations Guide"
      ),
      ".",
    ]);
  }
}

function inputProperty<T, K extends keyof T>(
  key: K,
  definition: Parameters<T>[K],
  output: Partial<T>
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
        (d) => d == definition.items?.[0],
        value,
        null,
        ...definition.items
      ),
      get value(): T[K] {
        return value.get()[1];
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

export function renderWizard<T>(
  state: Partial<T>,
  wizard: Wizard<T>,
  filenameFormatter: FilenameFormatter,
  exportSearches: ExportSearchCommand[]
): UIElement {
  const childDisplay = singleState(
    (state: { state: Partial<T>; next: WizardNext<T> } | null) => {
      if (state == null) {
        return blank();
      }
      let final: UIElement = null;
      if (state.next.then == null) {
        final = [
          br(),
          { type: "icon", icon: "flower2" },
          endings[Math.floor(Math.random() * endings.length)],
        ];
      } else {
        final = renderWizard(
          state.state,
          state.next.then,
          filenameFormatter,
          exportSearches
        );
      }

      return [
        state.next.information
          .flat(Number.MAX_VALUE)
          .map((i) => renderInformation(i, filenameFormatter, exportSearches)),
        final,
      ];
    }
  );
  let inner: UIElement = "Unknown Step";
  switch (wizard.type) {
    case "choice":
      const selectors = radioSelector(
        { type: "icon", icon: "arrow-down-circle" },
        { type: "icon", icon: "arrow-down-circle-fill" },
        mapModel(childDisplay.model, (step: WizardStep<T> | null) => {
          if (step == null) {
            return null;
          } else {
            const newState = { ...state };
            try {
              return { state: newState, next: step(newState) };
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
      const output: Partial<T> = { ...state };
      const fields = Object.keys(wizard.parameters).map((key) => {
        const k = key as keyof T;
        return inputProperty(k, wizard.parameters[k], output);
      });

      inner = [
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

            childDisplay.model.statusChanged({
              state: output,
              next: wizard.processor(output),
            });
          }
        ),
        br(),
      ];
      break;
    case "fetch":
      const { ui: fetchUi, model: fetchModel } = pane("medium");
      inner = [fetchUi, br()];
      fetchModel.statusWaiting();
      const fetchOutput: Partial<T> = { ...state };
      const promises: Promise<void>[] = [];
      const refresh = () => {
        for (const key of Object.keys(wizard.parameters)) {
          const k = key as keyof T;
          const parameter = wizard.parameters[k];
          if (parameter === undefined) {
            continue;
          }
          switch (parameter.type) {
            case "action-ids":
              promises.push(
                fetchAsPromise("action-ids", [parameter.filter]).then((ids) => {
                  fetchOutput[k] = (ids.sort() as unknown) as T[keyof T];
                })
              );
              break;
            case "action-tags":
              promises.push(
                fetchAsPromise("tags", [parameter.filter]).then((ids) => {
                  fetchOutput[k] = (ids.sort() as unknown) as T[keyof T];
                })
              );
              break;
            case "count":
              promises.push(
                fetchAsPromise("count", [parameter.filter]).then((count) => {
                  fetchOutput[k] = (count as unknown) as T[keyof T];
                })
              );
              break;
            case "refiller":
              promises.push(
                fetchAsPromise("simulate", {
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
                    fetchOutput[k] = (setNew(
                      items,
                      parameter.compare
                    ) as unknown) as T[keyof T];
                  } else {
                    throw new Error(
                      result.errors.join("\n") || "Unknown error."
                    );
                  }
                })
              );
              break;
          }
        }
        Promise.all(promises)
          .then(() => {
            fetchModel.statusChanged(
              button(
                [{ type: "icon", icon: "arrow-repeat" }, "Refresh"],
                "Reload this data and start again from this point.",
                refresh
              )
            );
            childDisplay.model.statusChanged({
              state: fetchOutput,
              next: wizard.processor(fetchOutput),
            });
          })
          .catch((e) => fetchModel.statusFailed(`${e}`, refresh));
      };
      refresh();
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
          const newState = { ...state, ...wizard.items[0].extra };
          const { information, then } = wizard.processor(newState);
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
              : renderWizard(newState, then, filenameFormatter, exportSearches),
          ];
          break;
        default:
          inner = [
            tabs(
              ...wizard.items.map(({ title, extra }) => {
                const newState = { ...state, ...extra };
                const { information, then } = wizard.processor(newState);
                return {
                  name: title,
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
                          newState,
                          then,
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
