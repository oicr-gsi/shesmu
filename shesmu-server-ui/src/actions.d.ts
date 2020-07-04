// This file will be generated by plugins on the Shemu server
import { UIElement } from "./html.js";
import { Action } from "./action.js";
import { FakeActionDefinition } from "./simulation.js";
declare module "actions" {
  export const actionRender: Map<string, (a: Action) => UIElement>;
  export const specialImports: ((
    data: string
  ) => null | FakeActionDefinition)[];
}