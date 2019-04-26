import { actionRender } from "./actions.js";

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

const actionStates = [
  "FAILED",
  "INFLIGHT",
  "QUEUED",
  "SUCCEEDED",
  "THROTTLED",
  "UNKNOWN",
  "WAITING"
];
const types = new Map();
const locations = new Map();
const selectedStates = new Map();
let availableLocations;

function clearSelectableMap(currentId, newId, stateMap, source, classForItem) {
  stateMap.clear();
  for (const item of source) {
    stateMap.set(item, false);
  }

  const currentElement = document.getElementById(currentId);
  const newElement = document.getElementById(newId);

  function redraw() {
    clearChildren(currentElement);
    clearChildren(newElement);
    for (const entry of stateMap.entries()) {
      const element = document.createElement("SPAN");
      element.innerText = entry[0] + " ";
      element.className = classForItem(entry[0]);
      (entry[1] ? currentElement : newElement).appendChild(element);
      const toggle = () => {
        stateMap.set(entry[0], !entry[1]);
        redraw();
      };
      if (entry[1]) {
        const removeElement = document.createElement("SPAN");
        removeElement.innerText = "✖";
        removeElement.onclick = toggle;
        element.appendChild(removeElement);
        currentElement.appendChild(document.createTextNode(" "));
      } else {
        element.onclick = toggle;
      }
    }
  }
  redraw();
}

function clearTypes() {
  clearSelectableMap(
    "currentTypes",
    "newTypes",
    types,
    Array.from(actionRender.keys()).sort(),
    type => ""
  );
}

function clearLocations() {
  clearSelectableMap(
    "currentLocations",
    "newLocations",
    locations,
    availableLocations.keys(),
    element => null
  );
}

function clearStates() {
  clearSelectableMap(
    "currentStates",
    "newStates",
    selectedStates,
    actionStates,
    state => `${state.toLowerCase()}`
  );
}

let selectedSavedSearch = null;

export function initialiseActionDash(definedLocations, serverSearches) {
  availableLocations = new Map(
    Object.entries(definedLocations).sort(entry => entry[0])
  );
  const searchTypeElement = document.getElementById("searchType");
  const searchTypesElement = document.getElementById("searchTypes");
  for (const searchDescription of [
    ["text", "Text (Case-sensitive)", true],
    ["texti", "Text (Case-insensitive)", false],
    ["regex", "Regular Expression", false]
  ]) {
    const element = document.createElement("SPAN");
    element.innerText = searchDescription[1];
    element.onclick = e => {
      searchType = searchDescription[0];
      searchTypeElement.innerText = searchDescription[1];
    };
    if (searchDescription[2]) {
      element.onclick(null);
    }
    searchTypesElement.appendChild(element);
  }
  document
    .getElementById("clearStatesButton")
    .addEventListener("click", clearStates);
  document
    .getElementById("clearTypesButton")
    .addEventListener("click", clearTypes);
  document
    .getElementById("listActionsButton")
    .addEventListener("click", listActions);
  document
    .getElementById("purgeButton")
    .addEventListener("click", () => purge(makeFilters()));
  document
    .getElementById("queryStatsButton")
    .addEventListener("click", queryStats);
  document
    .getElementById("copyButton")
    .addEventListener("click", () => copyJson(makeFilters()));
  document
    .getElementById("showQueryButton")
    .addEventListener("click", () =>
      showFilterJson(makeFilters(), document.getElementById("results"))
    );
  document
    .getElementById("listActionsSavedButton")
    .addEventListener("click", () => {
      if (selectedSavedSearch !== null) {
        nextPage(
          {
            filters: selectedSavedSearch,
            limit: 25,
            skip: 0
          },
          document.getElementById("results"),
          true
        );
      }
    });
  document
    .getElementById("queryStatsSavedButton")
    .addEventListener("click", () => {
      if (selectedSavedSearch !== null) {
        getStats(selectedSavedSearch, document.getElementById("results"), true);
      }
    });
  document.getElementById("copySavedButton").addEventListener("click", () => {
    if (selectedSavedSearch !== null) {
      copyJson(selectedSavedSearch);
    }
  });
  document
    .getElementById("showQuerySavedButton")
    .addEventListener("click", () => {
      if (selectedSavedSearch !== null) {
        showFilterJson(selectedSavedSearch, document.getElementById("results"));
      }
    });
  document.getElementById("purgeSavedButton").addEventListener("click", () => {
    if (selectedSavedSearch !== null) {
      purge(selectedSavedSearch);
    }
  });
  if (availableLocations.size > 0) {
    document
      .getElementById("clearLocationsButton")
      .addEventListener("click", clearLocations);
    clearLocations();
  }
  clearStates();
  clearTypes();

  const customSearchPane = document.getElementById("customSearchPane");
  const savedSearchPane = document.getElementById("savedSearchPane");
  const customSearchButton = document.getElementById("customSearchButton");
  const savedSearchButton = document.getElementById("savedSearchButton");
  customSearchButton.onclick = () => {
    customSearchPane.style.display = "block";
    customSearchButton.className = "tab selected";
    savedSearchPane.style.display = "none";
    savedSearchButton.className = "tab";
  };
  savedSearchButton.onclick = () => {
    savedSearchPane.style.display = "block";
    customSearchButton.className = "tab";
    customSearchPane.style.display = "none";
    savedSearchButton.className = "tab selected";
  };

  function showSavedSearches() {
    selectedSavedSearch = null;
    try {
      savedSearches(
        JSON.parse(localStorage.getItem("shesmu_searches") || "{}"),
        true,
        showSavedSearches
      );
    } catch (e) {
      console.log(e);
    }
    savedSearches(serverSearches, false, showSavedSearches);
  }
  showSavedSearches();
  document
    .getElementById("saveSearchButton")
    .addEventListener("click", () => saveSearch(showSavedSearches));
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

    const button = document.createElement("SPAN");
    button.className = "load";
    button.innerText = "Save";
    dialog.appendChild(button);
    button.onclick = () => {
      const name = input.value.trim();
      let filters = null;
      try {
        filters = JSON.parse(filterJSON.value);
      } catch (e) {
        makePopup().innerText = e;
        return;
      }
      if (name) {
        let localSearches = {};
        try {
          localSearches = JSON.parse(
            localStorage.getItem("shesmu_searches") || "{}"
          );
        } catch (e) {
          console.log(e);
        }
        localSearches[name] = filters;
        localStorage.setItem("shesmu_searches", JSON.stringify(localSearches));
        close();
        showSavedSearches();
      }
    };
  });

  document.getElementById("importButton").addEventListener("click", () => {
    const [dialog, close] = makePopup(true);
    const importJSON = document.createElement("TEXTAREA");
    dialog.appendChild(importJSON);

    const button = document.createElement("SPAN");
    button.className = "load";
    button.innerText = "Import";
    dialog.appendChild(button);
    button.onclick = () => {
      let localSearches = {};
      try {
        localSearches = JSON.parse(
          localStorage.getItem("shesmu_searches") || "{}"
        );
      } catch (e) {
        console.log(e);
      }
      try {
        for (const entry of Object.entries(JSON.parse(importJSON.value))) {
          localSearches[entry[0]] = entry[1];
        }
      } catch (e) {
        makePopup().innerText = e;
        return;
      }

      localStorage.setItem("shesmu_searches", JSON.stringify(localSearches));
      close();
      showSavedSearches();
    };
  });

  document.getElementById("exportButton").addEventListener("click", () => {
    try {
      copyJson(JSON.parse(localStorage.getItem("shesmu_searches") || "{}"));
    } catch (e) {
      console.log(e);
    }
  });
}

function copyJson(data) {
  const buffer = document.getElementById("copybuffer");
  buffer.value = JSON.stringify(data, null, 2);
  buffer.style = "display: inline;";
  buffer.select();
  document.execCommand("Copy");
  buffer.style = "display: none;";
}

function saveSearch(showSavedSearches) {
  const filters = makeFilters();
  const [dialog, close] = makePopup(true);
  if (filters.length == 0) {
    dialog.innerText = "Umm, saving an empty search seems really pointless.";
    return;
  }
  dialog.appendChild(document.createTextNode("Save search as: "));
  const input = document.createElement("INPUT");
  input.type = "text";
  dialog.appendChild(input);

  const button = document.createElement("SPAN");
  button.className = "load";
  button.innerText = "Save";
  dialog.appendChild(button);
  button.onclick = () => {
    const name = input.value.trim();
    if (name) {
      let localSearches = {};
      try {
        localSearches = JSON.parse(
          localStorage.getItem("shesmu_searches") || "{}"
        );
      } catch (e) {
        console.log(e);
      }
      localSearches[name] = filters;
      localStorage.setItem("shesmu_searches", JSON.stringify(localSearches));
      close();
      showSavedSearches();
    }
  };
}

function savedSearches(searches, userDefined, showSavedSearches) {
  const searchContainer = document.getElementById("savedSearches");
  if (userDefined) {
    clearChildren(searchContainer);
  }
  const results = document.getElementById("results");
  for (const entry of Object.entries(searches)) {
    const element = document.createElement("DIV");
    searchContainer.appendChild(element);
    element.innerText = entry[0];
    element.onclick = () => {
      for (let i = 0; i < searchContainer.children.length; i++) {
        searchContainer.children[i].className = "";
      }
      element.className = "selected";
      selectedSavedSearch = entry[1];
    };
    if (userDefined) {
      const close = document.createElement("SPAN");
      element.appendChild(close);
      close.innerText = "✖";
      close.onclick = () => {
        try {
          const localSearches = JSON.parse(
            localStorage.getItem("shesmu_searches") || "{}"
          );
          delete localSearches[entry[0]];
          localStorage.setItem(
            "shesmu_searches",
            JSON.stringify(localSearches)
          );
          showSavedSearches();
        } catch (e) {
          console.log(e);
        }
      };
    }
  }
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

  for (let span of ["added", "checked", "statuschanged"]) {
    const start = parseEpoch(`${span}Start`);
    const end = parseEpoch(`${span}End`);
    if (start !== null && end != null) {
      filters.push({ type: span, start: start, end: end });
    }
  }

  const text = document.getElementById("searchText").value.trim();
  if (text) {
    switch (searchType) {
      case "text":
        filters.push({ type: "text", matchCase: true, text: text });
        break;
      case "texti":
        filters.push({ type: "text", matchCase: false, text: text });
        break;
      case "regex":
        filters.push({ type: "regex", pattern: text });
        break;
    }
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

function listActions() {
  const query = {
    filters: makeFilters(),
    limit: 25,
    skip: 0
  };
  nextPage(query, document.getElementById("results"), true);
}

export function filterForOlive(filename, line, column, timestamp) {
  return [
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
  ];
}

function makePopup(returnClose) {
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
    }
  };
  closeButton.onclick = e => document.body.removeChild(modal);

  return returnClose ? [inner, closeButton.onclick] : inner;
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

export function queryStatsPopup(filters) {
  getStats(filters, makePopup(), false);
}

export function text(t) {
  const element = document.createElement("P");
  const tSpecial = t.replace(/\n/g, "⏎");
  if (t.length > 100 || tSpecial != t) {
    element.title = "There's a lot to unpack here.";
    element.innerText = tSpecial.substring(0, 98) + "...";
    element.style.cursor = "pointer";
    element.onclick = () => {
      const popup = makePopup();
      const pre = document.createElement("PRE");
      pre.innerText = t;
      popup.appendChild(pre);
    };
  } else {
    element.innerText = t;
  }
  return element;
}

export function link(url, t) {
  const element = document.createElement("A");
  element.innerText = t + " 🔗";
  element.target = "_blank";
  element.href = url;
  return element;
}

export function jsonParameters(action) {
  return Object.entries(action.parameters).map(p =>
    text(`Parameter ${p[0]} = ${JSON.stringify(p[1], null, 2)}`)
  );
}

export function title(action, t) {
  const element = action.url ? link(action.url, t) : text(t);
  element.title = action.state;
  return element;
}

function defaultRenderer(action) {
  return [title(action, `Unknown Action: ${action.type}`)];
}

function nextPage(query, targetElement, onActionPage) {
  results(targetElement, "/query", JSON.stringify(query), (container, data) => {
    const jumble = document.createElement("DIV");
    if (data.results.length == 0) {
      jumble.innerText = "No actions found.";
    }

    data.results.forEach(action => {
      const tile = document.createElement("DIV");
      tile.className = `action state_${action.state.toLowerCase()}`;
      (actionRender.get(action.type) || defaultRenderer)(action).forEach(
        element => tile.appendChild(element)
      );
      const checkDate = new Date(action.lastChecked).toString();
      const addedDate = new Date(action.lastAdded).toString();
      const statusChangedDate = new Date(action.lastStatusChange).toString();
      tile.appendChild(
        text(`Last Checked: ${checkDate} (${action.lastChecked})`)
      );
      tile.appendChild(text(`Last Added: ${addedDate} (${action.lastAdded})`));
      tile.appendChild(
        text(
          `Last Status Change: ${statusChangedDate} (${
            action.lastStatusChange
          })`
        )
      );
      action.locations.forEach(l => {
        const t = `Made from ${l.file}:${l.line}:${l.column}[${new Date(
          l.time
        ).toISOString()}]`;
        tile.appendChild(text(t));
        if (l.url) {
          tile.appendChild(link(l.url, "View Source"));
        }
        if (onActionPage) {
          tile.appendChild(
            link(
              `olivedash#${l.file}:${l.line}:${l.column}:${l.time}`,
              "View in Olive dashboard"
            )
          );
        }
      });
      const showHide = document.createElement("P");
      showHide.style.cursor = "pointer";
      const json = document.createElement("PRE");
      json.className = "json collapsed";
      json.innerText = JSON.stringify(action, null, 2);
      tile.appendChild(showHide);
      tile.appendChild(json);
      let visible = true;
      showHide.onclick = () => {
        visible = !visible;
        showHide.innerText = visible ? "⊟ JSON" : "⊞ JSON";
        json.style.maxHeight = visible ? `${json.scrollHeight}px` : null;
      };
      showHide.onclick();
      jumble.appendChild(tile);
    });

    container.appendChild(jumble);
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
  });
}

function queryStats() {
  getStats(makeFilters(), document.getElementById("results"), true);
}

function showFilterJson(filters, targetElement) {
  clearChildren(targetElement);
  const pre = document.createElement("PRE");
  pre.className = "json";
  pre.innerText = JSON.stringify(filters, null, 2);
  targetElement.appendChild(pre);
}

function propertyFilterMaker(name) {
  switch (name) {
    case "sourcefile":
      return f => ({ type: "sourcefile", files: [f] });
    case "sourcelocation":
      return l => ({ type: "sourcelocation", locations: [l] });
    case "status":
      return s => ({ type: "status", states: [s] });
    case "type":
      return t => ({ type: "type", types: [t] });
    default:
      return () => "";
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
    default:
      return name;
  }
}

function formatBin(name) {
  switch (name) {
    case "added":
    case "checked":
    case "statuschanged":
      return x => {
        const d = new Date(x);
        let diff = Math.ceil((new Date() - d) / 1000);
        let ago = "";
        for (let span of [
          [604800, "w"],
          [86400, "d"],
          [3600, "h"],
          [60, "m"]
        ]) {
          const chunk = Math.floor(diff / span[0]);
          if (chunk > 0) {
            ago = `${ago}${chunk}${span[1]}`;
            diff = diff % span[0];
          }
        }
        if (diff > 0 || !ago) {
          ago = `${ago}${diff}s ago`;
        }
        return [ago, `${x} ${d.toISOString()}`];
      };
    default:
      return x => [x, ""];
  }
}

function setColorIntensity(element, value, maximum) {
  element.style.backgroundColor = `hsl(191, 95%, ${Math.ceil(
    97 - (value || 0) / maximum * 20
  )}%)`;
}

function purge(filters) {
  const targetElement = document.getElementById("results");
  if (filters.length == 0) {
    clearChildren(targetElement);
    const sarcasm = document.createElement("P");
    sarcasm.innerText =
      "Yeah, no. You can't nuke all the actions. Maybe try a subset.";
    targetElement.appendChild(sarcasm);
    const purgeButton = document.createElement("SPAN");
    purgeButton.className = "load danger";
    purgeButton.innerText = "🔥 NUKE IT ALL FROM ORBIT 🔥";
    targetElement.appendChild(purgeButton);
    purgeButton.onclick = () => {
      purgeActions(filters, targetElement, () => {});
    };
  } else {
    purgeActions(filters, targetElement, () => {});
  }
}

function purgeActions(filters, targetElement, callback) {
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
      callback();
    }
  );
}

function getStats(filters, targetElement, onActionPage) {
  results(
    targetElement,
    "/stats",
    JSON.stringify(filters),
    (container, data) => {
      if (data.length == 0) {
        container.innerText = "No statistics are available.";
        return;
      }
      const help = document.createElement("P");
      help.innerText =
        "Click any cell or table heading to view matching results.";
      targetElement.appendChild(help);

      const drillDown = document.createElement("DIV");
      let selectedElement = null;
      const makeClick = (clickable, filters) => {
        clickable.onclick = () => {
          if (selectedElement) {
            selectedElement.classList.remove("userselected");
          }
          selectedElement = clickable;
          selectedElement.classList.add("userselected");
          clearChildren(drillDown);
          const clickResult = document.createElement("DIV");
          const toolBar = document.createElement("P");
          const listButton = document.createElement("SPAN");
          listButton.className = "load";
          listButton.innerText = "🔍 List";
          toolBar.appendChild(listButton);
          const statsButton = document.createElement("SPAN");
          statsButton.className = "load";
          statsButton.innerText = "📈 Stats";
          toolBar.appendChild(statsButton);
          const jsonButton = document.createElement("SPAN");
          jsonButton.className = "load accessory";
          jsonButton.innerText = "🛈 Show Request";
          toolBar.appendChild(jsonButton);
          const purgeButton = document.createElement("SPAN");
          purgeButton.className = "load danger";
          purgeButton.innerText = "☠️ PURGE";
          toolBar.appendChild(purgeButton);
          listButton.onclick = () => {
            nextPage(
              {
                filters: filters,
                limit: 25,
                skip: 0
              },
              clickResult,
              onActionPage
            );
          };
          statsButton.onclick = () => {
            getStats(filters, clickResult, onActionPage);
          };
          jsonButton.onclick = () => {
            showFilterJson(filters, clickResult);
          };
          purgeButton.onclick = () => {
            purgeActions(filters, targetElement, () => {
              const refreshToolbar = document.createElement("DIV");
              const refreshButton = document.createElement("SPAN");
              refreshButton.className = "load accessory";
              refreshButton.innerText = "🔄 Refresh";
              refreshToolbar.appendChild(refreshButton);
              targetElement.appendChild(refreshToolbar);
              refreshButton.onclick = () =>
                getStats(filters, targetElement, onActionPage);
            });
          };
          drillDown.appendChild(toolBar);
          drillDown.appendChild(clickResult);
        };
      };
      data.forEach(stat => {
        const element = document.createElement("DIV");
        switch (stat.type) {
          case "text":
            (() => {
              element.innerText = stat.value;
            })();
            break;
          case "table":
            (() => {
              const table = document.createElement("TABLE");
              element.appendChild(table);
              stat.table.forEach(row => {
                let prettyTitle;
                switch (row.kind) {
                  case "bin":
                    prettyTitle = x => `${x} ${nameForBin(row.type)}`;
                    break;
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
                if (row.kind == "bin") {
                  const values = formatBin(row.type)(row.value);
                  value.innerText = values[0];
                  value.title = values[1];
                } else {
                  value.innerText = row.value;
                }
                tr.appendChild(value);
                if (row.kind == "property") {
                  makeClick(
                    tr,
                    filters.concat([propertyFilterMaker(row.type)(row.json)])
                  );
                } else {
                  tr.onclick = () => {
                    clearChildren(drillDown);
                    if (selectedElement) {
                      selectedElement.classList.remove("userselected");
                    }
                    selectedElement = null;
                  };
                }
              });
            })();
            break;
          case "crosstab":
            (() => {
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
                currentHeader.innerText = col.name;
                header.appendChild(currentHeader);
                makeClick(currentHeader, filters.concat([col.filter]));
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
                currentHeader.innerText = rowKey;
                currentRow.appendChild(currentHeader);
                makeClick(currentHeader, filters.concat([rowFilter]));

                for (let col of columns) {
                  const currentValue = document.createElement("TD");
                  currentValue.innerText = stat.data[rowKey][col.name] || "0";
                  currentRow.appendChild(currentValue);
                  setColorIntensity(
                    currentValue,
                    stat.data[rowKey][col.name],
                    maximum
                  );
                  makeClick(
                    currentValue,
                    filters.concat([col.filter, rowFilter])
                  );
                }
              }
            })();
            break;

          case "histogram":
            (() => {
              const section = document.createElement("H1");
              section.innerText = nameForBin(stat.bin);
              element.appendChild(section);
              const maximum = Math.max(1, Math.max(...stat.counts));
              const table = document.createElement("TABLE");
              element.appendChild(table);
              const header = document.createElement("TR");
              table.appendChild(header);
              const startHeader = document.createElement("TH");
              startHeader.innerText = "≥";
              header.appendChild(startHeader);
              const endHeader = document.createElement("TH");
              endHeader.innerText = "<";
              header.appendChild(endHeader);
              const valueHeader = document.createElement("TH");
              valueHeader.innerText = "Actions";
              header.appendChild(valueHeader);

              const formattedBoundaries = stat.boundaries.map(
                formatBin(stat.bin)
              );

              for (let i = 0; i < stat.counts.length; i++) {
                const row = document.createElement("TR");
                table.appendChild(row);
                const start = document.createElement("TH");
                start.innerText = formattedBoundaries[i][0];
                start.title = formattedBoundaries[i][1];
                row.appendChild(start);
                makeClick(
                  start,
                  filters.concat([
                    {
                      type: stat.bin,
                      start: stat.boundaries[i],
                      end: null
                    }
                  ])
                );

                const end = document.createElement("TH");
                end.innerText = formattedBoundaries[i + 1][0];
                end.title = formattedBoundaries[i + 1][1];
                row.appendChild(end);
                makeClick(
                  end,
                  filters.concat([
                    {
                      type: stat.bin,
                      start: null,
                      end: stat.boundaries[i + 1]
                    }
                  ])
                );

                const value = document.createElement("TD");
                value.innerText = stat.counts[i];
                row.appendChild(value);
                makeClick(
                  value,
                  filters.concat([
                    {
                      type: stat.bin,
                      start: stat.boundaries[i],
                      end: stat.boundaries[i + 1]
                    }
                  ])
                );
                setColorIntensity(value, stat.counts[i], maximum);
              }
            })();
            break;

          default:
            element.innerText = `Unknown stat type: ${stat.type}`;
        }
        container.appendChild(element);
      });
      container.appendChild(drillDown);
    }
  );
}

export function toggleBytecode(title) {
  const visible = !title.nextSibling.style.maxHeight;

  title.innerText = visible ? "⊟ Bytecode" : "⊞ Bytecode";
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
