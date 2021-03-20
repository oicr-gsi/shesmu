import ca.on.oicr.gsi.shesmu.mongo.MongoPluginType;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;

module ca.on.oicr.gsi.shesmu.plugin.mongo {
  exports ca.on.oicr.gsi.shesmu.mongo;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.databind;
  requires org.mongodb.bson;
  requires org.mongodb.driver.core;
  requires org.mongodb.driver.sync.client;

  provides PluginFileType with
      MongoPluginType;
}
