export function blank() {
  return [];
}

export function collapse(title, ...inner) {
  const items = inner.flat(Number.MAX_VALUE);
  if (items.length == 0) return [];
  const showHide = document.createElement("P");
  showHide.className = "collapse close";
  showHide.innerText = title;
  showHide.onclick = e => toggleCollapse(showHide);
  const contents = document.createElement("DIV");
  items.forEach(item => contents.appendChild(item));
  return [showHide, contents];
}

export function jsonParameters(action) {
  return objectTable(action.parameters, "Parameters", x =>
    JSON.stringify(x, null, 2)
  );
}

export function link(url, t) {
  const element = document.createElement("A");
  element.innerText = t + " 🔗";
  element.target = "_blank";
  element.href = url;
  return element;
}

export function objectTable(object, title, valueFormatter) {
  return collapse(
    title,
    table(
      Object.entries(object).sort((a, b) => a[0].localeCompare(b[0])),
      ["Name", x => x[0]],
      ["Value", x => valueFormatter(x[1])]
    )
  );
}
export function table(rows, ...headers) {
  if (rows.length == 0) return [];
  const table = document.createElement("TABLE");
  const headerRow = document.createElement("TR");
  table.appendChild(headerRow);
  for (const [name, func] of headers) {
    const column = document.createElement("TH");
    column.innerText = name;
    headerRow.appendChild(column);
  }
  for (const row of rows) {
    const dataRow = document.createElement("TR");
    for (const [name, func] of headers) {
      const cell = document.createElement("TD");
      const result = func(row);
      if (Array.isArray(result)) {
        result.flat(Number.MAX_VALUE).forEach(x => cell.appendChild(x));
      } else if (result instanceof HTMLElement) {
        cell.appendChild(result);
      } else {
        cell.innerText = result;
      }
      dataRow.appendChild(cell);
    }
    table.appendChild(dataRow);
  }
  return table;
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

export function timespan(title, time) {
  if (!time) return [];
  const [ago, absolute] = formatTimeBin(time);
  return text(`${title}: ${absolute} (${ago})`);
}

export function title(action, t) {
  const element = action.url ? link(action.url, t) : text(t);
  element.title = action.state;
  let tags;
  if (action.tags.length > 0) {
    tags = document.createElement("DIV");
    tags.className = "filterlist";
    tags.innerText = "Tags: ";
    action.tags.forEach(tag => {
      const button = document.createElement("SPAN");
      button.innerText = tag;
      list.appendChild(button);
    });
  } else {
    tags = [];
  }
  return [
    element,
    table(action.locations, ...sourceColumns),
    table(
      [
        ["Last Checked", "lastChecked"],
        ["Last Added", "lastAdded"],
        ["Last Status Change", "lastStatusChange"],
        ["External Last Modification", "external"]
      ],
      ["Event", x => x[0]],
      [
        "Time",
        x => {
          const time = action[x[1]];
          if (!time) return "Unknown";
          const [ago, absolute] = formatTimeBin(time);
          return `${absolute} (${ago})`;
        }
      ]
    ),
    tags
  ];
}

export function visibleText(text) {
  return text.replace(" ", "␣").replace("\n", "⏎");
}
