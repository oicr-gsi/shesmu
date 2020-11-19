actionRender.set("fake", a =>
  [title(a, `Fake ${a.name}`)].concat(jsonParameters(a))
);

specialImports.push(async (data, resolver) => {
  try {
    const json = JSON.parse(data);
    if (
      json.hasOwnProperty("name") &&
      json.hasOwnProperty("parameters") &&
      json.hasOwnProperty("kind") &&
      json.kind == "action"
    ) {
      const parameters = {};
      for (const p of json.parameters) {
        if (
          !p.hasOwnProperty("name") ||
          !p.hasOwnProperty("required") ||
          !p.hasOwnProperty("type")
        ) {
          return null;
        }
        parameters[p.name] = {
          required: p.required,
          type: await resolver(
            "shesmu::json_descriptor",
            JSON.stringify(p.type)
          )
        };
      }
      return { name: json.name, parameters: parameters, errors: [] };
    }
  } catch (e) {}
  return null;
});
