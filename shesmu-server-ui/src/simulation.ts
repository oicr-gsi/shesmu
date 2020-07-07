import { Alert, alertNavigator, AlertFilter } from "./alert.js";
import { infoForProduces, OliveType } from "./olive.js";
import {
  mutableLocalStore,
  locallyStoredString,
  refreshable,
  saveClipboardJson,
  loadFile,
  saveFile,
} from "./io.js";
import {
  addElements,
  buttonAccessory,
  button,
  br,
  tabs,
  collapsible,
  table,
  group,
  initialise,
  dropdown,
  tagList,
  text,
  tile,
  UIElement,
  blank,
  preformatted,
  header,
  inputCheckbox,
  Tab,
  italic,
  multipaneState,
  findProxy,
  setFindHandler,
  dialog,
  inputText,
  buttonClose,
  inputTextArea,
  paginatedList,
  temporaryState,
  singleState,
  mono,
} from "./html.js";
import {
  formatTimeSpan,
  MutableStore,
  mutableStoreWatcher,
  matchKeywordInArbitraryData,
  validIdentifier,
  combineModels,
} from "./util.js";
import { specialImports } from "./actions.js";

/**
 * An exported definition from a simulated script
 */
type Export = ExportConstant | ExportFunction;

/**
 * A constant definition exported by a simulated scrip.
 */
interface ExportConstant {
  type: "constant";
  /** The exported name */
  name: string;
  /** The type as a Shesmu descriptor */
  returns: string;
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
  name: string;
  parameters: FakeActionParameters;
  errors: string[];
}
/**
 * The parameters used by a fake action
 */
export type FakeActionParameters = {
  [name: string]: { type: TypeInfo; required: boolean };
};
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
/**
 * The response from the sever after attempting simulation
 */
interface SimulationResponse {
  alerts?: SimulatedAlert[];
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

function importAction(
  store: MutableStore<string, FakeActionParameters>,
  name: string | null,
  data: string
): void {
  for (const importReads of specialImports) {
    const result = importReads(data);
    if (result) {
      if (result.errors.length) {
        dialog((c) => result.errors.map((e) => text(e)));
      } else {
        const givenName = result.name || name;
        if (givenName) {
          store.set(givenName, result.parameters);
        } else {
          dialog((close) => {
            const newName = inputText();
            return [
              "Save action as: ",
              newName.ui,
              br(),
              button("Add", "Save to fake action collection.", () => {
                if (validIdentifier.test(newName.getter())) {
                  store.set(newName.getter(), result.parameters);
                  close();
                } else {
                  dialog((c) => "This name isn't a valid Shesmu identifier.");
                }
              }),
            ];
          });
        }
      }
      return;
    }
  }
  dialog((c) => "Cannot identify uploaded action format. Sorry.");
}

export function initialiseSimulationDashboard(
  ace: AceAjax.Ace,
  container: HTMLElement,
  completeSound: HTMLAudioElement,
  scriptName: string,
  scriptBody: string
) {
  initialise();
  let fileName = scriptName || "unknown.shesmu";
  let fakeActionDefinitions: MutableStore<
    string,
    FakeActionParameters
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
              "⎘ Copy",
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
            button("✎ Rename", "Rename action definition.", () =>
              dialog((close) => {
                const rename = inputText(name);
                return [
                  "Rename action to: ",
                  rename.ui,
                  br(),
                  button("Rename", "Rename action.", () => {
                    if (validIdentifier.test(rename.getter())) {
                      close();
                      fakeActionDefinitions.delete(name);
                      fakeActionDefinitions.set(rename.getter(), declaration);
                    } else {
                      dialog(
                        (c) =>
                          "I know that seems like a cool name, but it's not a valid Shesmu identifier (letters, numbers, and underscore, starting with a lower case letter)."
                      );
                    }
                  }),
                ];
              })
            ),
            buttonClose("Delete action definition.", () =>
              fakeActionDefinitions.delete(name)
            ),
          ],
        ]
      )
  );
  fakeActionDefinitions = mutableStoreWatcher(
    mutableLocalStore<FakeActionParameters>("shesmu_fake_actions"),
    fakeActionsModel
  );
  const script = document.createElement("DIV");
  script.className = "editor";
  const editor = ace.edit(script);
  editor.session.setMode("ace/mode/shesmu");
  editor.session.setOption("useWorker", false);
  editor.session.setTabSize(2);
  editor.session.setUseSoftTabs(true);
  editor.setFontSize("14pt");
  editor.setValue(scriptBody || localStorage.getItem("shesmu_script") || "", 0);
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

  const waitForData = inputCheckbox("Wait for fresh data", false);
  const dashboardState = singleState((response: SimulationResponse | null) => {
    const tabList: Tab[] = [
      {
        name: "Script",
        contents: group(script, errorTable.ui),
      },
      {
        name: "Extra Definitions",
        contents: group(
          button(
            "➕ Import Action",
            "Uploads a file containing an action.",
            () =>
              loadFile((name, data) =>
                importAction(fakeActionDefinitions, name.split(".")[0], data)
              )
          ),
          button("➕ Add Action", "Adds an action from a definition.", () =>
            dialog((close) => {
              const actionJson = inputTextArea();
              return [
                "Action definition:",
                br(),
                actionJson.ui,
                br(),
                button("Add", "Save to fake action collection.", () => {
                  importAction(
                    fakeActionDefinitions,
                    null,
                    actionJson.getter()
                  );
                  close();
                }),
              ];
            })
          ),
          fakeActionsUi
        ),
      },
    ];
    if (response) {
      if (document.visibilityState == "hidden") {
        completeSound.play();
      }

      if (response.alerts?.length) {
        const { main, toolbar, find } = alertNavigator(
          response.alerts,
          (a) => [],
          temporaryState([] as AlertFilter<RegExp>[]),
          [
            ["Line", (l: SimulatedLocation) => l.line.toString()],
            ["Column", (l: SimulatedLocation) => l.column.toString()],
          ]
        );
        tabList.push({
          name: "Alerts",
          contents: [toolbar, br(), main],
          find: find,
        });
      }
      if (response.actions?.length) {
        tabList.push({
          name: "Actions",
          contents: renderActions(response.actions),
        });
      }
      if (response.olives?.length) {
        const alertFindProxy = findProxy();
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
              const { main, toolbar, find } = alertNavigator(
                oliveAlerts,
                (a) => [],
                temporaryState([] as AlertFilter<RegExp>[]),
                [
                  ["Line", (l: SimulatedLocation) => l.line.toString()],
                  ["Column", (l: SimulatedLocation) => l.column.toString()],
                ]
              );
              alertFindProxy.updateHandle(find);
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
          dataflow: (olive: SimulatedOlive) =>
            document.adoptNode(
              new DOMParser().parseFromString(olive.diagram, "image/svg+xml")
                .documentElement
            ),
        });
        const oliveSelection = dropdown(
          (olive) => [
            infoForProduces(olive.produces).icon,
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
            find: alertFindProxy.find,
          },
          { name: "Dataflow", contents: oliveState.components.dataflow }
        );
        tabList.push({
          name: "Olive",
          contents: [oliveSelection, br(), oliveTabs.ui],
          find: oliveTabs.find,
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
              ([name, declaration]) => name,
              Object.entries(response.refillers)[0],
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
              Object.entries(response.dumpers)[0],
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
    }
    const dashboardTabs = tabs(...tabList);
    setFindHandler(dashboardTabs.find);
    return dashboardTabs.ui;
  });
  const main = refreshable(
    "/simulate",
    (request) => {
      editor.getSession().clearAnnotations();
      return {
        body: JSON.stringify({
          fakeActions: Object.fromEntries(fakeActionDefinitions),
          dryRun: false,
          readStale: !waitForData.getter(),
          script: request,
        }),
        method: "POST",
      };
    },
    combineModels(dashboardState.model, errorTable.model),
    true
  );

  const { last: lastTheme, model: savedTheme } = locallyStoredString(
    "shesmu_theme",
    "ace/theme/chrome"
  );
  addElements(
    container,
    group(
      button("🤖 Simulate", "Run olive simulation and fetch results", () =>
        main.statusChanged(editor.getValue())
      ),
      waitForData.ui,
      buttonAccessory(
        "🠝 Upload File",
        "Upload a file from your computer to simulate",
        () =>
          loadFile((name, data) => {
            fileName = name;
            editor.setValue(data, 0);
          })
      ),
      buttonAccessory(
        "🡇 Download File",
        "Save script in editor to your computer",
        () => saveFile(editor.getValue(), "text/plain", fileName)
      ),
      " Theme: ",
      dropdown(
        (theme) => {
          switch (theme) {
            case "ace/theme/ambiance":
              return "Ambiance";
            case "ace/theme/chrome":
              return "Chrome";
            default:
              return "Unknown";
          }
        },
        lastTheme,
        combineModels(savedTheme, {
          reload: () => {},
          statusChanged: (input: string) => editor.setTheme(input),
          statusFailed: (_message: string, _retry: (() => void) | null) => {},
          statusWaiting: () => {},
        }),
        null,
        "ace/theme/ambiance",
        "ace/theme/chrome"
      )
    ),
    br(),
    dashboardState.ui
  );
  document.addEventListener(
    "keydown",
    (e) => {
      // Map Ctrl-S or Command-S to download/save
      if (
        (window.navigator.platform.match("Mac") ? e.metaKey : e.ctrlKey) &&
        e.keyCode == 83
      ) {
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
      fetch("/simulate", {
        body: JSON.stringify({
          fakeActions: Object.fromEntries(fakeActionDefinitions),
          dryRun: true,
          readStale: true,
          script: editor.getValue(),
        }),
        method: "POST",
      })
        .then((response) => response.json())
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
        columns.some(([name, extractor]) =>
          matchKeywordInArbitraryData(k, extractor(item))
        )
      )
  );
}
