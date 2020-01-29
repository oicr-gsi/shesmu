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
  title(a, `Workflow ${a.workflowName} (${a.workflowAccession})`),
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
