import {
  ClickHandler,
  UIElement,
  blank,
  br,
  butter,
  button,
  buttonAccessory,
  buttonCustom,
  buttonDanger,
  checkRandomPermutation,
  checkRandomSequence,
  collapsible,
  dialog,
  dropdown,
  group,
  historyState,
  img,
  inputText,
  inputTextArea,
  italic,
  link,
  makeUrl,
  multipaneState,
  popupMenu,
  preformatted,
  refreshButton,
  setRootDashboard,
  singleState,
  synchronizerFields,
  table,
  tableFromRows,
  tableRow,
  tabs,
  tagList,
  text,
  tile,
} from "./html.js";
import {
  MutableStore,
  SourceLocation,
  StatefulModel,
  combineModels,
  commonPathPrefix,
  computeDuration,
  mapModel,
  mutableStoreWatcher,
} from "./util.js";
import { ActionFilter, BasicQuery, createSearch } from "./actionfilters.js";
import {
  fetchCustomWithBusyDialog,
  fetchJsonWithBusyDialog,
  loadFile,
  mutableLocalStore,
  paginatedRefreshable,
  saveClipboard,
  saveClipboardJson,
  saveFile,
} from "./io.js";
import { actionStats } from "./stats.js";
import { actionRender } from "./actions.js";
import { helpArea } from "./help.js";
/**
 * The minimum information provided for actions by the server.
 */
export interface Action {
  actionId: string;
  commands: ActionCommand[];
  errors: string[];
  external: number;
  lastAdded: number;
  lastChecked: number;
  lastStatusChange: number;
  locations: SourceLocation[];
  state: string;
  tags: string[];
  type: string;
  updateInProgress: boolean;
  url: string;
}
/**
 * A command that can be performed on an action
 */
export interface ActionCommand extends BaseCommand {
  allowBulk: boolean;
}
/**
 * Response from paginated action query endpoint
 */
interface ActionQueryResponse {
  offset: number;
  total: number;
  results: Action[];
  bulkCommands: BulkCommand[];
}
interface BaseCommand {
  command: string;
  buttonText: string;
  showPrompt: boolean;
}
interface BulkCommand extends BaseCommand {
  annoyUser: boolean;
  count: number;
}
/**
 * The information for a button that can export the current search to some other service
 */
export type ExportSearchCommand = [
  string,
  string,
  (filters: ActionFilter[]) => void
];
/**
 * The format of a saved search ready for use by the UI
 */
type SearchDefinition = [string, ActionFilter[]];
/**
 * The format of searches provided by the server
 */
type ServerSearches = { [name: string]: ActionFilter[] };
/**
 * The status of an action
 */
export type Status = typeof statuses[number];

export const standardExports: ExportSearchCommand[] = [
  ["⎘ To Clipboard", "Export search to the clipboard.", saveClipboardJson],
  [
    "⎘ To Clipboard for Ticket",
    "Export search to the clipboard in a way that can be pasted in a text document.",
    (filters) => saveClipboard(encodeSearch(filters)),
  ],
  [
    "📁 To File",
    "Download search as a file.",
    (filters) =>
      saveFile(JSON.stringify(filters), "application/json", "My Search.search"),
  ],
  [
    "🖥 cURL Actions",
    "Convert search to a cURL command to extract actions.",
    (filters) =>
      copyCUrlCommand("query", {
        filters: filters,
        skip: 0,
        limit: 100000,
      }),
  ],
  [
    "🖥 Wget Actions",
    "Convert search to a Wget command to extract actions.",
    (filters) =>
      copyWgetCommand("query", {
        filters: filters,
        skip: 0,
        limit: 100000,
      }),
  ],
  [
    "🖥 cURL Purge",
    "Convert search to a cURL command to purge matching actions.",
    (filters) => copyCUrlCommand("pruge", filters),
  ],
  [
    "🖥 Wget Purge",
    "Convert search to a Wget command to purge matching actions.",
    (filters) => copyWgetCommand("purge", filters),
  ],
];

/**
 * The states an action can be in
 */
export const statuses = [
  "FAILED",
  "HALP",
  "INFLIGHT",
  "QUEUED",
  "SUCCEEDED",
  "THROTTLED",
  "UNKNOWN",
  "WAITING",
  "ZOMBIE",
] as const;

export function statusButton(state: Status, click: ClickHandler): UIElement {
  return buttonCustom(
    state,
    statusDescription(state),
    [`state_${state.toLowerCase()}`],
    click
  );
}

export function statusDescription(status: Status): string {
  switch (status) {
    case "FAILED":
      return "The action has been attempted and encounter an error (possibly recoverable).";
    case "HALP":
      return "The action is in a state where it needs human attention or intervention to correct itself.";
    case "INFLIGHT":
      return "The action is currently being executed.";
    case "QUEUED":
      return "The action is waiting for a remote system to start it.";
    case "SUCCEEDED":
      return "The action is complete.";
    case "THROTTLED":
      return "The action is being rate limited by a Shesmu throttler or by an over-capacity signal.";
    case "UNKNOWN":
      return "The actions state is not currently known either due to an exception or not having been attempted.";
    case "WAITING":
      return "The action cannot be started due to a resource being unavailable.";
    case "ZOMBIE":
      return "The action is never going to complete. This is not necessarily a failed state; testing or debugging actions should be in this state.";
  }
}
/**
 * Create a UI element that can fetch and display actions from the server given a set of filters
 */
export function actionDisplay(
  exportSearches: ExportSearchCommand[]
): {
  actions: UIElement;
  bulkCommands: UIElement;
  model: StatefulModel<ActionFilter[]>;
} {
  let reload: () => void = () => {};
  type QueryState = [ActionFilter[], ActionQueryResponse | null];
  type ActionQueryRenderer = (info: QueryState) => UIElement;
  const { model, components } = multipaneState<
    QueryState,
    { actions: ActionQueryRenderer; bulkCommands: ActionQueryRenderer }
  >(
    "actions",
    {
      actions: ([_filters, response]: QueryState) =>
        response
          ? response.results.length == 0
            ? "No actions match."
            : response.results.map((action) => {
                const css = ["action", `state_${action.state.toLowerCase()}`];
                if (action.updateInProgress) {
                  css.push("updating");
                }

                return tile(
                  css,
                  group(
                    link(
                      makeUrl("actiondash", {
                        filters: { id: [action.actionId] },
                        saved: "All Actions",
                      }),
                      action.actionId
                    ),
                    buttonAccessory(
                      "⎘ Copy Id",
                      "Copy action identifier to clipboard.",
                      () => saveClipboard(action.actionId)
                    ),
                    buttonDanger(
                      "☠️ Purge Action",
                      "Remove this action from Shesmu. This does not stop an olive from generating it again.",
                      () =>
                        fetchJsonWithBusyDialog(
                          "purge",
                          {
                            body: JSON.stringify([
                              {
                                type: "id",
                                ids: [action.actionId],
                              },
                            ]),
                            method: "POST",
                          },
                          (count: number) => {
                            if (count > 1) {
                              dialog(() => [
                                `Purged ${count} actions!!! This is awkward. The unique action IDs aren't unique!`,
                                img("ohno.gif"),
                              ]);
                            }
                            reload();
                          }
                        )
                    ),
                    action.commands.map(
                      ({ command, buttonText, showPrompt }) => {
                        const performCommand = () =>
                          fetchJsonWithBusyDialog(
                            "command",
                            {
                              body: JSON.stringify({
                                command: command,
                                filters: [
                                  {
                                    type: "id",
                                    ids: [action.actionId],
                                  },
                                ],
                              }),
                              method: "POST",
                            },
                            (count: number) => {
                              if (count == 0) {
                                dialog(() => [
                                  "This action is indifferent to your pleas. Maybe the action's internal state has changed? Try refreshing.",
                                  img("indifferent.gif"),
                                ]);
                              } else if (count > 1) {
                                dialog(() => [
                                  `The command executed on ${count} actions!!! This is awkward. The unique action IDs aren't unique!`,
                                  img("ohno.gif"),
                                ]);
                              }
                              reload();
                            }
                          );
                        return buttonDanger(
                          buttonText,
                          `Perform special command ${command} on this action.`,
                          showPrompt
                            ? () => {
                                dialog((close) => [
                                  `Perform command ${command} on this action? This is your moment of sober second thought.`,
                                  br(),
                                  buttonDanger(
                                    buttonText.toUpperCase(),
                                    "Really do it!",
                                    () => {
                                      close();
                                      performCommand();
                                    }
                                  ),
                                  br(),
                                  button(
                                    "Back away slowly",
                                    "Don't do anything.",
                                    close
                                  ),
                                ]);
                              }
                            : performCommand
                        );
                      }
                    )
                  ),
                  (actionRender.get(action.type) || defaultRenderer)(action),
                  collapsible(
                    "JSON",
                    preformatted(JSON.stringify(action, null, 2))
                  )
                );
              })
          : "Actions not loaded yet.",
      bulkCommands: ([filters, response]: QueryState) => [
        buttonAccessory(
          "🡇 Export Search",
          "Export this search to a file or the clipboard or for use in other software.",
          () =>
            dialog((close) =>
              standardExports
                .concat(exportSearches)
                .map(([name, description, callback]) =>
                  button(name, description, () => {
                    callback(filters);
                    close();
                  })
                )
            )
        ),
        response && response.bulkCommands.length
          ? buttonAccessory(
              "🡇 Export Command",
              "Generate a command line to perform a command command.",
              () =>
                dialog((_close) =>
                  tableFromRows(
                    response.bulkCommands.map(({ buttonText, command }) =>
                      tableRow(
                        null,
                        { contents: buttonText },
                        {
                          contents: button(
                            "cUrl",
                            `Copy a cURL command to invoke the ${command} command on the actions selected.`,
                            () =>
                              copyCUrlCommand("command", {
                                command: command,
                                filters: filters,
                              })
                          ),
                        },
                        {
                          contents: button(
                            "Wget",
                            `Copy a Wget command to invoke the ${command} command on the actions selected.`,
                            () =>
                              copyWgetCommand("command", {
                                command: command,
                                filters: filters,
                              })
                          ),
                        }
                      )
                    )
                  )
                )
            )
          : blank(),
        response && response.total
          ? buttonDanger(
              "☠️ Purge Actions",
              "Remove actions from Shesmu. This does not stop an olive from generating them again.",
              () =>
                fetchJsonWithBusyDialog(
                  "purge",
                  {
                    body: JSON.stringify(filters),
                    method: "POST",
                  },
                  (count: number) => {
                    let imgSrc: string;
                    if (count == 0) {
                      imgSrc = "shrek.gif";
                    } else if (count < 5) {
                      imgSrc = "holtburn.gif";
                    } else if (count < 20) {
                      imgSrc = "vacuum.gif";
                    } else if (count < 100) {
                      imgSrc = "car.gif";
                    } else if (count < 500) {
                      imgSrc = "flamethrower.gif";
                    } else if (count < 1000) {
                      imgSrc = "thorshchariot.gif";
                    } else if (count < 5000) {
                      imgSrc = "volcano.gif";
                    } else {
                      imgSrc = "starwars.gif";
                    }
                    butter(
                      3000,
                      `Removed ${count} actions.`,
                      br(),
                      img(imgSrc)
                    );
                    reload();
                  }
                )
            )
          : blank(),
        response
          ? response.bulkCommands.length < 6
            ? response.bulkCommands.map((command) =>
                buttonDanger(
                  command.buttonText,
                  `Perform special command ${command.command} on ${command.count} actions.`,
                  createCallbackForCommand(command, filters, reload)
                )
              )
            : buttonDanger(
                "🔧 Bulk Commands ▼",
                "Perform a number of action-specific commands.",
                popupMenu(
                  true,
                  ...response.bulkCommands.map((command) => ({
                    label: command.buttonText,
                    action: createCallbackForCommand(command, filters, reload),
                  }))
                )
              )
          : blank(),
      ],
    },
    "bulkCommands"
  );
  const io = paginatedRefreshable(
    25,
    "query",
    (filters, offset, pageLength) => ({
      body: JSON.stringify({
        filters: filters,
        limit: pageLength,
        skip: offset,
      }),
      method: "POST",
    }),
    (_filters: ActionFilter[] | null, response: ActionQueryResponse | null) =>
      response ? { offset: response.offset, total: response.total } : null,
    model
  );
  reload = io.model.reload;
  return {
    actions: [io.ui, components.actions],
    bulkCommands: components.bulkCommands,
    model: io.model,
  };
}

function createCallbackForCommand(
  command: BulkCommand,
  filters: ActionFilter[],
  reload: () => void
): () => void {
  let performCommand = () =>
    fetchJsonWithBusyDialog(
      "command",
      {
        body: JSON.stringify({
          command: command.command,
          filters: filters,
        }),
        method: "POST",
      },
      (count: number) => {
        butter(5000, `The command executed on ${count} actions.`);
        reload();
      }
    );
  if (command.annoyUser) {
    const realPerform = performCommand;
    const annoyFunction: (count: number, callback: () => void) => UIElement =
      Math.random() < 0.5 ? checkRandomPermutation : checkRandomSequence;
    performCommand = () =>
      dialog((close) => [
        "It's not that I don't trust that you know what you want to do, but solve this riddle.",
        annoyFunction(command.count, () => {
          close();
          realPerform();
        }),
      ]);
  }
  if (command.showPrompt) {
    const realPerform = performCommand;
    performCommand = () => {
      dialog((close) => [
        `Perform command ${command.command} on ${command.count} actions? This is your moment of sober second thought.`,
        br(),
        buttonDanger(command.buttonText.toUpperCase(), "Really do it!", () => {
          close();
          realPerform();
        }),
        br(),
        button("Back away slowly", "Don't do anything.", close),
      ]);
    };
  }
  return performCommand;
}

function copyCUrlCommand(slug: string, request: any) {
  saveClipboard(
    `curl -d '${JSON.stringify(request)}' -X POST ${location.origin}/${slug}`
  );
}
function copyWgetCommand(slug: string, request: any) {
  saveClipboard(
    `wget --post-data '${JSON.stringify(request)}' ${location.origin}/${slug}`
  );
}
function defaultRenderer(action: Action): UIElement {
  return title(action, `Unknown Action: ${action.type}`);
}
export function encodeSearch(filters: ActionFilter[]): string {
  return (
    "shesmusearch:" +
    btoa(
      JSON.stringify(filters).replace(
        /[\u007F-\uFFFF]/g,
        (chr) => "\\u" + ("0000" + chr.charCodeAt(0).toString(16)).substr(-4)
      )
    )
  );
}

function collectSearches(
  serverSearches: ServerSearches,
  saved: Iterable<SearchDefinition>
): SearchDefinition[] {
  return [["All Actions", []] as SearchDefinition].concat(
    [
      ...Object.entries(serverSearches).map(
        ([name, filter]) => [name, filter] as SearchDefinition
      ),
      ...saved,
    ].sort((a, b) => a[0].localeCompare(b[0]))
  );
}

export function initialiseActionDash(
  serverSearches: ServerSearches,
  sources: SourceLocation[],
  savedQueryName: string | null,
  userFilters: string | BasicQuery | null,
  exportSearches: ExportSearchCommand[]
) {
  const filenameFormatter = commonPathPrefix(sources.map((s) => s.file));
  const {
    filters: filterSynchonizer,
    saved: savedSynchonizer,
  } = synchronizerFields(
    historyState(
      {
        filters: userFilters === null ? {} : userFilters,
        saved: savedQueryName || "All Actions",
      },
      (input) =>
        `${input.saved} with ${
          typeof input.filters == "string" ? input.filters : "Basic Search"
        }`
    )
  );
  let localSearches: MutableStore<string, ActionFilter[]> = new Map();

  const { actions: actionUi, bulkCommands, model: actionModel } = actionDisplay(
    exportSearches
  );
  const { ui: statsUi, model: statsModel } = actionStats(
    (...limits) => addPropertySearch(...limits),
    (typeName, start, end) => addRangeSearch(typeName, start, end),
    standardExports.concat(exportSearches)
  );
  const { ui: saveUi, model: saveModel } = singleState(
    (input: ActionFilter[]) =>
      buttonAccessory(
        "💾 Add to My Searches",
        "Save this search to the local search collection.",
        () =>
          dialog((close) => {
            const nameBox = inputText();
            return [
              "Name for Search: ",
              nameBox.ui,
              br(),
              button(
                "💾 Add to My Searches",
                "Save this search to the local search collection.",
                () => {
                  const name = nameBox.value.trim();
                  if (name) {
                    localSearches.set(name, input);
                    close();
                  }
                }
              ),
            ];
          })
      ),
    true
  );
  const combinedActionsModel = combineModels(
    actionModel,
    statsModel,
    saveModel
  );
  const {
    buttons,
    entryBar,
    model,
    addPropertySearch,
    addRangeSearch,
  } = createSearch(
    filterSynchonizer,
    combinedActionsModel,
    true,
    filenameFormatter,
    sources
  );
  const { model: deleteModel, ui: deleteUi } = singleState(
    (input: SearchDefinition) =>
      localSearches.get(input[0])
        ? [
            button(
              "✎ Rename Search",
              "Change the name of this search in the local search collection.",
              () =>
                dialog((close) => {
                  const newNameEntry = inputText(input[0]);
                  return [
                    "New name: ",
                    newNameEntry.ui,
                    br(),
                    button("✎ Rename", "Change searchs name.", () => {
                      const newName = newNameEntry.value.trim();
                      if (newName && newName != input[0]) {
                        localSearches.delete(input[0]);
                        localSearches.set(newName, input[1]);
                        close();
                      }
                    }),
                  ];
                })
            ),
            button(
              "✖ Delete Search",
              "Remove this search from your local search collection.",
              () => localSearches.delete(input[0])
            ),
          ]
        : blank()
  );
  const { model: searchModel, ui: searchSelector } = singleState(
    (saved: Iterable<SearchDefinition>): UIElement =>
      dropdown(
        ([name, _filters]: SearchDefinition) => name,
        ([name, _filters]) => name == savedSynchonizer.get(),
        combineModels(
          deleteModel,
          mapModel(model, ([_name, filters]) => filters)
        ),
        {
          synchronizer: savedSynchonizer,
          extract: ([name, _filters]: SearchDefinition) => name,
          predicate: (selected: string, [name, _filters]: SearchDefinition) =>
            name == selected,
        },
        ...collectSearches(serverSearches, saved)
      )
  );
  localSearches = mutableStoreWatcher(
    mutableLocalStore<ActionFilter[]>("shesmu_searches"),
    searchModel
  );

  const tabsUi = tabs(
    { contents: statsUi, name: "Overview" },
    { contents: actionUi, name: "Actions" }
  );
  setRootDashboard(
    "actiondash",
    tile(
      [],
      buttonAccessory(
        "⬆️ Import Search",
        "Add a previously exported search.",
        () =>
          dialog((close) => {
            const addSearch = (
              name: string,
              search: string,
              closeOnFailure: boolean
            ) => {
              let filters: ActionFilter[] | null = null;
              if (search.startsWith("shesmusearch:")) {
                try {
                  filters = JSON.parse(
                    atob(search.substring("shesmusearch:".length))
                  );
                } catch (e) {
                  console.log(e);
                }
              } else {
                filters = JSON.parse(search);
              }
              if (filters) {
                fetchCustomWithBusyDialog(
                  "printquery",
                  {
                    method: "POST",
                    body: JSON.stringify({
                      type: "and",
                      filters: filters,
                    } as ActionFilter),
                  },
                  (p) =>
                    p
                      .then((response) => response.text())
                      .then((_r) => {
                        // We don't really want this value from the server, but it has validated the filter for us.
                        localSearches.set(name, filters!);
                      })
                );
                close();
              } else {
                if (closeOnFailure) {
                  close();
                }
                dialog((_close) => "Provided search is invalid.");
              }
            };
            const name = inputText();
            const search = inputTextArea();
            return [
              "Name for Search: ",
              name.ui,
              br(),
              search.ui,
              br(),
              "Load a search that was generated using ",
              italic("Export Search"),
              " and then one of ",
              italic("To Clipboard"),
              ", ",
              italic("To Clipboard for Ticket"),
              ", or ",
              italic("To File"),
              " will accepteded here.",
              br(),
              buttonAccessory(
                "⬆️ Upload File",
                "Use the contents of a file for the search.",
                () => loadFile((name, data) => addSearch(name, data, true))
              ),
              button(
                "💾 Add to My Searches",
                "Save this search to the local search collection.",
                () => {
                  const nameStr = name.value.trim();
                  const searchStr = search.value.trim();
                  if (nameStr && searchStr) {
                    addSearch(nameStr, searchStr, false);
                  }
                }
              ),
            ];
          })
      ),
      searchSelector,
      saveUi,
      deleteUi,
      helpArea("action")
    ),
    tile([], refreshButton(combinedActionsModel.reload), buttons, bulkCommands),
    tile([], entryBar),
    tabsUi
  );
}

/**
 * Display a standard header for an action
 * @param action the action, as provided by the server
 * @param label a human-friendly description of the action
 */
export function title(action: Action, label: string): UIElement {
  const title =
    action.state + (action.updateInProgress ? " Update in progress" : "");
  const element = action.url
    ? link(action.url, label, title)
    : text(label, title);
  const fileNameFormatter = commonPathPrefix(
    action.locations.map((l: { file: any }) => l.file)
  );
  return [
    element,
    table(
      action.locations,
      ["File", (l: SourceLocation) => fileNameFormatter(l.file)],
      ["Line", (l: SourceLocation) => l.line?.toString() || "*"],
      ["Column", (l: SourceLocation) => l.column?.toString() || "*"],
      ["Source Hash", (l: SourceLocation) => l.hash || "*"],
      [
        "Olive",
        (l: { file: any; line: any; column: any; hash: any }) =>
          link(
            "/olivedash?saved=" +
              encodeURIComponent(
                JSON.stringify({
                  file: l.file,
                  line: l.line,
                  column: l.column,
                  hash: l.hash,
                })
              ),
            "View in Dashboard"
          ),
      ],
      [
        "Source",
        (l: SourceLocation) => (l.url ? link(l.url, "View Source") : blank()),
      ]
    ),
    table(
      [
        ["Last Time Action was Last Run", (a: Action) => a.lastChecked],
        [
          "Time Since Action was Last Generated by an Olive",
          (a: Action) => a.lastAdded,
        ],
        [
          "Last Time Action's Status Last Changed",
          (a: Action) => a.lastStatusChange,
        ],
        ["External Last Modification", (a: Action) => a.external],
      ],
      ["Event", (x: [string, (a: Action) => number]) => x[0]],
      [
        "Time",
        (x: [string, (a: Action) => number]) => {
          const time = x[1](action);
          if (!time) return "Unknown";
          const { ago, absolute } = computeDuration(time);
          return `${absolute} (${ago})`;
        },
      ]
    ),
    tagList("Tags: ", action.tags),
    collapsible(
      "Errors",
      table(action.errors || [], ["Message", (x: any) => x])
    ),
  ];
}
