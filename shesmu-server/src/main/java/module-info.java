import ca.on.oicr.gsi.shesmu.core.actions.fake.FakeLocalDefinitionInstance;
import ca.on.oicr.gsi.shesmu.core.actions.fake.FakeRemoteDefinitionInstance;
import ca.on.oicr.gsi.shesmu.core.constants.JsonFileDefinitionFileType;
import ca.on.oicr.gsi.shesmu.core.groupers.AlwaysIncludeGrouperDefinition;
import ca.on.oicr.gsi.shesmu.core.groupers.CombinationsGrouperDefinition;
import ca.on.oicr.gsi.shesmu.core.groupers.CrossTabGrouperDefinition;
import ca.on.oicr.gsi.shesmu.core.groupers.PowerSetGrouperDefinition;
import ca.on.oicr.gsi.shesmu.core.input.shesmu.ShesmuIntrospectionFormat;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;

module ca.on.oicr.gsi.shesmu.server {
  exports ca.on.oicr.gsi.shesmu.core.actions.fake;
  exports ca.on.oicr.gsi.shesmu.core.signers;
  exports ca.on.oicr.gsi.shesmu.core;
  exports ca.on.oicr.gsi.shesmu.runtime.subsample;
  exports ca.on.oicr.gsi.shesmu.runtime;
  exports ca.on.oicr.gsi.shesmu.server;
  exports ca.on.oicr.gsi.shesmu;

  uses ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
  uses ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
  uses ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
  uses ca.on.oicr.gsi.shesmu.plugin.types.TypeParser;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.core;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.datatype.jsr310;
  requires org.apache.commons.csv;
  requires simpleclient.common;
  requires simpleclient.hotspot;
  requires simpleclient;
  requires java.desktop;
  requires java.management;
  requires java.net.http;
  requires java.xml;
  requires jdk.httpserver;
  requires org.objectweb.asm.commons;
  requires org.objectweb.asm.util;
  requires org.objectweb.asm;

  provides GrouperDefinition with
      AlwaysIncludeGrouperDefinition,
      CombinationsGrouperDefinition,
      CrossTabGrouperDefinition,
      PowerSetGrouperDefinition;
  provides InputFormat with
      ShesmuIntrospectionFormat;
  provides PluginFileType with
      FakeLocalDefinitionInstance,
      FakeRemoteDefinitionInstance,
      JsonFileDefinitionFileType;
}
