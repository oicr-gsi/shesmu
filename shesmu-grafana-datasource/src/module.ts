import { DataSourcePlugin } from '@grafana/data';
import { DataSource } from './DataSource';
import { ConfigEditor } from './ConfigEditor';
import { QueryEditor } from './QueryEditor';
import { ActionQuery, ShesmuDataSourceOptions } from './types';

export const plugin = new DataSourcePlugin<DataSource, ActionQuery, ShesmuDataSourceOptions>(DataSource)
  .setConfigEditor(ConfigEditor)
  .setQueryEditor(QueryEditor);
