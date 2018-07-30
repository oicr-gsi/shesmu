function fetchConstant(name, element) {
  element.className = "busy";
  element.innerText = "Fetching...";
  fetch("/constant", {
    body: JSON.stringify(name),
    method: "POST"
  })
    .then(response => {
      element.innerText = "🔄 Refresh";
      element.className = "load";
      if (response.ok) {
        return Promise.resolve(response);
      } else {
        return Promise.reject(new Error("Failed to load"));
      }
    })
    .then(response => response.json())
    .then(data => {
      if (data.hasOwnProperty("value")) {
        element.nextElementSibling.innerText = data.value;
        element.nextElementSibling.className = "data";
      } else {
        element.nextElementSibling.innerText = data.error;
        element.nextElementSibling.className = "error";
      }
    })
    .catch(function(error) {
      element.nextElementSibling.innerText = error.message;
      element.nextElementSibling.className = "error";
    });
}

function runFunction(name, element, parameterParser) {
  element.className = "busy";
  element.innerText = "Running...";
  let parameters = [];
  let paramsOk = true;
  for (let parameter = 0; parameter < parameterParser.length; parameter++) {
    paramsOk &= parser.parse(
      document.getElementById(`${name}$${parameter}`).value,
      parameterParser[parameter],
      x => parameters.push(x),
      message => {
        element.nextElementSibling.innerText = `Argument ${parameter}: ${message} (retry)`;
        element.nextElementSibling.className = "error";
      }
    );
  }
  if (!paramsOk) {
    return;
  }
  fetch("/function", {
    body: JSON.stringify({ name: name, args: parameters }),
    method: "POST"
  })
    .then(response => {
      element.innerText = "▶ Run";
      element.className = "load";
      if (response.ok) {
        return Promise.resolve(response);
      } else {
        return Promise.reject(new Error("Failed to load"));
      }
    })
    .then(response => response.json())
    .then(data => {
      if (data.hasOwnProperty("value")) {
        element.nextElementSibling.innerText = data.value;
        element.nextElementSibling.className = "data";
      } else {
        element.innerText = data.error;
        element.className = "error";
      }
    })
    .catch(function(error) {
      element.innerText = error.message;
      element.className = "error";
    });
}

function prettyType() {
  const element = document.getElementById("prettyType");
  element.className = "busy";
  element.innerText = "Prettying...";
  fetch("/type", {
    body: JSON.stringify(document.getElementById("uglySignature").value),
    method: "POST"
  })
    .then(response => {
      if (response.ok) {
        return Promise.resolve(response);
      } else if (response.code === 400) {
        return Promise.reject(new Error("Invalid type signature."));
      } else {
        return Promise.reject(new Error("Failed to load"));
      }
    })
    .then(response => response.json())
    .then(data => {
      element.innerText = data;
      element.className = "data";
    })
    .catch(function(error) {
      element.innerText = error.message;
      element.className = "error";
    });
}

parser = {
  _: function(input) {
    return { good: false, input: input, error: "Cannot parse bad type." };
  },
  a: function(innerType) {
    return input => {
      const output = [];
      while (true) {
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
    match = input.match(/^\s*Date\s+(\d{4})-(\d{2})-(\d{2})/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: new Date(
          parseInt(match[1]),
          parseInt(match[2]) - 1,
          parseInt(match[3])
        ).getTime()
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
const types = [];

function clearActionStates() {
  actionStates.forEach(s => {
    document.getElementById(`include_${s}`).checked = false;
  });
}

function drawTypes() {
  const container = document.getElementById("types");
  while (container.hasChildNodes()) {
    container.removeChild(container.lastChild);
  }

  types.sort();
  types.forEach(typeName => {
    const element = document.createElement("SPAN");
    element.innerText = typeName + " ";
    const removeElement = document.createElement("SPAN");
    removeElement.innerText = "✖";
    removeElement.onclick = function() {
      removeTypeName(typeName);
    };

    element.appendChild(removeElement);
    container.appendChild(element);
  });
}

function removeTypeName(typeName) {
  const index = types.indexOf(typeName);
  if (index > -1) {
    types.splice(index, 1);
    drawTypes();
    const option = document.createElement("OPTION");
    option.text = typeName;
    document.getElementById("newType").add(option);
  }
}

function clearTypes() {
  while (types.length) {
    types.pop();
  }
  drawTypes();
  fillNewTypeSelect();
}

function addType(typeName) {
  if (typeName && !types.includes(typeName)) {
    types.push(typeName);
    drawTypes();
  }
}

function addTypeForm() {
  const element = document.getElementById("newType");
  addType(element.value.trim());
  element.remove(element.selectedIndex);
}

function fillNewTypeSelect() {
  const element = document.getElementById("newType");
  while (element.length > 0) {
    element.remove(0);
  }
  for (const typeName of actionRender.keys()) {
    const option = document.createElement("OPTION");
    option.text = typeName;
    element.add(option);
  }
}

function makeFilters() {
  const filters = [];
  const selectedStates = actionStates.filter(
    s => document.getElementById(`include_${s}`).checked
  );
  if (selectedStates.length) {
    filters.push({ type: "status", states: selectedStates });
  }
  const epochElement = document.getElementById("epoch");
  const epochInput = epochElement.value.trim();
  epochElement.className = null;
  if (epochInput.match(/^[0-9]+$/)) {
    filters.push({ type: "after", epoch: parseInt(epochInput) });
  } else if (epochInput.length > 0) {
    epochElement.className = "error";
    return;
  }
  if (types.length > 0) {
    filters.push({ type: "type", types: types });
  }
  return filters;
}

function results(container, slug, body, render) {
  while (container.hasChildNodes()) {
    container.removeChild(container.lastChild);
  }
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
    .then(data => render(container, data))
    .catch(function(error) {
      const element = document.createElement("SPAN");
      element.innerText = error.message;
      element.className = "error";
      container.appendChild(element);
    });
}

function queryActions() {
  const query = {
    filters: makeFilters(),
    limit: 25,
    skip: 0
  };
  nextPage(query, document.getElementById("results"));
}

function text(t) {
  const element = document.createElement("P");
  if (t.length > 100) {
    let visible = true;
    element.title = "There's a lot to unpack here.";
    element.onclick = function() {
      visible = !visible;
      element.innerText = visible ? t : t.substring(0, 98) + "...";
    };
    element.onclick();
  } else {
    element.innerText = t;
  }
  return element;
}

function link(url, t) {
  const element = document.createElement("A");
  element.innerText = t + " 🔗";
  element.target = "_blank";
  element.href = url;
  return element;
}

function jsonParameters(action) {
  return Object.entries(action.parameters).map(p =>
    text(`Parameter ${p[0]} = ${JSON.stringify(p[1])}`)
  );
}

function title(action, t) {
  const element = action.url ? link(action.url, t) : text(t);
  element.title = action.state;
  return element;
}

function defaultRenderer(action) {
  return [title(action, `Unknown Action: ${action.type}`)];
}

function nextPage(query, targetElement) {
  results(
    targetElement,
    "/query",
    JSON.stringify(query),
    (container, data) => {
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
        const updateDate = new Date(action.lastUpdated * 1000).toString();
        const addedDate = new Date(action.lastAdded * 1000).toString();
        tile.appendChild(
          text(`Last Updated: ${updateDate} (${action.lastUpdated})`)
        );
        tile.appendChild(
          text(`Last Added: ${addedDate} (${action.lastAdded})`)
        );
        const showHide = document.createElement("P");
        const json = document.createElement("PRE");
        json.innerText = JSON.stringify(action, null, 2);
        tile.appendChild(showHide);
        tile.appendChild(json);
        let visible = true;
        showHide.onclick = () => {
          visible = !visible;
          showHide.innerText = visible ? "⊟ JSON" : "⊞ JSON";
          json.style = visible ? "display: block" : "display: none";
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
              page.className = "load";
            }
            page.onclick = () =>
              nextPage(
                {
                  filters: query.filters,
                  skip: skip,
                  limit: query.limit
                },
                targetElement
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
    }
  );
}
