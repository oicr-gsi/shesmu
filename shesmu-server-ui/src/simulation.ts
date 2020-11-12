import { Alert, alertNavigator, AlertFilter } from "./alert.js";
import { infoForProduces, OliveType } from "./olive.js";
import { parseDescriptor } from "./definitions.js";
import * as valueParser from "./parser.js";
import {
  loadFile,
  locallyStored,
  locallyStoredString,
  mutableLocalStore,
  refreshable,
  saveClipboard,
  saveClipboardJson,
  saveFile,
  fetchAsPromise,
  fetchJsonWithBusyDialog,
} from "./io.js";
import {
  Tab,
  UIElement,
  blank,
  br,
  butter,
  button,
  buttonAccessory,
  checkKey,
  collapsible,
  dialog,
  dropdown,
  group,
  header,
  inputText,
  inputTextArea,
  italic,
  mono,
  multipaneState,
  paginatedList,
  preformatted,
  setRootDashboard,
  singleState,
  table,
  tabs,
  tabsModel,
  tagList,
  temporaryState,
  text,
  tile,
  svgFromStr,
  spotCounter,
  link,
} from "./html.js";
import {
  MutableStore,
  combineModels,
  countIterable,
  formatTimeSpan,
  mapModel,
  matchKeywordInArbitraryData,
  mutableStoreWatcher,
  reducingModel,
  validIdentifier,
} from "./util.js";
import { specialImports } from "./actions.js";
import { helpArea } from "./help.js";
import {
  compressToEncodedURIComponent,
  decompressFromEncodedURIComponent,
} from "./lz-string.js";

/**
 * An exported definition from a simulated script
 */
type Export = ExportConstant | ExportDefine | ExportFunction;

/**
 * A constant definition exported by a simulated script.
 */
interface ExportConstant {
  type: "constant";
  /** The exported name */
  name: string;
  /** The type as a Shesmu descriptor */
  returns: string;
}
/**
 * An olive definition exported by a simulated script.
 */
interface ExportDefine {
  type: "define";
  /** The exported name */
  name: string;
  /** The input format consume by this olive*/
  inputFormat: string;
  /**
   * The output variables and their types
   */
  output: { [name: string]: string };
  /** The parameter types as Shesmu descriptors */
  parameters: string[];
  /** If true, the definition is returning an “unmodified” data stream.
   *
   * The data stream may actaully be modified through a <tt>Flatten</tt> or <tt>Require</tt> operation, but it is close enough to the original format to still compute signatures on.
   */
  isRoot: boolean;
}
/**
 * A function definition exported by a simulated script
 */
interface ExportFunction {
  type: "function";
  /** The exported name */
  name: string;
  /** The type as a Shesmu descriptor */
  returns: string;
  /** The parameter types as Shesmu descriptors */
  parameters: string[];
}
/**
 * A definition for a fake action to be used in simulation
 */
export interface FakeActionDefinition {
  name?: string;
  parameters?: FakeActionParameters;
  errors: string[];
}
/**
 * The parameters used by a fake action
 */
export type FakeActionParameters = {
  [name: string]: { type: TypeInfo; required: boolean };
};

export type FakeConstant = {
  type: string;
  value: any;
};
export type FakeRefillerParameters = { [parameter: string]: string };
/**
 * A record produced by a refiller in simulation
 */
type RefillerRecord = { [s: string]: any };

/**
 * A simulated action
 */
interface SimulatedAction {
  locations: { line: number; column: number }[];
  name: string;
  parameters: { [name: string]: any };
  tags: string[];
}

/**
 * A simulated alert
 */
type SimulatedAlert = Alert<SimulatedLocation>;
/**
 * The location of an olive in the script being simulated
 */
interface SimulatedLocation {
  line: number;
  column: number;
}

/**
 * The olive information provided by the simulator
 *
 * This is different from the information provided for real olives.
 */
interface SimulatedOlive {
  diagram: string;
  line: number;
  syntax: string;
  description: string | null;
  produces: OliveType;
  column: number;
  duration: number;
}
export interface SimulationRequest {
  allowUnused: boolean;
  fakeActions: { [name: string]: FakeActionParameters };
  fakeConstants: { [name: string]: FakeConstant };
  fakeRefillers: { [name: string]: FakeRefillerParameters };
  dryRun: boolean;
  readStale: boolean;
  script: string;
}
/**
 * The response from the sever after attempting simulation
 */
export interface SimulationResponse {
  alerts?: SimulatedAlert[];
  exceptionThrown: boolean;
  exports?: Export[];
  errors: string[];
  bytecode?: string;
  overloadedInputs?: string[];
  dumpers?: { [name: string]: any[][] };
  olives?: SimulatedOlive[];
  refillers?: { [name: string]: RefillerRecord[] };
  overloadedServices?: string[];
  metrics?: string;
  actions?: SimulatedAction[];
}
export type TypeInfo = string | { [name: string]: TypeInfo };

async function importAction(
  store: MutableStore<string, FakeActionParameters>,
  name: string | null,
  data: string
): Promise<void> {
  for (const importReads of specialImports) {
    const result = await importReads(data, (format, type) =>
      fetchAsPromise("type", { value: type, format: format }).then(
        (v) => v.descriptor
      )
    );
    if (result) {
      if (result.errors.length) {
        dialog(() => result.errors.map((e) => text(e)));
      } else if (result.parameters) {
        const givenName = result.name || name;
        if (givenName) {
          store.set(givenName, result.parameters);
        } else {
          const parameters = result.parameters;
          dialog((close) => {
            const newName = inputText();
            return [
              "Save action as: ",
              newName.ui,
              br(),
              button("Add", "Save to fake action collection.", () => {
                if (validIdentifier.test(newName.value)) {
                  store.set(newName.value, parameters);
                  close();
                } else {
                  dialog(() => "This name isn't a valid Shesmu identifier.");
                }
              }),
            ];
          });
        }
      }
      return;
    }
  }
  dialog(() => "Cannot identify uploaded action format. Sorry.");
}
async function importRefiller(
  store: MutableStore<string, FakeRefillerParameters>,
  name: string | null,
  data: string
): Promise<void> {
  try {
    const body = JSON.parse(data);
    if (typeof body == "object") {
      for (const name of Object.keys(body)) {
        if (!validIdentifier.test(name)) {
          dialog(
            () => "Refiller parameter ${name} isn't a valid Shesmu identifier."
          );
        }
        body[name] = await fetchAsPromise("type", {
          value: JSON.stringify(body[name]),
          format: "shesmu::json_descriptor",
        }).then((v) => v.descriptor);
      }
      if (name) {
        store.set(name, body);
      } else {
        dialog((close) => {
          const newName = inputText();
          return [
            "Save action as: ",
            newName.ui,
            br(),
            button("Add", "Save to fake action collection.", () => {
              if (validIdentifier.test(newName.value)) {
                store.set(newName.value, body);
                close();
              } else {
                dialog(() => "This name isn't a valid Shesmu identifier.");
              }
            }),
          ];
        });
      }
    } else {
      dialog(() => "Refiller is missing valid name and parameters");
    }
  } catch (e) {
    dialog(() => "Refiller is not valid JSON.");
  }
}

export function initialiseSimulationDashboard(
  ace: AceAjax.Ace,
  container: HTMLElement,
  completeSound: HTMLAudioElement,
  scriptName: string | null,
  scriptBody: string | null,
  decodeBody: boolean,
  typeFormats: { [format: string]: string }
) {
  let fileName = scriptName || "unknown.shesmu";
  let fakeActionDefinitions: MutableStore<
    string,
    FakeActionParameters
  > = new Map();
  let fakeConstantDefinitions: MutableStore<string, FakeConstant> = new Map();
  let fakeRefillerDefinitions: MutableStore<
    string,
    { [parameter: string]: string }
  > = new Map();

  const { ui: fakeActionsUi, model: fakeActionsModel } = singleState(
    (items: Iterable<[string, FakeActionParameters]>) =>
      table(
        Array.from(items),
        ["Name", ([name, _declaration]) => name],
        [
          "",
          ([name, declaration]) => [
            buttonAccessory(
              [{ type: "icon", icon: "clipboard" }, "Copy"],
              "Copy action definition to clipboard.",
              () =>
                saveClipboardJson({
                  name: name,
                  parameters: Object.entries(declaration).map(
                    ([paramName, parameter]) => ({
                      name: paramName,
                      required: parameter.required,
                      type: parameter.type,
                    })
                  ),
                })
            ),
            buttonAccessory(
              [{ type: "icon", icon: "download" }, "Download"],
              "Download action definition.",
              () =>
                saveFile(
                  JSON.stringify({
                    kind: "action",
                    name: name,
                    parameters: Object.entries(declaration).map(
                      ([paramName, parameter]) => ({
                        name: paramName,
                        required: parameter.required,
                        type: parameter.type,
                      })
                    ),
                  }),
                  "application/json",
                  name + ".actiondef"
                )
            ),
            buttonAccessory(
              [{ type: "icon", icon: "pencil" }, "Rename"],
              "Rename action definition.",
              () =>
                dialog((close) => {
                  const rename = inputText(name);
                  return [
                    "Rename action to: ",
                    rename.ui,
                    br(),
                    button("Rename", "Rename action.", () => {
                      if (validIdentifier.test(rename.value)) {
                        close();
                        fakeActionDefinitions.delete(name);
                        fakeActionDefinitions.set(rename.value, declaration);
                      } else {
                        butter(
                          3000,
                          "I know that seems like a cool name, but it's not a valid Shesmu identifier (letters, numbers, and underscore, starting with a lower case letter)."
                        );
                      }
                    }),
                  ];
                })
            ),
            buttonAccessory(
              [{ type: "icon", icon: "trash" }, "Delete"],
              "Delete action definition.",
              () => fakeActionDefinitions.delete(name)
            ),
          ],
        ]
      )
  );
  const {
    ui: fakeConstantsUi,
    model: fakeConstantsModel,
  } = singleState((items: Iterable<[string, FakeConstant]>) =>
    table(
      Array.from(items),
      ["Name", ([name, _declaration]) => name],
      [
        "Value",
        ([_name, declaration]) =>
          preformatted(JSON.stringify(declaration.value, null, 2)),
      ],
      [
        "",
        ([name, _declaration]) => [
          buttonAccessory(
            [{ type: "icon", icon: "trash" }, "Delete"],
            "Delete constant definition.",
            () => fakeConstantDefinitions.delete(name)
          ),
        ],
      ]
    )
  );
  const { ui: fakeRefillersUi, model: fakeRefillersModel } = singleState(
    (items: Iterable<[string, FakeRefillerParameters]>) =>
      table(
        Array.from(items),
        ["Name", ([name, _declaration]) => name],
        [
          "",
          ([name, parameters]) => [
            buttonAccessory(
              [{ type: "icon", icon: "download" }, "Download"],
              "Download refiller definition.",
              () =>
                saveFile(
                  JSON.stringify(parameters),
                  "application/json",
                  name + ".refillerdef"
                )
            ),
            buttonAccessory(
              [{ type: "icon", icon: "pencil" }, "Rename"],
              "Rename refiller definition.",
              () =>
                dialog((close) => {
                  const rename = inputText(name);
                  return [
                    "Rename refiller to: ",
                    rename.ui,
                    br(),
                    button("Rename", "Rename refiller.", () => {
                      if (validIdentifier.test(rename.value)) {
                        close();
                        fakeRefillerDefinitions.delete(name);
                        fakeRefillerDefinitions.set(rename.value, parameters);
                      } else {
                        butter(
                          3000,
                          "I know that seems like a cool name, but it's not a valid Shesmu identifier (letters, numbers, and underscore, starting with a lower case letter)."
                        );
                      }
                    }),
                  ];
                })
            ),
            buttonAccessory(
              [{ type: "icon", icon: "trash" }, "Delete"],
              "Delete refiller definition.",
              () => fakeRefillerDefinitions.delete(name)
            ),
          ],
        ]
      )
  );

  const spot = spotCounter((c) => {
    switch (c) {
      case 0:
        return "No local defintions";
      case 1:
        return "One local defintion.";
      default:
        return `${c} local definitions`;
    }
  });
  const [actionSpotModel, constantSpotModel, refillerSpotModel] = reducingModel(
    spot.model,
    (accumulator: number, value: number | null) =>
      value == null ? accumulator : value + accumulator,
    0,
    3
  );
  fakeActionDefinitions = mutableStoreWatcher(
    mutableLocalStore<FakeActionParameters>("shesmu_fake_actions"),
    combineModels(fakeActionsModel, mapModel(actionSpotModel, countIterable))
  );
  fakeConstantDefinitions = mutableStoreWatcher(
    mutableLocalStore<FakeConstant>("shesmu_fake_constants"),
    combineModels(
      fakeConstantsModel,
      mapModel(constantSpotModel, countIterable)
    )
  );
  fakeRefillerDefinitions = mutableStoreWatcher(
    mutableLocalStore<FakeRefillerParameters>("shesmu_fake_refillers"),
    combineModels(
      fakeRefillersModel,
      mapModel(refillerSpotModel, countIterable)
    )
  );
  const script = document.createElement("DIV");
  script.className = "editor";
  const editor = ace.edit(script);
  editor.session.setMode("ace/mode/shesmu");
  editor.session.setOption("useWorker", false);
  editor.session.setTabSize(2);
  editor.session.setUseSoftTabs(true);
  editor.setFontSize("14pt");
  editor.setValue(
    (decodeBody && scriptBody
      ? decompressFromEncodedURIComponent(scriptBody)
      : scriptBody) ||
      localStorage.getItem("shesmu_script") ||
      "",
    0
  );
  const errorTable = singleState((response: SimulationResponse | null) => {
    const annotations: AceAjax.Annotation[] = [];
    const ui = table(
      (response?.errors || []).map((err) => {
        const match = err.match(/^(\d+):(\d+): *(.*$)/);
        if (match) {
          const line = parseInt(match[1]);
          const column = parseInt(match[2]);
          const errorText = match[3];
          annotations.push({
            row: line - 1,
            column: column - 1,
            text: errorText,
            type: "error",
          });
          return { line: line, column: column, message: errorText };
        } else {
          return { line: null, column: null, message: err };
        }
      }),
      ["Line", (e) => e.line?.toString() || ""],
      ["Column", (e) => e.column?.toString() || ""],
      ["Error", (e) => e.message]
    );
    editor.getSession().setAnnotations(annotations);
    return ui;
  });

  const readStale = locallyStored<boolean>("shesmu_read_stale", true);
  const allowUnused = locallyStored<boolean>("shesmu_allow_unused", false);
  const tabbedArea = tabsModel(
    1,
    {
      name: "Script",
      contents: group(
        { element: script, find: null, reveal: null, type: "ui" },
        errorTable.ui
      ),
    },
    {
      name: ["Extra Definitions", spot.ui],
      contents: [
        { type: "icon", icon: "camera-reels-fill" },
        { type: "b", contents: "Action Definitions" },
        br(),
        link("defs", "All actions known"),
        " to the Shesmu server are available in simulation. If testing something that is not yet available or needs to be modified, the action definition can be imported here. Actions here take priority over actions from the server. If exporting actions to ",
        mono(".actnow"),
        " files, do not use actions definitions here or the server will not recognise them.",
        br(),
        button(
          [{ type: "icon", icon: "upload" }, "Import Action"],
          "Uploads a file containing an action.",
          () =>
            loadFile((name, data) =>
              importAction(fakeActionDefinitions, name.split(".")[0], data)
            )
        ),
        button(
          [{ type: "icon", icon: "plus-square" }, "Add Action"],
          "Adds an action from a definition.",
          () =>
            dialog((close) => {
              const actionJson = inputTextArea();
              return [
                "Action definition:",
                br(),
                actionJson.ui,
                br(),
                button(
                  [{ type: "icon", icon: "plus-square" }, "Add"],
                  "Save to fake action collection.",
                  () => {
                    importAction(fakeActionDefinitions, null, actionJson.value);
                    close();
                  }
                ),
              ];
            })
        ),
        br(),
        fakeActionsUi,
        br(),
        { type: "icon", icon: "braces" },
        { type: "b", contents: "Constants" },
        br(),
        button(
          [{ type: "icon", icon: "plus-square" }, "Add Constant"],
          "Adds a new constant.",
          () =>
            dialog((close) => {
              const nameInput = inputText();
              const typeSelector = temporaryState("shesmu::olive");
              const typeInput = inputText();
              const valueInput = inputTextArea();

              return [
                "Name: ",
                mono("shesmu::simulated::"),
                nameInput.ui,
                br(),
                "Format: ",
                dropdown(
                  (format) => typeFormats[format],
                  (format) => format == typeSelector.get(),
                  typeSelector,
                  null,
                  ...Object.keys(typeFormats)
                ),
                br(),
                "Type: ",
                typeInput.ui,
                br(),
                "Value: ",
                valueInput.ui,
                br(),
                button(
                  [{ type: "icon", icon: "plus-square" }, "Add Constant"],
                  "Add constant.",
                  () => {
                    if (validIdentifier.test(nameInput.value)) {
                      fetchJsonWithBusyDialog(
                        "type",
                        { value: typeInput.value, format: typeSelector.get() },
                        (v) => {
                          const result = parseDescriptor(
                            v.descriptor,
                            valueParser
                          )[0](valueInput.value);
                          if (result.good) {
                            fakeConstantDefinitions.set(nameInput.value, {
                              type: v.descriptor,
                              value: result.output,
                            });
                            close();
                          } else {
                            dialog(() => result.error || "Cannot parse value.");
                          }
                        }
                      );
                    } else {
                      dialog(
                        () => "This name isn't a valid Shesmu identifier."
                      );
                    }
                  }
                ),
              ];
            })
        ),
        fakeConstantsUi,
        br(),
        { type: "icon", icon: "trash-fill" },
        { type: "b", contents: "Refillers" },
        br(),
        button(
          [{ type: "icon", icon: "upload" }, "Import Refiller"],
          "Uploads a file containing refiller.",
          () =>
            loadFile((name, data) =>
              importRefiller(fakeRefillerDefinitions, name.split(".")[0], data)
            )
        ),
        fakeRefillersUi,
      ],
    }
  );
  const dashboardState = mapModel(
    tabbedArea.models[0],
    (response: SimulationResponse | null) => {
      const tabList: Tab[] = [];
      if (response) {
        if (document.visibilityState == "hidden") {
          completeSound.play();
        }

        if (response.alerts?.length) {
          const { main, toolbar } = alertNavigator(
            response.alerts,
            () => [],
            temporaryState<AlertFilter<RegExp>[]>([]),
            [
              ["Line", (l: SimulatedLocation) => l.line.toString()],
              ["Column", (l: SimulatedLocation) => l.column.toString()],
            ]
          );
          tabList.push({
            name: "Alerts",
            contents: [toolbar, br(), main],
          });
        }
        if (response.actions?.length) {
          tabList.push({
            name: "Actions",
            contents: renderActions(response.actions),
          });
        }
        if (response.olives?.length) {
          type SimulatedOliveRenderer = (olive: SimulatedOlive) => UIElement;
          const oliveState = multipaneState<
            SimulatedOlive,
            {
              actions: SimulatedOliveRenderer;
              alerts: SimulatedOliveRenderer;
              overview: SimulatedOliveRenderer;
              dataflow: SimulatedOliveRenderer;
            }
          >("overview", {
            actions: (olive: SimulatedOlive) => {
              const oliveActions = (response.actions || []).filter((a) =>
                a.locations.some(
                  (l) => l.line == olive.line && l.column == l.column
                )
              );
              return oliveActions.length
                ? renderActions(oliveActions)
                : text("No actions found.");
            },
            alerts: (olive: SimulatedOlive) => {
              const oliveAlerts = (response.alerts || []).filter((a) =>
                a.locations.some(
                  (l) => l.line == olive.line && l.column == l.column
                )
              );
              if (oliveAlerts.length) {
                const { main, toolbar } = alertNavigator(
                  oliveAlerts,
                  () => [],
                  temporaryState<AlertFilter<RegExp>[]>([]),
                  [
                    ["Line", (l: SimulatedLocation) => l.line.toString()],
                    ["Column", (l: SimulatedLocation) => l.column.toString()],
                  ]
                );
                return [toolbar, br(), main];
              } else {
                return text("No alerts found.");
              }
            },
            overview: (olive: SimulatedOlive) =>
              table(
                Object.entries({
                  Runtime: formatTimeSpan(olive.duration / 1e6),
                }),
                ["Information", (x) => x[0]],
                ["Value", (x) => x[1]]
              ),
            dataflow: (olive: SimulatedOlive) => svgFromStr(olive.diagram),
          });
          const oliveSelection = dropdown(
            (olive) => [
              { type: "icon", icon: infoForProduces(olive.produces).icon },
              " ",
              italic(olive.syntax),
              " – ",
              olive.description || "No description.",
            ],
            null,
            oliveState.model,
            null,
            ...response.olives
          );
          const oliveTabs = tabs(
            {
              name: "Overview",
              contents: oliveState.components.overview,
            },
            { name: "Actions", contents: oliveState.components.actions },
            {
              name: "Alerts",
              contents: oliveState.components.alerts,
            },
            { name: "Dataflow", contents: oliveState.components.dataflow }
          );
          tabList.push({
            name: "Olive",
            contents: [oliveSelection, br(), oliveTabs],
          });
        }
        if (response.exports?.length) {
          tabList.push({
            name: "Exports",
            contents: response.exports.map((ex) => {
              switch (ex.type) {
                case "constant":
                  return [
                    header(ex.name),
                    table(
                      [["Returns", ex.returns]],
                      ["Position", (x) => x[0]],
                      ["Type", (x) => x[0]]
                    ),
                  ];
                case "define":
                  return [
                    header(ex.name),
                    table(
                      [
                        ["Input Format", ex.inputFormat],
                        ["Should Sign After", ex.isRoot ? "Yes" : "No"],
                      ]
                        .concat(
                          ex.parameters.map((type, index) => [
                            `Parameter ${index + 1}`,
                            type,
                          ])
                        )
                        .concat(
                          Object.entries(ex.output).map(([name, type]) => [
                            `Output Variable ${name}`,
                            type,
                          ])
                        ),
                      ["Position", (x) => x[0]],
                      ["Type", (x) => x[0]]
                    ),
                  ];

                case "function":
                  return [
                    header(ex.name),
                    table(
                      [["Return", ex.returns]].concat(
                        ex.parameters.map((type, index) => [
                          `Parameter ${index + 1}`,
                          type,
                        ])
                      ),
                      ["Position", (x) => x[0]],
                      ["Type", (x) => x[0]]
                    ),
                  ];
                default:
                  return blank();
              }
            }),
          });
        }
        if (response.refillers && Object.keys(response.refillers).length) {
          const refillerState = singleState(
            ([name, entries]: [string, RefillerRecord[]]) =>
              entries.length > 0
                ? renderJsonTable<RefillerRecord>(
                    name + ".refiller.json",
                    entries,
                    ...Object.keys(entries[0])
                      .sort((a, b) => a.localeCompare(b))
                      .map(
                        (name) =>
                          [name, (row: RefillerRecord) => row[name]] as [
                            string,
                            (row: RefillerRecord) => any
                          ]
                      )
                  )
                : ["Olive provided no records to ", mono(name), " refiller."]
          );
          tabList.push({
            name: "Refill Output",
            contents: [
              dropdown(
                ([name]) => name,
                null,
                refillerState.model,
                null,
                ...Object.entries(response.refillers)
              ),
              br(),
              refillerState.ui,
            ],
          });
        }
        if (response.dumpers && Object.entries(response.dumpers).length) {
          const dumpState = singleState((input: [string, any[][]] | null) =>
            input && input[1].length > 0
              ? renderJsonTable<any[]>(
                  name + ".dump.json",
                  input[1],
                  ...Array.from(input[1][0].keys()).map(
                    (i) =>
                      [`Column ${i + 1}`, (row) => row[i]] as [
                        string,
                        (input: any[]) => any
                      ]
                  )
                )
              : ["Olive provided no records to ", mono(name), " dumper."]
          );
          tabList.push({
            name: "Dumpers",
            contents: group(
              dropdown(
                (input) => (input ? input[0] : blank()),
                null,
                dumpState.model,
                null,
                ...Object.entries(response.dumpers)
              ),
              br(),
              dumpState.ui
            ),
          });
        }
        if (response.overloadedInputs?.length) {
          tabList.push({
            name: "Overloaded Inputs",
            contents: [
              text(
                "The following input formats are unavailable and prevented the simulation from running:"
              ),
              br(),
              table(response.overloadedInputs, ["Input Format", (x) => x]),
            ],
          });
          butter(
            3000 + response.overloadedInputs.length * 1000,
            "Required input formats are unavailable:",
            response.overloadedInputs.map((s) => [
              br(),
              { type: "icon", icon: "cloud-slash" },
              s,
            ])
          );
        }

        if (response.overloadedServices?.length) {
          tabList.push({
            name: "Overloaded Services",
            contents: [
              text(
                "The following services are unavailable and prevented the simulation from running:"
              ),
              br(),
              table(response.overloadedServices, ["Service", (x) => x]),
            ],
          });
          butter(
            3000 + response.overloadedServices.length * 1000,
            "Required services are unavailable:",
            response.overloadedServices.map((s) => [
              br(),
              { type: "icon", icon: "wifi-off" },
              s,
            ])
          );
        }
        if (response.metrics) {
          tabList.push({
            name: "Prometheus Metrics",
            contents: preformatted(response.metrics),
          });
        }
        if (response.bytecode) {
          tabList.push({
            name: "Bytecode",
            contents: preformatted(response.bytecode),
          });
        }
        if (!response.bytecode || response.exceptionThrown) {
          // Errors might be present even if the script compiled okay, but if no bytecode was generated, we can be sure it's borked.
          butter(
            3000,
            response.errors.length == 1
              ? "Unable to simulate due to an error."
              : `Unable to simulate due to ${response.errors.length} errors.`
          );
        }
      }
      return { tabs: tabList, activate: response?.errors.length === 0 };
    }
  );
  const main = refreshable(
    "simulate",
    combineModels(dashboardState, errorTable.model),
    true
  );
  const simulationModel = mapModel(main, (request: string) => {
    editor.getSession().clearAnnotations();
    return {
      allowUnused: allowUnused.get(),
      fakeActions: Object.fromEntries(fakeActionDefinitions),
      fakeConstants: Object.fromEntries(fakeConstantDefinitions),
      fakeRefillers: Object.fromEntries(fakeRefillerDefinitions),
      dryRun: false,
      readStale: readStale.get(),
      script: request,
    };
  });

  const savedTheme = locallyStoredString("shesmu_theme", "ace/theme/chrome");
  setRootDashboard(
    container,
    group(
      button(
        [{ type: "icon", icon: "bug" }, "Simulate"],
        "Run olive simulation and fetch results",
        () => simulationModel.statusChanged(editor.getValue())
      ),
      buttonAccessory(
        [{ type: "icon", icon: "file-earmark-arrow-up" }, "Upload File"],
        "Upload a file from your computer to simulate",
        () =>
          loadFile((name, data) => {
            fileName = name;
            editor.setValue(data, 0);
          })
      ),
      buttonAccessory(
        [{ type: "icon", icon: "file-earmark-arrow-down" }, "Download File"],
        "Save script in editor to your computer",
        () => saveFile(editor.getValue(), "text/plain", fileName)
      ),
      buttonAccessory(
        [{ type: "icon", icon: "share" }, "Share"],
        "Copy this script as a link to the clipboard",
        () =>
          saveClipboard(
            `${window.location.origin}${
              window.location.pathname
            }?share=${compressToEncodedURIComponent(editor.getValue())}`
          )
      ),

      dropdown(
        (stale, selected) => {
          if (stale) {
            return [
              { type: "icon", icon: "server" },
              selected ? blank() : "Use Cached Data",
            ];
          } else {
            return [
              { type: "icon", icon: "hdd-network" },
              selected ? blank() : "Wait for Fresh Data",
            ];
          }
        },
        (wait) => wait == readStale.get(),
        combineModels(),
        {
          synchronizer: readStale,
          predicate: (recovered, item) => recovered == item,
          extract: (x) => x,
        },
        false,
        true
      ),
      dropdown(
        (unused, selected) => {
          if (unused) {
            return [
              { type: "icon", icon: "shield-slash" },
              selected ? blank() : "Allow Unused Variables",
            ];
          } else {
            return [
              { type: "icon", icon: "shield" },
              selected ? blank() : "Forbid Unused Variables",
            ];
          }
        },
        (wait) => wait == allowUnused.get(),
        combineModels(),
        {
          synchronizer: allowUnused,
          predicate: (recovered, item) => recovered == item,
          extract: (x) => x,
        },
        false,
        true
      ),
      dropdown(
        (theme, selected) => {
          switch (theme) {
            case "ace/theme/ambiance":
              return [
                { type: "icon", icon: "moon" },
                selected ? blank() : "Ambiance",
              ];
            case "ace/theme/chrome":
              return [
                { type: "icon", icon: "sun" },
                selected ? blank() : "Chrome",
              ];
            default:
              return [
                { type: "icon", icon: "question-circle-fill" },
                selected ? blank() : "Unknown",
              ];
          }
        },
        (theme) => theme == savedTheme.get(),
        {
          reload: () => {},
          statusChanged: (input: string) => editor.setTheme(input),
          statusFailed: (_message: string, _retry: (() => void) | null) => {},
          statusWaiting: () => {},
        },
        {
          synchronizer: savedTheme,
          predicate: (recovered, item) => recovered == item,
          extract: (x) => x,
        },
        "ace/theme/ambiance",
        "ace/theme/chrome"
      ),
      helpArea("simulator")
    ),
    br(),
    tabbedArea.ui
  );
  document.addEventListener(
    "keydown",
    (e) => {
      // Map Ctrl-S or Command-S to download/save
      if (checkKey(e, "s")) {
        e.preventDefault();
        saveFile(editor.getValue(), "text/plain", fileName);
      }
    },
    false
  );
  let checking = false;
  let checkTimeout: number;

  const updateSyntax = () => {
    if (!checking) {
      checking = true;
      // This does not check for overload because editor is best-effort
      fetchAsPromise("simulate", {
        allowUnused: false,
        fakeActions: Object.fromEntries(fakeActionDefinitions),
        fakeConstants: Object.fromEntries(fakeConstantDefinitions),
        fakeRefillers: Object.fromEntries(fakeRefillerDefinitions),
        dryRun: true,
        readStale: true,
        script: editor.getValue(),
      })
        .then(errorTable.model.statusChanged)
        .finally(() => (checking = false));
    }
  };
  editor.getSession().on("change", () => {
    if (!checking) {
      clearTimeout(checkTimeout);
      checkTimeout = window.setTimeout(updateSyntax, 1000);
    }
    localStorage.setItem("shesmu_script", editor.getValue());
  });
  main.force(null);
}

function renderActions(actions: SimulatedAction[]): UIElement {
  return paginatedList(
    "simulation.actnow",
    actions,
    (selected) =>
      selected.map((action) =>
        tile(
          ["action", "state_simulated"],
          text(action.name),
          tagList("Tags: ", action.tags),
          table(
            Object.entries(action.parameters).sort((a, b) =>
              a[0].localeCompare(b[0])
            ),
            ["Name", (x) => x[0]],
            ["Value", (x) => JSON.stringify(x[1], null, 2)]
          ),
          collapsible(
            "Locations",
            table(
              action.locations,
              ["Line", (l) => l.line.toString()],
              ["Column", (l) => l.column.toString()]
            )
          )
        )
      ),
    (a, keywords) =>
      keywords.every(
        (k) =>
          a.name.toLowerCase().indexOf(k) != -1 ||
          a.tags.some((tag) => tag.toLowerCase().indexOf(k) != -1) ||
          matchKeywordInArbitraryData(k, a.parameters)
      )
  );
}

function renderJsonTable<T>(
  filename: string,
  data: T[],
  ...columns: [string, (item: T) => any][]
): UIElement {
  return paginatedList(
    filename,
    data,
    (selected) =>
      table(
        selected,
        ...columns.map(([name, extractor]): [string, (row: T) => UIElement] => [
          name,
          (row: T) => preformatted(JSON.stringify(extractor(row), null, 2)),
        ])
      ),
    (item, keywords) =>
      keywords.every((k) =>
        columns.some(([, extractor]) =>
          matchKeywordInArbitraryData(k, extractor(item))
        )
      )
  );
}
