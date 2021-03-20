import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import ca.on.oicr.gsi.shesmu.plugin.input.unixfs.UnixFileDefinition;

module ca.on.oicr.gsi.shesmu {
  exports ca.on.oicr.gsi.shesmu.plugin.action;
  exports ca.on.oicr.gsi.shesmu.plugin.authentication;
  exports ca.on.oicr.gsi.shesmu.plugin.cache;
  exports ca.on.oicr.gsi.shesmu.plugin.dumper;
  exports ca.on.oicr.gsi.shesmu.plugin.files;
  exports ca.on.oicr.gsi.shesmu.plugin.filter;
  exports ca.on.oicr.gsi.shesmu.plugin.functions;
  exports ca.on.oicr.gsi.shesmu.plugin.grouper;
  exports ca.on.oicr.gsi.shesmu.plugin.input.unixfs;
  exports ca.on.oicr.gsi.shesmu.plugin.input;
  exports ca.on.oicr.gsi.shesmu.plugin.json;
  exports ca.on.oicr.gsi.shesmu.plugin.refill;
  exports ca.on.oicr.gsi.shesmu.plugin.signature;
  exports ca.on.oicr.gsi.shesmu.plugin.types;
  exports ca.on.oicr.gsi.shesmu.plugin.wdl;
  exports ca.on.oicr.gsi.shesmu.plugin;

  requires ca.on.oicr.gsi.serverutils;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.core;
  requires com.fasterxml.jackson.databind;
  requires simpleclient;
  requires java.management;
  requires java.net.http;
  requires java.xml;

  provides InputFormat with
      UnixFileDefinition;
}
