export type ParseResult = {
  good: boolean;
  input: string;
  error?: string;
  output?: any;
};
export type Parser = (input: string) => ParseResult;

export function _(input: string): ParseResult {
  return { good: false, input: input, error: "Cannot parse bad type." };
}
export function a(innerType: Parser): Parser {
  return (input: string) => {
    const output = [];
    for (;;) {
      let match = input.match(output.length == 0 ? /^\s*\[/ : /^\s*([\],])/);
      if (!match) {
        return {
          good: false,
          input: input,
          error:
            output.length == 0
              ? "Expected [ in list."
              : "Expected ] or , for list.",
        };
      }
      if (match[1] == "]") {
        return {
          good: true,
          input: input.substring(match[0].length),
          output: output,
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
}
export function b(input: string): ParseResult {
  let match = input.match(/^\s*([Tt]rue|[Ff]alse)/);
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: match[1].toLowerCase() == "true",
    };
  } else {
    return { good: false, input: input, error: "Expected boolean." };
  }
}
export function d(input: string): ParseResult {
  let match = input.match(/^\s*EpochSecond\s+(\d*)/);
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: parseInt(match[1]) * 1000,
    };
  }
  match = input.match(/^\s*EpochMilli\s+(\d*)/);
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: parseInt(match[1]),
    };
  }
  match = input.match(
    /^\s*Date\s+(\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}:\d{2}(Z|[+-]\d{2}))?)/
  );
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: new Date(match[1]).getTime(),
    };
  } else {
    return { good: false, input: input, error: "Expected date." };
  }
}
export function j(input: string): ParseResult {
  let match = input.match(/^\s*(\d+(\.\d*)?([eE][+-]?\d+)?)/);
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: parseFloat(match[1]),
    };
  }
  match = input.match(
    /^\s*"(((?=\\)\\(["\\\/bfnrt]|u[0-9a-fA-F]{4}))|[^"\\\0-\x1F\x7F]+)*"/
  );
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: match[1] || "",
    };
  }
  match = input.match(/^\s*true/);
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: true,
    };
  }
  match = input.match(/^\s*false/);
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: false,
    };
  }
  match = input.match(/^\s*null/);
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: null,
    };
  }
  match = input.match(/^\s*\[/);
  if (match) {
    const result = [];
    let current = input.substring(match[0].length);

    while (true) {
      match = current.match(/^\s*]/);
      if (match) {
        return {
          good: true,
          input: current.substring(match[0].length),
          output: result,
        };
      }
      if (result.length) {
        match = current.match(/^\s*,/);
        if (!match) {
          return {
            good: false,
            input: current,
            error: "Expected , or ].",
          };
        }
        current = current.substring(match[0].length);
      }

      const inner = j(current);
      if (!inner.good) {
        return inner;
      }
      result.push(inner.output);
      current = inner.input;
    }
  }
  match = input.match(/^\s*{/);
  if (match) {
    const result = [];
    let current = input.substring(match[0].length);

    while (true) {
      match = current.match(/^\s*}/);
      if (match) {
        return {
          good: true,
          input: current.substring(match[0].length),
          output: Object.fromEntries(result),
        };
      }

      if (result.length) {
        match = current.match(/\s*,/);
        if (!match) {
          return {
            good: false,
            input: current,
            error: "Expected }.",
          };
        }
        current = current.substring(match[0].length);
      }
      match = current.match(
        /^\s*"(((?=\\)\\(["\\\/bfnrt]|u[0-9a-fA-F]{4}))|[^"\\\0-\x1F\x7F]+)*"\s*:/
      );
      if (!match) {
        return {
          good: false,
          input: current,
          error: "Expected property name.",
        };
      }
      const name = match[1];
      current = current.substring(match[0].length);

      const inner = j(current);
      if (!inner.good) {
        return inner;
      }
      result.push([name, inner.output]);
      current = inner.input;
    }
  }
  return {
    good: false,
    input: input,
    error: "Unexpected input.",
  };
}
export function f(input: string): ParseResult {
  let match = input.match(/^\s*(\d*(\.\d*([eE][+-]?\d+)?))/);
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: parseFloat(match[1]),
    };
  } else {
    return {
      good: false,
      input: input,
      error: "Expected floating point number.",
    };
  }
}
export function i(input: string): ParseResult {
  let match = input.match(/^\s*(\d*)/);
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: parseInt(match[1]),
    };
  } else {
    return { good: false, input: input, error: "Expected integer." };
  }
}
export function m(keyType: Parser, valueType: Parser): Parser {
  return (input) => {
    const output = [];
    let match = input.match(/^\s*Dict/);
    if (!match) {
      return {
        good: false,
        input: input,
        error: "Expected Dict { in dictionary.",
      };
    }
    input = input.substring(match[0].length);
    for (;;) {
      match = input.match(output.length == 0 ? /^\s*(\{)/ : /^\s*([\},])/);
      if (!match) {
        return {
          good: false,
          input: input,
          error:
            output.length == 0
              ? "Expected { in dictionary."
              : "Expected } or , for dictionary.",
        };
      }
      if (match[1] == "}") {
        return {
          good: true,
          input: input.substring(match[0].length),
          output: output,
        };
      }
      const keyState = keyType(input.substring(match[0].length));
      if (keyState.good) {
        match = keyState.input.match(/\s*=\s*/);
        if (!match) {
          return {
            good: false,
            input: keyState.input,
            error: "Expected = in dictionary.",
          };
        }
        const valueState = valueType(keyState.input.substring(match[0].length));
        if (valueState.good) {
          output.push([keyState.output, valueState.output]);
          input = valueState.input;
        } else {
          return valueState;
        }
      } else {
        return keyState;
      }
    }
  };
}
export function o(fieldTypes: { [name: string]: Parser }): Parser {
  return (input) => {
    const output: { [name: string]: any } = {};
    let first = true;
    // We're going to iterate over the keys so we get the right number of fields, but we won't actually use them directly since we don't know the order the user gave them to us in
    for (let field in Object.keys(fieldTypes)) {
      let match = input.match(first ? /^\s*{/ : /^\s*,/);
      if (!match) {
        return {
          good: false,
          input: input,
          error: first ? "Expected { for object." : "Expected , for object.",
        };
      }
      first = false;
      const fieldStart = input
        .substring(match[0].length)
        .match(/^\s*([a-z][a-z0-9_]*)\s*=\s*/);
      if (!fieldStart) {
        return {
          good: false,
          input: input,
          error: "Expected field name for object.",
        };
      }
      if (output.hasOwnProperty(fieldStart[1])) {
        return {
          good: false,
          input: input,
          error: `Duplicate field ${fieldStart[1]} in object.`,
        };
      }

      const fieldType = fieldTypes[fieldStart[1]];
      const state = fieldType(
        input.substring(match[0].length + fieldStart[0].length)
      );
      if (state.good) {
        output[fieldStart[1]] = state.output;
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
        output: output,
      };
    } else {
      return { good: false, input: input, error: "Expected } in object." };
    }
  };
}
export function p(input: string): ParseResult {
  let match = input.match(/^\s*'(([^'\\]|\\')*)'/);
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: match[1].replace("\\'", "'"),
    };
  } else {
    return { good: false, input: input, error: "Expected path." };
  }
}
export function q(inner: Parser): Parser {
  return (input) => {
    let match = input.match(/^\s*`\s*`\s*/);
    if (match) {
      return {
        good: true,
        input: input.substring(match[0].length),
        output: null,
      };
    }
    match = input.match(/^\s*`\s*/);
    if (match) {
      const result = inner(input.substring(match[0].length));
      if (result.good) {
        match = result.input.match(/^\s*`\s*/);
        if (match) {
          return result;
        } else {
          return { good: false, input: result.input, error: "Expected `." };
        }
      } else {
        return result;
      }
    } else {
      return { good: false, input: input, error: "Expected `." };
    }
  };
}
export function s(input: string): ParseResult {
  let match = input.match(/^\s*"(([^"\\]|\\")*)"/);
  if (match) {
    return {
      good: true,
      input: input.substring(match[0].length),
      output: match[1].replace('\\"', '"'),
    };
  } else {
    return { good: false, input: input, error: "Expected string." };
  }
}
export function t(innerTypes: Parser[]): Parser {
  return (input) => {
    const output = [];
    for (let i = 0; i < innerTypes.length; i++) {
      let match = input.match(i == 0 ? /^\s*{/ : /^\s*,/);
      if (!match) {
        return {
          good: false,
          input: input,
          error: i == 0 ? "Expected { for tuple." : "Expected , for tuple.",
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
        output: output,
      };
    } else {
      return { good: false, input: input, error: "Expected } in tuple." };
    }
  };
}
function nullParser(input: string): ParseResult {
  return { input: input, good: true };
}
export function u(unionTypes: { [type: string]: Parser | null }): Parser {
  return (input) => {
    const match = input.match(/^\s*([A-Z][A-Z_0-9]+)\s*/);
    if (match) {
      if (unionTypes.hasOwnProperty(match[1])) {
        const result = (unionTypes[match[1]] || nullParser)(
          input.substr(match[0].length)
        );
        if (result.good) {
          return {
            good: true,
            input: result.input,
            output: { type: match[1], contents: result.output },
          };
        } else {
          return result;
        }
      } else {
        return {
          good: false,
          input: input,
          output: `Invalid algebraic type ${match[1]}.`,
        };
      }
    } else {
      return { good: false, input: input, error: "Expected algebraic type." };
    }
  };
}
export function parse(
  input: string,
  parse: Parser,
  resultHandler: (output: any) => void,
  errorHandler: (error: string, position: number) => void
) {
  let state = parse(input);
  if (!state.good) {
    errorHandler(state.error!, input.length - state.input.length);
    return false;
  }
  if (state.input.match(/^\s*$/) == null) {
    errorHandler("Junk at end of input.", input.length - state.input.length);
    return false;
  }
  resultHandler(state.output);
  return true;
}
