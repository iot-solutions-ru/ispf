export interface AnalyticsTagSourceDto {
  path: string;
  variable: string;
  field: string;
}

export interface AnalyticsTagLineageNodeDto {
  id: string;
  kind: string;
  label: string;
  path: string;
  variable: string;
}

export interface AnalyticsTagLineageEdgeDto {
  from: string;
  to: string;
  relation: string;
}

export interface AnalyticsTagLineageGraphDto {
  nodes: AnalyticsTagLineageNodeDto[];
  edges: AnalyticsTagLineageEdgeDto[];
}

export interface AnalyticsTagCatalogEntryDto {
  path: string;
  displayName: string;
  helper: string;
  expression: string;
  outputVariable: string;
  sources: AnalyticsTagSourceDto[];
  upstreamTagPaths: string[];
  downstreamTagPaths: string[];
  windowBucket: string;
  rollupBuckets: string[];
  periodicMs: number;
  enabled: boolean;
  qualityStatus: string;
  lastEvalStatus: string;
  lastEvalAt: string | null;
  lastTickAt: string | null;
  lineage: AnalyticsTagLineageGraphDto;
}

export interface AnalyticsTagCatalogListDto {
  count: number;
  tags: AnalyticsTagCatalogEntryDto[];
}
