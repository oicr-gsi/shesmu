import {
  blank,
  collapse,
  link,
  table,
  text,
  title,
  visibleText
} from "./utils.js";
import { actionRender } from "./actions.js";

function makeButton(label, className, callback) {
  const button = document.createElement("SPAN");
  button.className = "load" + className;
  button.innerText = label;
  button.addEventListener("click", callback);
  return button;
}

function button(label, callback) {
  return makeButton(label, "", callback);
}
function accessoryButton(label, callback) {
  return makeButton(label, " accessory", callback);
}
function dangerButton(label, callback) {
  return makeButton(label, " danger", callback);
}

function statusButton(state) {
  const button = document.createElement("SPAN");
  button.title = actionStates[state];
  button.innerText = state;
  button.className = `load state_${state.toLowerCase()}`;
  return button;
}

function breakSlashes(text) {
  return text.replace(/\//g, "/\u200B");
}

function addThrobber(container) {
  const throbber = document.createElement("DIV");
  throbber.className = "lds-circle";
  throbber.appendChild(document.createElement("DIV"));
  container.appendChild(throbber);
}

function clearChildren(container) {
  while (container.hasChildNodes()) {
    container.removeChild(container.lastChild);
  }
}

function fetchJsonWithBusyDialog(url, parameters, callback) {
  const closeBusy = makeBusyDialog();
  fetch(url, parameters)
    .then(response => {
      if (response.ok) {
        return Promise.resolve(response);
      } else {
        return Promise.reject(new Error("Failed to load"));
      }
    })
    .then(response => response.json())
    .then(response => {
      closeBusy();
      callback(response);
    })
    .catch(error => {
      closeBusy();
      output.innerText = error.message;
    });
}

export function fetchConstant(name) {
  fetchJsonWithBusyDialog(
    "/constant",
    {
      body: JSON.stringify(name),
      method: "POST"
    },
    data => {
      const output = makePopup();
      if (data.hasOwnProperty("value")) {
        const dataDiv = document.createElement("pre");
        dataDiv.className = "json";
        dataDiv.innerText = JSON.stringify(data.value, null, 2);
        output.appendChild(dataDiv);
      } else {
        output.innerText = data.error;
      }
    }
  );
}

export function runFunction(name, parameterTypes) {
  const parameters = [];
  const errors = [];
  if (
    !parameterTypes.every((parameterType, parameter) =>
      parser.parse(
        document.getElementById(`${name}$${parameter}`).value,
        parameterType,
        x => parameters.push(x),
        message => {
          const p = document.createElement("P");
          p.innerText = `Argument ${parameter}: ${message}`;
          errors.push(p);
        }
      )
    )
  ) {
    const errorDialog = makePopup();
    errors.forEach(err => errorDialog.appendChild(err));
    return;
  }
  fetchJsonWithBusyDialog(
    "/function",
    {
      body: JSON.stringify({ name: name, args: parameters }),
      method: "POST"
    },
    data => {
      const output = makePopup();
      if (data.hasOwnProperty("value")) {
        const dataDiv = document.createElement("PRE");
        dataDiv.className = "json";
        dataDiv.innerText = JSON.stringify(data.value, null, 2);
        output.appendChild(dataDiv);
      } else {
        output.innerText = data.error;
      }
    }
  );
}

export function parseType() {
  const format = document.getElementById("format");
  fetchJsonWithBusyDialog(
    "/type",
    {
      body: JSON.stringify({
        value: document.getElementById("typeValue").value,
        format: format.options[format.selectedIndex].value
      }),
      method: "POST"
    },
    data => {
      document.getElementById("humanType").innerText = data.humanName;
      document.getElementById("descriptorType").innerText = data.descriptor;
    }
  );
}

export const parser = {
  _: function(input) {
    return { good: false, input: input, error: "Cannot parse bad type." };
  },
  a: function(innerType) {
    return input => {
      const output = [];
      for (;;) {
        let match = input.match(output.length == 0 ? /^\s*\[/ : /^\s*([\],])/);
        if (!match) {
          return {
            good: false,
            input: input,
            error:
              output.length == 0
                ? "Expected [ in list."
                : "Expected ] or , for list."
          };
        }
        if (match[1] == "]") {
          return {
            good: true,
            input: input.substring(match[0].length),
            output: output
          };
        }
        const state = innerType(input.substring(match[0].length));
        if (state.good) {
          output.push(state.output);
          input = state.input;
        } else {
          return state;
        }
      }
    };
  },
  b: function(input) {
    let match = input.match(/^\s*([Tt]rue|[Ff]alse)/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: match[1].toLowerCase() == "true"
      };
    } else {
      return { good: false, input: input, error: "Expected boolean." };
    }
  },
  d: function(input) {
    let match = input.match(/^\s*EpochSecond\s+(\d*)/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: parseInt(match[1]) * 1000
      };
    }
    match = input.match(/^\s*EpochMilli\s+(\d*)/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: parseInt(match[1])
      };
    }
    match = input.match(
      /^\s*Date\s+(\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}:\d{2}(Z|[+-]\d{2}))?)/
    );
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: new Date(match[1]).getTime()
      };
    } else {
      return { good: false, input: input, error: "Expected date." };
    }
  },
  f: function(input) {
    let match = input.match(/^\s*(\d*(\.\d*([eE][+-]?\d+)?))/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: parseFloat(match[1])
      };
    } else {
      return {
        good: false,
        input: input,
        error: "Expected floating point number."
      };
    }
  },
  i: function(input) {
    let match = input.match(/^\s*(\d*)/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: parseInt(match[1])
      };
    } else {
      return { good: false, input: input, error: "Expected integer." };
    }
  },
  o: function(fieldTypes) {
    return input => {
      const output = {};
      let first = true;
      // We're going to iterate over the keys so we get the right number of fields, but we won't actually use them directly since we don't know the order the user gave them to us in
      for (let field in Object.keys(fieldTypes)) {
        let match = input.match(first ? /^\s*{/ : /^\s*,/);
        if (!match) {
          return {
            good: false,
            input: input,
            error: first ? "Expected { for object." : "Expected , for object."
          };
        }
        first = false;
        const fieldStart = input
          .substring(match[0].length)
          .match(/^\s*([a-z][a-z0-9_]*)\s*=\s*/);
        if (!fieldStart) {
          return {
            good: false,
            input: input,
            error: "Expected field name for object."
          };
        }
        if (output.hasOwnProperty(fieldStart[1])) {
          return {
            good: false,
            input: input,
            error: `Duplicate field ${fieldStart[1]} in object.`
          };
        }

        const fieldType = fieldTypes[fieldStart[1]];
        const state = fieldType(
          input.substring(match[0].length + fieldStart[0].length)
        );
        if (state.good) {
          output[fieldStart[1]] = state.output;
          input = state.input;
        } else {
          return state;
        }
      }
      let closeMatch = input.match(/^\s*}/);
      if (closeMatch) {
        return {
          good: true,
          input: input.substring(closeMatch[0].length),
          output: output
        };
      } else {
        return { good: false, input: input, error: "Expected } in object." };
      }
    };
  },
  p: function(input) {
    let match = input.match(/^\s*'(([^"\\]|\\")*)'/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: match[1].replace("\\'", "'")
      };
    } else {
      return { good: false, input: input, error: "Expected path." };
    }
  },
  s: function(input) {
    let match = input.match(/^\s*"(([^"\\]|\\")*)"/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: match[1].replace('\\"', '"')
      };
    } else {
      return { good: false, input: input, error: "Expected string." };
    }
  },
  t: function(innerTypes) {
    return input => {
      const output = [];
      for (let i = 0; i < innerTypes.length; i++) {
        let match = input.match(i == 0 ? /^\s*{/ : /^\s*,/);
        if (!match) {
          return {
            good: false,
            input: input,
            error: i == 0 ? "Expected { for tuple." : "Expected , for tuple."
          };
        }
        const state = innerTypes[i](input.substring(match[0].length));
        if (state.good) {
          output.push(state.output);
          input = state.input;
        } else {
          return state;
        }
      }
      let closeMatch = input.match(/^\s*}/);
      if (closeMatch) {
        return {
          good: true,
          input: input.substring(closeMatch[0].length),
          output: output
        };
      } else {
        return { good: false, input: input, error: "Expected } in tuple." };
      }
    };
  },
  parse: function(input, parse, resultHandler, errorHandler) {
    let state = parse(input);
    if (!state.good) {
      errorHandler(state.error, input.length - state.input.length);
      return false;
    }
    if (state.input.match(/^\s*$/) == null) {
      errorHandler("Junk at end of input.", input.length - state.input.length);
      return false;
    }
    resultHandler(state.output);
    return true;
  }
};

const actionStates = {
  FAILED:
    "The action has been attempted and encounter an error (possibly recoverable).",
  HALP:
    "The action is in a state where it needs human attention or intervention to correct itself.",
  INFLIGHT: "The action is currently being executed.",
  QUEUED: "The action is waiting for a remote system to start it.",
  SUCCEEDED: "The action is complete.",
  THROTTLED:
    "The action is being rate limited by a Shesmu throttler or by an over-capacity signal.",
  UNKNOWN:
    "The actions state is not currently known either due to an exception or not having been attempted.",
  WAITING: "The action cannot be started due to a resource being unavailable."
};

const timeUnits = {
  milliseconds: 1,
  seconds: 1000,
  minutes: 60000,
  hours: 3600000,
  days: 86400000
};

const timeSpans = ["added", "checked", "statuschanged", "external"];
const selectedAgoUnit = new Map();
const types = new Map();
const locations = new Map();
const selectedStates = new Map();
let availableLocations;
let closeActiveMenu = () => {};
let activeMenu = null;

function makeDropDown(
  activeElement,
  listElement,
  setter,
  labelMaker,
  isDefault,
  items
) {
  let open = false;
  activeElement.parentNode.onclick = e => {
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
      closeActiveMenu = external => {
        listElement.className = external ? "ready" : "";
        open = false;
        activeMenu = null;
      };
    }
  };
  activeElement.parentNode.onmouseover = e => {
    if (e.target == listElement.parentNode && !open) {
      closeActiveMenu(true);
    }
  };
  activeElement.parentNode.onmouseout = () => {
    if (!open) {
      listElement.className = "ready";
    }
  };
  clearChildren(listElement);
  for (const item of items) {
    const element = document.createElement("SPAN");
    const label = labelMaker(item);
    element.innerText = label;
    element.onclick = e => {
      setter(item);
      activeElement.innerText = label;
      if (open) {
        closeActiveMenu(false);
      }
    };
    if (isDefault(item)) {
      setter(item);
      activeElement.innerText = label;
    }
    listElement.appendChild(element);
  }
}

function dropDown(setter, labelMaker, isDefault, items) {
  const container = document.createElement("SPAN");
  container.className = "dropdown";
  const activeElement = document.createElement("SPAN");
  activeElement.innerText = "Select";
  container.appendChild(activeElement);
  container.appendChild(document.createTextNode(" ▼"));
  const listElement = document.createElement("DIV");
  container.appendChild(listElement);
  makeDropDown(
    activeElement,
    listElement,
    setter,
    labelMaker,
    isDefault,
    items
  );
  return container;
}

export function initialiseActionDash(serverSearches, tags, savedQueryName) {
  document.addEventListener("click", e => {
    if (activeMenu != null) {
      for (
        let targetElement = e.target;
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
  let localSearches = {};
  try {
    localSearches = JSON.parse(localStorage.getItem("shesmu_searches") || "{}");
  } catch (e) {
    console.log(e);
  }

  let selectedName = null;
  let selectedQuery = null;
  const searchList = document.getElementById("searches");
  const searchName = document.getElementById("searchName");
  const results = document.getElementById("results");
  const redrawDropDown = () =>
    makeDropDown(
      searchName,
      searchList,
      ([name, query]) => {
        if (window.history.state != name) {
          window.history.pushState(name, name, `actiondash?saved=${name}`);
        }
        savedQueryName = null;
        selectedName = name;
        selectedQuery = query;
        getStats(
          query,
          tags,
          results,
          true,
          true,
          (reset, updateLocalSearches) => {
            if (reset) {
              savedQueryName = "All Actions";
            }
            if (updateLocalSearches) {
              updateLocalSearches(localSearches);
              localStorage.setItem(
                "shesmu_searches",
                JSON.stringify(localSearches)
              );
            }
            redrawDropDown();
          }
        );
      },
      ([name, query]) => name,
      ([name, query]) => name == savedQueryName,
      [["All Actions", []]]
        .concat(Object.entries(serverSearches))
        .concat(Object.entries(localSearches))
    );
  const updateLocalSearches = () => {
    localStorage.setItem("shesmu_searches", JSON.stringify(localSearches));
    redrawDropDown();
  };

  window.addEventListener("popstate", e => {
    if (e.state) {
      savedQueryName = e.state;
      redrawDropDown();
    }
  });

  function savedSearches(
    searches,
    userDefined,
    showSavedSearches,
    shouldLoad,
    savedQueryName
  ) {
    if (userDefined) {
      clearChildren(searchContainer);
    }
    for (const [name, query] of Object.entries(searches)) {
      const element = document.createElement("DIV");
      searchContainer.appendChild(element);
      element.innerText = name;
      element.onclick = () => {
        for (let i = 0; i < searchContainer.children.length; i++) {
          searchContainer.children[i].className = "";
        }
        element.className = "selected";
      };
      if (userDefined) {
        const close = document.createElement("SPAN");
        element.appendChild(close);
        close.innerText = "✖";
      } else {
        const link = document.createElement("SPAN");
        element.appendChild(link);
        link.innerText = "🔗";
        link.onclick = () => {
          window.history.pushState(null, name, `actiondash?saved=${name}`);
        };
      }
      if (shouldLoad && name == savedQueryName) {
        nextPage(queryJson, document.getElementById("results"), true);
      }
    }
  }

  document.getElementById("pasteSearchButton").addEventListener("click", () => {
    const [dialog, close] = makePopup(true);
    dialog.appendChild(document.createTextNode("Save search as: "));
    const input = document.createElement("INPUT");
    input.type = "text";
    dialog.appendChild(input);
    dialog.appendChild(document.createElement("BR"));
    dialog.appendChild(document.createTextNode("Filter JSON:"));
    const filterJSON = document.createElement("TEXTAREA");
    dialog.appendChild(filterJSON);

    dialog.appendChild(
      button("Save", () => {
        const name = input.value.trim();
        let filters = null;
        try {
          filters = JSON.parse(filterJSON.value);
        } catch (e) {
          makePopup().innerText = e;
          return;
        }
        if (name) {
          localSearches[name] = filters;
          close();
          updateLocalSearches();
        }
      })
    );
  });

  document.getElementById("importButton").addEventListener("click", () => {
    const [dialog, close] = makePopup(true);
    const importJSON = document.createElement("TEXTAREA");
    dialog.appendChild(importJSON);

    dialog.appendChild(
      button("Import", () => {
        for (const entry of Object.entries(JSON.parse(importJSON.value))) {
          localSearches[entry[0]] = entry[1];
        }
        close();
        updateLocalSearches();
      })
    );
  });
  document
    .getElementById("deleteSearchButton")
    .addEventListener("click", () => {
      if (localSearches.hasOwnProperty(selectedName)) {
        delete localSearches[selectedName];
        savedQueryName = "All Actions";
        updateLocalSearches();
      } else {
        makePopup().innerText =
          "Search is stored on the Shesmu server and cannot be deleted from this interface.";
      }
    });

  document
    .getElementById("exportButton")
    .addEventListener("click", () => copyJson(localSearches));

  redrawDropDown();
}

function copyJson(data) {
  const closeBusy = makeBusyDialog();
  const buffer = document.getElementById("copybuffer");
  buffer.value = JSON.stringify(data, null, 2);
  buffer.style = "display: inline;";
  buffer.select();
  document.execCommand("Copy");
  buffer.style = "display: none;";
  window.setTimeout(closeBusy, 300);
}

function saveSearch(filters, updateSearchList) {
  const [dialog, close] = makePopup(true);
  if (filters.length == 0) {
    dialog.innerText = "Umm, saving an empty search seems really pointless.";
    return;
  }
  dialog.appendChild(document.createTextNode("Save search as: "));
  const input = document.createElement("INPUT");
  input.type = "text";
  dialog.appendChild(input);

  dialog.appendChild(
    button("Save", () => {
      const name = input.value.trim();
      if (name) {
        close();
        updateSearchList(localSearches => (localSearches[name] = filters));
      }
    })
  );
}

function parseEpoch(elementId) {
  const epochElement = document.getElementById(elementId);
  const epochInput = epochElement.value.trim();
  epochElement.className = "";
  if (epochInput.length == 0) {
    return null;
  }
  const result = parser.d(epochInput);
  if (result.good) {
    return result.output;
  } else {
    epochElement.className = "error";
    return null;
  }
}

function addFilterFromStateMap(filters, type, attribute, map, mapFunc) {
  const values = Array.from(map.entries())
    .filter(entry => entry[1])
    .map(entry => mapFunc(entry[0]));
  if (values.length > 0) {
    filters.push({ type: type, [attribute]: values });
  }
}

let searchType = "text";

function makeFilters() {
  const filters = [];
  addFilterFromStateMap(filters, "status", "states", selectedStates, x => x);
  addFilterFromStateMap(filters, "type", "types", types, x => x);
  addFilterFromStateMap(filters, "sourcelocation", "locations", locations, x =>
    availableLocations.get(x)
  );

  for (let span of timeSpans) {
    const start = parseEpoch(`${span}Start`);
    const end = parseEpoch(`${span}End`);
    if (start !== null || end !== null) {
      filters.push({ type: span, start: start, end: end });
    }
    const offsetValue = document.getElementById(`${span}Ago`).value.trim();
    if (offsetValue) {
      filters.push({
        type: `${span}ago`,
        offset: parseInt(offsetValue) * selectedAgoUnit.get(span)
      });
    }
  }

  const text = document.getElementById("searchText").value.trim();
  if (text) {
    filters.push(searchType(text));
  }
  return filters;
}

function results(container, slug, body, render) {
  clearChildren(container);
  addThrobber(container);
  fetch(slug, {
    body: body,
    method: "POST"
  })
    .then(response => {
      if (response.ok) {
        return Promise.resolve(response);
      } else {
        return Promise.reject(new Error("Failed to load"));
      }
    })
    .then(response => response.json())
    .then(data => {
      clearChildren(container);
      render(container, data);
    })
    .catch(function(error) {
      clearChildren(container);
      const element = document.createElement("SPAN");
      element.innerText = error.message;
      element.className = "error";
      container.appendChild(element);
    });
}

export function actionsForOlive(filename, line, column, timestamp) {
  let localSearches = {};
  try {
    localSearches = JSON.parse(localStorage.getItem("shesmu_searches") || "{}");
  } catch (e) {
    console.log(e);
  }

  getStats(
    [
      {
        type: "sourcelocation",
        locations: [
          {
            file: filename,
            line: line,
            column: column,
            time: timestamp
          }
        ]
      }
    ],
    [],
    makePopup(),
    false,
    true,
    (reset, updateLocalSearches) => {
      if (updateLocalSearches) {
        updateLocalSearches(localSearches);
        localStorage.setItem("shesmu_searches", JSON.stringify(localSearches));
      }
    }
  );
}

function makePopup(returnClose, afterClose) {
  const modal = document.createElement("DIV");
  modal.className = "modal close";

  const dialog = document.createElement("DIV");
  modal.appendChild(dialog);

  const closeButton = document.createElement("DIV");
  closeButton.innerText = "✖";

  dialog.appendChild(closeButton);

  const inner = document.createElement("DIV");
  dialog.appendChild(inner);

  document.body.appendChild(modal);
  modal.onclick = e => {
    if (e.target == modal) {
      document.body.removeChild(modal);
      if (afterClose) {
        afterClose();
      }
    }
  };
  closeButton.onclick = e => {
    document.body.removeChild(modal);
    if (afterClose) {
      afterClose();
    }
  };

  return returnClose ? [inner, closeButton.onclick] : inner;
}

function makeTabs(container, selectedTab, ...tabs) {
  const panes = tabs.map(t => document.createElement("DIV"));
  const buttons = tabs.map((t, index) => {
    const button = document.createElement("SPAN");
    button.innerText = t;
    button.addEventListener("click", e => {
      panes.forEach((pane, i) => {
        pane.style.display = i == index ? "block" : "none";
      });
      buttons.forEach((button, i) => {
        button.className = i == index ? "tab selected" : "tab";
      });
    });
    return button;
  });

  const buttonBar = document.createElement("DIV");
  container.appendChild(buttonBar);
  for (const button of buttons) {
    buttonBar.appendChild(button);
  }
  for (const pane of panes) {
    container.appendChild(pane);
  }
  for (let i = 0; i < tabs.length; i++) {
    buttons[i].className = i == selectedTab ? "tab selected" : "tab";
    panes[i].style.display = i == selectedTab ? "block" : "none";
  }
  return panes;
}

function makeBusyDialog() {
  const modal = document.createElement("DIV");
  modal.className = "modal";
  addThrobber(modal);
  document.body.appendChild(modal);
  return () => document.body.removeChild(modal);
}

export function listActionsPopup(filters) {
  nextPage(
    {
      filters: filters,
      limit: 25,
      skip: 0
    },
    makePopup(),
    false
  );
}

function defaultRenderer(action) {
  return title(action, `Unknown Action: ${action.type}`);
}

let sourceColumns = [];

function nextPage(query, targetElement, onActionPage) {
  results(targetElement, "/query", JSON.stringify(query), (container, data) => {
    const jumble = document.createElement("DIV");
    if (data.results.length == 0) {
      jumble.innerText = "No actions found.";
    }

    sourceColumns = [
      ["File", l => l.file],
      ["Line", l => l.line],
      ["Column", l => l.column],
      [
        "Time",
        l => {
          const [ago, absolute] = formatTimeBin(l.time);
          return `${absolute} (${ago})`;
        }
      ],
      ["Source", l => (l.url ? link(l.url, "View Source") : blank())]
    ];
    if (onActionPage) {
      sourceColumns.push([
        "Olive",
        l =>
          link(
            `olivedash#${l.file}:${l.line}:${l.column}:${l.time}`,
            "View Olive"
          )
      ]);
    }

    data.results.forEach(action => {
      const tile = document.createElement("DIV");
      tile.className = `action state_${action.state.toLowerCase()}`;
      (actionRender.get(action.type) || defaultRenderer)(action)
        .flat(Number.MAX_VALUE)
        .forEach(element => tile.appendChild(element));
      const json = document.createElement("PRE");
      json.className = "json";
      json.innerText = JSON.stringify(action, null, 2);
      collapse("JSON", json).forEach(x => tile.appendChild(x));
      jumble.appendChild(tile);
    });

    if (data.total == data.results.length) {
      const size = document.createElement("DIV");
      size.innerText = `${data.total} actions.`;
      container.appendChild(size);
    } else {
      const size = document.createElement("DIV");
      size.innerText = `${data.results.length} of ${data.total} actions.`;
      container.appendChild(size);
      const pager = document.createElement("DIV");
      const numButtons = Math.ceil(data.total / query.limit);
      const current = Math.floor(query.skip / query.limit);

      let rendering = true;
      for (let i = 0; i < numButtons; i++) {
        if (
          i <= 2 ||
          i >= numButtons - 2 ||
          (i >= current - 2 && i <= current + 2)
        ) {
          rendering = true;
          const page = document.createElement("SPAN");
          const skip = i * query.limit;
          page.innerText = `${i + 1}`;
          if (skip != query.skip) {
            page.className = "load accessory";
          }
          page.onclick = () =>
            nextPage(
              {
                filters: query.filters,
                skip: skip,
                limit: query.limit
              },
              targetElement,
              onActionPage
            );
          pager.appendChild(page);
        } else if (rendering) {
          const ellipsis = document.createElement("SPAN");
          ellipsis.innerText = "...";
          pager.appendChild(ellipsis);
          rendering = false;
        }
      }
      container.appendChild(pager);
    }
    container.appendChild(jumble);
  });
}

function showFilterJson(filters, targetElement) {
  clearChildren(targetElement);
  const pre = document.createElement("PRE");
  pre.className = "json";
  pre.innerText = JSON.stringify(filters, null, 2);
  targetElement.appendChild(pre);
}

function addToSet(value) {
  return list =>
    list
      ? list
          .concat([value])
          .sort()
          .filter((item, index, array) => item == 0 || item != array[index - 1])
      : [value];
}

function propertyFilterMaker(name) {
  switch (name) {
    case "sourcefile":
      return f => ["sourcefile", addToSet(f)];
    case "status":
      return s => ["status", addToSet(s)];
    case "type":
      return t => ["type", addToSet(t)];
    default:
      return () => null;
  }
}

function nameForBin(name) {
  switch (name) {
    case "added":
      return "Time Since Action was Last Generated by an Olive";
    case "checked":
      return "Last Time Action was Last Run";
    case "statuschanged":
      return "Last Time Action's Status Last Changed";
    case "external":
      return "External Last Modification Time";
    default:
      return name;
  }
}

function formatTimeSpan(x) {
  let diff = Math.abs(Math.ceil(x / 1000));
  let result = "";
  let chunkcount = 0;
  for (let [span, name] of [
    [31557600, "y"],
    [86400, "d"],
    [3600, "h"],
    [60, "m"],
    [1, "s"]
  ]) {
    const chunk = Math.floor(diff / span);
    if (chunk > 0 || chunkcount > 0) {
      result = `${result}${chunk}${name} `;
      diff = diff % span;
      if (++chunkcount > 2) {
        break;
      }
    }
  }
  return result;
}

function formatTimeBin(x) {
  const d = new Date(x);
  const span = new Date() - d;
  let ago = formatTimeSpan(span);
  if (ago) {
    ago = ago + (span < 0 ? "from now" : "ago");
  } else {
    ago = "now";
  }
  return [ago, `${d.toISOString()}`];
}

function setColorIntensity(element, value, maximum) {
  element.style.backgroundColor = `hsl(191, 95%, ${Math.ceil(
    97 - (value || 0) / maximum * 20
  )}%)`;
}

const headerAngle = Math.PI / 4;

function purge(filters, afterClose) {
  const [targetElement, close] = makePopup(true, afterClose);
  if (filters.length == 0) {
    clearChildren(targetElement);
    const sarcasm = document.createElement("P");
    sarcasm.innerText =
      "Yeah, no. You probably shouldn't nuke all the actions. Maybe try a subset.";
    targetElement.appendChild(sarcasm);
    targetElement.appendChild(
      dangerButton("🔥 NUKE IT ALL FROM ORBIT 🔥", () => {
        purgeActions(filters, targetElement);
      })
    );
    targetElement.appendChild(document.createElement("BR"));
    targetElement.appendChild(button("Back away slowly", close));
  } else {
    purgeActions(filters, targetElement);
  }
}

function purgeActions(filters, targetElement) {
  results(
    targetElement,
    "/purge",
    JSON.stringify(filters),
    (container, data) => {
      const message = document.createElement("P");
      message.innerText = `Removed ${data} actions.`;
      if (data) {
        message.appendChild(document.createElement("BR"));
        const image = document.createElement("IMG");
        image.src = "thorschariot.gif";
        message.appendChild(image);
      }
      container.appendChild(message);
    }
  );
}

function removeFromList(value) {
  return list => (list ? list.filter(x => x !== value) : []);
}

function editText(original, callback) {
  const [dialog, close] = makePopup(true);
  dialog.appendChild(document.createTextNode("Search for text: "));
  const input = document.createElement("INPUT");
  input.type = "text";
  input.value = original.text;
  dialog.appendChild(input);
  dialog.appendChild(document.createElement("BR"));
  const matchCaseLabel = document.createElement("LABEL");
  const matchCase = document.createElement("INPUT");
  matchCase.type = "checkbox";
  matchCase.checked = original.matchCase;
  matchCaseLabel.appendChild(matchCase);
  matchCaseLabel.appendChild(document.createTextNode("Case sensitive"));
  dialog.appendChild(matchCaseLabel);
  dialog.appendChild(document.createElement("BR"));
  dialog.appendChild(
    button("Save", () => {
      close();
      const text = input.value.trim();
      callback(x =>
        (x || [])
          .filter(v => v.text != original.text && v.text != text)
          .concat(text ? [{ text: text, matchCase: matchCase.checked }] : [])
      );
    })
  );
  if (original.text) {
    dialog.appendChild(
      button("Delete", () => {
        close();
        callback(x => (x || []).filter(v => v.text != original.text));
      })
    );
  }
}

function editRegex(original, callback) {
  const [dialog, close] = makePopup(true);
  dialog.appendChild(document.createTextNode("Search for regex: "));
  const input = document.createElement("INPUT");
  input.type = "text";
  input.value = original;
  dialog.appendChild(input);
  dialog.appendChild(document.createElement("BR"));
  dialog.appendChild(
    button("Save", () => {
      close();
      callback(x =>
        (x || [])
          .filter(v => v != original && v != input.value)
          .concat(input.value ? [input.value] : [])
      );
    })
  );
  if (original) {
    dialog.appendChild(
      button("Delete", () => {
        close();
        callback(x => (x || []).filter(v => v != original));
      })
    );
  }
}

function timeDialog(callback) {
  const [dialog, close] = makePopup(true);
  for (const span of timeSpans) {
    dialog.appendChild(
      button(nameForBin(span), () => {
        close();
        callback(span);
      })
    );
    dialog.appendChild(document.createElement("BR"));
  }
}

function editTime(original, callback) {
  const [dialog, close] = makePopup(true);
  const makeSelector = (initial, title, target) => {
    const selected = initial ? new Date(initial) : new Date();
    target.appendChild(document.createTextNode(title));
    target.appendChild(document.createElement("BR"));
    const label = document.createElement("LABEL");
    const enabled = document.createElement("INPUT");
    enabled.type = "checkbox";
    enabled.checked = !initial;
    label.appendChild(enabled);
    label.appendChild(document.createTextNode("Unbounded"));
    target.appendChild(label);
    target.appendChild(document.createElement("BR"));
    const inputs = [];
    const makeNumberBox = (min, getter, setter) => {
      const input = document.createElement("INPUT");
      input.type = "number";
      input.min = min;
      input.value = getter();
      input.disabled = enabled.checked;
      target.appendChild(input);
      input.addEventListener("change", () => setter(input.valueAsNumber));
      inputs.push(input);
      return input;
    };

    makeNumberBox(
      0,
      () => selected.getFullYear(),
      v => selected.setFullYear(v)
    );
    target.appendChild(document.createTextNode(" "));
    let day;
    target.appendChild(
      dropDown(
        ([number, name]) => {
          selected.setMonth(number);
          if (day) {
            day.max = new Date(selected.getFullYear(), number + 1, 0).getDate();
            if (day.valueAsNumber > day.max) {
              day.valueAsNumber = day.max;
            }
          }
        },
        ([number, name]) => name,
        ([number, name]) => number == selected.getMonth(),
        [
          [0, "January"],
          [1, "February"],
          [2, "March"],
          [3, "April"],
          [4, "May"],
          [5, "June"],
          [6, "July"],
          [7, "August"],
          [8, "September"],
          [9, "October"],
          [10, "November"],
          [11, "December"]
        ]
      )
    );
    target.appendChild(document.createTextNode(" "));
    day = makeNumberBox(1, () => selected.getDate(), v => selected.setDate(v));
    day.max = new Date(
      selected.getFullYear(),
      selected.getMonth() + 1,
      0
    ).getDate();
    target.appendChild(document.createElement("BR"));
    makeNumberBox(
      0,
      () => selected.getHours(),
      v => selected.setHours(v)
    ).max = 23;
    target.appendChild(document.createTextNode(" : "));
    makeNumberBox(
      0,
      () => selected.getMinutes(),
      v => {
        selected.setMinutes(v);
        selected.setSeconds(0);
        selected.setMilliseconds(0);
      }
    ).max = 59;

    enabled.addEventListener("click", () =>
      inputs.forEach(input => (input.disabled = enabled.checked))
    );
    return () => (enabled.checked ? null : selected.getTime());
  };
  const table = document.createElement("TABLE");
  dialog.appendChild(table);
  const row = document.createElement("TR");
  table.appendChild(row);
  const startCell = document.createElement("TD");
  row.appendChild(startCell);
  const endCell = document.createElement("TD");
  row.appendChild(endCell);

  const start = makeSelector(original.start, "Start date:", startCell);
  const end = makeSelector(original.end, "End date:", endCell);

  dialog.appendChild(
    button("Save", () => {
      close();
      callback(x => ({ start: start(), end: end() }));
    })
  );
  if (original.start || original.end) {
    dialog.appendChild(
      button("Delete", () => {
        close();
        callback(x => ({ start: null, end: null }));
      })
    );
  }
}

function editTimeAgo(original, callback) {
  const [dialog, close] = makePopup(true);
  let value = 0;
  let units = timeUnits["hours"];
  if (original) {
    for (const [name, multiplier] of Object.entries(timeUnits)) {
      if (original % multiplier == 0) {
        value = original / multiplier;
        units = multiplier;
      }
    }
  }
  dialog.appendChild(document.createTextNode("Time since present: "));
  const input = document.createElement("INPUT");
  input.type = "number";
  input.min = 0;
  input.value = value;
  dialog.appendChild(input);
  dialog.appendChild(document.createTextNode(" "));
  dialog.appendChild(
    dropDown(
      ([name, multiplier]) => (units = multiplier),
      ([name, multiplier]) => name,
      ([name, multiplier]) => multiplier == units,
      Object.entries(timeUnits)
    )
  );
  dialog.appendChild(document.createElement("BR"));
  dialog.appendChild(
    button("Save", () => {
      close();
      callback(
        x =>
          Number.isNaN(input.valueAsNumber) ? 0 : input.valueAsNumber * units
      );
    })
  );
  if (original) {
    dialog.appendChild(
      button("Delete", () => {
        close();
        callback(x => 0);
      })
    );
  }
}

function renderFilter(tile, filter, mutateCallback) {
  const deleteButton = (container, typeName, updateFunction) => {
    if (mutateCallback) {
      const close = document.createElement("SPAN");
      container.appendChild(close);
      close.className = "close";
      close.innerText = "✖";
      close.addEventListener("click", e => {
        e.stopPropagation();
        mutateCallback(typeName, updateFunction);
      });
    }
  };
  const editable = (container, typeName, original, editor) => {
    if (mutateCallback) {
      container.addEventListener("click", () =>
        editor(original, update => mutateCallback(typeName, update))
      );
    }
  };
  if (filter.negate) {
    tile.className = "negated";
  }
  switch (filter.type) {
    case "added":
    case "checked":
    case "statuschanged":
    case "external":
      {
        const title = document.createElement("DIV");
        title.innerText = nameForBin(filter.type);
        tile.appendChild(title);
        deleteButton(title, filter.type, x => ({ start: null, end: null }));
        if (filter.start) {
          const start = document.createElement("DIV");
          const [ago, absolute] = formatTimeBin(filter.start);
          start.innerText = "⇤ " + ago + " —";
          start.title = absolute;
          start.style.cssFloat = "left";
          tile.appendChild(start);
        }
        if (filter.end) {
          const end = document.createElement("DIV");
          const [ago, absolute] = formatTimeBin(filter.end);
          end.innerText = "— " + ago + " ⇥";
          end.title = absolute;
          end.style.cssFloat = "right";
          tile.appendChild(end);
        }
        if (filter.start && filter.end) {
          const duration = document.createElement("DIV");
          duration.innerText =
            "🕑 " + formatTimeSpan(filter.end - filter.start);
          duration.style.clear = "both";
          duration.style.textAlign = "center";
          tile.appendChild(duration);
        }
        editable(tile, filter.type, filter, editTime);
      }
      break;
    case "addedago":
    case "checkedago":
    case "statuschangedago":
    case "externalago":
      {
        const title = document.createElement("DIV");
        title.innerText =
          nameForBin(filter.type.slice(0, -3)) + " Since Present";
        tile.appendChild(title);
        deleteButton(title, filter.type, x => 0);
        const duration = document.createElement("DIV");
        duration.innerText = "🕑 " + formatTimeSpan(filter.offset);
        tile.appendChild(duration);
        editable(tile, filter.type, filter.offset, editTimeAgo);
      }
      break;

    case "regex": {
      const title = document.createElement("DIV");
      title.innerText = "Regular Expression";
      tile.appendChild(title);
      deleteButton(title, "regex", x => x.filter(v => v != filter.pattern));
      const pattern = document.createElement("PRE");
      pattern.innerText = filter.pattern;
      tile.appendChild(pattern);
      editable(tile, "regex", filter.pattern, editRegex);
      break;
    }
    case "text":
      {
        const title = document.createElement("DIV");
        title.innerText = filter.matchCase
          ? "Case-Sensitive Text Search"
          : "Case-Insensitive Text Search";
        tile.appendChild(title);
        deleteButton(title, "text", x => x.filter(v => v.text != filter.text));
        const text = document.createElement("PRE");
        text.innerText = visibleText(filter.text);
        tile.appendChild(text);
        editable(tile, "text", filter, editText);
      }
      break;

    case "sourcefile":
      {
        const title = document.createElement("DIV");
        title.innerText = "Olive Source File";
        tile.appendChild(title);
        const list = document.createElement("DIV");
        list.className = "filterlist";
        tile.appendChild(list);
        filter.files.forEach(file => {
          const fileButton = document.createElement("SPAN");
          fileButton.innerText = breakSlashes(file);
          list.appendChild(fileButton);
          deleteButton(fileButton, "sourcefile", removeFromList(file));
        });
      }
      break;

    case "sourcelocation":
      {
        const title = document.createElement("DIV");
        title.innerText = "Olive Source";
        tile.appendChild(title);
        tile.appendChild(
          table(
            filter.locations,
            ["File", l => l.file],
            ["Line", l => l.line],
            ["Column", l => l.column],
            [
              "Time",
              l => {
                const [ago, absolute] = formatTimeBin(l.time);
                return `${absolute} (${ago})`;
              }
            ]
          )
        );
      }
      break;

    case "status":
      {
        const title = document.createElement("DIV");
        title.innerText = "Action State";
        tile.appendChild(title);
        const list = document.createElement("DIV");
        list.className = "filterlist";
        tile.appendChild(list);
        filter.states.forEach(state => {
          const button = statusButton(state);
          deleteButton(button, "status", removeFromList(state));
          list.appendChild(button);
        });
      }
      break;

    case "tag":
      {
        const title = document.createElement("DIV");
        title.innerText = "Tag";
        tile.appendChild(title);
        const list = document.createElement("DIV");
        list.className = "filterlist";
        tile.appendChild(list);
        filter.tags.forEach(tag => {
          const button = document.createElement("SPAN");
          button.innerText = tag;
          deleteButton(button, "tag", removeFromList(tag));
          list.appendChild(button);
        });
      }
      break;

    case "type":
      {
        const title = document.createElement("DIV");
        title.innerText = "Action Type";
        tile.appendChild(title);
        const list = document.createElement("DIV");
        list.className = "filterlist";
        tile.appendChild(list);
        filter.types.forEach(type => {
          const button = document.createElement("SPAN");
          button.innerText = type;
          deleteButton(button, "type", removeFromList(type));
          list.appendChild(button);
        });
      }
      break;

    case "and":
    case "or":
      {
        const title = document.createElement("DIV");
        title.innerText = filter.type == "and" ? "All of" : "Any of";
        tile.appendChild(title);
        const list = document.createElement("DIV");
        list.className = "filters";
        list.style.marginLeft = "1em";
        tile.appendChild(list);
        filter.filters.forEach(child => renderFilter(list, child));
      }
      break;

    default:
      tile.innerText = JSON.stringify(filter);
  }
}

function synthesiseFilters(metaFilter) {
  const filters = [];
  if (metaFilter.hasOwnProperty("type") && metaFilter.type.length > 0) {
    filters.push({ type: "type", types: metaFilter.type });
  }
  if (metaFilter.hasOwnProperty("status") && metaFilter.status.length > 0) {
    filters.push({ type: "status", states: metaFilter.status });
  }
  if (metaFilter.hasOwnProperty("tag") && metaFilter.tag.length > 0) {
    filters.push({ type: "tag", tags: metaFilter.tag });
  }
  if (
    metaFilter.hasOwnProperty("sourcefile") &&
    metaFilter.sourcefile.length > 0
  ) {
    filters.push({ type: "sourcefile", files: metaFilter.sourcefile });
  }

  for (const timespan of ["added", "checked", "statuschanged", "external"]) {
    if (metaFilter.hasOwnProperty(timespan)) {
      const start = metaFilter[timespan].start || null;
      const end = metaFilter[timespan].end || null;
      if (start || end) {
        filters.push({ type: timespan, start: start, end: end });
      }
    }
  }

  for (const span of timeSpans) {
    const ago = span + "ago";
    if (metaFilter[ago]) {
      filters.push({ type: ago, offset: metaFilter[ago] });
    }
  }

  if (metaFilter.text) {
    metaFilter.text.forEach(({ text, matchCase }) =>
      filters.push({
        type: "text",
        matchCase: matchCase,
        text: text
      })
    );
  }
  if (metaFilter.regex) {
    metaFilter.regex.forEach(regex =>
      filters.push({ type: "regex", pattern: regex })
    );
  }
  return filters;
}

function getStats(
  filters,
  tags,
  targetElement,
  onActionPage,
  showActions,
  updateSearchList
) {
  let additionalFilters = [];
  clearChildren(targetElement);
  const toolBar = document.createElement("P");
  targetElement.appendChild(toolBar);
  const queryBuilder = document.createElement("DIV");
  queryBuilder.className = "filters";
  targetElement.appendChild(queryBuilder);
  const [statsPane, listPane] = makeTabs(
    targetElement,
    showActions ? 1 : 0,
    "Overview",
    "Actions"
  );
  function mutateFilters(type, update) {
    additionalFilters.push({
      ...additionalFilters[additionalFilters.length - 1]
    });
    const current = additionalFilters[additionalFilters.length - 1];
    current[type] = update(current[type]);
    refresh();
  }
  const refresh = () => {
    clearChildren(queryBuilder);
    filters.forEach(filter => {
      const filterTile = document.createElement("DIV");
      queryBuilder.appendChild(filterTile);
      renderFilter(filterTile, filter, null);
    });
    const customFilters =
      additionalFilters.length == 0
        ? []
        : synthesiseFilters(additionalFilters[additionalFilters.length - 1]);
    customFilters.forEach((filter, index) => {
      const filterTile = document.createElement("DIV");
      queryBuilder.appendChild(filterTile);
      renderFilter(filterTile, filter, mutateFilters);
    });
    if (filters.length + customFilters.length == 0) {
      queryBuilder.innerText = "All actions.";
    }
    const f = filters.concat(customFilters);
    results(statsPane, "/stats", JSON.stringify(f), renderStats);
    nextPage(
      {
        filters: f,
        limit: 25,
        skip: 0
      },
      listPane,
      onActionPage
    );
  };
  const addFilters = (...f) => {
    additionalFilters.push(
      additionalFilters.length > 0
        ? { ...additionalFilters[additionalFilters.length - 1] }
        : {}
    );
    const current = additionalFilters[additionalFilters.length - 1];
    for (const [type, update] of f) {
      current[type] = update(current[type]);
    }
    refresh();
  };

  toolBar.appendChild(button("🔄 Refresh", refresh));
  toolBar.appendChild(
    accessoryButton("➕ Add Filter", () => {
      const [dialog, close] = makePopup(true);
      dialog.appendChild(
        button("🕑 Fixed Time Range", () => {
          close();
          timeDialog(n =>
            editTime({ start: null, end: null }, update =>
              mutateFilters(n, update)
            )
          );
        })
      );
      dialog.appendChild(
        button("🕑 Time Since Now", () => {
          close();
          timeDialog(n =>
            editTimeAgo(0, update => mutateFilters(n + "ago", update))
          );
        })
      );
      dialog.appendChild(
        button("🔠 Text", () => {
          close();
          editText({ text: "", matchCase: false }, update =>
            mutateFilters("text", update)
          );
        })
      );
      dialog.appendChild(
        button("*️⃣  Regular Expression", () => {
          close();
          editRegex("", update => mutateFilters("regex", update));
        })
      );
      dialog.appendChild(
        button("🏁 Status", () => {
          close();
          const statusDialog = makePopup();
          const table = document.createElement("TABLE");
          statusDialog.appendChild(table);
          Object.entries(actionStates).forEach(([state, description]) => {
            const row = document.createElement("TR");
            table.appendChild(row);
            const buttonCell = document.createElement("TD");
            row.appendChild(buttonCell);
            const button = statusButton(state);
            buttonCell.appendChild(button);
            button.addEventListener("click", () =>
              addFilters(["status", addToSet(state)])
            );
            const pCell = document.createElement("TD");
            row.appendChild(pCell);
            const p = document.createElement("P");
            p.innerText = description;
            pCell.appendChild(p);
          });
        })
      );
      dialog.appendChild(
        button("🎬 Action Type", () => {
          close();
          const typeDialog = makePopup();
          Array.from(actionRender.keys())
            .sort()
            .forEach(type =>
              typeDialog.appendChild(
                button(type, () => addFilters(["type", addToSet(type)]))
              )
            );
        })
      );
      if (tags.length) {
        dialog.appendChild(
          button("🏷️ Tags", () => {
            close();
            const tagDialog = makePopup();
            tags
              .sort()
              .forEach(tag =>
                tagDialog.appendChild(
                  button(tag, () => addFilters(["tag", addToSet(tag)]))
                )
              );
          })
        );
      }
    })
  );
  toolBar.appendChild(
    accessoryButton("💾 Save Search", () => {
      const customFilters =
        additionalFilters.length == 0
          ? []
          : synthesiseFilters(additionalFilters[additionalFilters.length - 1]);
      if (customFilters.length > 0) {
        saveSearch(filters.concat(customFilters), updateLocalSearches =>
          updateSearchList(false, updateLocalSearches)
        );
      } else {
        makePopup().innerText = "No changes to save.";
      }
    })
  );
  toolBar.appendChild(
    accessoryButton("⎌ Undo", () => {
      additionalFilters.pop();
      refresh();
    })
  );
  toolBar.appendChild(
    accessoryButton("⌫ Revert to Saved", () => {
      additionalFilters = [];
      refresh();
    })
  );
  if (onActionPage) {
    toolBar.appendChild(
      accessoryButton("✖ Clear Search", () => updateSearchList(true, null))
    );
  }
  toolBar.appendChild(
    dangerButton("☠️ PURGE", () =>
      purge(
        filters.concat(
          additionalFilters.length == 0
            ? []
            : synthesiseFilters(additionalFilters[additionalFilters.length - 1])
        ),
        refresh
      )
    )
  );

  const renderStats = (container, data) => {
    if (data.length == 0) {
      container.innerText = "No statistics are available.";
      return;
    }
    const help = document.createElement("P");
    help.innerText =
      "Click any cell or table heading to view matching results.";
    container.appendChild(help);

    const makeClick = (element, ...f) => {
      f = f.filter(x => !!x);
      if (!f.length) {
        return;
      }
      element.style.cursor = "pointer";
      element.addEventListener("click", e => {
        addFilters(...f);
        e.stopPropagation();
      });
    };

    let selectedElement = null;
    data.forEach(stat => {
      const element = document.createElement("DIV");
      switch (stat.type) {
        case "text":
          element.innerText = stat.value;
          break;
        case "table":
          {
            const table = document.createElement("TABLE");
            element.appendChild(table);
            stat.table.forEach(row => {
              let prettyTitle;
              switch (row.kind) {
                case "property":
                  prettyTitle = x => `${x} ${row.property}`;
                  break;
                default:
                  prettyTitle = x => x;
              }
              const tr = document.createElement("TR");
              table.appendChild(tr);
              const title = document.createElement("TD");
              title.innerText = prettyTitle(row.title);
              tr.appendChild(title);
              const value = document.createElement("TD");
              value.innerText = breakSlashes(row.value.toString());
              tr.appendChild(value);
              if (row.kind == "property") {
                makeClick(tr, propertyFilterMaker(row.type)(row.json));
              }
            });
          }
          break;
        case "crosstab":
          {
            const makeColumnFilter = propertyFilterMaker(stat.column);
            const makeRowFilter = propertyFilterMaker(stat.row);

            const table = document.createElement("TABLE");
            element.appendChild(table);

            const header = document.createElement("TR");
            table.appendChild(header);

            header.appendChild(document.createElement("TH"));
            const columns = stat.columns.sort().map(col => ({
              name: col.name,
              filter: makeColumnFilter(col.value)
            }));
            for (let col of columns) {
              const currentHeader = document.createElement("TH");
              currentHeader.innerText = breakSlashes(col.name);
              header.appendChild(currentHeader);
              makeClick(currentHeader, col.filter);
            }
            const maximum = Math.max(
              1,
              Math.max(
                ...Object.values(stat.data).map(row =>
                  Math.max(...Object.values(row))
                )
              )
            );

            for (let rowKey of Object.keys(stat.data).sort()) {
              const rowFilter = makeRowFilter(stat.rows[rowKey]);
              const currentRow = document.createElement("TR");
              table.appendChild(currentRow);

              const currentHeader = document.createElement("TH");
              currentHeader.innerText = breakSlashes(rowKey);
              currentRow.appendChild(currentHeader);
              makeClick(currentRow, rowFilter);

              for (let col of columns) {
                const currentValue = document.createElement("TD");
                if (stat.data[rowKey][col.name]) {
                  currentValue.innerText = stat.data[rowKey][col.name];
                }
                currentRow.appendChild(currentValue);
                setColorIntensity(
                  currentValue,
                  stat.data[rowKey][col.name],
                  maximum
                );
                makeClick(currentValue, col.filter, rowFilter);
              }
            }
          }
          break;

        case "histogram":
          {
            const boundaryLabels = stat.boundaries.map(x => formatTimeBin(x));
            const max = Math.log(
              Math.max(...Object.values(stat.counts).flat()) + 1
            );
            const labels = Object.keys(stat.counts).map(
              bin => " " + nameForBin(bin)
            );
            const div = document.createElement("div");
            div.className = "histogram";
            let selectionStart = null;
            div.width = "90%";
            element.appendChild(div);
            const canvas = document.createElement("canvas");
            const ctxt = canvas.getContext("2d");
            const rowHeight = 40;
            const fontHeight = 10; // We should be able to compute this from the font metrics, but they don't provide it, so uhh...10pts.
            const columnLabelHeight =
              Math.sin(headerAngle) *
                Math.max(
                  ...boundaryLabels.map(l => ctxt.measureText(l[0]).width)
                ) +
              2 * fontHeight;
            canvas.height = labels.length * rowHeight + columnLabelHeight;
            div.appendChild(canvas);
            const currentTime = document.createElement("span");
            currentTime.innerText = "\u00A0";
            element.appendChild(currentTime);
            const redraw = () => {
              const cs = getComputedStyle(div);
              const width = parseInt(cs.getPropertyValue("width"), 10);
              canvas.width = width;

              const labelWidth = Math.max(
                ...labels.map(l => ctxt.measureText(l).width)
              );
              const columnWidth =
                (width - labelWidth) / (boundaryLabels.length - 1);
              const columnSkip = Math.ceil(
                2 * fontHeight * Math.cos(headerAngle) / columnWidth
              );

              const repaint = selectionEnd => {
                ctxt.clearRect(0, 0, width, canvas.height);
                ctxt.fillStyle = "#000";
                boundaryLabels.forEach((label, index) => {
                  if (index % columnSkip == 0) {
                    // We can only apply rotation about the origin, so move the origin to the point where we want to draw the text, rotate it, draw the text at the origin, then reset the coordinate system.
                    ctxt.translate(index * columnWidth, columnLabelHeight);
                    ctxt.rotate(-headerAngle);
                    ctxt.fillText(
                      label[0],
                      fontHeight * Math.tan(headerAngle),
                      0
                    );
                    ctxt.setTransform(1, 0, 0, 1, 0, 0);
                  }
                });
                Object.entries(stat.counts).forEach(
                  ([bin, counts], binIndex) => {
                    if (counts.length != boundaryLabels.length - 1) {
                      throw new Error(
                        `Data type ${bin} has ${
                          counts.length
                        } but expected ${boundaryLabels.length - 1}`
                      );
                    }
                    for (
                      let countIndex = 0;
                      countIndex < counts.length;
                      countIndex++
                    ) {
                      if (
                        selectionStart &&
                        selectionEnd &&
                        selectionStart.bin == binIndex &&
                        selectionEnd.bin == binIndex &&
                        countIndex >=
                          Math.min(
                            selectionStart.boundary,
                            selectionEnd.boundary
                          ) &&
                        countIndex <=
                          Math.max(
                            selectionStart.boundary,
                            selectionEnd.boundary
                          )
                      ) {
                        ctxt.fillStyle = "#E0493B";
                      } else {
                        ctxt.fillStyle = "#06AED5";
                      }
                      ctxt.globalAlpha = Math.log(counts[countIndex] + 1) / max;
                      ctxt.fillRect(
                        countIndex * columnWidth + 1,
                        binIndex * rowHeight + 2 + columnLabelHeight,
                        columnWidth - 2,
                        rowHeight - 4
                      );
                    }
                    ctxt.fillStyle = "#000";
                    ctxt.globalAlpha = 1;
                    ctxt.fillText(
                      labels[binIndex],
                      width - labelWidth,
                      binIndex * rowHeight +
                        (rowHeight + fontHeight) / 2 +
                        columnLabelHeight
                    );
                  }
                );
              };
              repaint(null);
              const findSelection = e => {
                if (e.button != 0) return null;
                const bounds = canvas.getBoundingClientRect();
                const x = e.clientX - bounds.left;
                const y = e.clientY - bounds.top - columnLabelHeight;
                if (y > 0 && x > 0 && x < width - labelWidth) {
                  return {
                    bin: Math.max(0, Math.floor(y / rowHeight)),
                    boundary: Math.max(
                      0,
                      Math.floor(
                        x / (width - labelWidth) * (boundaryLabels.length - 1)
                      )
                    )
                  };
                }
                return null;
              };
              canvas.onmousedown = e => {
                selectionStart = findSelection(e);
                if (selectionStart) {
                  currentTime.innerText =
                    Object.values(stat.counts)[selectionStart.bin][
                      selectionStart.boundary
                    ] +
                    " actions over " +
                    formatTimeSpan(
                      stat.boundaries[selectionStart.boundary + 1] -
                        stat.boundaries[selectionStart.boundary]
                    ) +
                    " (" +
                    boundaryLabels[selectionStart.boundary][0] +
                    " to " +
                    boundaryLabels[selectionStart.boundary + 1][0] +
                    ")";
                  currentTime.title =
                    boundaryLabels[selectionStart.boundary][1];
                  repaint(selectionStart);
                } else {
                  currentTime.innerText = "\u00A0";
                  currentTime.title = "";
                }
              };
              const mouseWhileDown = (e, after) => {
                const selectionEnd = findSelection(e);
                repaint(selectionEnd);
                if (selectionStart.bin == selectionEnd.bin) {
                  const startBound = Math.min(
                    selectionStart.boundary,
                    selectionEnd.boundary
                  );
                  const endBound =
                    Math.max(selectionStart.boundary, selectionEnd.boundary) +
                    1;
                  const [typeName, counts] = Object.entries(stat.counts)[
                    selectionEnd.bin
                  ];
                  const sum = counts.reduce(
                    (acc, value, index) =>
                      index >= startBound && index < endBound
                        ? acc + value
                        : acc,
                    0
                  );
                  currentTime.innerText =
                    sum +
                    " actions over " +
                    formatTimeSpan(
                      stat.boundaries[endBound] - stat.boundaries[startBound]
                    ) +
                    " (" +
                    boundaryLabels[startBound][0] +
                    " to " +
                    boundaryLabels[endBound][0] +
                    ")";
                  currentTime.title =
                    boundaryLabels[startBound][1] +
                    " to " +
                    boundaryLabels[endBound][1];
                  after(
                    typeName,
                    stat.boundaries[startBound],
                    stat.boundaries[endBound]
                  );
                } else {
                  currentTime.innerText = "\u00A0";
                  currentTime.title = "";
                }
              };
              canvas.onmouseup = e => {
                mouseWhileDown(e, (typeName, start, end) => {
                  addFilters([
                    typeName,
                    x => ({
                      start: Math.max(start, x ? x.start || 0 : 0),
                      end: Math.min(end, x ? x.end || Infinity : Infinity)
                    })
                  ]);
                });
                selectionStart = null;
              };
              canvas.onmousemove = e => {
                if (selectionStart) {
                  mouseWhileDown(e, (typeName, start, end) => {});
                }
              };
            };
            let timeout = window.setTimeout(redraw, 100);
            window.addEventListener("resize", () => {
              clearTimeout(timeout);
              window.setTimeout(redraw, 100);
            });
          }
          break;

        default:
          element.innerText = `Unknown stat type: ${stat.type}`;
      }
      container.appendChild(element);
    });
  };
  refresh();
}

export function toggleCollapse(title) {
  const visible = !title.nextSibling.style.maxHeight;

  title.className = visible ? "collapse open" : "collapse close";
  title.nextSibling.style.maxHeight = visible
    ? `${title.nextSibling.scrollHeight}px`
    : null;
}

export function runCheck(button, sourceCode, outputContainer) {
  clearChildren(outputContainer);
  addThrobber(outputContainer);
  fetch("/checkhtml", {
    body: sourceCode,
    method: "POST"
  })
    .then(response => {
      if (response.ok) {
        return Promise.resolve(response);
      } else {
        return Promise.reject(new Error("Failed to load"));
      }
    })
    .then(response => response.text())
    .then(text => new window.DOMParser().parseFromString(text, "text/html"))
    .then(response => {
      clearChildren(outputContainer);
      const body = response.getElementsByTagName("body")[0];
      while (body.children.length > 0) {
        outputContainer.appendChild(document.adoptNode(body.children[0]));
      }
    })
    .catch(function(error) {
      outputContainer.innerText = error.message;
    });
}

export function loadFile(textContainer) {
  const input = document.createElement("INPUT");
  input.type = "file";

  input.onchange = e => {
    const reader = new FileReader();
    reader.onload = rev => (textContainer.value = rev.target.result);
    reader.readAsText(e.target.files[0], "UTF-8");
  };

  input.click();
}

export function pauseOlive(element, target) {
  target.pause = element.getAttribute("is-paused") !== "true";
  fetchJsonWithBusyDialog(
    "/pauseolive",
    {
      body: JSON.stringify(target),
      method: "POST"
    },
    response => {
      element.innerText = response ? "▶ Resume Actions" : "⏸ Pause Actions";
      element.setAttribute("is-paused", response);
    }
  );
}

export function clearDeadPause(row, target, purgeFirst) {
  const removePause = () => {
    target.pause = false;
    fetchJsonWithBusyDialog(
      "/pauseolive",
      {
        body: JSON.stringify(target),
        method: "POST"
      },
      response => {
        if (!response) {
          const tbody = row.parentNode;
          tbody.removeChild(row);
          if (tbody.childNodes.length == 1) {
            const div = tbody.parentNode.parentNode;
            div.parentNode.removeChild(div);
          }
        }
      }
    );
  };
  if (purgeFirst) {
    fetchJsonWithBusyDialog(
      "/purge",
      {
        body: JSON.stringify([
          {
            type: "sourcelocation",
            locations: [target]
          }
        ]),
        method: "POST"
      },
      removePause
    );
  } else {
    removePause();
  }
}
