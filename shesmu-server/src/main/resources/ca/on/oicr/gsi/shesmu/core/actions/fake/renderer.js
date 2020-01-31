actionRender.set("fake", a =>
  [title(a, `Fake ${a.name}`)].concat(jsonParameters(a))
);

specialImports.push(data => {
  try {
    const json = JSON.parse(data);
    if (json.hasOwnProperty("name") && json.hasOwnProperty("parameters")) {
      return { name: json.name, parameters: json.parameters, errors: [] };
    }
  } catch (e) {}
  return null;
});
