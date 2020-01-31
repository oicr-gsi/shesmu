const automaticIniParams = new Set([
  "metadata",
  "output_dir",
  "parent-accessions",
  "parent_accession",
  "parent_accessions",
  "workflow-run-accession",
  "workflow_bundle_dir",
  "workflow_run_accession"
]);

const maybeJsonVisibleText = input => {
  try {
    return preformatted(JSON.stringify(JSON.parse(input), null, 2));
  } catch (e) {
    return visibleText(input);
  }
};

actionRender.set("niassa", a => [
  title(a, `Workflow ${a.workflowAccession}`),
  a.workflowRunAccession
    ? text(`Workflow Run Accession: ${a.workflowRunAccession}`)
    : blank(),
  a.workingDirectory
    ? text(`Working Directory: ${a.workingDirectory}`)
    : blank(),
  text(`Major Olive Version: ${a.majorOliveVersion}`),
  objectTable(a.annotations, "Annotations", x => x),
  objectTable(a.ini, "INI from Olive", maybeJsonVisibleText),
  objectTable(
    a.discoveredIni || {},
    `INI from ${a.workflowRunAccession}`,
    maybeJsonVisibleText
  ),
  a.discoveredIni && Object.entries(a.discoveredIni).length > 0
    ? collapse(
        "Differences in INI",
        table(
          Object.keys(a.discoveredIni)
            .concat(Object.keys(a.ini))
            .sort()
            .filter(
              (item, index, array) =>
                !automaticIniParams.has(item) &&
                (item == 0 || item != array[index - 1])
            ),
          ["Key", k => k],
          [
            "Values",
            k => {
              const isDiscovered = a.discoveredIni.hasOwnProperty(k);
              const isOlive = a.ini.hasOwnProperty(k);
              if (isDiscovered && !isOlive) {
                return "Extra";
              } else if (!isDiscovered && isOlive) {
                return "Missing";
              } else if (a.ini[k] !== a.discoveredIni[k]) {
                return "Different";
              }
              return "";
            }
          ]
        )
      )
    : blank(),
  collapse(
    "LIMS Keys from Olive",
    table(
      a.limsKeys,
      ["Provider", k => k.provider],
      ["ID", k => k.id],
      ["Version", k => k.version],
      ["Last Modified", k => k.lastModified]
    )
  ),
  collapse(
    "Signatures from Olive",
    table(
      a.signatures,
      ["Provider", k => k.provider],
      ["ID", k => k.id],
      ["Version", k => k.version],
      ["Last Modified", k => k.lastModified],
      ["Signature SHA1", k => k.signature]
    )
  ),
  collapse(
    "Prior Workflow Runs",
    table(
      a.matches,
      ["Workflow Run", m => m.workflowRunAccession],
      ["Status", m => m.state],
      ["Stale", m => (m.stale ? "🍞 Stale" : "🍅 Fresh")],
      [
        "LIMS Keys",
        m => {
          if (m.missingLimsKeys && m.extraLimsKeys) {
            return "⬍ Messy Overlap";
          }
          if (m.extraLimsKeys) {
            return "⬆️ Extra";
          }
          if (m.missingLimsKeys) {
            return "⬇️ Missing";
          }
          return "✓ Same";
        }
      ],
      ["Input Files", m => (m.fileSubset ? "📂️️ Missing" : "✓ Same")]
    )
  )
]);

actionRender.set("niassa-annotation", a => [
  title(a, `Annotate ${a.name} ${a.accession}`),
  text(`Key: ${a.key}`),
  text(`Value: ${a.value}`)
]);

function processIniType(name, type, toplevel) {
  if (type === "boolean") {
    return "b";
  } else if (type === "integer") {
    return "i";
  } else if (type === "float") {
    return "f";
  } else if (type === "path") {
    return "p";
  } else if (type === "string") {
    return "s";
  } else if (type === "json") {
    return "j";
  } else if (typeof type === "object" && type && type.hasOwnProperty("is")) {
    switch (type.is) {
      case "date":
        return "d";
      case "list":
        return "a" + processIniType(name, type.of, false);
      case "tuple":
        const elements = type.of.map(element =>
          processIniType(name, element, false)
        );
        return "a" + elements.length + elements.join("");
      case "wdl":
        if (toplevel) {
          return type.parameters;
        } else {
          makePopup().innerText = `In ${name}, WDL type is nested. WDL type block may only appear as a top-level type.`;
          return "!";
        }
    }
  }
  makePopup().innerText = `Utterly incomprehensible type ${JSON.stringify(
    type
  )} for parameter ${name}.`;
  return "!";
}

specialImports.push(data => {
  try {
    const json = JSON.parse(data);
    if (typeof json == "object" && json && json.hasOwnProperty("accession")) {
      const errors = [];
      const output = { major_olive_version: { required: true, type: "i" } };
      for (const parameter of json.parameters || []) {
        if (
          parameter.hasOwnProperty("required") &&
          parameter.hasOwnProperty("iniName") &&
          parameter.hasOwnProperty("name") &&
          parameter.hasOwnProperty("type")
        ) {
          output[parameter.name] = {
            required: parameter.required || false,
            type: processIniType(parameter.name, parameter.type, true)
          };
        } else {
          errors.push(`Malformed parameter: ${JSON.stringify(parameter)}`);
        }
      }
      for (const userAnnotation of json.userAnnotations || []) {
        output[userAnnotation] = { required: true, type: "s" };
      }
      if (json.hasOwnProperty("type")) {
        const limsKeyType = niassaLimsKeyTypes[json.type];
        if (!limsKeyType) {
          errors.push(`Invalid type ${json.type} found in file.`);
        } else {
          output[limsKeyType[0]] = { required: true, type: limsKeyType[1] };
        }
        return { name: null, errors: errors, parameters: output };
      } else {
        return null;
      }
    } else {
      return { errors: ["No workflow type is specified."] };
    }
  } catch (e) {
    // This is not JSON, so it can't be ours
    return null;
  }
});
