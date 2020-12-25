import defaults from 'lodash/defaults';

import {
  DataQueryRequest,
  DataQueryResponse,
  DataSourceApi,
  DataSourceInstanceSettings,
  MutableDataFrame,
  FieldType,
} from '@grafana/data';

import { getBackendSrv } from '@grafana/runtime';

import { ActionQuery, ShesmuDataSourceOptions, defaultQuery } from './types';

export interface ParseQueryResponse {
  errors: ParseQueryError[];
  formatted?: string;
  filter?: any;
}
export interface ParseQueryError {
  line: number;
  column: number;
  message: string;
}

export class DataSource extends DataSourceApi<ActionQuery, ShesmuDataSourceOptions> {
  url: string;

  constructor(instanceSettings: DataSourceInstanceSettings<ShesmuDataSourceOptions>) {
    super(instanceSettings);
    this.url = instanceSettings.url!;
  }

  async query(options: DataQueryRequest<ActionQuery>): Promise<DataQueryResponse> {
    const data = await Promise.all(
      options.targets.map(async target => {
        const query = defaults(target, defaultQuery);
        let filter: any[];
        if (query.queryText) {
          const parseResponse = await getBackendSrv()
            .fetch<ParseQueryResponse>({
              method: 'POST',
              url: `${this.url}/parsequery`,
              data: JSON.stringify(query.queryText),
            })
            .toPromise();
          if (parseResponse.ok) {
            filter = [parseResponse.data.filter];
          } else {
            throw new Error(
              parseResponse.data.errors.map(({ line, column, message }) => `${line}:${column}: ${message}`).join('\n')
            );
          }
        } else {
          filter = [];
        }
        const response = await getBackendSrv()
          .fetch<number>({
            method: 'POST',
            url: `${this.url}/count`,
            data: JSON.stringify(filter),
          })
          .toPromise();
        if (response.ok) {
          return new MutableDataFrame({
            refId: query.refId,
            fields: [
              {
                name: 'time',
                values: [options.range!.from.valueOf(), options.range!.to.valueOf()],
                type: FieldType.time,
              },
              {
                name: query.queryText || 'All Actions',
                values: [response.data, response.data],
                type: FieldType.number,
              },
            ],
          });
        } else {
          throw new Error(response.statusText);
        }
      })
    );

    return { data };
  }

  async testDatasource() {
    const result = await getBackendSrv()
      .fetch({
        method: 'POST',
        url: `${this.url}/parsequery`,
        data: JSON.stringify('status = succeeded'),
      })
      .toPromise();

    return result.ok
      ? {
          status: 'success',
          message: 'Success',
        }
      : {
          status: 'failed',
          message: result.statusText,
        };
  }
}
