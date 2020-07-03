import { saveFile } from "./io.js";
import { computeDuration, StatefulModel } from "./util.js";
/**
 * A function to render an item that can handle click events.
 */
export type ActiveItemRenderer<T> = (item: T, click: ClickHandler) => UIElement;
/**
 * The callback for handling mouse events
 */
export type ClickHandler = (e: MouseEvent) => void;

/**
 * A row in a drop down table
 */
export interface DropdownTableRow<T> {
  /** The value that is selected*/
  value: T;
  /** The cells that should be put in the table for this entry */
  label: TableCell[];
}
/**
 * A minitable in a drop-down table
 */
export interface DropdownTableSection<T> {
  /** The value that is selected when this section is selected*/
  value: T;
  /**
   * The title for this table
   */
  label: UIElement;
  /**
   * The entries in the table
   */
  children: DropdownTableRow<T>[];
}
/**
 * A function which will intercept Ctrl-F
 * @returns true if it wishes to intercept the browser's default behaviour
 */
export type FindHandler = (() => boolean) | null;

/**
 * A mapping type for UI elements that have a multi-pane display
 *
 * TypeScript allows mapping an input object to a transformed output type. The caller will provide an object of functions to generate different panes will receive an output object of those panes with the same keys. This mapper defines that operation to the type system.
 */
export type NamedComponents<T> = {
  [P in keyof T]: UIElement;
};

/**
 * A callback that is capable of listening to updates in the state synchronizer.
 */
export type StateListener<T> = ((state: T) => void) | null;
/**
 * An interface to keep two different system synchonrized
 */
export interface StateSynchronizer<T> extends StatefulModel<T> {
  /**
   * Read the current state.
   */
  get(): T;
  /**
   * Set a listener to listen for state updates
   *
   * Only one listener is selected at a time
   */
  listen(listener: StateListener<T>): void;
}
/**
 * An accessor for synchronizing the fields in an object independently.
 */
type SynchronizedFields<T> = {
  [P in keyof T]: StateSynchronizer<T[P]>;
};
/**
 * The type of a single tab in a multi-tabbed display
 */
export interface Tab {
  /** The header title for the tab */
  name: string;
  /** The contents the body of the tab */
  contents: UIElement;
  /**Find a function to replace Ctrl-F when the tab is active */
  find?: FindHandler;
  /** If true, this tab will be selected by default*/
  selected?: boolean;
}
/**
 * The contents of a table cell
 */
export interface TableCell {
  /**
   * A callback if the cell is clicked
   */
  click?: ClickHandler;
  /**
   * The elements that should be in the cell.
   */
  contents: UIElement;
  /**
   * If true, the cell is a header; if false or absent, it is a data cell.
   */
  header?: boolean;
  /**
   * The background colour intensity between 0 and 1
   *
   * This is used by some stats tables to highlight cells based on their numeric counts as a fraction of the total. Unconventionally, 0 is white (no intensity) and 1 is a theme-appropriate blue.
   */
  intensity?: number;
  /**
   * The number of columns to span
   */
  span?: number;
  /**
   * A tooltip for the cell
   */
  title?: string;
}
/**
 * A bit of data that can be placed in a GUI element.
 */
export type UIElement = UIElement[] | string | Node;

/**
 * A list that can be updated
 */
export interface UpdateableList<T> {
  add(item: T): void;
  keepOnly(predicate: (item: T) => boolean): void;
  replace(items: T[]): void;
}
let activeMenu: HTMLElement | null = null;
let closeActiveMenu: (isExternal: boolean) => void = (v) => {};
let findOverride: FindHandler = null;

/**
 * Add all GUI elements to an existing HTML element
 */
export function addElements(
  target: HTMLElement,
  ...elements: UIElement[]
): void {
  elements.flat(Number.MAX_VALUE).forEach((result: string | Node) => {
    if (typeof result == "string") {
      target.appendChild(document.createTextNode(result));
    } else {
      target.appendChild(result);
    }
  });
}

/**
 * Add a blank UI element
 *
 * This isn't very useful, but simplifies some code paths in the action tile building.
 */
export function blank(): UIElement {
  return [];
}
/**
 * Create a line break
 */
export function br(): UIElement {
  return document.createElement("br");
}

/**
 * Create a modal dialog with a throbber in the middle.
 *
 * It returns a function to close this dialog
 */
export function busyDialog(): () => void {
  const modal = document.createElement("div");
  modal.className = "modal";
  addElements(modal, throbber());
  document.body.appendChild(modal);
  return () => document.body.removeChild(modal);
}

/**
 * Create a normal button
 */
export function button(
  label: UIElement,
  title: string,
  callback: ClickHandler
) {
  return buttonCustom(label, title, [], callback);
}

/**
 * Create a button for a less important feature
 *
 * In the Shesmu UI, some features (such as exporting searches) as considered less important, and get a softer colour than the normal buttons.
 */
export function buttonAccessory(
  label: UIElement,
  title: string,
  callback: ClickHandler
) {
  return buttonCustom(label, title, ["accessory"], callback);
}

/**
 * Create a button with a custom UI design
 */
export function buttonCustom(
  label: UIElement,
  title: string,
  className: string[],
  callback: ClickHandler
) {
  const button = document.createElement("span");
  button.classList.add("load");
  for (const name of className) {
    button.classList.add(name);
  }
  addElements(button, label);
  button.title = title;
  button.addEventListener("click", callback);
  return button;
}

/**
 * Create a close button
 */
export function buttonClose(title: string, callback: ClickHandler): UIElement {
  return buttonIcon("✖", title, callback);
}
/**
 * Create a button for a dangerous feature
 */
export function buttonDanger(
  label: UIElement,
  title: string,
  callback: ClickHandler
) {
  return buttonCustom(label, title, ["danger"], callback);
}
/**
 * Create an edit button
 */
export function buttonEdit(title: string, callback: ClickHandler): UIElement {
  return buttonIcon("✎", title, callback);
}
/**
 * Create a button that has no “button” styling, used for features where naked icons are helpful
 */
export function buttonIcon(
  icon: string,
  title: string,
  callback: ClickHandler
): UIElement {
  const button = document.createElement("span");
  button.className = "close";
  button.innerText = icon;
  button.title = title;
  button.style.cursor = "pointer";
  button.addEventListener("click", (e) => {
    e.stopPropagation();
    callback(e);
  });
  return button;
}

/**
 * Remove all child nodes from an element
 */
export function clearChildren(container: HTMLElement) {
  while (container.hasChildNodes()) {
    container.removeChild(container.lastChild!);
  }
}

/**
 * A collapsible section
 * @param title the name of the section
 * @param inner the contents of the section
 */
export function collapsible(title: string, ...inner: UIElement[]): UIElement {
  const contents = document.createElement("div");
  addElements(contents, ...inner);
  if (!contents.hasChildNodes()) {
    return [];
  }
  const showHide = document.createElement("p");
  showHide.className = "collapse close";
  showHide.innerText = title;
  showHide.onclick = (e) => {
    const visible = !contents.style.maxHeight;

    showHide.className = visible ? "collapse open" : "collapse close";
    contents.style.maxHeight = visible ? `${contents.scrollHeight}px` : "none";
  };
  return [showHide, contents];
}
const months: string[] = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

/**
 * Create a date picker that can be disabled
 */
export function dateEditor(
  initial: number | null
): { ui: UIElement; getter: () => number | null } {
  const enabled = inputCheckbox("Unbounded", !initial);
  const selected = typeof initial == "number" ? new Date(initial) : new Date();
  const year = inputNumber(selected.getFullYear(), 1970, null);
  const initialMonth: [number, string] = [
    selected.getMonth(),
    months[selected.getMonth()] || "Unknown",
  ];
  const monthModel = temporaryState(initialMonth);
  const month = dropdown(
    ([_month, name]: [number, string]) => name,
    initialMonth,
    monthModel,
    null,
    ...months.entries()
  );
  const day = inputNumber(selected.getDate(), 1, 31);
  const hour = inputNumber(selected.getHours(), 0, 23);
  const minute = inputNumber(selected.getMinutes(), 0, 59);

  return {
    ui: [
      enabled.ui,
      br(),
      year.ui,
      month,
      day.ui,
      " ",
      hour.ui,
      ":",
      minute.ui,
    ],
    getter: () =>
      enabled.getter()
        ? null
        : new Date(
            year.getter(),
            monthModel.get()[0],
            day.getter(),
            hour.getter(),
            minute.getter(),
            0,
            0
          ).getTime(),
  };
}

/**
 * Show a dialog box with the provided contents
 *
 *  @param contents a callback to generate the contents of the dialog; it is given a function to close the dialog
 * @param afterClose an optional callback that will be invoked when the dialog is closed for any reason
 */

export function dialog(
  contents: (close: () => void) => UIElement,
  afterClose?: () => void
): void {
  const modal = document.createElement("div");
  modal.className = "modal close";

  const dialog = document.createElement("div");
  modal.appendChild(dialog);

  const closeButton = document.createElement("div");
  closeButton.innerText = "✖";

  dialog.appendChild(closeButton);

  const inner = document.createElement("div");
  dialog.appendChild(inner);

  document.body.appendChild(modal);
  modal.addEventListener("click", (e) => {
    if (e.target == modal) {
      document.body.removeChild(modal);
      if (afterClose) {
        afterClose();
      }
    }
  });
  const close = () => {
    document.body.removeChild(modal);
    if (afterClose) {
      afterClose();
    }
  };
  closeButton.addEventListener("click", close);
  inner.addEventListener("click", (e) => e.stopPropagation());
  addElements(inner, contents(close));
}
/**
 * Create a drop down list
 *
 * This acts like a passive user input that gets read on demand.
 * @param labelMaker a function to produce a label the user will see for the label
 * @param model a model to manage this dropdown's state
 * @returns the UI element to change
 */
export function dropdown<T, S>(
  labelMaker: (input: T) => UIElement,
  initial: T | null,
  model: StatefulModel<T>,
  synchronizer: {
    synchronizer: StateSynchronizer<S>;
    predicate: (recovered: S, item: T) => boolean;
    extract: (item: T) => S;
  } | null,
  ...items: T[]
): UIElement {
  const container = document.createElement("span");
  container.className = "dropdown";
  const activeElement = document.createElement("span");
  container.appendChild(activeElement);
  container.appendChild(document.createTextNode(" ▼"));
  const listElement = document.createElement("div");
  container.appendChild(listElement);
  let open = false;
  container.addEventListener("click", (e) => {
    if (e.target == activeElement.parentNode || e.target == activeElement) {
      if (open) {
        open = false;
        closeActiveMenu(false);
        return;
      }
      closeActiveMenu(true);
      open = true;
      listElement.className = "forceOpen";
      activeMenu = activeElement;
      closeActiveMenu = (external) => {
        listElement.className = external ? "ready" : "";
        open = false;
        activeMenu = null;
      };
    }
  });
  container.addEventListener("mouseover", (e) => {
    if (e.target == listElement.parentNode && !open) {
      closeActiveMenu(true);
    }
  });
  container.addEventListener("mouseout", () => {
    if (!open) {
      listElement.className = "ready";
    }
  });
  const synchronizerCallbacks: ((state: S) => void)[] = [];
  for (const item of items) {
    const element = document.createElement("span");
    const label = labelMaker(item);
    addElements(element, label);
    element.addEventListener("click", (e) => {
      model.statusChanged(item);
      clearChildren(activeElement);
      addElements(activeElement, label);
      if (open) {
        closeActiveMenu(false);
      }
    });
    if (item == initial || item == items[0]) {
      clearChildren(activeElement);
      addElements(activeElement, label);
      model.statusChanged(item);
      if (synchronizer) {
        synchronizer.synchronizer.statusChanged(synchronizer.extract(item));
      }
    }
    synchronizerCallbacks.push((state) => {
      if (synchronizer?.predicate(state, item)) {
        clearChildren(activeElement);
        addElements(activeElement, label);
        model.statusChanged(item);
      }
    });
    listElement.appendChild(element);
  }
  if (synchronizer) {
    synchronizer.synchronizer.listen((value) =>
      synchronizerCallbacks.forEach((callback) => callback(value))
    );
  }
  return container;
}
/**
 * Create a drop down table that can be filtered
 *
 * This acts like a passive user input that gets read on demand.
 * @param model a model to manage this dropdown's state
 * @param activeLabelMaker a function to produce a label the user will see for the label
 * @param searchPredicate a function that determines if the current user search keywords match an item
 * @returns the UI element to change
 */
export function dropdownTable<T, S>(
  model: StatefulModel<T | null>,
  synchronizer: {
    synchronzier: StateSynchronizer<S>;
    predicate: (recovered: S, item: T | null) => boolean;
    extract: (item: T | null) => S;
  } | null,
  activeLabelMaker: (input: T | null) => UIElement,
  searchPredicate: (input: T | null, keywords: string[]) => boolean,
  ...items: DropdownTableSection<T | null>[]
): UIElement {
  const container = document.createElement("span");
  container.className = "dropdown";
  const activeElement = document.createElement("span");
  addElements(activeElement, activeLabelMaker(null));
  container.appendChild(activeElement);
  container.appendChild(document.createTextNode(" ▼"));
  const listElement = document.createElement("div");
  container.appendChild(listElement);
  const searchFilters: ((keywords: string[]) => void)[] = [];
  addElements(
    listElement,
    group(
      "Filter: ",
      inputSearch((input) => {
        const keywords = input
          .toLowerCase()
          .split(/\W+/)
          .filter((s) => s);
        for (const searchFilter of searchFilters) {
          searchFilter(keywords);
        }
      })
    )
  );
  let open = false;
  container.addEventListener("click", (e) => {
    if (e.target == activeElement.parentNode || e.target == activeElement) {
      if (open) {
        open = false;
        closeActiveMenu(false);
        return;
      }
      closeActiveMenu(true);
      open = true;
      listElement.className = "forceOpen";
      activeMenu = activeElement;
      closeActiveMenu = (external) => {
        listElement.className = external ? "ready" : "";
        open = false;
        activeMenu = null;
      };
    }
  });
  container.addEventListener("mouseover", (e) => {
    if (e.target == listElement.parentNode && !open) {
      closeActiveMenu(true);
    }
  });
  container.addEventListener("mouseout", () => {
    if (!open) {
      listElement.className = "ready";
    }
  });
  const synchronizerCallbacks: ((state: S) => void)[] = [];
  if (items.length) {
    for (const { value, label, children } of items) {
      const block = document.createElement("div");
      const groupLabel = document.createElement("span");
      addElements(groupLabel, label);
      groupLabel.addEventListener("click", (e) => {
        model.statusChanged(value);
        if (synchronizer) {
          synchronizer.synchronzier.statusChanged(synchronizer.extract(value));
        }
        clearChildren(activeElement);
        addElements(activeElement, activeLabelMaker(value));
        if (open) {
          closeActiveMenu(false);
        }
      });
      searchFilters.push((keywords) => {
        block.style.display =
          searchPredicate(value, keywords) ||
          children.some((child) => searchPredicate(child.value, keywords))
            ? "block"
            : "none";
      });
      block.appendChild(groupLabel);
      synchronizerCallbacks.push((state) => {
        if (synchronizer?.predicate(state, value)) {
          clearChildren(activeElement);
          addElements(activeElement, activeLabelMaker(value));
          model.statusChanged(value);
        }
      });
      if (children.length) {
        addElements(
          block,
          tableFromRows(
            children.map((child) => {
              const row = tableRow(() => {
                model.statusChanged(child.value);
                if (synchronizer) {
                  synchronizer.synchronzier.statusChanged(
                    synchronizer.extract(child.value)
                  );
                }
                clearChildren(activeElement);
                addElements(activeElement, activeLabelMaker(child.value));
              }, ...child.label);
              searchFilters.push((keywords) => {
                row.style.display = searchPredicate(child.value, keywords)
                  ? "block"
                  : "none";
              });
              synchronizerCallbacks.push((state) => {
                if (synchronizer?.predicate(state, child.value)) {
                  clearChildren(activeElement);
                  addElements(activeElement, activeLabelMaker(child.value));
                  model.statusChanged(child.value);
                }
              });
              return row;
            })
          )
        );
      }
      listElement.appendChild(block);
    }
  } else {
    addElements(listElement, "No items.");
  }
  model.statusChanged(null);
  if (synchronizer) {
    synchronizer.synchronzier.listen((value) =>
      synchronizerCallbacks.forEach((callback) => callback(value))
    );
  }
  return container;
}

/**
 * In some update contexts, the find handler may need to be replocated between split context. This create a proxy to allow rewiriting a find handler.
 */
export function findProxy(): {
  find: FindHandler;
  updateHandle: (original: FindHandler) => void;
  update: (original: { ui: UIElement; find: FindHandler }) => UIElement;
  updateMany: <T>(original: { components: T; find: FindHandler }) => T;
} {
  let findHandler: FindHandler | null = null;
  return {
    find: () => (findHandler ? findHandler() : false),
    updateHandle: (replacement) => (findHandler = replacement),
    update: (original) => {
      findHandler = original.find;
      return original.ui;
    },
    updateMany: (original) => {
      findHandler = original.find;
      return original.components;
    },
  };
}

/**
 * Create a group of elements with flexbox layout
 */
export function flexGroup(...contents: UIElement[]): UIElement {
  const element = document.createElement("span");
  element.style.display = "flex";
  addElements(element, ...contents);
  return element;
}
/**
 * Create a group of elements
 */
export function group(...contents: UIElement[]): UIElement {
  const element = document.createElement("span");
  addElements(element, ...contents);
  return element;
}
/**
 * Display items in section header
 */
export function header(title: string): UIElement {
  const element = document.createElement("h2");
  element.innerText = title;
  return element;
}

/**
 * Create a way to synchronize the browser history with an object
 * @param initial the state to use on start up
 * @param title a function to produce a title; this isn't displayed anywhere, but might be in the future according to Mozilla
 */
export function historyState<T extends { [name: string]: any }>(
  initial: T,
  title: (input: T) => string
): StateSynchronizer<T> {
  let listener: StateListener<T> = null;
  let current = initial;
  window.addEventListener("popstate", (e) => {
    if (e.state) {
      current = e.state as T;
      if (listener) {
        listener(current);
      }
    }
  });

  return {
    reload: () => {},
    statusChanged: (input: T) => {
      if (input != current) {
        current = input;
        window.history.pushState(
          input,
          title(input),
          window.location.pathname +
            "?" +
            Object.entries(input)
              .map(
                ([key, value]) =>
                  key + "=" + encodeURIComponent(JSON.stringify(value))
              )
              .join("&")
        );
      }
    },
    statusFailed: (message, retry) => console.log(message),
    statusWaiting: () => {},
    get(): T {
      return current;
    },
    listen: (newListener: StateListener<T>) => {
      listener = newListener;
      if (listener) {
        listener(current);
      }
    },
  };
}

/**
 * Create an image
 */
export function img(src: string): UIElement {
  const image = document.createElement("img");
  image.src = src;
  return image;
}
/**
 * Set up the general handlers for the UI
 */
export function initialise() {
  document.addEventListener("click", (e: MouseEvent) => {
    if (activeMenu != null) {
      for (
        let targetElement: Node | null = e.target as Node;
        targetElement;
        targetElement = targetElement.parentNode
      ) {
        if (targetElement == activeMenu.parentNode) {
          return;
        }
      }
      closeActiveMenu(true);
    }
  });
  window.addEventListener("keydown", (e) => {
    if (
      findOverride &&
      (e.keyCode === 114 || (e.ctrlKey && e.keyCode === 70)) &&
      findOverride()
    ) {
      e.preventDefault();
    }
  });
}
/**
 * Create a checkbox
 * @param label the text associated with the checkbox
 * @param initial the default state of the check box
 * @returns the UI element and a function to check the current check status
 */
export function inputCheckbox(
  label: string,
  initial: boolean
): { ui: UIElement; getter: () => boolean } {
  const labelElement = document.createElement("label");
  const checkbox = document.createElement("input");
  checkbox.type = "checkbox";
  checkbox.checked = initial;
  labelElement.appendChild(checkbox);
  labelElement.appendChild(document.createTextNode(label));
  return { ui: labelElement, getter: () => checkbox.checked };
}
/**
 * Create a search input box.
 */
export function inputSearch(updateHandler: (input: string) => void): UIElement {
  const input = document.createElement("input");
  input.type = "search";
  input.addEventListener("input", (e) => {
    updateHandler(input.value.trim());
  });

  return input;
}
export function inputNumber(
  value: number,
  min: number,
  max: number | null
): { ui: UIElement; getter: () => number; enable: (state: boolean) => void } {
  const input = document.createElement("input");
  input.type = "number";
  input.min = min.toString();
  if (max) {
    input.max = max.toString();
  }
  input.value = value.toString();
  return {
    ui: input,
    getter: () => input.valueAsNumber,
    enable: (state) => (input.disabled = !state),
  };
}

/**
 * Create a text input box.
 */
export function inputText(
  initial?: string
): { ui: UIElement; getter: () => string } {
  const input = document.createElement("input");
  input.type = "text";
  if (initial) {
    input.value = initial;
  }
  return { ui: input, getter: () => input.value };
}
/**
 * Create a big text input box.
 */
export function inputTextArea(
  initial?: string
): { ui: UIElement; getter: () => string } {
  const input = document.createElement("textarea");
  if (initial) {
    input.value = initial;
  }
  return { ui: input, getter: () => input.value };
}
/**
 * Display some italic text.
 */
export function italic(text: string): UIElement {
  const element = document.createElement("i");
  element.innerText = text;
  return element;
}

/**
 * Show an an action with a `parameters` object as a table of key-value pairs with the values being formatted as human-friendly JSON.
 */
export function jsonParameters(action: { parameters: object }): UIElement {
  return objectTable(action.parameters, "Parameters", (x: any) =>
    JSON.stringify(x, null, 2)
  );
}

/**
 * Create a hyperlink
 * @param url the target of the hyperlink
 * @param contents the label for the link
 * @param title an optional tooltip
 */
export function link(
  url: string,
  contents: string | number,
  title?: string
): UIElement {
  const element = document.createElement("a");
  element.innerText = `${contents} 🔗`;
  element.target = "_blank";
  element.href = url;
  element.title = title || "";
  return element;
}
/**
 * Display some monospaced text.
 */
export function mono(text: string): UIElement {
  const element = document.createElement("span");
  element.style.fontFamily = "monospace";
  element.innerText = text;
  return element;
}
/**
 * This create multiple panels with some shared state that can be updated
 * @param primary the main panel that should display error notification
 * @param formatters a collection of callback that will be called to update the contents of the panes when the state changes
 * @param silentOnChange if a panel name is listed here, it will be blank when updating/showing an error. This is useful for toolbars
 */
export function multipaneState<
  T,
  F extends { [name: string]: (input: T) => UIElement }
>(
  primary: keyof F | null,
  formatters: F,
  ...silentOnChange: (keyof F)[]
): { model: StatefulModel<T>; components: NamedComponents<F> } {
  const updaters: ((input: T) => void)[] = [];
  const rawUpdaters: ((input: UIElement) => void)[] = [];
  let primaryUpdater: (input: UIElement) => void = () => {};
  const panes = Object.fromEntries(
    Object.entries(formatters).map(([name, formatter], index) => {
      const { ui, update } = pane();
      updaters.push((input) => update(formatter(input)));
      if (silentOnChange.includes(name)) {
        rawUpdaters.push((_element) => update(blank()));
      } else {
        rawUpdaters.push(update);
      }
      if (index == 0 || name == primary) {
        primaryUpdater = update;
      }
      return [name, ui];
    })
  ) as NamedComponents<F>;

  return {
    model: {
      reload: () => {},
      statusChanged: (input: T) => {
        for (const updater of updaters) {
          updater(input);
        }
      },
      statusWaiting: () => {
        for (const updater of rawUpdaters) {
          updater(throbberSmall());
        }
        primaryUpdater(throbber());
      },
      statusFailed: (message: string, retry: (() => void) | null) => {
        for (const updater of rawUpdaters) {
          updater(img("dead.svg"));
        }
        primaryUpdater([
          text(message),
          retry ? button("Retry", "Attempt operation again.", retry) : blank(),
        ]);
      },
    },
    components: panes,
  };
}

/**
 * Display an object as a table of key-value pairs with a custom value display format
 * @param object the object to display
 * @param title the label for the table
 * @param valueFormatter a function to display the values in the table
 */
export function objectTable<T>(
  object: { [propertyName: string]: T },
  title: string,
  valueFormatter: (value: T) => string
) {
  return collapsible(
    title,
    table(
      Object.entries(object).sort((a: [string, T], b: [string, T]) =>
        a[0].localeCompare(b[0])
      ),
      ["Name", (x: [string, T]) => x[0]],
      ["Value", (x: [string, T]) => valueFormatter(x[1])]
    )
  );
}

/**
 * Create a selection of numbered buttons for a pager
 */
export function pager(
  numButtons: number,
  current: number,
  drawPager: (index: number) => void
): UIElement {
  const pager = document.createElement("span");
  let rendering = true;
  if (numButtons > 1) {
    for (let i = 0; i < numButtons; i++) {
      if (
        i <= 2 ||
        i >= numButtons - 2 ||
        (i >= current - 2 && i <= current + 2)
      ) {
        rendering = true;
        const page = document.createElement("span");
        const index = i;
        page.innerText = `${index + 1}`;
        if (index != current) {
          page.className = "load accessory";
          page.addEventListener("click", () => drawPager(index));
        }
        pager.appendChild(page);
      } else if (rendering) {
        const ellipsis = document.createElement("span");
        ellipsis.innerText = "...";
        pager.appendChild(ellipsis);
        rendering = false;
      }
    }
  }
  return pager;
}
/**
 * Create a paginated list of downloadable data
 * @param filename the name to use for downloading
 * @param data the total set of data to use
 * @param render a function to render a subset of the data that is provided
 * @param predicate a function to determine if an entry matches keywords the user has entered in the filter box
 */
export function paginatedList<T>(
  filename: string,
  data: T[],
  render: (items: T[]) => UIElement,
  predicate: (item: T, keywords: string[]) => boolean
): UIElement {
  let condition = (x: T) => true;
  const { ui, update } = pane();
  const showData = () => {
    const selectedData = data.filter(condition);
    const numPerPage = 10;
    const numButtons = Math.ceil(selectedData.length / numPerPage);
    const drawPager = (current: number) => {
      update(
        pager(numButtons, current, drawPager),
        render(
          selectedData.slice(current * numPerPage, (current + 1) * numPerPage)
        )
      );
    };
    drawPager(0);
  };
  showData();
  return group(
    group(
      button("📁 Download", "Download data as a file.", () => {
        saveFile(JSON.stringify(data), "application/json", filename);
      }),
      button(
        "📁 Download Selected",
        "Download filtered data as a file.",
        () => {
          saveFile(
            JSON.stringify(data.filter(condition)),
            "application/json",
            filename
          );
        }
      ),
      " Filter: ",
      inputSearch((search) => {
        const keywords = search.toLowerCase().split(/\W+/);
        if (keywords.length) {
          condition = (x) => predicate(x, keywords);
        } else {
          condition = (x) => true;
        }
        showData();
      })
    ),
    ui
  );
}
/**
 * Create a mutable section of UI.
 */
export function pane(): {
  ui: UIElement;
  update: (...elements: UIElement[]) => void;
} {
  const element = document.createElement("span");
  return {
    ui: element,
    update: (...contents) => {
      clearChildren(element);
      addElements(element, ...contents);
    },
  };
}
/**
 * Display items in a paragraph node
 */
export function paragraph(...contents: UIElement[]): UIElement {
  const element = document.createElement("p");
  addElements(element, ...contents);
  return element;
}

/**
 * Display preformatted text.
 */
export function preformatted(text: string): UIElement {
  const pre = document.createElement("pre");
  pre.style.overflowX = "scroll";
  pre.innerText = text;
  return pre;
}
/**
 * Create a standard refresh button
 */
export function refreshButton(callback: () => void): UIElement {
  return button(
    "🔄 Refresh",
    "Update current view with new data from server.",
    callback
  );
}

/**
 * Set the global Ctrl-F interceptor
 */
export function setFindHandler(handler: FindHandler | null) {
  findOverride = handler;
}
/** Create a dialog of buttons that can be multi-selected and filtered
 * @param items the items to display
 * @param setItems a callback for the selected output
 * @param render generate a label and tooltop for each item
 * @param predicated a function to determine if an item matches the search filter
 * @param breakLines whether each button should appear on a separated lines
 */
export function pickFromSet<T>(
  items: readonly T[],
  setItems: (results: T[]) => void,
  render: (item: T) => [string, string],
  predicate: (item: T, keywords: string[]) => boolean,
  breakLines: boolean
) {
  pickFromSetCustom(
    items,
    setItems,
    (item, click) => {
      const [name, title] = render(item);
      return button(name, title, click);
    },
    predicate,
    breakLines
  );
}
/** Create a dialog of buttons that can be multi-selected and filtered
 * @param items the items to display
 * @param setItems a callback for the selected output
 * @param render generate a button for each item
 * @param predicated a function to determine if an item matches the search filter
 * @param breakLines whether each button should appear on a separated lines
 */
export function pickFromSetCustom<T>(
  items: readonly T[],
  setItems: (results: T[]) => void,
  render: ActiveItemRenderer<T>,
  predicate: (item: T, keywords: string[]) => boolean,
  breakLines: boolean
) {
  dialog((close) => {
    const selected: T[] = [];
    const list = pane();
    const showItems = (p: (input: T) => boolean) => {
      const filteredItems = items
        .filter((item) => selected.indexOf(item) == -1)
        .filter(p)
        .map((item) => {
          return [
            render(item, (e) => {
              selected.push(item);
              if (!e.ctrlKey) {
                setItems(selected);
                e.stopPropagation();
                close();
              }
            }),
            breakLines ? br() : blank(),
          ];
        });
      list.update(filteredItems.length ? filteredItems : "No matches.");
    };

    showItems((x) => true);

    return [
      paragraph(
        "Filter: ",
        inputSearch((search) => {
          const keywords = search
            .trim()
            .toLowerCase()
            .split(/\W+/)
            .filter((x) => x);
          if (keywords.length) {
            showItems((x) => predicate(x, keywords));
          } else {
            showItems((x) => true);
          }
        })
      ),
      list.ui,
      paragraph("Control-click to select multiple."),
    ];
  });
}

/**
 * Make text that makes whitespace visible
 */
export function revealWhitespace(text: string): string {
  return text.replace("\n", "⏎").replace("\t", "⇨").replace(/\s/, "␣");
}

/**
 * This create multiple panels with a single shared state that can be updated and this regenerates all the panels
 * @param primary the main panel that should display error notification
 * @param formatters a collection of callback that will be called to update the contents of the panes when the state changes
 * @param keys all the keys provided by the formatter
 */
export function sharedPane<T, F extends { [name: string]: UIElement }>(
  primary: keyof F | null,
  formatter: (input: T) => F,
  ...keys: (keyof F)[]
): { model: StatefulModel<T>; components: NamedComponents<F> } {
  const updaters: ((input: F) => void)[] = [];
  const rawUpdaters: ((input: UIElement) => void)[] = [];
  let primaryUpdater: (input: UIElement) => void = () => {};
  const panes = Object.fromEntries(
    keys.map((name, index) => {
      const { ui, update } = pane();
      updaters.push((input) => update(input[name]));
      rawUpdaters.push(update);
      if (index == 0 || name == primary) {
        primaryUpdater = update;
      }
      return [name, ui];
    })
  ) as NamedComponents<F>;

  return {
    model: {
      reload: () => {},
      statusChanged: (input: T) => {
        const state = formatter(input);
        for (const updater of updaters) {
          updater(state);
        }
      },
      statusWaiting: () => {
        for (const updater of rawUpdaters) {
          updater(throbberSmall());
        }
      },
      statusFailed: (message: string, retry: (() => void) | null) => {
        for (const updater of rawUpdaters) {
          updater(img("dead.svg"));
        }
        primaryUpdater([
          text(message),
          retry ? button("Retry", "Attempt operation again.", retry) : blank(),
        ]);
      },
    },
    components: panes,
  };
}
/**
 * This create single panels with some shared state that can be updated
 * @param formatter a callback that will be called to update the contents of the pane when the state changes
 */
export function singleState<T>(
  formatter: (input: T) => UIElement
): { model: StatefulModel<T>; ui: UIElement } {
  const { ui, update } = pane();
  return {
    model: {
      reload: () => {},
      statusChanged: (input: T) => {
        update(formatter(input));
      },
      statusWaiting: () => {
        update(throbberSmall());
      },
      statusFailed: (message: string, retry: (() => void) | null) => {
        update(
          text(message),
          retry ? button("Retry", "Attempt operation again.", retry) : blank()
        );
      },
    },
    ui: ui,
  };
}

/**
 * Create a list that can be updated and push that into a model
 */
export function statefulList<T>(model: StatefulModel<T[]>): UpdateableList<T> {
  let list: T[] = [];
  return {
    add(item: T): void {
      list = list.concat([item]);
      model.statusChanged(list);
    },
    keepOnly(predicate: (item: T) => boolean): void {
      list = list.filter(predicate);
      model.statusChanged(list);
    },
    replace(items: T[]): void {
      list = [...items];
      model.statusChanged(list);
    },
  };
}

/**
 * Create a stateful list connected to a state synchronizer.
 */
export function statefulListBind<T>(
  synchronizer: StateSynchronizer<T[]>
): { list: UpdateableList<T>; register: (model: StatefulModel<T[]>) => void } {
  let model: StatefulModel<T[]> | null = null;
  let list: T[] = [...synchronizer.get()];
  synchronizer.listen((x) => {
    if (x != list) {
      list = x;
      if (model) {
        model.statusChanged(list);
      }
    }
  });
  return {
    list: {
      add(item: T): void {
        list = list.concat([item]);
        if (model) {
          model.statusChanged(list);
        }
        synchronizer.statusChanged(list);
      },
      keepOnly(predicate: (item: T) => boolean): void {
        list = list.filter(predicate);
        if (model) {
          model.statusChanged(list);
        }
        synchronizer.statusChanged(list);
      },
      replace(items: T[]): void {
        list = [...items];
        if (model) {
          model.statusChanged(list);
        }
        synchronizer.statusChanged(list);
      },
    },
    register: (newModel) => {
      model = newModel;
      newModel.statusChanged(list);
    },
  };
}

/**
 * Display text with an optional like through it
 */
export function strikeout(
  strike: boolean,
  contents: string | number
): UIElement {
  const element = document.createElement("p");
  element.innerText = `${contents}`.replace(/\n/g, "⏎");
  if (strike) {
    element.style.textDecoration = "line-through";
  }
  return element;
}
/**
 * Allow synchronising the fields in an object separately
 */
export function synchronizerFields<
  T extends { [name: string]: any },
  K extends keyof T
>(parent: StateSynchronizer<T>): SynchronizedFields<T> {
  let listeners: ((input: T) => void)[] = [];
  parent.listen((x) => {
    for (const listener of listeners) {
      listener(x);
    }
  });
  return (Object.fromEntries(
    Object.keys(parent.get()).map((key) => {
      let currentListener: StateListener<T[K]> = null;
      listeners.push((input: T) => {
        if (currentListener) {
          currentListener(input[key]);
        }
      });
      return [
        key,
        {
          reload: () => {},
          statusChanged(state: T[K]): void {
            parent.statusChanged({ ...parent.get(), [key]: state });
          },
          statusWaiting(): void {},
          statusFailed(message: string, retry: () => void): void {
            console.log(message);
          },
          get(): T[K] {
            return parent.get()[key];
          },
          listen(listener: StateListener<T[K]>): void {
            currentListener = listener;
            if (currentListener) {
              currentListener(parent.get()[key]);
            }
          },
        } as StateSynchronizer<T[K]>,
      ];
    })
  ) as unknown) as SynchronizedFields<T>;
}
/**
 * Display a table from the supplied items.
 * @param rows the items to use for each row
 * @param  headers a list fo columns, each with a title and a function to render that column for each row
 */
export function table<T>(
  rows: T[],
  ...headers: [string, (value: T) => UIElement][]
): UIElement {
  if (rows.length == 0) return [];
  const table = document.createElement("table");
  const headerRow = document.createElement("tr");
  table.appendChild(headerRow);
  for (const [name, func] of headers) {
    const column = document.createElement("th");
    column.innerText = name;
    headerRow.appendChild(column);
  }
  for (const row of rows) {
    const dataRow = document.createElement("tr");
    for (const [name, func] of headers) {
      const cell = document.createElement("td");
      addElements(cell, func(row));
      dataRow.appendChild(cell);
    }
    table.appendChild(dataRow);
  }
  return table;
}
/**
 * Create a table from a collection of rows
 */
export function tableFromRows(rows: HTMLTableRowElement[]): UIElement {
  if (rows.length == 0) return [];
  const table = document.createElement("table");
  for (const row of rows) {
    table.appendChild(row);
  }
  return table;
}
/**
 * Create a single row to put in a table
 * @param click an optional click handler
 * @param cells the cells to put in this row
 */
export function tableRow(
  click: ClickHandler | null,
  ...cells: TableCell[]
): HTMLTableRowElement {
  const row = document.createElement("tr");
  for (const { contents, span, header, click, intensity, title } of cells) {
    const cell = document.createElement(header ? "th" : "td");
    addElements(cell, contents);
    if (span) {
      cell.colSpan = span;
    }
    if (click) {
      cell.style.cursor = "pointer";
      cell.addEventListener("click", click);
    }
    if (intensity != null) {
      cell.style.backgroundColor = `hsl(191, 95%, ${Math.ceil(
        97 - Math.max(0, Math.min(1, intensity)) * 20
      )}%)`;
    }
    if (title) {
      cell.title = title;
    }

    row.appendChild(cell);
  }
  if (click) {
    row.style.cursor = "pointer";
    row.addEventListener("click", click);
  }
  return row;
}
/**
 * Create a tabbed area with multiple panes
 *
 * @param tabs each tab to display; each tabe has a name, contents, and an optional Ctrl-F handler. One tab may be marked as selected to be active by default
 */
export function tabs(...tabs: Tab[]): { ui: UIElement; find: FindHandler } {
  let original = true;
  let findHandler: FindHandler = null;
  const panes = tabs.map((t) => document.createElement("div"));
  const buttons = tabs.map(({ name, contents, find }, index) => {
    const button = document.createElement("span");
    button.innerText = name;
    addElements(panes[index], contents);
    button.addEventListener("click", (e) => {
      panes.forEach((pane, i) => {
        pane.style.display = i == index ? "block" : "none";
      });
      buttons.forEach((button, i) => {
        button.className = i == index ? "tab selected" : "tab";
      });
      findHandler = find || null;
      original = false;
    });
    return button;
  });

  const container = document.createElement("div");
  const buttonBar = document.createElement("div");
  container.appendChild(buttonBar);
  for (const button of buttons) {
    buttonBar.appendChild(button);
  }
  for (const pane of panes) {
    container.appendChild(pane);
  }
  let selectedTab = Math.max(
    0,
    tabs.findIndex((t) => t.selected || false)
  );
  for (let i = 0; i < tabs.length; i++) {
    buttons[i].className = i == selectedTab ? "tab selected" : "tab";
    panes[i].style.display = i == selectedTab ? "block" : "none";
  }
  return {
    ui: container,
    find: () => {
      if (findHandler) {
        return findHandler();
      } else {
        return false;
      }
    },
  };
}
/**
 * Create a UI element with a bunch of fake buttons for tags/types
 */
export function tagList(title: string, entries: string[]): UIElement {
  if (entries.length) {
    const output = document.createElement("span");
    output.className = "filterlist";
    output.innerText = title;
    entries.forEach((entry) => {
      const button = document.createElement("span");
      button.innerText = entry;
      output.appendChild(button);
      output.appendChild(document.createTextNode(" "));
    });
    return output;
  } else {
    return blank();
  }
}

/**
 * Display a throbber to indicate that the user should be patient.
 */
export function throbber(): UIElement {
  const throbber = document.createElement("object");
  throbber.data = "press.svg";
  throbber.type = "image/svg+xml";
  throbber.className = "throbber";
  throbber.style.visibility = "hidden";
  window.setTimeout(() => (throbber.style.visibility = "visible"), 500);
  return throbber;
}
/**
 * Display a throbber to indicate that the user should be patient that's limited to the line height.
 */
export function throbberSmall(): UIElement {
  const throbber = document.createElement("span");
  throbber.className = "throbber";
  throbber.appendChild(document.createElement("span"));
  throbber.appendChild(document.createElement("span"));
  throbber.appendChild(document.createElement("span"));
  return throbber;
}

/**
 * Create a state synchronizer that just caches the value in a local variable.
 */
export function temporaryState<T>(initial: T): StateSynchronizer<T> {
  let current: T = initial;
  let listener: StateListener<T> = null;
  return {
    reload: () => {},
    statusChanged: (input: T) => {
      current = input;
    },
    statusFailed: (message, retry) => console.log(message),
    statusWaiting: () => {},
    get(): T {
      return current;
    },
    listen: (newListener: StateListener<T>) => {
      listener = newListener;
      if (listener) {
        listener(current);
      }
    },
  };
}

/**
 * Display text as a paragraph
 */
export function text(contents: string | number, title?: string): UIElement {
  const element = document.createElement("p");
  element.innerText = `${contents}`.replace(/\n/g, "⏎");
  element.title = title || "";
  return element;
}
/**
 * Create a block with specific CSS styling
 */
export function tile(classes: string[], ...contents: UIElement[]): UIElement {
  const element = document.createElement("div");
  for (const c of classes) {
    element.classList.add(c);
  }
  addElements(element, ...contents);
  return element;
}
/**
 * Display an absolute time in a nice way.
 */
export function timespan(title: string, time: number): UIElement {
  if (!time) return [];
  const { ago, absolute } = computeDuration(time);
  return text(`${title}: ${absolute} (${ago})`);
}

/**
 * Create a UI element whose contents can be replaced asynchronously using a processing function
 * @param initial the initial data to display
 * @param renderer a function to convert the data to a displayable format
 * @returns the UI element and a function to change its contents
 */
export function updateable<T>(
  initial: T,
  renderer: (data: T) => UIElement
): { ui: UIElement; update: (data: T) => void } {
  const { ui, update } = pane();
  return {
    ui: ui,
    update: (data) => update(renderer(data)),
  };
}

// Preload images
new Image().src = "press.svg";
new Image().src = "dead.svg";
