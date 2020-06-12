import { fetchJsonWithBusyDialog } from "./io.js";
import { dialog, text, preformatted, UIElement } from "./html.js";
import { Parser, parse } from "./parser.js";

type JsonDescriptor =
  | string
  | JsonDescriptor[]
  | { is: "optional"; inner: JsonDescriptor }
  | { is: "list"; inner: JsonDescriptor }
  | { is: "dictionary"; key: JsonDescriptor; value: JsonDescriptor };

interface TypeResponse {
  humanName: string;
  descriptor: string;
  wdlType: string;
  jsonDescriptor: JsonDescriptor;
}

export function parseType() {
  const format = document.getElementById("format") as HTMLSelectElement;
  fetchJsonWithBusyDialog<TypeResponse>(
    "/type",
    {
      body: JSON.stringify({
        value: (document.getElementById("typeValue") as HTMLInputElement).value,
        format: format.options[format.selectedIndex].value,
      }),
      method: "POST",
    },
    (data) => {
      document.getElementById("humanType")!.innerText = data.humanName;
      document.getElementById("descriptorType")!.innerText = data.descriptor;
      document.getElementById("wdlType")!.innerText = data.wdlType;
      document.getElementById("jsonDescriptorType")!.innerText = JSON.stringify(
        data.jsonDescriptor
      );
    }
  );
}

function fetchValue(url: string, body: any): void {
  fetchJsonWithBusyDialog<{ value?: any; error?: string }>(
    url,
    {
      body: JSON.stringify(body),
      method: "POST",
    },
    (data) =>
      dialog((close) =>
        data.hasOwnProperty("value")
          ? preformatted(JSON.stringify(data.value, null, 2))
          : text(data.error || "Unknown error")
      )
  );
}

export function fetchConstant(name: string): void {
  fetchValue("/constant", name);
}

export function runFunction(name: string, parameterTypes: Parser[]) {
  const parameters: any[] = [];
  const errors: UIElement[] = [];
  if (
    !parameterTypes.every((parameterType, parameter) =>
      parse(
        (document.getElementById(`${name}$${parameter}`) as HTMLInputElement)
          .value,
        parameterType,
        (x) => parameters.push(x),
        (message) => errors.push(text(`Argument ${parameter}: ${message}`))
      )
    )
  ) {
    dialog((close) => errors);
    return;
  }
  fetchValue("/function", { name: name, args: parameters });
}
