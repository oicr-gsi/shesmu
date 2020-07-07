const automaticIniParams = new Set([
  "metadata",
  "output_dir",
  "parent-accessions",
  "parent_accession",
  "parent_accessions",
  "workflow-run-accession",
  "workflow_bundle_dir",
  "workflow_run_accession",
]);

const maybeJsonVisibleText = (input) => {
  try {
    return preformatted(JSON.stringify(JSON.parse(input), null, 2));
  } catch (e) {
    return revealWhitespace(input);
  }
};

actionRender.set("niassa", (a) => [
  title(a, `Workflow ${a.workflowName} (${a.workflowAccession})`),
  table(
    [
      ["Major Olive Version", a.majorOliveVersion.toString()],
      a.workflowRunAccession
        ? ["Workflow Run Accession", a.workflowRunAccession.toString()]
        : null,
      a.workingDirectory
        ? ["Working Directory", breakSlashes(a.workingDirectory)]
        : null,
      a.cromwellId
        ? [
            "Cromwell ID",
            a.cromwellUrl
              ? link(
                  `${a.cromwellUrl}/api/workflows/v1/${a.cromwellId}/metadata`,
                  a.cromwellId
                )
              : a.cromwellId,
          ]
        : null,
      a.cromwellRoot
        ? ["Cromwell Workflow Directory", breakSlashes(a.cromwellRoot)]
        : null,
    ].filter((x) => x),
    ["Workflow Information", (x) => x[0]],
    ["Value", (x) => x[1]]
  ),
  objectTable(a.annotations, "Annotations", (x) => x),
  objectTable(a.supplementalAnnotations, "Supplemental Annotations", (x) => x),
  objectTable(a.ini, "INI from Olive", maybeJsonVisibleText),
  objectTable(
    a.discoveredIni || {},
    `INI from ${a.workflowRunAccession}`,
    maybeJsonVisibleText
  ),
  a.discoveredIni && Object.entries(a.discoveredIni).length > 0
    ? collapsible(
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
          ["Key", (k) => k],
          [
            "Values",
            (k) => {
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
            },
          ]
        )
      )
    : blank(),
  collapsible(
    "LIMS Keys from Olive",
    table(
      a.limsKeys,
      ["Provider", (k) => k.provider],
      ["ID", (k) => k.id],
      ["Version", (k) => k.version],
      ["Last Modified", (k) => k.lastModified]
    )
  ),
  collapsible(
    "Input File SWIDs from Olive",
    table(a.inputFiles, ["File SWID", (k) => k.toString()])
  ),
  collapsible(
    "Signatures from Olive",
    table(
      a.signatures,
      ["Provider", (k) => k.provider],
      ["ID", (k) => k.id],
      ["Version", (k) => k.version],
      ["Last Modified", (k) => k.lastModified],
      ["Signature SHA1", (k) => k.signature]
    )
  ),
  collapsible(
    "Output Files",
    table(
      a.files || [],
      ["Accession", (f) => f.accession.toString()],
      ["Path", (f) => f.path],
      ["Metatype", (f) => f.metatype]
    )
  ),
  collapsible(
    `Logs from Cromwell Job ${a.cromwellId}`,
    table(
      a.cromwellLogs || [],
      ["Task", (x) => x.task],
      ["Attempt", (x) => x.attempt.toString()],
      ["Scatter", (x) => (x.shardIndex < 0 ? "N/A" : x.shardIndex.toString())],
      ["Backend", (x) => x.backend || "Unknown"],
      ["Job ID", (x) => x.jobId || "Unknown"],
      ["Standard Error", (x) => (x.stderr ? breakSlashes(x.stderr) : "N/A")],
      ["Standard Output", (x) => (x.stdout ? breakSlashes(x.stdout) : "N/A")]
    )
  ),
  collapsible(
    "Prior Workflow Runs",
    table(
      a.matches,
      ["Workflow Run", (m) => strikeout(m.skipped, m.workflowRunAccession)],
      [
        "Workflow",
        (m) =>
          strikeout(
            m.skipped,
            m.workflowAccession == a.workflowAccession
              ? "Current"
              : m.workflowAccession.toString()
          ),
      ],
      ["Status", (m) => strikeout(m.skipped, m.state)],
      ["Stale", (m) => strikeout(m.skipped, m.stale ? "🍞 Stale" : "🍅 Fresh")],
      [
        "LIMS Keys",
        (m) => {
          if (m.missingLimsKeys && m.extraLimsKeys) {
            return strikeout(m.skipped, "⬍ Messy Overlap");
          }
          if (m.extraLimsKeys) {
            return strikeout(m.skipped, "⬆️ Extra");
          }
          if (m.missingLimsKeys) {
            return strikeout(m.skipped, "⬇️ Missing");
          }
          return strikeout(m.skipped, "✓ Same");
        },
      ],
      [
        "Input Files",
        (m) => strikeout(m.skipped, m.fileSubset ? "📂️️ Missing" : "✓ Same"),
      ]
    )
  ),
]);

actionRender.set("niassa-annotation", (a) => [
  title(a, `Annotate ${a.name} ${a.accession}`),
  text(`Key: ${a.key}`),
  text(`Value: ${a.value}`),
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
        const elements = type.of.map((element) =>
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

specialImports.push((data) => {
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
            type: processIniType(parameter.name, parameter.type, true),
          };
        } else {
          errors.push(`Malformed parameter: ${JSON.stringify(parameter)}`);
        }
      }
      for (const [userAnnotation, type] of Object.entries(
        json.userAnnotations || {}
      )) {
        output[userAnnotation] = { required: true, type: type };
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
