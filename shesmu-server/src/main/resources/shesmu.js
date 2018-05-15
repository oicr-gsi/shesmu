function fetchConstant(name, element) {
  element.className = "busy";
  element.innerText = "Fetching...";
  fetch("/constant", {
    body: JSON.stringify(name),
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
      if (data.hasOwnProperty("value")) {
        element.innerText = data.value;
        element.className = "data";
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

function runFunction(name, element, parameterParser) {
  element.className = "busy";
  element.innerText = "Running...";
  let parameters = [];
  for (let parameter = 0; parameter < parameterParser.length; parameter++) {
    try {
      parameters.push(
        parameterParser[parameter](
          document.getElementById(`${name}$${parameter}`).value
        )
      );
    } catch (e) {
      element.innerText = `Argument ${parameter}: ${e.message} (retry)`;
      element.className = "error";
      return;
    }
  }
  fetch("/function", {
    body: JSON.stringify({ name: name, args: parameters }),
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
      if (data.hasOwnProperty("value")) {
        element.innerText = data.value + " (refresh)";
        element.className = "data";
      } else {
        element.innerText = data.error + " (retry)";
        element.className = "error";
      }
    })
    .catch(function(error) {
      element.innerText = error.message + " (retry)";
      element.className = "error";
    });
}

function parseTuple(innerTypes) {
  return JSON.parse; // TODO do a better job
}
function parseList(innerType) {
  return JSON.parse; // TODO do a better job
}
