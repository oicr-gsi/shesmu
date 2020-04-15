import {
  blank,
  breakSlashes,
  collapse,
  commonPathPrefix,
  formatTimeBin,
  formatTimeSpan,
  link,
  preformatted,
  table,
  text,
  title,
  objectTable,
  toggleCollapse,
  visibleText
} from "./utils.js";
import { actionRender, specialImports } from "./actions.js";

function makeButton(label, title, className, callback) {
  const button = document.createElement("SPAN");
  button.className = "load" + className;
  button.innerText = label;
  button.title = title;
  button.addEventListener("click", callback);
  return button;
}

function button(label, title, callback) {
  return makeButton(label, title, "", callback);
}
function accessoryButton(label, title, callback) {
  return makeButton(label, title, " accessory", callback);
}
function dangerButton(label, title, callback) {
  return makeButton(label, title, " danger", callback);
}

function alertEntries(entries) {
  return Object.entries(entries).map(([name, value]) => {
    const span = document.createElement("SPAN");
    span.className = "label";
    span.innerText = `${name} = ${value}`;
    return [span, document.createElement("BR")];
  });
}

function statusButton(state, isButton) {
  const button = document.createElement("SPAN");
  button.title = actionStates[state];
  button.innerText = state;
  button.classList.add(`state_${state.toLowerCase()}`);
  if (isButton) {
    button.classList.add("load");
  }
  return button;
}

function addThrobber(container) {
  const throbber = document.createElement("OBJECT");
  throbber.data = "press.svg";
  throbber.type = "image/svg+xml";
  throbber.className = "throbber";
  throbber.style.visibility = "hidden";
  container.appendChild(throbber);
  window.setTimeout(() => (throbber.style.visibility = "visible"), 500);
}

function infoForProduces(produces) {
  switch (produces) {
    case "ACTIONS":
      return ["🎬 ", "Produces actions"];
    case "ALERTS":
      return ["🔔", "Produces alerts"];
    case "REFILL":
      return ["🗑", "Refills a database"];
    default:
      return ["🤷", "I have no idea what this olive does."];
  }
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
      } else if (response.status == 503) {
        closeBusy();
        const dialog = makePopup();
        dialog.appendChild(text("Shesmu is currently overloaded."));
        dialog.appendChild(
          button("Retry", "Attempt operation again.", () =>
            fetchJsonWithBusyDialog(url, parameters, callback)
          )
        );

        return Promise.reject(null);
      } else {
        return Promise.reject(
          new Error(`Failed to load: ${response.status} ${response.statusText}`)
        );
      }
    })
    .then(response => response.json())
    .then(response => {
      callback(response);
    })
    .catch(error => {
      if (error) {
        makePopup().innerText = error.message;
      }
    })
    .finally(closeBusy);
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
      document.getElementById("wdlType").innerText = data.wdlType;
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
  j: function(input) {
    let match = input.match(/^\s*(\d+(\.\d*)?([eE][+-]?\d+)?)/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: parseFloat(match[1])
      };
    }
    match = input.match(
      /^\s*"(((?=\\)\\(["\\\/bfnrt]|u[0-9a-fA-F]{4}))|[^"\\\0-\x1F\x7F]+)*"/
    );
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: match[1] || ""
      };
    }
    match = input.match(/^\s*true/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: true
      };
    }
    match = input.match(/^\s*false/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: false
      };
    }
    match = input.match(/^\s*null/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: null
      };
    }
    match = input.match(/^\s*\[/);
    if (match) {
      const result = [];
      let current = input.substring(match[0].length);

      while (true) {
        match = current.match(/^\s*]/);
        if (match) {
          return {
            good: true,
            input: current.substring(match[0].length),
            output: result
          };
        }
        if (result.length) {
          match = current.match(/^\s*,/);
          if (!match) {
            return {
              good: false,
              input: current,
              error: "Expected , or ]."
            };
          }
          current = current.substring(match[0].length);
        }

        const inner = parser.j(current);
        if (!inner.good) {
          return inner;
        }
        result.push(inner.output);
        current = inner.input;
      }
    }
    match = input.match(/^\s*{/);
    if (match) {
      const result = [];
      let current = input.substring(match[0].length);

      while (true) {
        match = current.match(/^\s*}/);
        if (match) {
          return {
            good: true,
            input: current.substring(match[0].length),
            output: Object.fromEntries(result)
          };
        }

        if (result.length) {
          match = current.match(/\s*,/);
          if (!match) {
            return {
              good: false,
              input: current,
              error: "Expected }."
            };
          }
          current = current.substring(match[0].length);
        }
        match = current.match(
          /^\s*"(((?=\\)\\(["\\\/bfnrt]|u[0-9a-fA-F]{4}))|[^"\\\0-\x1F\x7F]+)*"\s*:/
        );
        if (!match) {
          return {
            good: false,
            input: current,
            error: "Expected property name."
          };
        }
        const name = match[1];
        current = current.substring(match[0].length);

        const inner = parser.j(current);
        if (!inner.good) {
          return inner;
        }
        result.push([name, inner.output]);
        current = inner.input;
      }
    }
    return {
      good: false,
      input: input,
      error: "Unexpected input."
    };
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
  m: function(keyType, valueType) {
    return input => {
      const output = [];
      let match = input.match(/^\s*Dict/);
      if (!match) {
        return {
          good: false,
          input: input,
          error: "Expected Dict { in dictionary."
        };
      }
      input = input.substring(match[0].length);
      for (;;) {
        match = input.match(output.length == 0 ? /^\s*(\{)/ : /^\s*([\},])/);
        if (!match) {
          return {
            good: false,
            input: input,
            error:
              output.length == 0
                ? "Expected { in dictionary."
                : "Expected } or , for dictionary."
          };
        }
        if (match[1] == "}") {
          return {
            good: true,
            input: input.substring(match[0].length),
            output: output
          };
        }
        const keyState = keyType(input.substring(match[0].length));
        if (keyState.good) {
          match = keyState.input.match(/\s*=\s*/);
          if (!match) {
            return {
              good: false,
              input: keyState.input,
              error: "Expected = in dictionary."
            };
          }
          const valueState = valueType(
            keyState.input.substring(match[0].length)
          );
          if (valueState.good) {
            output.push([keyState.output, valueState.output]);
            input = valueState.input;
          } else {
            return valueState;
          }
        } else {
          return keyState;
        }
      }
    };
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

export function initialiseActionDash(
  serverSearches,
  tags,
  sources,
  savedQueryName,
  userFilters
) {
  initialise();
  let localSearches = {};
  try {
    localSearches = JSON.parse(localStorage.getItem("shesmu_searches") || "{}");
  } catch (e) {
    console.log(e);
  }

  let currentName = null;
  const searchList = document.getElementById("searches");
  const searchName = document.getElementById("searchName");
  const results = document.getElementById("results");
  const redrawDropDown = (selectedName, initialCustomFilter, isPop) =>
    makeDropDown(
      searchName,
      searchList,
      ([name, query]) => {
        currentName = name;
        getStats(
          query,
          tags,
          sources,
          results,
          true,
          targetElement => makeTabs(targetElement, 0, "Overview", "Actions"),

          (reset, updateLocalSearches, newName) => {
            if (reset) {
              redrawDropDown("All Actions", {}, false);
            } else if (updateLocalSearches) {
              updateLocalSearches(localSearches);
              localStorage.setItem(
                "shesmu_searches",
                JSON.stringify(localSearches)
              );
              redrawDropDown(newName, {}, false);
            }
          },
          name == selectedName ? initialCustomFilter : null,
          filters => {
            if (
              !isPop ||
              name != selectedName ||
              initialCustomFilter != filters
            ) {
              window.history.pushState(
                [name, filters],
                name,
                `actiondash?saved=${encodeURIComponent(
                  name
                )}&filters=${encodeURIComponent(JSON.stringify(filters))}`
              );
            }
          }
        );
      },
      ([name, query]) => name,
      ([name, query]) => name == selectedName,
      [["All Actions", []]].concat(
        Object.entries(serverSearches)
          .concat(Object.entries(localSearches))
          .sort(([a], [b]) => a.localeCompare(b))
      )
    );
  const updateLocalSearches = name => {
    localStorage.setItem("shesmu_searches", JSON.stringify(localSearches));
    redrawDropDown(name, null, false);
  };

  window.addEventListener("popstate", e => {
    if (e.state) {
      const [selectedQuery, initialCustomFilter] = e.state;
      redrawDropDown(selectedQuery, initialCustomFilter, true);
    }
  });

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
      button("Save", "Save to local search collection.", () => {
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
          updateLocalSearches(name);
        }
      })
    );
  });

  document.getElementById("importButton").addEventListener("click", () => {
    const createSaveDialog = (imported, provisionalName) => {
      const [nameDialog, nameClose] = makePopup(true);
      const nameInput = document.createElement("INPUT");
      nameInput.type = "text";
      nameInput.value = provisionalName;
      nameDialog.appendChild(document.createTextNode("Name for search: "));
      nameDialog.appendChild(nameInput);
      nameDialog.appendChild(document.createElement("BR"));
      nameDialog.appendChild(
        button("Import", "Adds the search to your collection", () => {
          if (nameInput.value) {
            nameClose();
            const newName = nameInput.value.trim();
            localSearches[newName] = imported;
            updateLocalSearches(newName);
          }
        })
      );
    };
    const [dialog, close] = makePopup(true);
    let provisionalName = "New Search";
    const input = document.createElement("TEXTAREA");
    dialog.appendChild(input);
    dialog.appendChild(document.createElement("BR"));
    dialog.appendChild(
      button("📁 From File", "Upload searches as a file.", () =>
        loadFile((name, data) => {
          input.value = data;
          provisionalName = name.replace(/\.search$/, "");
        })
      )
    );
    dialog.appendChild(
      button("Import", "Import search from JSON data.", () => {
        try {
          if (input.value.trim().startsWith("shesmusearch:")) {
            close();
            createSaveDialog(
              JSON.parse(atob(input.value.split(/:/, 2)[1])),
              "Search from Ticket"
            );
            return;
          }
          const imported = JSON.parse(input.value);
          if (Array.isArray(imported)) {
            createSaveDialog(imported, provisionalName);
          } else {
            for (const entry of Object.entries(imported)) {
              localSearches[entry[0]] = entry[1];
            }
            updateLocalSearches("All Actions");
          }
          close();
        } catch (e) {
          makePopup().innerText = e.message;
        }
      })
    );
  });
  document
    .getElementById("deleteSearchButton")
    .addEventListener("click", () => {
      if (localSearches.hasOwnProperty(currentName)) {
        delete localSearches[currentName];
        updateLocalSearches("All Actions");
      } else {
        makePopup().innerText =
          "Search is stored on the Shesmu server and cannot be deleted from this interface.";
      }
    });

  document.getElementById("exportButton").addEventListener("click", () => {
    if (Object.keys(localSearches).length) {
      const [dialog, close] = makePopup(true);
      dialog.appendChild(
        button("⎘ To Clipboard", "Export searches to the clipboard.", () => {
          copyJson(localSearches);
          close();
        })
      );
      dialog.appendChild(
        button("📁 To File", "Download searches as a file.", () => {
          downloadData(
            JSON.stringify(localSearches),
            "application/json",
            "My Searches.json"
          );
          close();
        })
      );
    } else {
      makePopup().innerText = "No saved searches to export.";
    }
  });
  let savedCustomFilter = null;
  try {
    savedCustomFilter = JSON.parse(userFilters);
  } catch (e) {
    console.log(e);
  }

  redrawDropDown(savedQueryName, savedCustomFilter, false);
}

function copyJson(data) {
  copyText(JSON.stringify(data, null, 2));
}
function copyText(data) {
  const closeBusy = makeBusyDialog();
  const buffer = document.createElement("TEXTAREA");
  buffer.value = data;
  buffer.style = "display: inline;";
  document.body.appendChild(buffer);
  buffer.select();
  document.execCommand("Copy");
  buffer.style = "display: none;";
  window.setTimeout(() => {
    closeBusy();
    document.body.removeChild(buffer);
  }, 300);
}

function downloadData(data, mimetype, fileName) {
  const blob = new Blob([data], { type: mimetype });

  const a = document.createElement("a");
  a.download = fileName;
  a.href = URL.createObjectURL(blob);
  a.dataset.downloadurl = ["text/plain", a.download, a.href].join(":");
  a.style.display = "none";
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(a.href), 1500);
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
    button("Save", "Add search to local search collection.", () => {
      const name = input.value.trim();
      if (name) {
        close();
        updateSearchList(
          localSearches => (localSearches[name] = filters),
          name
        );
      }
    })
  );
}

function filterableDialog(items, addItem, render, predicate, breakLines) {
  const [dialog, close] = makePopup(true);
  const list = document.createElement("DIV");
  const showItems = p => {
    clearChildren(list);
    items.filter(p).forEach(item => {
      const [name, title] = render(item);
      list.appendChild(
        button(name, title, e => {
          addItem(item);
          if (!e.ctrlKey) {
            e.stopPropagation();
            close();
          }
        })
      );
      if (breakLines) {
        list.appendChild(document.createElement("BR"));
      }
    });
    if (list.childElementCount == 0) {
      list.innerText = "No matches";
    }
  };
  const search = document.createElement("DIV");
  const searchInput = document.createElement("INPUT");
  searchInput.type = "search";
  search.appendChild(document.createTextNode("Filter: "));
  search.appendChild(searchInput);
  dialog.appendChild(search);

  dialog.appendChild(list);
  const help = document.createElement("P");
  help.innerText = "Control-click to select multiple.";
  dialog.appendChild(help);

  searchInput.addEventListener("input", e => {
    const keywords = searchInput.value
      .trim()
      .toLowerCase()
      .split(/\W+/);
    if (keywords.length) {
      showItems(x => predicate(x, keywords));
    } else {
      showItems(x => true);
    }
  });

  showItems(x => true);
}

let findOverride = null;

function initialise() {
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
  window.addEventListener("keydown", e => {
    if (
      findOverride &&
      (e.keyCode === 114 || (e.ctrlKey && e.keyCode === 70))
    ) {
      e.preventDefault();
      findOverride();
    }
  });
}

function pauseButton(slug, suffix, holder, sourceLocation) {
  const throttleButton = document.createElement("SPAN");
  throttleButton.className = "load danger";
  throttleButton.title =
    "Throttle/unthrottle actions generated. This does not stop the olive from running.";
  const renderPaused = () => {
    holder.pauseSpan.innerText = holder.paused ? " ⏸" : " ▶";
    holder.pauseSpan.title = holder.paused ? "Paused" : "Running";
    throttleButton.innerText = holder.paused
      ? "▶ Resume Actions " + suffix
      : "⏸ Pause Actions " + suffix;
  };
  throttleButton.addEventListener("click", () => {
    fetchJsonWithBusyDialog(
      slug,
      {
        body: JSON.stringify({
          ...sourceLocation,
          pause: !holder.paused
        }),
        method: "POST"
      },
      response => {
        holder.paused = response;
        renderPaused();
      }
    );
  });
  renderPaused();
  return throttleButton;
}

function pauseBadge(holder) {
  const pauseSpan = document.createElement("span");
  holder.pauseSpan = pauseSpan;
  if (holder.hasOwnProperty("paused")) {
    pauseSpan.innerText = holder.paused ? " ⏸" : " ▶";
    pauseSpan.title = holder.paused ? "Paused" : "Running";
  }
  return pauseSpan;
}

export function initialiseOliveDash(
  oliveFiles,
  deadPauses,
  deadFilePauses,
  saved,
  userFilters
) {
  initialise();
  const container = document.getElementById("olives");
  const resultsContainer = document.getElementById("results");
  const prepareFileInfo = (file, infoPane, bytecodePane) => {
    bytecodePane.appendChild(preformatted(file.bytecode));

    const infoTable = document.createElement("TABLE");
    infoPane.appendChild(infoTable);

    const statusRow = document.createElement("TR");
    infoTable.appendChild(statusRow);
    const statusHeader = document.createElement("TD");
    statusHeader.innerText = "Status";
    statusRow.appendChild(statusHeader);
    const statusCell = document.createElement("TD");
    statusCell.innerText = file.status;
    statusRow.appendChild(statusCell);

    const lastRunRow = document.createElement("TR");
    infoTable.appendChild(lastRunRow);
    const lastRunHeader = document.createElement("TD");
    lastRunHeader.innerText = "Last Run";
    lastRunRow.appendChild(lastRunHeader);
    const lastRunCell = document.createElement("TD");
    if (file.lastRun) {
      const [ago, exact] = formatTimeBin(file.lastRun);
      lastRunCell.innerText = ago;
      lastRunCell.title = exact;
    } else {
      lastRunCell.innerText = "Never";
    }
    lastRunRow.appendChild(lastRunCell);

    const runtimeRow = document.createElement("TR");
    infoTable.appendChild(runtimeRow);
    const runtimeHeader = document.createElement("TD");
    runtimeHeader.innerText = "Run Time";
    runtimeRow.appendChild(runtimeHeader);
    const runtimeCell = document.createElement("TD");
    if (file.runtime) {
      runtimeCell.innerText = formatTimeSpan(file.runtime);
    } else {
      runtimeCell.innerText = "Unknown";
    }
    runtimeRow.appendChild(runtimeCell);

    const inputFormatRow = document.createElement("TR");
    infoTable.appendChild(inputFormatRow);
    const inputFormatHeader = document.createElement("TD");
    inputFormatHeader.innerText = "Input Format";
    inputFormatRow.appendChild(inputFormatHeader);
    const inputFormatCell = document.createElement("TD");
    inputFormatRow.appendChild(inputFormatCell);
    const inputFormatLink = document.createElement("A");
    inputFormatLink.innerText = file.format;
    inputFormatLink.href = `inputdefs#${file.format}`;
    inputFormatCell.appendChild(inputFormatLink);

    const sourceHashRow = document.createElement("TR");
    infoTable.appendChild(sourceHashRow);
    const sourceHashHeader = document.createElement("TD");
    sourceHashHeader.innerText = "Source Hash";
    sourceHashRow.appendChild(sourceHashHeader);
    const sourceHashCell = document.createElement("TD");
    sourceHashCell.innerText = file.hash;
    sourceHashRow.appendChild(sourceHashCell);

    return infoTable;
  };
  const activeOlive = document.createElement("SPAN");

  const renderFile = (file, prettyFileName, initialCustomFilter, isPop) => {
    clearChildren(activeOlive);
    activeOlive.innerText = `All Olives in ${prettyFileName}`;
    if (!isPop) {
      window.history.pushState(
        { file: file.filename, prettyFileName: prettyFileName, filters: null },
        file.filename,
        "olivedash?saved=" +
          encodeURIComponent(JSON.stringify({ file: file.filename }))
      );
    }
    if (file.olives.some(olive => olive.produces == "ACTIONS")) {
      getStats(
        [
          {
            type: "sourcefile",
            files: [file.filename]
          }
        ],
        [],
        [],
        resultsContainer,
        false,
        targetElement => {
          const [infoPane, listPane, bytecodePane] = makeTabs(
            targetElement,
            0,
            "Overview",
            "Actions",
            "Bytecode"
          );
          prepareFileInfo(file, infoPane, bytecodePane);

          const statsHeader = document.createElement("H2");
          statsHeader.innerText = "Actions";
          infoPane.appendChild(statsHeader);
          const stats = document.createElement("DIV");
          infoPane.appendChild(stats);

          return [stats, listPane];
        },
        (reset, updateLocalSearches) => {
          if (updateLocalSearches) {
            updateLocalSearches(localSearches);
            localStorage.setItem(
              "shesmu_searches",
              JSON.stringify(localSearches)
            );
          }
        },
        initialCustomFilter,
        filters => {
          if (!isPop || filters != initialCustomFilter) {
            window.history.pushState(
              {
                file: file.filename,
                line: null,
                column: null,
                hash: file.hash,
                filters: filters
              },
              file.filename,
              `olivedash?saved=${encodeURIComponent(
                JSON.stringify({
                  file: file.filename,
                  line: null,
                  column: null,
                  hash: null
                })
              )}&filters=${encodeURIComponent(JSON.stringify(filters))}`
            );
          }
        },
        pauseButton("/pausefile", "in script", file, {
          file: file.filename
        })
      );
    } else {
      findOverride = null;
      clearChildren(resultsContainer);
      const [infoPane, bytecodePane] = makeTabs(
        resultsContainer,
        0,
        "Overview",
        "Bytecode"
      );
      prepareFileInfo(file, infoPane, bytecodePane);
    }
  };
  const renderOlive = (file, olive, initialCustomFilter, isPop) => {
    clearChildren(activeOlive);
    const oliveSyntax = document.createElement("I");
    oliveSyntax.innerText = olive.syntax;
    activeOlive.appendChild(oliveSyntax);
    activeOlive.appendChild(document.createTextNode(" ― " + olive.description));
    const sourceLocation = JSON.stringify({
      file: file.filename,
      line: olive.line,
      column: olive.column,
      hash: file.hash
    });
    const prepareInfo = (infoPane, metroPane, bytecodePane) => {
      const infoTable = prepareFileInfo(file, infoPane, bytecodePane);

      if (olive.url) {
        const sourceRow = document.createElement("TR");
        infoTable.appendChild(sourceRow);
        const sourceHeader = document.createElement("TD");
        sourceHeader.innerText = "Source Code";
        sourceRow.appendChild(sourceHeader);
        const sourceCell = document.createElement("TD");
        sourceRow.appendChild(sourceCell);
        const sourceLink = document.createElement("A");
        sourceLink.innerText = "View";
        sourceLink.href = olive.url;
        sourceCell.appendChild(sourceLink);
      }

      const simulateRow = document.createElement("TR");
      infoTable.appendChild(simulateRow);
      const simulateHeader = document.createElement("TD");
      simulateHeader.innerText = "Simulation";
      simulateRow.appendChild(simulateHeader);
      const simulateCell = document.createElement("TD");
      simulateRow.appendChild(simulateCell);
      const simulateLink = document.createElement("A");
      simulateLink.innerText = "Edit in Olive Simulator";
      simulateLink.href = `simulatedash?script=${encodeURIComponent(
        file.filename
      )}`;
      simulateCell.appendChild(simulateLink);

      olive.tags.forEach(tag => {
        const tagRow = document.createElement("TR");
        infoTable.appendChild(tagRow);
        const tagHeader = document.createElement("TD");
        tagHeader.innerText = "Tag";
        tagRow.appendChild(tagHeader);
        const tagCell = document.createElement("TD");
        tagCell.innerText = tag;
        tagRow.appendChild(tagCell);
      });

      const metroRequest = () => {
        clearChildren(metroPane);
        const metroLock = getDelayLock(container);
        fetch("/metrodiagram", {
          body: JSON.stringify({
            file: file.filename,
            line: olive.line,
            column: olive.column,
            hash: file.hash
          }),
          method: "POST"
        })
          .then(response => {
            if (response.ok) {
              return Promise.resolve(response);
            } else if (response.status == 404) {
              return Promise.reject(new Error("Olive has been replaced"));
            } else if (response.status == 503) {
              metroPane.appendChild(text("Shesmu is overloaded"));
              metroPane.appendChild(
                button("Retry", "Try to draw metro diagram", metroRequest)
              );

              return Promise.reject(null);
            } else {
              return Promise.reject(
                new Error(
                  `Failed to load: ${response.status} ${response.statusText}`
                )
              );
            }
          })
          .then(response => response.text())
          .then(data => {
            if (metroLock()) {
              const svg = new window.DOMParser().parseFromString(
                data,
                "image/svg+xml"
              );
              metroPane.appendChild(document.adoptNode(svg.documentElement));
            }
          })
          .catch(function(error) {
            if (error && metroLock()) {
              const element = document.createElement("SPAN");
              element.innerText = error.message;
              element.className = "error";
              metroPane.appendChild(element);
            }
          });
      };
      metroRequest();
    };

    const updateOliveFilter = filters => {
      if (!isPop || filters != initialCustomFilter) {
        window.history.pushState(
          {
            file: file.filename,
            line: olive.line,
            column: olive.column,
            hash: file.hash,
            filters: filters
          },
          olive.syntax + " ― " + olive.description,
          `olivedash?saved=${encodeURIComponent(
            JSON.stringify({
              file: file.filename,
              line: olive.line,
              column: olive.column,
              hash: file.hash
            })
          )}&filters=${encodeURIComponent(JSON.stringify(filters))}`
        );
      }
    };
    const throttleFileButton = file.olives.some(
      olive => olive.produces == "ACTIONS"
    )
      ? pauseButton("/pausefile", "in script", file, {
          file: file.filename
        })
      : null;

    switch (olive.produces) {
      case "ACTIONS":
        {
          const throttleButton = pauseButton(
            "/pauseolive",
            "from olive",
            olive,
            {
              file: file.filename,
              line: olive.line,
              column: olive.column,
              hash: file.hash
            }
          );
          getStats(
            [
              {
                type: "sourcelocation",
                locations: [
                  {
                    file: file.filename,
                    line: olive.line,
                    column: olive.column,
                    hash: file.hash
                  }
                ]
              }
            ],
            [],
            [],
            resultsContainer,
            false,
            targetElement => {
              const [infoPane, metroPane, listPane, bytecodePane] = makeTabs(
                targetElement,
                0,
                "Overview",
                "Dataflow",
                "Actions",
                "Bytecode"
              );
              prepareInfo(infoPane, metroPane, bytecodePane);

              const statsHeader = document.createElement("H2");
              statsHeader.innerText = "Actions";
              infoPane.appendChild(statsHeader);
              const stats = document.createElement("DIV");
              infoPane.appendChild(stats);

              return [stats, listPane];
            },
            (reset, updateLocalSearches) => {
              if (updateLocalSearches) {
                updateLocalSearches(localSearches);
                localStorage.setItem(
                  "shesmu_searches",
                  JSON.stringify(localSearches)
                );
              }
            },
            initialCustomFilter,
            updateOliveFilter,
            throttleButton,
            throttleFileButton
          );
        }
        break;
      case "ALERTS":
        {
          clearChildren(resultsContainer);
          const [infoPane, alertsPane, metroPane, bytecodePane] = makeTabs(
            resultsContainer,
            0,
            "Overview",
            "Alerts",
            "Dataflow",
            "Bytecode"
          );
          prepareInfo(infoPane, metroPane, bytecodePane);
          results(
            alertsPane,
            "queryalerts",
            JSON.stringify({
              type: "sourcelocation",
              locations: [
                {
                  file: file.filename,
                  line: olive.line,
                  column: olive.column,
                  hash: file.hash
                }
              ]
            }),
            (container, alerts) =>
              showAlertNavigator(
                alerts,
                [],
                container,
                a => link(a.generatorURL, "Permalink"),
                updateOliveFilter,
                throttleFileButton
              )
          );
        }
        break;
      default: {
        findOverride = null;
        clearChildren(resultsContainer);
        const [infoPane, metroPane, bytecodePane] = makeTabs(
          resultsContainer,
          0,
          "Overview",
          "Dataflow",
          "Bytecode"
        );
        prepareInfo(infoPane, metroPane, bytecodePane);
        if (throttleFileButton) {
          infoPane.appendChild(throttleFileButton);
        }
        if (!isPop) {
          window.history.pushState(
            {
              file: file.filename,
              line: olive.line,
              column: olive.column,
              hash: file.hash,
              filters: null
            },
            olive.syntax + " ― " + olive.description,
            `olivedash?saved=${encodeURIComponent(sourceLocation)}&filters=null`
          );
        }
      }
    }
  };
  if (deadPauses.length > 0 || deadFilePauses.length > 0) {
    const title = document.createElement("h1");
    title.innerText = "Active Olives";
    olives.appendChild(title);
  }

  if (oliveFiles.length) {
    const fileNameFormatter = commonPathPrefix(oliveFiles.map(f => f.filename));
    const oliveDropdown = document.createElement("SPAN");
    oliveDropdown.className = "olivemenu";
    container.appendChild(oliveDropdown);
    activeOlive.innerText = "Select";
    oliveDropdown.appendChild(activeOlive);
    oliveDropdown.appendChild(document.createTextNode(" ▼"));
    const olivePanel = document.createElement("DIV");
    oliveDropdown.appendChild(olivePanel);
    const oliveSearch = document.createElement("DIV");
    olivePanel.appendChild(oliveSearch);

    const visibilityUpdates = [];

    oliveSearch.className = "olivesearch";
    const oliveSearchInput = document.createElement("INPUT");
    oliveSearchInput.type = "search";
    oliveSearch.appendChild(document.createTextNode("Filter: "));
    oliveSearch.appendChild(oliveSearchInput);
    oliveSearchInput.addEventListener("input", e => {
      const keywords = oliveSearchInput.value
        .trim()
        .toLowerCase()
        .split(/\W+/);
      if (keywords.length) {
        for (const { texts, elements } of visibilityUpdates) {
          const visible = keywords.every(keyword =>
            texts.some(t => t.indexOf(keyword) != -1)
          );
          for (const element of elements) {
            element.style.display = visible ? null : "none";
          }
        }
      } else {
        for (const { texts, elements } of visibilityUpdates) {
          for (const element of elements) {
            element.style.display = null;
          }
        }
      }
    });

    const oliveList = document.createElement("DIV");
    oliveList.className = "olivelist";
    olivePanel.appendChild(oliveList);
    let open = true;
    oliveDropdown.addEventListener("click", e => {
      if (e.target == activeOlive.parentNode || e.target == activeOlive) {
        if (open) {
          open = false;
          closeActiveMenu(false);
          return;
        }
        closeActiveMenu(true);
        open = true;
        olivePanel.className = "forceOpen";
        activeMenu = activeOlive;
        closeActiveMenu = external => {
          olivePanel.className = external ? "ready" : "";
          open = false;
          activeMenu = null;
        };
      }
    });
    activeOlive.parentNode.onmouseover = e => {
      if (e.target == olivePanel.parentNode && !open) {
        closeActiveMenu(true);
      }
    };
    activeOlive.parentNode.onmouseout = () => {
      if (!open) {
        olivePanel.className = "ready";
      }
    };

    oliveFiles.forEach(file => {
      const title = document.createElement("h2");
      const prettyFileName = fileNameFormatter(file.filename);
      title.innerText = prettyFileName;
      title.title = file.filename;
      oliveList.appendChild(title);
      const elements = [title];
      visibilityUpdates.push({
        texts: file.filename
          .split(/\//)
          .filter(x => x)
          .concat(
            file.olives.flatMap(olive =>
              [olive.syntax, olive.description]
                .flatMap(t => t.trim().split(/\W+/))
                .concat(olive.tags)
            )
          )
          .map(x => x.toLowerCase()),
        elements: elements
      });
      title.appendChild(pauseBadge(file));

      if (file.olives.length) {
        title.style.cursor = "pointer";
        title.addEventListener("click", () =>
          renderFile(file, prettyFileName, null, false)
        );
        const table = document.createElement("table");
        elements.push(table);
        oliveList.appendChild(table);
        const header = document.createElement("tr");
        table.appendChild(header);
        for (const name of ["Syntax", "Description", "Line", ""]) {
          const cell = document.createElement("th");
          cell.innerText = name;
          cell.style.whiteSpace = "nowrap";
          header.appendChild(cell);
        }
        file.olives.forEach(olive => {
          const tr = document.createElement("tr");
          table.appendChild(tr);

          const syntax = document.createElement("td");
          syntax.innerText = olive.syntax;
          tr.appendChild(syntax);

          const description = document.createElement("td");
          description.innerText = olive.description;
          tr.appendChild(description);

          const line = document.createElement("td");
          line.innerText = olive.line;
          tr.appendChild(line);

          const badges = document.createElement("td");
          const [producesIcon, producesDescription] = infoForProduces(
            olive.produces
          );
          badges.innerText = producesIcon;
          badges.title = producesDescription;
          badges.appendChild(pauseBadge(olive));
          tr.appendChild(badges);
          tr.style.cursor = "pointer";
          tr.addEventListener("click", e => {
            clearChildren(activeOlive);
            const oliveSyntax = document.createElement("I");
            oliveSyntax.innerText = olive.syntax;
            activeOlive.appendChild(oliveSyntax);
            activeOlive.appendChild(
              document.createTextNode(" ― " + olive.description)
            );
            renderOlive(file, olive, null, false);
            if (open) {
              closeActiveMenu(false);
            }
          });
          if (
            saved &&
            saved.file == file.filename &&
            saved.line == olive.line &&
            saved.column == olive.column &&
            saved.hash == file.hash
          ) {
            clearChildren(activeOlive);
            const oliveSyntax = document.createElement("I");
            oliveSyntax.innerText = olive.syntax;
            activeOlive.appendChild(oliveSyntax);
            activeOlive.appendChild(
              document.createTextNode(" ― " + olive.description)
            );
            open = false;
            saved = null;
            let initialCustomFilter = null;
            try {
              initialCustomFilter = JSON.parse(userFilters);
            } catch (e) {
              console.log(e);
            }
            renderOlive(file, olive, initialCustomFilter, false);
          }
        });
        if (saved && saved.file == file.filename && !saved.line) {
          open = false;
          saved = null;
          let initialCustomFilter = null;
          try {
            initialCustomFilter = JSON.parse(userFilters);
          } catch (e) {
            console.log(e);
          }
          renderFile(file, prettyFileName, initialCustomFilter, true);
        }
      } else {
        const empty = document.createElement("P");
        empty.innerText = "No olives in this file.";
        elements.push(empty);
        oliveList.appendChild(empty);
      }
    });
    if (open) {
      olivePanel.className = "forceOpen";
      activeMenu = activeOlive;
      closeActiveMenu = external => {
        olivePanel.className = external ? "ready" : "";
        open = false;
        activeMenu = null;
      };
    }
  } else {
    const empty = document.createElement("P");
    empty.innerText = "No olives on this server.";
    container.appendChild(empty);
  }

  if (deadPauses.length > 0 || deadFilePauses.length > 0) {
    const title = document.createElement("h1");
    title.innerText = "Paused Dead Olives";
    title.title =
      "The following olives were paused but the olives have been replaced or deleted. They may still be throttling actions from running. If a new olive is producing the same actions, then the actions will still be throttled! The actions can be viewed, but the information about the olive is gone.";
    olives.appendChild(title);

    const deadTable = document.createElement("TABLE");
    olives.appendChild(deadTable);
    const deadHeader = document.createElement("tr");
    deadTable.appendChild(deadHeader);
    for (const name of ["File", "Line", "Column", "Source Hash"]) {
      const cell = document.createElement("th");
      cell.innerText = name;
      deadHeader.appendChild(cell);
    }
    const deadTableBody = document.createElement("TBODY");
    deadTable.appendChild(deadTableBody);

    let remainingPauses = deadPauses.length + deadFilePauses.length;
    const showPause = (loc, clear) => {
      const tr = document.createElement("tr");
      deadTableBody.appendChild(tr);

      const file = document.createElement("td");
      breakSlashes(loc.file).forEach(x => file.appendChild(x));
      tr.appendChild(file);

      const line = document.createElement("td");
      line.innerText = loc.line || "*";
      tr.appendChild(line);

      const column = document.createElement("td");
      column.innerText = loc.column || "*";
      tr.appendChild(column);

      const sourceHash = document.createElement("td");
      sourceHash.innerText = loc.hash || "*";
      tr.appendChild(sourceHash);

      tr.style.cursor = "pointer";
      tr.addEventListener("click", e => {
        getStats(
          [
            {
              type: "sourcelocation",
              locations: [loc]
            }
          ],
          [],
          [],
          resultsContainer,
          false,
          targetElement => {
            const [infoPane, listPane] = makeTabs(
              targetElement,
              0,
              "Overview",
              "Actions"
            );

            const cleanup = () => {
              deadTableBody.removeChild(tr);
              if (--remainingPauses == 0) {
                olives.removeChild(title);
                olives.removeChild(deadTable);
              }
            };
            infoPane.appendChild(
              dangerButton(
                "▶ Resume Actions",
                "Allow an actions currently paused to resume.",
                e => clear(false, cleanup)
              )
            );
            infoPane.appendChild(
              dangerButton(
                "☠️ PURGE ACTIONS",
                "Remove any actions currently paused.",
                e => clear(true, cleanup)
              )
            );

            const statsHeader = document.createElement("H2");
            statsHeader.innerText = "Actions";
            infoPane.appendChild(statsHeader);
            const stats = document.createElement("DIV");
            infoPane.appendChild(stats);

            return [stats, listPane];
          },
          (reset, updateLocalSearches) => {
            if (updateLocalSearches) {
              updateLocalSearches(localSearches);
              localStorage.setItem(
                "shesmu_searches",
                JSON.stringify(localSearches)
              );
            }
          },
          null,
          filters => {}
        );
      });
    };
    deadFilePauses.forEach(deadFilePause => {
      const sourceLocation = {
        file: deadFilePause,
        line: null,
        column: null,
        hash: null
      };

      showPause(sourceLocation, (purgeFirst, callback) => {
        const removePause = () => {
          fetchJsonWithBusyDialog(
            "/pausefile",
            {
              body: JSON.stringify({ file: deadFilePause, pause: false }),
              method: "POST"
            },
            callback
          );
        };
        if (purgeFirst) {
          fetchJsonWithBusyDialog(
            "/purge",
            {
              body: JSON.stringify([
                {
                  type: "sourcelocation",
                  locations: [sourceLocation]
                }
              ]),
              method: "POST"
            },
            removePause
          );
        } else {
          removePause();
        }
      });
    });
    deadPauses.forEach(deadPause => {
      const sourceLocation = {
        file: deadPause.file,
        line: deadPause.line,
        column: deadPause.column,
        hash: deadPause.hash
      };

      showPause(sourceLocation, (purgeFirst, callback) => {
        const removePause = () => {
          fetchJsonWithBusyDialog(
            "/pauseolive",
            {
              body: JSON.stringify({ ...sourceLocation, pause: false }),
              method: "POST"
            },
            callback
          );
        };
        if (purgeFirst) {
          fetchJsonWithBusyDialog(
            "/purge",
            {
              body: JSON.stringify([
                {
                  type: "sourcelocation",
                  locations: [sourceLocation]
                }
              ]),
              method: "POST"
            },
            removePause
          );
        } else {
          removePause();
        }
      });
    });
  }
  window.addEventListener("popstate", e => {
    if (e.state) {
      oliveFiles.filter(file => file.filename == e.state.file).forEach(file => {
        if (e.state.line) {
          file.olives
            .filter(
              olive =>
                e.state.line == olive.line &&
                e.state.column == olive.column &&
                e.state.hash == file.hash
            )
            .forEach(olive => renderOlive(file, olive, e.state.filters, true));
        } else {
          renderFile(file, e.state.prettyFileName, e.state.filters, true);
        }
      });
    }
  });
}

function results(container, slug, body, render) {
  clearChildren(container);
  addThrobber(container);
  const checkLock = getDelayLock(container);
  fetch(slug, {
    body: body,
    method: "POST"
  })
    .then(response => {
      if (response.ok) {
        return Promise.resolve(response);
      } else if (response.status == 503) {
        if (checkLock()) {
          clearChildren(container);
          container.appendChild(text("Shesmu is overloaded"));
          container.appendChild(
            button("Retry", "Try to get results again", () =>
              results(container, slug, body, render)
            )
          );
        }
        return Promise.reject(null);
      } else {
        return Promise.reject(new Error("Failed to load"));
      }
    })
    .then(response => response.json())
    .then(data => {
      if (checkLock()) {
        clearChildren(container);
        render(container, data);
      }
    })
    .catch(function(error) {
      if (error && checkLock()) {
        clearChildren(container);
        const element = document.createElement("SPAN");
        element.innerText = error.message;
        element.className = "error";
        container.appendChild(element);
      }
    });
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
  modal.addEventListener("click", e => {
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
  inner.addEventListener("click", e => e.stopPropagation());

  return returnClose ? [inner, close] : inner;
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

function nextPage(query, targetElement, onActionPage) {
  results(targetElement, "/query", JSON.stringify(query), (container, data) => {
    const jumble = document.createElement("DIV");
    if (data.results.length == 0) {
      jumble.innerText = "No actions found.";
    }

    data.results.forEach(action => {
      const tile = document.createElement("DIV");
      tile.className = `action state_${action.state.toLowerCase()}${
        action.updateInProgress ? " updating" : ""
      }`;
      const toolbar = document.createElement("P");
      toolbar.appendChild(
        link(
          `actiondash?filters=${encodeURIComponent(
            JSON.stringify({ id: [action.actionId] })
          )}&saved=All%20Actions`,
          action.actionId
        )
      );
      toolbar.appendChild(
        button("⎘ Copy Id", "Copy action identifier to clipboard.", () =>
          copyText(action.actionId)
        )
      );
      toolbar.appendChild(
        dangerButton("☠️ PURGE ACTION", "Remove this action.", () =>
          fetchJsonWithBusyDialog(
            "/purge",
            {
              body: JSON.stringify([
                {
                  type: "id",
                  ids: [action.actionId]
                }
              ]),
              method: "POST"
            },
            count => {
              if (count > 1) {
                const dialog = makePopup();
                dialog.appendChild(
                  document.createTextNode(
                    `Purged ${count} actions!!! This is awkward. The unique action IDs aren't unique!`
                  )
                );
                const image = document.createElement("IMG");
                dialog.appendChild(image);
                image.src = "ohno.gif";
              }
              nextPage(query, targetElement, onActionPage);
            }
          )
        )
      );
      [toolbar, (actionRender.get(action.type) || defaultRenderer)(action)]
        .flat(Number.MAX_VALUE)
        .forEach(element => tile.appendChild(element));
      const json = document.createElement("PRE");
      json.className = "json";
      json.innerText = JSON.stringify(action, null, 2);
      collapse("JSON", json).forEach(x => tile.appendChild(x));

      for (const [commandName, command] of Object.entries(
        action.commands || {}
      )) {
        toolbar.appendChild(
          dangerButton(
            commandName,
            `Perform special command ${command} on this action.`,
            () =>
              fetchJsonWithBusyDialog(
                "/command",
                {
                  body: JSON.stringify({
                    command: command,
                    filters: [
                      {
                        type: "id",
                        ids: [action.actionId]
                      }
                    ]
                  }),
                  method: "POST"
                },
                count => {
                  if (count == 0) {
                    const dialog = makePopup();
                    dialog.appendChild(
                      document.createTextNode(
                        "This action is indifferent to your pleas."
                      )
                    );
                    const image = document.createElement("IMG");
                    dialog.appendChild(image);
                    image.src = "indifferent.gif";
                  } else if (count > 1) {
                    const dialog = makePopup();
                    dialog.appendChild(
                      document.createTextNode(
                        `The command executed on ${count} actions!!! This is awkward. The unique action IDs aren't unique!`
                      )
                    );
                    const image = document.createElement("IMG");
                    dialog.appendChild(image);
                    image.src = "ohno.gif";
                  }
                  nextPage(query, targetElement, onActionPage);
                }
              )
          )
        );
      }
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

function setColorIntensity(element, value, maximum) {
  element.style.backgroundColor = `hsl(191, 95%, ${Math.ceil(
    97 - ((value || 0) / maximum) * 20
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
      dangerButton(
        "🔥 NUKE IT ALL FROM ORBIT 🔥",
        "Purge all actions from Shesmu server.",
        () => {
          purgeActions(filters, targetElement);
        }
      )
    );
    targetElement.appendChild(document.createElement("BR"));
    targetElement.appendChild(
      button("Back away slowly", "Do not purge all actions.", close)
    );
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
      message.appendChild(document.createElement("BR"));
      const image = document.createElement("IMG");
      message.appendChild(image);
      if (data == 0) {
        image.src = "shrek.gif";
      } else if (data < 5) {
        image.src = "holtburn.gif";
      } else if (data < 20) {
        image.src = "vacuum.gif";
      } else if (data < 100) {
        image.src = "car.gif";
      } else if (data < 500) {
        image.src = "flamethrower.gif";
      } else if (data < 1000) {
        image.src = "thorshchariot.gif";
      } else if (data < 5000) {
        image.src = "volcano.gif";
      } else {
        image.src = "starwars.gif";
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
    button("Save", "Update text search filter in current search.", () => {
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
      button("Delete", "Remove text search filer from current search.", () => {
        close();
        callback(x => (x || []).filter(v => v.text != original.text));
      })
    );
  }
}

function editRegex(original, callback) {
  const [dialog, close] = makePopup(true);
  dialog.appendChild(document.createTextNode("Search for regex: "));
  const error = document.createElement("SPAN");
  const input = document.createElement("INPUT");
  input.type = "text";
  input.value = original;
  dialog.appendChild(input);
  dialog.appendChild(error);
  dialog.appendChild(document.createElement("BR"));
  dialog.appendChild(
    button(
      "Save",
      "Update regular expression search in current filter.",
      () => {
        try {
          new RegExp(input.value);
        } catch (e) {
          error.innerText = " " + e.message;
          return;
        }
        close();
        callback(x =>
          (x || [])
            .filter(v => v != original && v != input.value)
            .concat(input.value ? [input.value] : [])
        );
      }
    )
  );
  if (original) {
    dialog.appendChild(
      button(
        "Delete",
        "Remove regular expression search from current filter.",
        () => {
          close();
          callback(x => (x || []).filter(v => v != original));
        }
      )
    );
  }
}

function timeDialog(callback) {
  const [dialog, close] = makePopup(true);
  for (const span of timeSpans) {
    dialog.appendChild(
      button(nameForBin(span), "", () => {
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
    button("Save", "Update time range filter in current search.", () => {
      close();
      callback(x => ({ start: start(), end: end() }));
    })
  );
  if (original.start || original.end) {
    dialog.appendChild(
      button("Delete", "Remove time range filter from current search.", () => {
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
    button("Save", "Update time range filter in current search.", () => {
      close();
      callback(
        x =>
          Number.isNaN(input.valueAsNumber) ? 0 : input.valueAsNumber * units
      );
    })
  );
  if (original) {
    dialog.appendChild(
      button("Delete", "Remove time range filter from current search.", () => {
        close();
        callback(x => 0);
      })
    );
  }
}

function closeButton(title, callback) {
  const close = document.createElement("SPAN");
  close.className = "close";
  close.innerText = "✖";
  close.title = title;
  close.style.cursor = "pointer";
  close.addEventListener("click", e => {
    e.stopPropagation();
    callback();
  });
  return close;
}

function renderFilter(tile, filter, mutateCallback) {
  const deleteButton = (container, typeName, updateFunction) => {
    if (mutateCallback) {
      container.appendChild(
        closeButton("Remove filter.", () =>
          mutateCallback(typeName, updateFunction)
        )
      );
    }
  };
  const editable = (container, typeName, original, editor) => {
    if (mutateCallback) {
      container.addEventListener("click", () =>
        editor(original, update => mutateCallback(typeName, update))
      );
    }
  };
  switch (filter.type) {
    case "added":
    case "checked":
    case "statuschanged":
    case "external":
      {
        const title = document.createElement("DIV");
        title.innerText =
          (filter.negate ? "Not " : "") + nameForBin(filter.type);
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
          (filter.negate ? "Not " : "") +
          nameForBin(filter.type.slice(0, -3)) +
          " Since Present";
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
      title.innerText = (filter.negate ? "Not " : "") + "Regular Expression";
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
        title.innerText =
          (filter.negate ? "Not " : "") +
          (filter.matchCase
            ? "Case-Sensitive Text Search"
            : "Case-Insensitive Text Search");
        tile.appendChild(title);
        deleteButton(title, "text", x => x.filter(v => v.text != filter.text));
        const text = document.createElement("PRE");
        text.innerText = visibleText(filter.text);
        tile.appendChild(text);
        editable(tile, "text", filter, editText);
      }
      break;

    case "id":
      {
        const title = document.createElement("DIV");
        title.innerText = (filter.negate ? "Not " : "") + "Action IDs";
        tile.appendChild(title);
        const headers = [["ID", a => a]];

        if (mutateCallback) {
          headers.push([
            "",
            a => {
              const close = document.createElement("SPAN");
              close.className = "close";
              close.innerText = "✖";
              close.addEventListener("click", e => {
                e.stopPropagation();
                mutateCallback("id", x => x.filter(ai => ai != a));
              });
              return close;
            }
          ]);
        }
        tile.appendChild(table(filter.ids, ...headers));
      }
      break;

    case "sourcefile":
      {
        const title = document.createElement("DIV");
        title.innerText =
          (filter.negate ? "Not from " : "") + "Olive Source File";
        tile.appendChild(title);
        const fileNameFormatter = commonPathPrefix(filter.files);
        const list = document.createElement("DIV");
        list.className = "filterlist";
        tile.appendChild(list);
        filter.files.forEach(file => {
          const fileButton = document.createElement("SPAN");
          fileButton.innerText = fileNameFormatter(file);
          list.appendChild(fileButton);
          deleteButton(fileButton, "sourcefile", removeFromList(file));
        });
      }
      break;

    case "sourcelocation":
      {
        const title = document.createElement("DIV");
        title.innerText = (filter.negate ? "Not from " : "") + "Olive Source";
        tile.appendChild(title);
        const fileNameFormatter = commonPathPrefix(
          filter.locations.map(l => l.file)
        );
        const headers = [
          ["File", l => fileNameFormatter(l.file)],
          ["Line", l => l.line || "*"],
          ["Column", l => l.column || "*"],
          ["Source Hash", l => l.hash || "*"]
        ];

        if (mutateCallback) {
          headers.push([
            "",
            l => {
              const close = document.createElement("SPAN");
              close.className = "close";
              close.innerText = "✖";
              close.addEventListener("click", e => {
                e.stopPropagation();
                mutateCallback("sourcelocation", x =>
                  x.filter(
                    li =>
                      li.file != l.file ||
                      li.line != l.line ||
                      li.column != l.column ||
                      li.hash != l.hash
                  )
                );
              });
              return close;
            }
          ]);
        }
        tile.appendChild(table(filter.locations, ...headers));
      }
      break;

    case "status":
      {
        const title = document.createElement("DIV");
        title.innerText = (filter.negate ? "Not in " : "") + "Action State";
        tile.appendChild(title);
        const list = document.createElement("DIV");
        list.className = "filterlist";
        tile.appendChild(list);
        filter.states.forEach(state => {
          const button = statusButton(state, !!mutateCallback);
          deleteButton(button, "status", removeFromList(state));
          list.appendChild(button);
        });
      }
      break;

    case "tag":
      {
        const title = document.createElement("DIV");
        title.innerText = (filter.negate ? "Without " : "") + "Tag";
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
        title.innerText = (filter.negate ? "Not of " : "") + "Action Type";
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
        title.innerText = filter.negate
          ? filter.type == "and"
            ? "None of"
            : "Not one of"
          : filter.type == "and"
            ? "All of"
            : "Any of";
        tile.appendChild(title);
        const list = document.createElement("DIV");
        list.className = "filters";
        list.style.marginLeft = "1em";
        tile.appendChild(list);
        filter.filters.forEach(child => {
          const childTile = document.createElement("DIV");
          renderFilter(childTile, child);
          list.appendChild(childTile);
        });
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
  if (metaFilter.hasOwnProperty("id") && metaFilter.id.length > 0) {
    filters.push({ type: "id", ids: metaFilter.id });
  }
  if (
    metaFilter.hasOwnProperty("sourcefile") &&
    metaFilter.sourcefile.length > 0
  ) {
    filters.push({ type: "sourcefile", files: metaFilter.sourcefile });
  }

  if (
    metaFilter.hasOwnProperty("sourcelocation") &&
    metaFilter.sourcelocation.length > 0
  ) {
    filters.push({
      type: "sourcelocation",
      locations: metaFilter.sourcelocation
    });
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

const delayLock = new WeakMap();

// Get a function that will check if this request was the last one the user
// issued. This is useful for slow queries where the users can trigger another
// query before the first one completes; if the newly triggered query returns
// first, this will prevent overwriting the output.
function getDelayLock(targetElement) {
  if (delayLock.has(targetElement)) {
    const value = delayLock.get(targetElement) + 1;
    delayLock.set(targetElement, value);
    return () => delayLock.get(targetElement) == value;
  } else {
    delayLock.set(targetElement, 0);
    return () => delayLock.get(targetElement) == 0;
  }
}

function getStats(
  filters,
  tags,
  sources,
  targetElement,
  onActionPage,
  prepareTabs,
  updateSearchList,
  userSuppliedFilter,
  updateURL,
  ...toolbarExtras
) {
  let additionalFilters = [];
  if (userSuppliedFilter) {
    additionalFilters.push(userSuppliedFilter);
  }
  clearChildren(targetElement);
  const toolBar = document.createElement("P");
  toolBar.className = "accessory";
  targetElement.appendChild(toolBar);
  const queryBuilder = document.createElement("DIV");
  queryBuilder.className = "filters";
  targetElement.appendChild(queryBuilder);
  const [statsPane, listPane] = prepareTabs(targetElement);
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
    // Don't show base filters on the olive page since it's always the olive context.
    if (onActionPage && filters.length) {
      const filterList = document.createElement("DIV");
      const filterPane = document.createElement("DIV");
      queryBuilder.appendChild(filterPane);
      collapse("Base Filters", filterList).forEach(x =>
        filterPane.appendChild(x)
      );

      filters.forEach(filter => {
        const filterTile = document.createElement("DIV");
        filterList.appendChild(filterTile);
        renderFilter(filterTile, filter, null);
      });
    }
    updateURL(
      additionalFilters.length == 0
        ? null
        : additionalFilters[additionalFilters.length - 1]
    );
    const customFilters =
      additionalFilters.length == 0
        ? []
        : synthesiseFilters(additionalFilters[additionalFilters.length - 1]);
    customFilters.forEach((filter, index) => {
      const filterTile = document.createElement("DIV");
      queryBuilder.appendChild(filterTile);
      renderFilter(filterTile, filter, mutateFilters);
    });
    if ((onActionPage ? filters.length : 0) + customFilters.length == 0) {
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
  findOverride = () =>
    editText({ text: "", matchCase: false }, update =>
      mutateFilters("text", update)
    );

  toolBar.appendChild(
    button(
      "🔄 Refresh",
      "Update current action counts and stats from server.",
      refresh
    )
  );
  toolBar.appendChild(
    accessoryButton(
      "➕ Add Filter",
      "Add a filter to limit the actions displayed.",
      () => {
        const [dialog, close] = makePopup(true);
        dialog.appendChild(
          button(
            "🕑 Fixed Time Range",
            "Add a filter that restricts between two absolute times.",
            () => {
              close();
              timeDialog(n =>
                editTime({ start: null, end: null }, update =>
                  mutateFilters(n, update)
                )
              );
            }
          )
        );
        dialog.appendChild(
          button(
            "🕑 Time Since Now",
            "Add a filter that restricts using a sliding window.",
            () => {
              close();
              timeDialog(n =>
                editTimeAgo(0, update => mutateFilters(n + "ago", update))
              );
            }
          )
        );
        dialog.appendChild(
          button(
            "👾 Action Identifier",
            "Add a unique action identifier.",
            () => {
              close();
              const [idDialog, closeId] = makePopup(true);
              idDialog.appendChild(
                document.createTextNode("Action Identifiers:")
              );
              idDialog.appendChild(document.createElement("BR"));
              const idText = document.createElement("TEXTAREA");
              idDialog.appendChild(idText);
              idDialog.appendChild(document.createElement("BR"));
              idDialog.appendChild(
                button(
                  "Add All",
                  "Add any action IDs in the text to the filter.",
                  () => {
                    closeId();
                    const ids = Array.from(
                      idText.value.matchAll(/shesmu:([0-9A-Fa-f]{40})/g),
                      m => "shesmu:" + m[1].toUpperCase()
                    );
                    mutateFilters(
                      "id",
                      list =>
                        list
                          ? list
                              .concat(ids)
                              .sort()
                              .filter(
                                (item, index, array) =>
                                  item == 0 || item != array[index - 1]
                              )
                          : ids
                    );
                  }
                )
              );
            }
          )
        );
        dialog.appendChild(
          button(
            "🔠 Text",
            "Add a filter that looks for actions with specific text.",
            () => {
              close();
              editText({ text: "", matchCase: false }, update =>
                mutateFilters("text", update)
              );
            }
          )
        );
        dialog.appendChild(
          button(
            "*️⃣  Regular Expression",
            "Add a filter that looks for actions that match a regular expression.",
            () => {
              close();
              editRegex("", update => mutateFilters("regex", update));
            }
          )
        );
        dialog.appendChild(
          button(
            "🏁 Status",
            "Add a filter that searches for actions in a particular state.",
            () => {
              close();
              const [statusDialog, closeStatus] = makePopup(true);
              const table = document.createElement("TABLE");
              statusDialog.appendChild(table);
              Object.entries(actionStates).forEach(([state, description]) => {
                const row = document.createElement("TR");
                table.appendChild(row);
                const buttonCell = document.createElement("TD");
                row.appendChild(buttonCell);
                const button = statusButton(state, true);
                buttonCell.appendChild(button);
                button.addEventListener("click", e => {
                  addFilters(["status", addToSet(state)]);
                  if (!e.ctrlKey) {
                    e.stopPropagation();
                    closeStatus();
                  }
                });
                const pCell = document.createElement("TD");
                row.appendChild(pCell);
                const p = document.createElement("P");
                p.innerText = description;
                pCell.appendChild(p);
              });

              const help = document.createElement("P");
              help.innerText = "Control-click to select multiple.";
              statusDialog.appendChild(help);
            }
          )
        );
        if (onActionPage) {
          dialog.appendChild(
            button(
              "🎬 Action Type",
              "Add a filter that searches for actions of a particular type.",
              () => {
                close();
                filterableDialog(
                  Array.from(actionRender.keys()).sort(),
                  type => addFilters(["type", addToSet(type)]),
                  type => [type, ""],
                  (type, keywords) =>
                    keywords.every(k => type.toLowerCase().indexOf(k) != -1),
                  false
                );
              }
            )
          );
        }
        if (tags.length) {
          dialog.appendChild(
            button(
              "🏷️ Tags",
              "Add a filter that searches for actions marked with a particular tag by an olive.",
              () => {
                close();
                filterableDialog(
                  tags.sort(),
                  tag => addFilters(["tag", addToSet(tag)]),
                  tag => [tag, ""],
                  (tag, keywords) =>
                    keywords.every(k => tag.toLowerCase().indexOf(k) != -1),
                  false
                );
              }
            )
          );
        }
        if (sources.length) {
          dialog.appendChild(
            button(
              "📍 Source Olive",
              "Add a filter that searches for actions that came from a particular olive (even if that olive has been replaced or deleted).",
              () => {
                close();
                const fileNameFormatter = commonPathPrefix(
                  sources.map(s => s.file)
                );
                filterableDialog(
                  sources
                    .sort(
                      (a, b) =>
                        a.file.localeCompare(b.file) ||
                        a.line - b.line ||
                        a.column - b.column ||
                        a.hash.localeCompare(b.hash)
                    )
                    .flatMap((source, index, array) => {
                      const previous = index == 0 ? null : array[index - 1];
                      const result = [];
                      if (index == 0 || source.file != previous.file) {
                        result.push({
                          file: source.file,
                          line: null,
                          column: null,
                          hash: null
                        });
                      }
                      if (
                        index == 0 ||
                        source.file != previous.file ||
                        source.line != previous.line
                      ) {
                        result.push({
                          file: source.file,
                          line: source.line,
                          column: null,
                          hash: null
                        });
                      }
                      if (
                        index == 0 ||
                        source.file != previous.file ||
                        source.line != previous.line ||
                        source.column != previous.column
                      ) {
                        result.push({
                          file: source.file,
                          line: source.line,
                          column: source.column,
                          hash: null
                        });
                      }
                      result.push(source);
                      return result;
                    }),
                  sourceLocation =>
                    addFilters([
                      "sourcelocation",
                      list => {
                        if (!list) {
                          return [sourceLocation];
                        } else if (
                          list.some(
                            loc =>
                              loc.file == sourceLocation.file &&
                              (!loc.line || loc.line == sourceLocation.line) &&
                              (!loc.column ||
                                loc.column == sourceLocation.column) &&
                              (!loc.hash || loc.hash == sourceLocation.hash)
                          )
                        ) {
                          // If the item we are adding is already a subset of something in the list, discard it.
                          return list;
                        } else {
                          // Discard anything which is a subset of what we have
                          const result = list.filter(
                            loc =>
                              loc.file != sourceLocation.file ||
                              (line && loc.line != sourceLocation.line) ||
                              (column && loc.column != sourceLocation.column) ||
                              (hash && loc.hash != sourceLocation.hash)
                          );
                          result.push(sourceLocation);
                          return result;
                        }
                      }
                    ]),
                  sourceLocation => [
                    fileNameFormatter(sourceLocation.file) +
                      (sourceLocation.line
                        ? ":" +
                          sourceLocation.line +
                          (sourceLocation.column
                            ? ":" +
                              sourceLocation.column +
                              (sourceLocation.hash
                                ? "[" + sourceLocation.hash + "]"
                                : "")
                            : "")
                        : ""),
                    sourceLocation.file
                  ],
                  (sourceLocation, keywords) =>
                    keywords.every(
                      k => sourceLocation.file.toLowerCase().indexOf(k) != -1
                    ),
                  true
                );
              }
            )
          );
        }
      }
    )
  );
  toolBar.appendChild(
    accessoryButton(
      "💾 Add to My Searches",
      "Save this search to the local search collection.",
      () => {
        const customFilters =
          additionalFilters.length == 0
            ? []
            : synthesiseFilters(
                additionalFilters[additionalFilters.length - 1]
              );
        if (customFilters.length > 0) {
          saveSearch(
            filters.concat(customFilters),
            (updateLocalSearches, name) =>
              updateSearchList(false, updateLocalSearches, name)
          );
        } else {
          makePopup().innerText = "No changes to save.";
        }
      }
    )
  );
  toolBar.appendChild(
    accessoryButton(
      "🡇 Export Search",
      "Export this search to a file or the clipboard.",
      () => {
        const customFilters =
          additionalFilters.length == 0
            ? filters
            : filters.concat(
                synthesiseFilters(
                  additionalFilters[additionalFilters.length - 1]
                )
              );
        if (customFilters.length > 0) {
          const [dialog, close] = makePopup(true);
          dialog.appendChild(
            button("⎘ To Clipboard", "Export search to the clipboard.", () => {
              copyJson(customFilters);
              close();
            })
          );
          dialog.appendChild(
            button(
              "⎘ To Clipboard for Ticket",
              "Export search to the clipboard in a way that can be pasted in a text document.",
              () => {
                copyText(
                  "shesmusearch:" +
                    btoa(
                      JSON.stringify(customFilters).replace(
                        /[\u007F-\uFFFF]/g,
                        chr =>
                          "\\u" +
                          ("0000" + chr.charCodeAt(0).toString(16)).substr(-4)
                      )
                    )
                );
                close();
              }
            )
          );
          dialog.appendChild(
            button("📁 To File", "Download search as a file.", () => {
              downloadData(
                JSON.stringify(customFilters),
                "application/json",
                "My Search.search"
              );
              close();
            })
          );
          dialog.appendChild(
            button(
              "🖥 cURL Actions",
              "Convert search to a cURL command to extract actions.",
              () => {
                copyText(
                  `curl -d '${JSON.stringify({
                    filters: customFilters,
                    skip: 0,
                    limit: 100000
                  })}' -X POST ${location.origin}/query`
                );
                close();
              }
            )
          );
          dialog.appendChild(
            button(
              "🖥 Wget Actions",
              "Convert search to a Wget command to extract actions.",
              () => {
                copyText(
                  `wget --post-data '${JSON.stringify({
                    filters: customFilters,
                    skip: 0,
                    limit: 100000
                  })}' ${location.origin}/query`
                );
                close();
              }
            )
          );
          dialog.appendChild(
            button(
              "🖥 cURL Purge",
              "Convert search to a cURL command to purge matching actions.",
              () => {
                copyText(
                  `curl -d '${JSON.stringify(customFilters)}' -X POST ${
                    location.origin
                  }/purge`
                );
                close();
              }
            )
          );
          dialog.appendChild(
            button(
              "🖥 Wget Purge",
              "Convert search to a Wget command to purge matching actions.",
              () => {
                copyText(
                  `wget --post-data '${JSON.stringify(customFilters)}' ${
                    location.origin
                  }/purge`
                );
                close();
              }
            )
          );
        } else {
          makePopup().innerText = "No search to save.";
        }
      }
    )
  );

  toolBar.appendChild(
    accessoryButton(
      "⎌ Undo",
      "Undo the last change made to this search.",
      () => {
        additionalFilters.pop();
        refresh();
      }
    )
  );
  toolBar.appendChild(
    accessoryButton(
      "⌫ Revert to Saved",
      "Remove all additions and revert to the base search.",
      () => {
        additionalFilters = [];
        refresh();
      }
    )
  );
  if (onActionPage) {
    toolBar.appendChild(
      accessoryButton(
        "✖ Clear Search",
        "Remove all search filters and view everything.",
        () => updateSearchList(true, null, null)
      )
    );
  }
  toolBar.appendChild(
    dangerButton(
      "☠️ PURGE",
      "Remove matching actions from the Shesmu server.",
      () =>
        purge(
          filters.concat(
            additionalFilters.length == 0
              ? []
              : synthesiseFilters(
                  additionalFilters[additionalFilters.length - 1]
                )
          ),
          refresh
        )
    )
  );
  for (const extra of toolbarExtras) {
    if (extra) {
      toolBar.appendChild(extra);
    }
  }

  const renderStats = (container, data) => {
    if (data.length == 0) {
      container.innerText = "No statistics are available.";
      return;
    }
    const help = document.createElement("P");
    help.innerText = "Click any cell or table heading to filter results.";
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
              breakSlashes(row.value.toString()).forEach(x =>
                value.appendChild(x)
              );
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
              breakSlashes(col.name).forEach(x => currentHeader.appendChild(x));
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
              breakSlashes(rowKey).forEach(x => currentHeader.appendChild(x));
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
                (2 * fontHeight * Math.cos(headerAngle)) / columnWidth
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
                        (x / (width - labelWidth)) * (boundaryLabels.length - 1)
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

export function loadFile(callback) {
  const input = document.createElement("INPUT");
  input.type = "file";

  input.onchange = e => {
    const reader = new FileReader();
    const name = e.target.files[0].name;
    reader.onload = rev => callback(name, rev.target.result);
    reader.readAsText(e.target.files[0], "UTF-8");
  };

  input.click();
}

export function initialiseSimulationDashboard(
  ace,
  container,
  completeSound,
  scriptName,
  scriptBody
) {
  initialise();
  let fakeActions = {};
  let fileName = scriptName || "unknown.shesmu";
  try {
    fakeActions = JSON.parse(
      localStorage.getItem("shesmu_fake_actions") || "{}"
    );
  } catch (e) {
    console.log(e);
  }
  const script = document.createElement("DIV");
  script.className = "editor";
  const editor = ace.edit(script);
  editor.session.setMode("ace/mode/shesmu");
  editor.session.setOption("useWorker", false);
  editor.session.setTabSize(2);
  editor.session.setUseSoftTabs(true);
  editor.setFontSize("14pt");
  editor.setValue(scriptBody || localStorage.getItem("shesmu_script") || "", 0);
  const extra = document.createElement("DIV");
  const toolBar = document.createElement("P");
  container.appendChild(toolBar);
  const outputContainer = document.createElement("DIV");
  container.appendChild(outputContainer);
  const errorTable = document.createElement("TABLE");
  container.appendChild(errorTable);
  errorTable.className = "errors";
  errorTable.style.display = "none";
  const errorTableHead = document.createElement("THEAD");
  errorTable.appendChild(errorTableHead);
  const errorHeader = document.createElement("TR");
  errorTableHead.appendChild(errorHeader);
  for (const name of ["Line", "Column", "Error"]) {
    const cell = document.createElement("th");
    cell.innerText = name;
    errorHeader.appendChild(cell);
  }
  const errorTableBody = document.createElement("TBODY");
  errorTable.appendChild(errorTableBody);
  const updateAnnotations = response => {
    const annotations = [];
    clearChildren(errorTableBody);
    if (response.hasOwnProperty("errors") && response.errors.length) {
      for (const err of response.errors) {
        const match = err.match(/^(\d+):(\d+): *(.*$)/);
        let rowContents;
        if (match) {
          const line = parseInt(match[1]);
          const column = parseInt(match[2]);
          const errorText = match[3];
          annotations.push({
            row: line - 1,
            column: column - 1,
            text: errorText,
            type: "error"
          });
          rowContents = [line, column, errorText];
        } else {
          rowContents = ["", "", err];
        }
        const errorRow = document.createElement("TR");
        errorTableBody.appendChild(errorRow);
        for (const value of rowContents) {
          const cell = document.createElement("td");
          cell.innerText = value;
          errorRow.appendChild(cell);
        }
      }
    }
    editor.getSession().setAnnotations(annotations);
    errorTable.style.display = errorTableBody.children.length
      ? "table"
      : "none";
  };
  const updateDataLabel = document.createElement("LABEL");
  const updateData = document.createElement("INPUT");
  updateData.type = "checkbox";
  updateData.checked = false;
  updateDataLabel.appendChild(updateData);
  updateDataLabel.appendChild(document.createTextNode("Wait for fresh data"));

  toolBar.appendChild(
    button("🤖 Simulate", "Run olive simulation and fetch results", () => {
      editor.getSession().clearAnnotations();
      fetchJsonWithBusyDialog(
        "/simulate",
        {
          body: JSON.stringify({
            fakeActions: fakeActions,
            dryRun: false,
            readStale: !updateData.checked,
            script: editor.getValue()
          }),
          method: "POST"
        },
        response => {
          const tabs = [];
          if (response.hasOwnProperty("alerts") && response.alerts.length) {
            tabs.push({
              name: "Alerts",
              render: tab =>
                showAlertNavigator(
                  response.alerts,
                  [],
                  tab,
                  a => [],
                  filters => {}
                )
            });
          }
          if (response.hasOwnProperty("actions") && response.actions.length) {
            tabs.push({
              name: "Actions",
              render: tab =>
                response.actions.forEach(a => {
                  const div = document.createElement("DIV");
                  tab.appendChild(div);
                  div.className = "action state_simulated";
                  div.appendChild(text(a.name));
                  div.appendChild(
                    table(
                      Object.entries(a.parameters).sort((a, b) =>
                        a[0].localeCompare(b[0])
                      ),
                      ["Name", x => x[0]],
                      ["Value", x => JSON.stringify(x[1], null, 2)]
                    )
                  );
                })
            });
          }
          if (response.hasOwnProperty("olives") && response.olives.length) {
            for (const olive of response.olives) {
              tabs.push({
                name:
                  infoForProduces(olive.produces)[0] +
                  " " +
                  olive.syntax +
                  " ― " +
                  olive.description,
                render: tab => {
                  tab.appendChild(
                    text(`Runtime: ${formatTimeSpan(olive.duration / 1e6)}`)
                  );
                  tab.appendChild(
                    document.adoptNode(
                      new DOMParser().parseFromString(
                        olive.diagram,
                        "image/svg+xml"
                      ).documentElement
                    )
                  );
                }
              });
            }
          }
          updateAnnotations(response);
          if (
            response.hasOwnProperty("exports") &&
            Object.keys(response.exports).length
          ) {
            tabs.push({
              name: "Exports",
              render: tab => {
                for (const [name, { returns, parameters }] of Object.entries(
                  response.exports
                )) {
                  const header = document.createElement("H2");
                  header.innerText = name;
                  tab.appendChild(header);
                  if (parameters) {
                    tab.appendChild(
                      table(
                        [["Return", returns]].concat(
                          parameters.map((type, index) => [
                            `Parameter ${i + 1}`,
                            type
                          ])
                        ),

                        ["Position", x => x[0]],
                        ["Type", x => x[0]]
                      )
                    );
                  } else {
                    tab.appendChild(text(`Constant ${returns}`));
                  }
                }
              }
            });
          }
          if (response.hasOwnProperty("refills")) {
            for (const [name, entries] of Object.entries(response.refills)) {
              if (entries.length > 0) {
                tabs.push({
                  name: "Refill ― " + name,
                  render: tab => {
                    const table = document.createElement("TABLE");
                    tab.appendChild(table);
                    const columns = Object.keys(entries[0]).sort((a, b) =>
                      a.localeCompare(b)
                    );

                    const header = document.createElement("TR");
                    table.appendChild(header);
                    for (const column of columns) {
                      const td = document.createElement("TD");
                      td.innerText = column;
                      header.appendChild(td);
                    }
                    for (const row of entries) {
                      const tr = document.createElement("TR");
                      table.appendChild(tr);
                      for (const column of columns) {
                        const td = document.createElement("TD");
                        td.innerText = JSON.stringify(row[column], null, 2);
                        tr.appendChild(td);
                      }
                    }
                  }
                });
              }
            }
          }
          if (response.hasOwnProperty("dumpers")) {
            for (const [name, entries] of Object.entries(response.dumpers)) {
              tabs.push({
                name: "Dump ― " + name,
                render: tab => {
                  const table = document.createElement("TABLE");
                  tab.appendChild(table);
                  for (const row of entries) {
                    const tr = document.createElement("TR");
                    table.appendChild(tr);
                    for (const value of row) {
                      const td = document.createElement("TD");
                      const dataDiv = document.createElement("pre");
                      dataDiv.className = "json";
                      dataDiv.innerText = JSON.stringify(value, null, 2);
                      td.appendChild(dataDiv);
                      tr.appendChild(td);
                    }
                  }
                }
              });
            }
          }
          if (
            response.hasOwnProperty("overloadedInputs") &&
            response.overloadedInputs.length
          ) {
            tabs.push({
              name: "Overloaded Inputs",
              render: tab => {
                tab.appendChild(
                  text("The following input formats are unavailable:")
                );
                for (const format of response.overloadedInputs) {
                  tab.appendChild(text(format));
                }
              }
            });
          }
          if (response.hasOwnProperty("metrics") && response.metrics) {
            tabs.push({
              name: "Prometheus Metrics",
              render: tab => tab.appendChild(preformatted(response.metrics))
            });
          }
          if (response.hasOwnProperty("bytecode")) {
            tabs.push({
              name: "Bytecode",
              render: tab => tab.appendChild(preformatted(response.bytecode))
            });
          }
          clearChildren(outputContainer);
          const [scriptPane, extraPane, ...tabPanes] = makeTabs(
            outputContainer,
            tabs.length > 0 ? 2 : 0,
            ...["Script", "Extra Definitions"].concat(
              tabs.map(({ name, render }) => name)
            )
          );
          scriptPane.appendChild(script);
          extraPane.appendChild(extra);
          for (let i = 0; i < tabPanes.length; i++) {
            tabs[i].render(tabPanes[i]);
          }
          if (document.visibilityState == "hidden") {
            completeSound.play();
          }
        }
      );
    })
  );
  toolBar.appendChild(updateDataLabel);
  toolBar.appendChild(
    accessoryButton(
      "🠝 Upload File",
      "Upload a file from your computer to simulate",
      () =>
        loadFile((name, data) => {
          fileName = name;
          editor.setValue(data, 0);
        })
    )
  );
  toolBar.appendChild(
    accessoryButton(
      "🡇 Download File",
      "Save this editor to your computer",
      () => downloadData(editor.getValue(), "text/plain", fileName)
    )
  );
  document.addEventListener(
    "keydown",
    function(e) {
      // Map Ctrl-S or Command-S to download/save
      if (
        (window.navigator.platform.match("Mac") ? e.metaKey : e.ctrlKey) &&
        e.keyCode == 83
      ) {
        e.preventDefault();
        downloadData(editor.getValue(), "text/plain", fileName);
      }
    },
    false
  );
  const savedTheme = localStorage.getItem("shesmu_theme") || "ace/theme/chrome";
  toolBar.appendChild(document.createTextNode(" Theme: "));
  toolBar.appendChild(
    dropDown(
      ([name, theme]) => {
        editor.setTheme(theme);
        localStorage.setItem("shesmu_theme", theme);
      },
      ([name, theme]) => name,
      ([name, theme]) => savedTheme == theme,
      [["Ambiance", "ace/theme/ambiance"], ["Chrome", "ace/theme/chrome"]]
    )
  );

  const [scriptPane, extraPane] = makeTabs(
    outputContainer,
    0,
    "Script",
    "Extra Definitions"
  );
  scriptPane.appendChild(script);
  extraPane.appendChild(extra);
  const fakeActionList = document.createElement("TABLE");
  const storeFakeActions = () =>
    localStorage.setItem("shesmu_fake_actions", JSON.stringify(fakeActions));

  const updateFakeActions = () => {
    clearChildren(fakeActionList);
    for (const [name, declaration] of Object.entries(fakeActions).sort(
      ([aName], [bName]) => aName.localeCompare(bName)
    )) {
      const row = document.createElement("TR");
      fakeActionList.appendChild(row);
      const nameCell = document.createElement("TD");
      nameCell.innerText = name;
      row.appendChild(nameCell);
      const editCell = document.createElement("TD");
      row.appendChild(editCell);
      const copy = document.createElement("SPAN");
      editCell.appendChild(copy);
      copy.innerText = "⎘";
      copy.title = "Copy action to clipboard.";
      copy.style.cursor = "pointer";
      copy.addEventListener("click", e => {
        e.stopPropagation();
        copyJson({
          name: name,
          parameters: Object.entries(declaration).map(
            ([paramName, parameter]) => ({
              name: paramName,
              required: parameter.required,
              type: parameter.type
            })
          )
        });
      });
      editCell.appendChild(document.createTextNode(" "));
      const edit = document.createElement("SPAN");
      editCell.appendChild(edit);
      edit.innerText = "✎";
      edit.title = "Rename action.";
      edit.style.cursor = "pointer";
      edit.addEventListener("click", e => {
        e.stopPropagation();

        const [dialog, close] = makePopup(true);
        dialog.appendChild(document.createTextNode("Rename action to: "));
        const input = document.createElement("INPUT");
        input.type = "text";
        input.value = name;
        dialog.appendChild(input);
        dialog.appendChild(document.createElement("BR"));

        dialog.appendChild(
          button("Rename", "Rename action.", () => {
            const newName = input.value.trim();
            if (newName != name) {
              delete fakeActions[name];
              fakeActions[newName] = declaration;
              storeFakeActions();
              updateFakeActions();
            }
            close();
          })
        );
      });
      editCell.appendChild(document.createTextNode(" "));
      editCell.appendChild(
        closeButton("Delete action.", () => {
          delete fakeActions[name];
          storeFakeActions();
          updateFakeActions();
        })
      );
    }
  };
  const importAction = (name, data) => {
    for (const importReads of specialImports) {
      const result = importReads(data);
      if (result) {
        if (result.errors.length) {
          const errorDialog = makePopup();
          for (const error of result.errors) {
            errorDialog.appendChild(text(error));
          }
        } else if (result.name || name) {
          fakeActions[result.name || name] = result.parameters;
          storeFakeActions();
          updateFakeActions();
        } else {
          const [dialog, close] = makePopup(true);
          dialog.appendChild(document.createTextNode("Save action as: "));
          const input = document.createElement("INPUT");
          input.type = "text";
          dialog.appendChild(input);
          dialog.appendChild(document.createElement("BR"));
          dialog.appendChild(
            button("Add", "Save to fake action collection.", () => {
              const newName = input.value.trim();
              if (newName) {
                fakeActions[newName] = result.parameters;
                storeFakeActions();
                updateFakeActions();
              }
              close();
            })
          );
        }
        return;
      }
    }
    makePopup().innerText = "I have no idea what this is.";
  };
  const extraToolBar = document.createElement("P");
  extraToolBar.className = "accessory";
  extra.appendChild(extraToolBar);
  extraToolBar.appendChild(
    button("➕ Import Action", "Uploads a file containing an action.", () =>
      loadFile((name, data) => importAction(name.split(".")[0], data))
    )
  );
  extraToolBar.appendChild(
    button("➕ Add Action", "Adds an action from a definition.", () => {
      const [dialog, close] = makePopup(true);
      dialog.appendChild(document.createTextNode("Action definition:"));
      const actionJSON = document.createElement("TEXTAREA");
      dialog.appendChild(actionJSON);

      dialog.appendChild(
        button("Add", "Save to fake action collection.", () => {
          importAction(null, actionJSON.value);
          close();
        })
      );
    })
  );
  updateFakeActions();
  extra.appendChild(fakeActionList);
  let checking = false;
  let checkTimeout;

  const updateSyntax = () => {
    if (!checking) {
      checking = true;
      // This does not check for overload because editor is best-effort
      fetch("/simulate", {
        body: JSON.stringify({
          fakeActions: fakeActions,
          dryRun: true,
          readStale: true,
          script: editor.getValue()
        }),
        method: "POST"
      })
        .then(response => response.json())
        .then(updateAnnotations)
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
}
function drawAlert(a, container, makeHeader) {
  container.classList.add(a.live ? "live" : "expired");
  [
    makeHeader(a),
    table(
      Object.entries(a.labels).sort((a, b) => a[0].localeCompare(b[0])),
      ["Label", x => x[0]],
      ["Value", x => x[1].split(/\n/).map(t => text(t))]
    ),
    [["Started", "startsAt"], ["Ended", "endsAt"]].map(([name, property]) => {
      const time = a[property];
      if (time) {
        const [ago, exact] = formatTimeBin(time);
        const timeInfo = document.createElement("P");
        timeInfo.innerText = `${name} ${ago}`;
        timeInfo.title = exact;
        return timeInfo;
      } else {
        return blank();
      }
    }),
    objectTable(a.annotations, "Annotations", x => x)
  ]
    .flat(Number.MAX_VALUE)
    .forEach(element => container.appendChild(element));
}

function showAlertNavigator(
  allAlerts,
  initialFilters,
  output,
  makeHeader,
  pushFilters,
  ...toolbarExtras
) {
  clearChildren(output);
  const showAlertGroup = (container, alerts, usedLabels, addFilter) => {
    clearChildren(container);
    const count = document.createElement("P");
    container.appendChild(count);
    if (alerts.length == 0) {
      count.innerText = "No matching alerts.";
      return;
    }
    count.innerText =
      alerts.length == 1 ? "Found 1 alert." : `Found ${alerts.length} alerts.`;
    const commonLabels = { ...alerts[0].labels };
    usedLabels.forEach(label => delete commonLabels[label]);
    alerts.forEach(a => {
      for (const [label, value] of Object.entries(a.labels)) {
        if (commonLabels[label] != value) {
          delete commonLabels[label];
        }
      }
    });
    const flex = document.createElement("DIV");
    flex.style.display = "flex";
    container.appendChild(flex);
    const table = document.createElement("TABLE");
    flex.appendChild(table);
    if (Object.keys(commonLabels).length) {
      for (const [label, value] of Object.entries(commonLabels)) {
        const row = document.createElement("TR");
        const header = document.createElement("TD");
        header.innerText = label;
        const labelValue = document.createElement("TD");
        labelValue.colSpan = 2;
        labelValue.innerText = value;
        row.appendChild(header);
        row.appendChild(labelValue);
        table.appendChild(row);
      }
    }

    const uselessLabels = Object.keys(commonLabels).concat(usedLabels);
    if (alerts.length > 10) {
      const breakdown = new Map();
      for (const a of alerts) {
        for (const [name, value] of Object.entries(a.labels)) {
          if (!uselessLabels.includes(name)) {
            if (!breakdown.has(name)) {
              breakdown.set(name, { total: 0, values: new Map() });
            }
            const counts = breakdown.get(name);
            counts.total++;
            counts.values.set(value, (counts.values.get(value) || 0) + 1);
          }
        }
      }

      const bestBreakdown = [...breakdown.entries()]
        .sort((a, b) => b[1].total - a[1].total)
        .filter(
          x =>
            x[1].total > 1 &&
            [...x[1].values.values()].some(c => c > 1 && c > x[1].total * 0.1)
        );
      bestBreakdown.length = Math.min(bestBreakdown.length, 10);
      if (bestBreakdown.length) {
        let activeBreakdown = null;
        for (const [label, { total, values }] of bestBreakdown) {
          const row = document.createElement("TR");
          row.style.cursor = "pointer";
          const header = document.createElement("TD");
          row.appendChild(header);
          header.appendChild(document.createTextNode(label));
          const labelCount = document.createElement("TD");
          labelCount.innerText = `${total} (${(
            (total / alerts.length) *
            100
          ).toFixed(2)}%)`;
          row.appendChild(labelCount);
          const more = document.createElement("TD");
          more.innerText = "Details ▶";
          row.appendChild(more);
          table.appendChild(row);
          row.addEventListener("click", e => {
            while (flex.childElementCount > 1) {
              flex.removeChild(flex.lastElementChild);
            }
            if (activeBreakdown == row) {
              row.style.backgroundColor = "inherit";
              activeBreakdown = null;
              table.style.width = null;
              return;
            }
            table.style.width = "auto";
            if (activeBreakdown) {
              activeBreakdown.style.backgroundColor = "inherit";
            }
            row.style.backgroundColor = "#D1EFED";
            activeBreakdown = row;
            const table = document.createElement("TABLE");
            flex.appendChild(table);
            const header = document.createElement("TR");
            table.appendChild(header);
            const labelCell = document.createElement("TH");
            labelCell.colSpan = 2;
            header.appendChild(labelCell);
            labelCell.appendChild(
              button(
                "🏷️ Has Label",
                "Show alerts that have this label with any value.",
                () => addFilter({ label: label, value: null, type: "has" })
              )
            );
            labelCell.appendChild(document.createTextNode(label));
            for (const [value, count] of values) {
              const row = document.createElement("TR");
              const valueCell = document.createElement("TD");
              valueCell.appendChild(
                button("=", "Show alerts that match this value.", () =>
                  addFilter({ label: label, value: value, type: "eq" })
                )
              );
              valueCell.appendChild(
                button("≠", "Hide alerts that match this value.", () =>
                  addFilter({ label: label, value: value, type: "ne" })
                )
              );
              row.appendChild(valueCell);
              valueCell.appendChild(
                document.createTextNode(value ? value : "<blank>")
              );
              const countCell = document.createElement("TD");
              countCell.innerText = `${count} (${(
                (count / total) *
                100
              ).toFixed(2)}%)`;
              row.appendChild(countCell);
              table.appendChild(row);
            }
          });
        }
      }
    }

    const total = document.createElement("P");
    const liveCount = alerts.filter(a => a.live).length;
    if (liveCount == 0) {
      total.innerText = `💤 ${alerts.length} expired alerts`;
    }
    if (liveCount == alerts.length) {
      total.innerText = `🔔 ${alerts.length} firing alerts`;
    } else {
      total.innerText = `${
        alerts.length
      } alerts 🔔 ${liveCount} firing 💤 ${alerts.length - liveCount} expired`;
    }
    container.appendChild(total);

    const pageList = document.createElement("DIV");
    container.appendChild(pageList);
    const numPerPage = 10;
    const numButtons = Math.ceil(alerts.length / numPerPage);
    const drawPager = current => {
      clearChildren(pageList);
      const pager = document.createElement("DIV");
      const alertList = document.createElement("DIV");
      pageList.appendChild(pager);
      pageList.appendChild(alertList);

      let rendering = true;
      for (let i = 0; i < numButtons; i++) {
        if (
          i <= 2 ||
          i >= numButtons - 2 ||
          (i >= current - 2 && i <= current + 2)
        ) {
          rendering = true;
          const page = document.createElement("SPAN");
          const index = i;
          page.innerText = `${index + 1}`;
          if (index != current) {
            page.className = "load accessory";
            page.addEventListener("click", () => drawPager(index));
          }
          pager.appendChild(page);
        } else if (rendering) {
          const ellipsis = document.createElement("SPAN");
          ellipsis.innerText = "...";
          pager.appendChild(ellipsis);
          rendering = false;
        }
      }
      clearChildren(alertList);
      alerts
        .slice(current * numPerPage, (current + 1) * numPerPage)
        .forEach(a => {
          const alertTile = document.createElement("DIV");
          alertTile.className = "alert";
          drawAlert(a, alertTile, makeHeader);
          alertList.appendChild(alertTile);
        });
    };
    drawPager(0);
  };
  let userFilters = initialFilters;
  const filterbar = document.createElement("SPAN");
  const toolbar = document.createElement("P");
  const results = document.createElement("DIV");
  output.appendChild(toolbar);
  output.appendChild(results);
  if (userFilters.length == 0 && allAlerts.some(a => a.live)) {
    userFilters.push({ type: "live", value: true, label: null });
  }
  const renderAlerts = () => {
    clearChildren(filterbar);
    let userFilterdAlerts = allAlerts;
    const uselessLabels = [];
    for (const { label, value, type } of userFilters) {
      const filterTile = document.createElement("SPAN");
      filterbar.appendChild(filterTile);
      const labelSpan = document.createElement("SPAN");
      labelSpan.className = "load";
      switch (type) {
        case "live":
          labelSpan.innerText = value ? "🔔 Firing" : "💤 Expired";
          userFilterdAlerts = userFilterdAlerts.filter(a => a.live == value);
          break;

        case "has":
          labelSpan.innerText = `🏷️ ${label}`;
          userFilterdAlerts = userFilterdAlerts.filter(a =>
            a.labels.hasOwnProperty(label)
          );
          break;
        case "has-regex":
          labelSpan.innerText = `🏷️ ~ ${label}`;
          userFilterdAlerts = userFilterdAlerts.filter(a =>
            Object.keys(a.labels).some(l => label.test(l))
          );
          break;
        case "eq":
          labelSpan.innerText = `${label} = ${value || "<blank>"}`;
          userFilterdAlerts = userFilterdAlerts.filter(
            a => a.labels[label] == value
          );
          uselessLabels.push(label);
          break;

        case "ne":
          labelSpan.innerText = `${label} ≠ ${value || "<blank>"}`;
          userFilterdAlerts = userFilterdAlerts.filter(
            a => a.labels[label] != value
          );
          break;

        case "regex":
          labelSpan.innerText = `${label} ~ ${value}`;
          userFilterdAlerts = userFilterdAlerts.filter(a =>
            value.test(a.labels[label])
          );
          break;
      }
      labelSpan.appendChild(
        closeButton("Remove filter.", () => {
          userFilters = userFilters.filter(
            x => x.label != label && x.value != value && x.type != type
          );
          renderAlerts();
        })
      );
      filterTile.appendChild(labelSpan);
    }
    pushFilters(userFilters);
    showAlertGroup(results, userFilterdAlerts, uselessLabels, f => {
      userFilters.push(f);
      renderAlerts();
    });
  };

  findOverride = () => {
    const dialog = makePopup();
    dialog.appendChild(document.createTextNode("Label: "));
    const label = document.createElement("INPUT");
    label.type = "text";
    dialog.appendChild(label);
    dialog.appendChild(document.createElement("BR"));
    dialog.appendChild(document.createTextNode("Value: "));
    const value = document.createElement("INPUT");
    value.type = "text";
    dialog.appendChild(value);
    dialog.appendChild(document.createElement("BR"));
    dialog.appendChild(
      button("Add", "Add alert filter.", () => {
        if (label.value.trim() && value.value.trim()) {
          close();
          userFilters.push({
            type: "eq",
            value: value.value.trim(),
            label: label.value.trim()
          });
          renderAlerts();
        }
      })
    );
  };
  toolbar.appendChild(
    button(
      "➕ Add Filter",
      "Add a filter to limit the alerts displayed.",
      () => {
        const [dialog, close] = makePopup(true);
        dialog.appendChild(
          button("🔔 Firing", "Currently firing alerts.", () => {
            close();
            userFilters = userFilters.filter(x => x.type != "live");
            userFilters.push({ type: "live", value: true, label: null });
            renderAlerts();
          })
        );
        dialog.appendChild(
          button("💤 Expired", "Not currently firing alerts.", () => {
            close();
            userFilters = userFilters.filter(x => x.type != "live");
            userFilters.push({ type: "live", value: false, label: null });
            renderAlerts();
          })
        );
        for (const { type, name, tooltip, processor } of [
          {
            type: "has",
            name: "🏷️ Has Label",
            tooltip: "Find actions a labels.",
            processor: x => x
          },
          {
            type: "has-regex",
            name: "*️⃣  Label Name Matches Regular Expression",
            tooltip:
              "Find actions with label names that match a regular expression.",
            processor: x => new RegExp(x)
          }
        ]) {
          dialog.appendChild(
            button(name, tooltip, () => {
              clearChildren(dialog);
              dialog.appendChild(document.createTextNode("Label: "));
              const label = document.createElement("INPUT");
              label.type = "text";
              dialog.appendChild(label);
              dialog.appendChild(document.createElement("BR"));
              dialog.appendChild(
                button("Add", "Add alert filter.", () => {
                  if (label.value.trim() && value.value.trim()) {
                    close();
                    userFilters.push({
                      type: type,
                      label: processor(value.value.trim()),
                      value: null
                    });
                    renderAlerts();
                  }
                })
              );
            })
          );
        }
        for (const { type, name, tooltip, processor } of [
          {
            type: "eq",
            name: "= Value Matches Text",
            tooltip: "Find actions with labels that match a particular value.",
            processor: x => x
          },
          {
            type: "ne",
            name: "≠ Value Does Not Match Text",
            tooltip:
              "Find actions with labels that do not match a particular value.",
            processor: x => x
          },
          {
            type: "regex",
            name: "*️⃣  Value Matches Regular Expression",
            tooltip:
              "Find actions with a label value that match a regular expression.",
            processor: x => new RegExp(x)
          }
        ]) {
          dialog.appendChild(
            button(name, tooltip, () => {
              clearChildren(dialog);
              dialog.appendChild(document.createTextNode("Label: "));
              const label = document.createElement("INPUT");
              label.type = "text";
              dialog.appendChild(label);
              dialog.appendChild(document.createElement("BR"));
              dialog.appendChild(document.createTextNode("Value: "));
              const value = document.createElement("INPUT");
              value.type = "text";
              dialog.appendChild(value);
              dialog.appendChild(document.createElement("BR"));
              dialog.appendChild(
                button("Add", "Add alert filter.", () => {
                  if (label.value.trim() && value.value.trim()) {
                    close();
                    userFilters.push({
                      type: type,
                      value: processor(value.value.trim()),
                      label: label.value.trim()
                    });
                    renderAlerts();
                  }
                })
              );
            })
          );
        }
      }
    )
  );
  for (const extra of toolbarExtras) {
    if (extra) {
      toolbar.appendChild(extra);
    }
  }
  toolbar.appendChild(filterbar);

  renderAlerts();
  return newFilters => {
    userFilters = newFilters;
    renderAlerts();
  };
}
export function initialiseAlertDashboard(initialFilterString, output) {
  initialise();
  const makeHeader = a => link(a.generatorURL, "Permalink");

  if (location.hash) {
    fetchJsonWithBusyDialog(
      "/getalert",
      { body: JSON.stringify(location.hash.substring(1)), method: "POST" },
      selectedAlert => {
        if (selectedAlert) {
          output.className = "alert";
          drawAlert(selectedAlert, output, makeHeader);
        } else {
          output.innerText = "Unknown alert.";
        }
      }
    );
  } else {
    fetchJsonWithBusyDialog("/allalerts", { method: "GET" }, alerts => {
      if (alerts.length) {
        let userFilters = [];
        try {
          for (const { label, value, type } of JSON.parse(
            initialFilterString
          )) {
            switch (type) {
              case "live":
              case "has":
              case "eq":
              case "ne":
                userFilters.push({ label: label, value: value, type: type });
                break;

              case "has-regex":
                userFilters.push({
                  label: new RegExp(label.substring(1, label.length - 1)),
                  value: value,
                  type: type
                });
                break;
              case "regex":
                userFilters.push({
                  label: label,
                  value: new RegExp(value.substring(1, value.length - 1)),
                  type: type
                });
                break;
            }
          }
        } catch (e) {
          console.log(e);
        }
        const updateFilters = showAlertNavigator(
          alerts,
          userFilters,
          output,
          makeHeader,
          userFilters =>
            window.history.pushState(
              userFilters,
              "",
              `alerts?filters=${encodeURIComponent(
                JSON.stringify(userFilters)
              )}`
            )
        );
        window.addEventListener("popstate", e => {
          if (e.state) {
            updateFilters(e.state);
          }
        });
      } else {
        output.innerText = "No alerts produced by this server.";
      }
    });
  }
}

// The filters contain regular expressions, so add a method to serialise them
Object.defineProperty(RegExp.prototype, "toJSON", {
  value: RegExp.prototype.toString
});

// Preload throbber image
new Image().src = "press.svg";
