import {
  ClickHandler,
  IconName,
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
  hr,
  img,
  inputText,
  inputTextArea,
  intervalCounter,
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
  mergingModel,
  mutableStoreWatcher,
} from "./util.js";
import {
  ActionFilter,
  BasicQuery,
  createSearch,
  renderFilters,
} from "./actionfilters.js";
import {
  ShesmuRequestType,
  fetchJsonWithBusyDialog,
  loadFile,
  locallyStored,
  mutableLocalStore,
  paginatedRefreshable,
  refreshable,
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
export interface ActionQueryResponse {
  offset: number;
  total: number;
  results: Action[];
  bulkCommands: BulkCommand[];
}
interface BaseCommand {
  command: string;
  buttonText: string;
  icon: IconName;
  showPrompt: boolean;
}
interface BulkCommand extends BaseCommand {
  annoyUser: boolean;
  count: number;
}
/**
 * The information for a button that can export the current search to some other service
 */
export type ExportSearchCommand = {
  label: string;
  icon: IconName;
  description: string;
  category: string;
  categoryIcon: IconName;
  callback: (filters: ActionFilter[]) => void;
};
/**
 * The format of a saved search ready for use by the UI
 */
type SearchDefinition = [string, ActionFilter[]];
/**
 * The format of searches provided by the server
 */
export type ServerSearches = { [name: string]: ActionFilter[] };
/**
 * The status of an action
 */
export type Status = typeof statuses[number];

export const standardExports: ExportSearchCommand[] = [
  {
    icon: "clipboard",
    label: "To Clipboard",
    description: "Export search to the clipboard.",
    category: "Share",
    categoryIcon: "share",
    callback: saveClipboardJson,
  },
  {
    icon: "clipboard-plus",
    label: "To Clipboard for Ticket",
    description:
      "Export search to the clipboard in a way that can be pasted in a text document.",
    category: "Share",
    categoryIcon: "share",
    callback: (filters) => saveClipboard(encodeSearch(filters)),
  },
  {
    icon: "cloud-download",
    label: "To File",
    description: "Download search as a file.",
    category: "Share",
    categoryIcon: "share",
    callback: (filters) =>
      saveFile(JSON.stringify(filters), "application/json", "My Search.search"),
  },
  {
    icon: "clipboard-data",
    label: "cURL Actions",
    description: "Convert search to a cURL command to extract actions.",
    category: "Command Line",
    categoryIcon: "terminal",
    callback: (filters) =>
      copyCUrlCommand("query", {
        filters: filters,
        skip: 0,
        limit: 100000,
      }),
  },
  {
    icon: "clipboard-data",
    label: "Wget Actions",
    description: "Convert search to a Wget command to extract actions.",
    category: "Command Line",
    categoryIcon: "terminal",
    callback: (filters) =>
      copyWgetCommand("query", {
        filters: filters,
        skip: 0,
        limit: 100000,
      }),
  },
  {
    icon: "clipboard-x",
    label: "cURL Purge",
    description: "Convert search to a cURL command to purge matching actions.",
    category: "Command Line",
    categoryIcon: "terminal",
    callback: (filters) => copyCUrlCommand("purge", filters),
  },
  {
    icon: "clipboard-x",
    label: "Wget Purge",
    description: "Convert search to a Wget command to purge matching actions.",
    category: "Command Line",
    categoryIcon: "terminal",
    callback: (filters) => copyWgetCommand("purge", filters),
  },
];

/**
 * The states an action can be in
 */
export const statuses = [
  "FAILED",
  "HALP",
  "INFLIGHT",
  "QUEUED",
  "SAFETY_LIMIT_REACHED",
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
    case "SAFETY_LIMIT_REACHED":
      return "The action has encountered some user-defined limit stopping it from proceeding.";
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
                      [{ type: "icon", icon: "clipboard" }, "Copy Id"],
                      "Copy action identifier to clipboard.",
                      () => saveClipboard(action.actionId)
                    ),
                    buttonDanger(
                      [
                        { type: "icon", icon: "x-octagon-fill" },
                        "Purge Action",
                      ],
                      "Remove this action from Shesmu. This does not stop an olive from generating it again.",
                      () =>
                        fetchJsonWithBusyDialog(
                          "purge",
                          [
                            {
                              type: "id",
                              ids: [action.actionId],
                            },
                          ],
                          (count) => {
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
                    menuForCommands(
                      "Commands",
                      action.commands,
                      (command) =>
                        `Perform special command ${command.command} on this action.`,
                      (command) =>
                        createCallbackForActionCommand(
                          command,
                          action.actionId,
                          reload
                        )
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
          [{ type: "icon", icon: "cloud-arrow-down" }, "Export Search"],
          "Export this search to a file or the clipboard or for use in other software.",
          () =>
            exportSearchDialog(standardExports.concat(exportSearches), filters)
        ),
        response && response.bulkCommands.length
          ? buttonAccessory(
              [{ type: "icon", icon: "cloud-arrow-down" }, "Export Command"],
              "Generate a command line to perform a command command.",
              () =>
                dialog((_close) =>
                  tableFromRows(
                    response.bulkCommands
                      .sort((a, b) => a.buttonText.localeCompare(b.buttonText))
                      .map(({ buttonText, icon, command }) =>
                        tableRow(
                          null,
                          {
                            contents: [
                              { type: "icon", icon: icon },
                              buttonText,
                            ],
                          },
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
              [{ type: "icon", icon: "x-octagon-fill" }, "Purge Actions"],
              "Remove actions from Shesmu. This does not stop an olive from generating them again.",
              () =>
                fetchJsonWithBusyDialog("purge", filters, (count) => {
                  butterForPurgeCount(count);
                  reload();
                })
            )
          : blank(),
        response
          ? menuForCommands(
              "Bulk Commands",
              response.bulkCommands,
              (command) =>
                `Perform special command ${command.command} on ${command.count} actions.`,
              (command) =>
                createCallbackForBulkCommand(command, filters, reload)
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
      filters: filters,
      limit: pageLength,
      skip: offset,
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

export function butterForPurgeCount(count: number) {
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
  butter(3000, `Removed ${count} actions.`, br(), img(imgSrc));
}

function createCallbackForActionCommand(
  command: ActionCommand,
  id: string,
  reload: () => void
): () => void {
  let performCommand = () =>
    fetchJsonWithBusyDialog(
      "command",
      {
        command: command.command,
        filters: [
          {
            type: "id",
            ids: [id],
          },
        ],
      },
      (count) => {
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
  if (command.showPrompt) {
    const realPerform = performCommand;
    performCommand = () => {
      dialog((close) => [
        `Perform command ${command.command} on ${id}? This is your moment of sober second thought.`,
        br(),
        buttonDanger(
          [
            { type: "icon", icon: command.icon },
            command.buttonText.toUpperCase(),
          ],
          "Really do it!",
          () => {
            close();
            realPerform();
          }
        ),
        br(),
        button("Back away slowly", "Don't do anything.", close),
      ]);
    };
  }
  return performCommand;
}

function createCallbackForBulkCommand(
  command: BulkCommand,
  filters: ActionFilter[],
  reload: () => void
): () => void {
  let performCommand = () =>
    fetchJsonWithBusyDialog(
      "command",
      {
        command: command.command,
        filters: filters,
      },
      (count) => {
        butter(
          5000,
          { type: "icon", icon: command.icon },
          "The command ",
          { type: "i", contents: command.buttonText },
          `executed on ${count} actions.`
        );
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
        buttonDanger(
          [
            { type: "icon", icon: command.icon },
            command.buttonText.toUpperCase(),
          ],
          "Really do it!",
          () => {
            close();
            realPerform();
          }
        ),
        br(),
        button("Back away slowly", "Don't do anything.", close),
      ]);
    };
  }
  return performCommand;
}

function copyCUrlCommand<K extends keyof ShesmuRequestType>(
  slug: K,
  request: ShesmuRequestType[K]
) {
  saveClipboard(
    `curl -d '${JSON.stringify(request)}' -X POST ${location.origin}/${slug}`
  );
}
function copyWgetCommand<K extends keyof ShesmuRequestType>(
  slug: K,
  request: ShesmuRequestType[K]
) {
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

export function exportSearchDialog(
  exportSearches: ExportSearchCommand[],
  filters: ActionFilter[]
): void {
  dialog((close) =>
    exportSearches
      .sort(
        (a, b) =>
          a.category.localeCompare(b.category) || a.label.localeCompare(b.label)
      )
      .map(
        (
          { icon, label, description, category, categoryIcon, callback },
          index,
          arr
        ) => [
          index == 0 || arr[index - 1].category != category
            ? [
                index > 0 ? br() : blank(),
                { type: "icon", icon: categoryIcon },
                { type: "b", contents: category },
                br(),
              ]
            : blank(),
          button([{ type: "icon", icon: icon }, label], description, () => {
            callback(filters);
            close();
          }),
        ]
      )
  );
}

function collectSearches(
  serverSearches: ServerSearches | null,
  saved: Iterable<SearchDefinition> | null
): SearchDefinition[] {
  return [["All Actions", []] as SearchDefinition].concat(
    [
      ...(serverSearches
        ? Object.entries(serverSearches).map(
            ([name, filter]) => [name, filter] as SearchDefinition
          )
        : []),
      ...(saved || []),
    ].sort((a, b) => a[0].localeCompare(b[0]))
  );
}

export function initialiseActionDash(
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
      "actiondash",
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
    (typeName, start, end, ...limits) =>
      addRangeSearch(typeName, start, end, ...limits),
    filenameFormatter,
    standardExports.concat(exportSearches)
  );
  const { ui: saveUi, model: saveModel } = singleState(
    (input: ActionFilter[]) =>
      buttonAccessory(
        [{ type: "icon", icon: "bookmark-heart" }, "Add to My Searches"],
        "Save this search to the local search collection.",
        () =>
          dialog((close) => {
            const nameBox = inputText();
            return [
              "Name for Search: ",
              nameBox.ui,
              br(),
              button(
                [
                  { type: "icon", icon: "bookmark-heart" },
                  "Add to My Searches",
                ],
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
            buttonAccessory(
              [{ type: "icon", icon: "pencil" }, "Rename Search"],
              "Change the name of this search in the local search collection.",
              () =>
                dialog((close) => {
                  const newNameEntry = inputText(input[0]);
                  return [
                    "New name: ",
                    newNameEntry.ui,
                    br(),
                    button(
                      [{ type: "icon", icon: "pencil" }, "Rename"],
                      "Change searchs name.",
                      () => {
                        const newName = newNameEntry.value.trim();
                        if (newName && newName != input[0]) {
                          localSearches.delete(input[0]);
                          localSearches.set(newName, input[1]);
                          close();
                        }
                      }
                    ),
                  ];
                })
            ),
            buttonAccessory(
              [{ type: "icon", icon: "bookmark-x" }, "Delete Search"],
              "Remove this search from your local search collection.",
              () => localSearches.delete(input[0])
            ),
          ]
        : blank()
  );
  const {
    ui: baseSearchUi,
    model: baseSearchModel,
  } = singleState(([_, filters]: [string, ActionFilter[]]) =>
    tile(["filters"], renderFilters(filters, filenameFormatter))
  );
  const { model: searchModel, ui: searchSelector } = singleState(
    (searches: SearchDefinition[]): UIElement =>
      dropdown(
        ([name, _filters]: SearchDefinition) => name,
        ([name, _filters]) => name == savedSynchonizer.get(),
        combineModels(
          deleteModel,
          baseSearchModel,
          mapModel(model, ([_name, filters]) => filters)
        ),
        {
          synchronizer: savedSynchonizer,
          extract: ([name, _filters]: SearchDefinition) => name,
          predicate: (selected: string, [name, _filters]: SearchDefinition) =>
            name == selected,
        },
        ...searches
      )
  );
  const [serverSearchModel, localSearchModel] = mergingModel(
    searchModel,
    collectSearches,
    true
  );
  localSearches = mutableStoreWatcher(
    mutableLocalStore<ActionFilter[]>("shesmu_searches"),
    localSearchModel
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
        [{ type: "icon", icon: "bookmark-plus" }, "Import Search"],
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
                fetchJsonWithBusyDialog(
                  "printquery",
                  {
                    type: "and",
                    filters: filters,
                  },
                  (_r) => {
                    // We don't really want this value from the server, but it has validated the filter for us.
                    localSearches.set(name, filters!);
                  }
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
                [{ type: "icon", icon: "upload" }, "Upload File"],
                "Use the contents of a file for the search.",
                () => loadFile((name, data) => addSearch(name, data, true))
              ),
              button(
                [
                  { type: "icon", icon: "bookmark-heart" },
                  "Add to My Searches",
                ],
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
      intervalCounter(
        900_000, // 15 minutes
        mapModel(
          refreshable(
            "savedsearches",
            mapModel(serverSearchModel, (input) => input || {}),
            false
          ),
          (_) => null
        ),
        {
          label: [{ type: "icon", icon: "bookmark-check" }, "Refresh Searches"],
          title: "Load any updated searches from the server.",
          synchronizer: locallyStored<boolean>("shesmu_update_searches", true),
        }
      ),
      helpArea("action")
    ),
    tile([], refreshButton(combinedActionsModel.reload), buttons, bulkCommands),
    tile([], collapsible("Base Search Filter", baseSearchUi, br())),
    tile([], entryBar),
    tabsUi
  );
}

function menuForCommands<T extends BaseCommand>(
  overflowName: string,
  commands: T[],
  titleGenerator: (command: T) => string,
  callbackGenerator: (commad: T) => () => void
): UIElement {
  return commands.length < 4
    ? commands
        .sort((a, b) => a.buttonText.localeCompare(b.buttonText))
        .map((command) =>
          buttonDanger(
            [{ type: "icon", icon: command.icon }, command.buttonText],
            titleGenerator(command),
            callbackGenerator(command)
          )
        )
    : buttonDanger(
        [{ type: "icon", icon: "wrench" }, overflowName, " ▼"],
        "Perform action-specific commands.",
        popupMenu(
          true,
          ...commands
            .sort((a, b) => a.buttonText.localeCompare(b.buttonText))
            .map((command) => ({
              label: [
                { type: "icon" as const, icon: command.icon },
                command.buttonText,
              ],
              action: callbackGenerator(command),
            }))
        )
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
