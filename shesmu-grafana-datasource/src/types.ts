import { DataQuery, DataSourceJsonData } from '@grafana/data';

export interface ActionQuery extends DataQuery {
  queryText?: string;
}

export const defaultQuery: Partial<ActionQuery> = {};

export interface ShesmuDataSourceOptions extends DataSourceJsonData {
  url?: string;
}
