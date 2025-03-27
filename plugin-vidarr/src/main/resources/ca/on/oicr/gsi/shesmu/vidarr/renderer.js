const vidarrDebugInfo = {
  cromwell: (di) => {
    function cromwellFailure(failures) {
      return failures.map((f) =>
        f.causedBy.length
          ? collapsible(f.message, cromwellFailure(f.causedBy))
          : text(f.message)
      );
    }
    function vidarrCromwellLink(path, stashed) {
      if (path && stashed) {
        return link(stashed, breakSlashes(path), "View Log");
      } else if (path) {
        return breakSlashes(path);
      } else if (stashed) {
        return link(stashed, "View Log");
      } else {
        return "N/A";
      }
    }

    return [
      table(
        [
          ["Cromwell Identifier", di.cromwellId],
          ["Cromwell Workflow Status", di.cromwellStatus],
          ["Cromwell Workflow Root", di.cromwellRoot],
        ],
        ["Information", (i) => i[0]],
        ["Value", (i) => i[1]]
      ),
      collapsible("Failures", cromwellFailure(di.cromwellFailures)),
      collapsible(
        "Cromwell Calls",
        table(
          di.cromwellCalls || [],
          ["Task", (x) => x.task],
          ["Attempt", (x) => x.attempt.toString()],
          [
            "Scatter",
            (x) => (x.shardIndex < 0 ? "N/A" : x.shardIndex.toString()),
          ],
          ["Status", (x) => x.executionStatus || "Unknown"],
          ["Backend", (x) => x.backend || "Unknown"],
          ["Job ID", (x) => x.jobId || "Unknown"],
          ["Return Code", (x) => x.returnCode || "Unknown"],
          ["Execution Status", (x) => x.executionStatus || "Unknown"],
          [
            "Standard Error",
            (x) => vidarrCromwellLink(x.stderr, x.stderr_stashed),
          ],
          [
            "Standard Output",
            (x) => vidarrCromwellLink(x.stdout, x.stdout_stashed),
          ],
          ["Failures", (x) => cromwellFailure(x.failures)]
        )
      ),
    ];
  },
};
const vidarrStateRenderer = {
  attempt: (a) => `Next Attempt: ${a.attempt}`,
  conflict: (a) => table(a.possibleMatches, ["Vidarr Workflow Run", (x) => x]),
  dead: (a) => "This is all there is.",
  missingKeys: (a) => [
    `Version ${missingVersion} not found on all LIMS keys`,
    table(
      a.corruptExternalIds,
      ["Provider", (k) => k.provider],
      ["ID", (k) => k.id]
    ),
  ],
  monitor: (a) => [
    table(
      [
        ["Vidarr ID", link(
		(a.info.completed ? a.workflowRunUrl.replace("status", "run") : a.workflowRunUrl), 
		a.info.id)],
        ["Modified", timespan(a.modified)],
        ["Created", timespan(a.info.created)],
        ["Started", timespan(a.info.started)],
        ["Phase", a.info.enginePhase],
        ["Operation Status", a.info.operationStatus || "N/A"],
        ["Completed", timespan(a.info.completed)],
        ["Running", a.info.running ? "Yes" : "No"],
        [
          "Pre-flight Okay",
          a.info.preflightOk === undefined
            ? "N/A"
            : a.info.preflightOk
            ? "Yes"
            : "No",
        ],
        ["Attempt", a.info.attempt.toString()],
        ["Target", a.info.target || "N/A"],
      ]
        .concat(a.services.map((s) => ["Required Service", s]))
        .filter((x) => x),
      ["Existing Workflow Information", (x) => x[0]],
      ["Value", (x) => x[1]]
    ),
    objectTable(a.info.arguments, "Arguments from Vidarr", (v) =>
      preformatted(JSON.stringify(v, null, 2))
    ),
    typeof a.info.engineParameters == "object" &&
    a.info.engineParameters !== null
      ? objectTable(a.info.engineParameters, "Engine Parameters", (v) =>
          preformatted(JSON.stringify(v, null, 2))
        )
      : "",
    objectTable(a.info.metadata, "Metadata from Vidarr", (v) =>
      preformatted(JSON.stringify(v, null, 2))
    ),
    objectTable(a.info.tracing || {}, "Resource Tracing", (v) => `${v}`),
    collapsible(
      "Operations",
      table(
        a.info.operations || [],
        ["Status", (o) => o.status],
        ["Engine Phase", (o) => o.enginePhase],
        ["Type", (o) => o.type || "N/A"],
        [
          "Recovery State",
          (o) =>
            collapsible(
              "State",
              preformatted(JSON.stringify(o.recoveryState, null, 2))
            ),
        ],
        [
          "Debug Information",
          (o) =>
            [
              Array.isArray(o.debugInformation)
                ? o.debugInformation
                : [o.debugInformation],
            ]
              .flat(Number.MAX_VALUE)
              .filter(
                (di) =>
                  di != null &&
                  typeof di == "object" &&
                  di.hasOwnProperty("type")
              )
              .map((di) =>
                (
                  vidarrDebugInfo[di.type] ||
                  ((x) => `Unknown debug information type: ${di.type}`)
                )(di)
              ),
        ]
      )
    ),
    collapsible(
      "Differences in Arguments",
      recursiveDifferences(
        "Vidarr",
        a.info.arguments,
        "Olive",
        a.request.arguments
      )
    ),
    collapsible(
      "Differences in Metadata",
      recursiveDifferences(
        "Vidarr",
        a.info.metadata,
        "Olive",
        a.request.metadata
      )
    ),
  ],
};

actionRender.set("vidarr-run", (a) => [
  title(a, `Workflow ${a.request.workflow} (${a.request.workflowVersion})`),
  table(
    [
      ["Priority", a.priority.toString()],
      ["Attempt", a.request.attempt.toString()],
      ["Target", a.request.target.toString()],
    ]
      .concat(a.services.map((s) => ["Required Service", s]))
      .filter((x) => x),
    ["Workflow Information", (x) => x[0]],
    ["Value", (x) => x[1]]
  ),
  objectTable(a.request.arguments, "Arguments from Olive", (v) =>
    preformatted(JSON.stringify(v, null, 2))
  ),
  typeof a.request.engineParameters == "object"
    ? objectTable(a.request.engineParameters, "Engine Parameters", (v) =>
        preformatted(JSON.stringify(v, null, 2))
      )
    : "",
  objectTable(a.request.metadata, "Metadata from Olive", (v) =>
    preformatted(JSON.stringify(v, null, 2))
  ),
  objectTable(a.request.labels, "Labels", (v) =>
    preformatted(revealWhitespace(v))
  ),
  collapsible(
    "LIMS Keys",
    table(
      a.request.externalKeys,
      ...[
        ["Provider", (k) => k.provider],
        ["ID", (k) => k.id],
      ].concat(
        a.request.externalKeys.length == 0
          ? []
          : Object.keys(a.request.externalKeys[0].versions).map((key) => [
              key,
              (lims) => lims.versions[key] || "Missing!",
            ])
      )
    )
  ),
  vidarrStateRenderer[a.runState](a),
]);

[
  {
    type: "runs",
    titleStr: "Workflow Runs",
    entries: (a) => a.workflowRuns,
    columns: [["Workflow Run Identifier", (i) => i]],
  },
  {
    type: "external-identifiers",
    titleStr: "External Identifiers",
    entries: (a) =>
      Object.entries(a.externalIds).flatMap(([provider, ids]) =>
        ids.map((id) => ({
          provider,
          id,
        }))
      ),

    columns: [[("Provider", (e) => e.provider)], ["Identifier", (e) => e.id]],
  },
].forEach(({ type, entries, titleStr, columns }) =>
  actionRender.set(`vidarr-unload-${type}`, (a) => [
    title(a, "Unload Workflow Runs"),
    a.vidarrOutputName ? `Output in ${a.vidarrOutputName}` : "Not attempted.",
    collapsible(titleStr, table(entries(a), ...columns)),
  ])
);
