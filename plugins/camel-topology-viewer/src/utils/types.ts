export interface TopologyNode {
  routeId: string;
  description?: string;
  from: string;
  fromScheme: string;
  nodeType: "route" | "trigger";
  exchangesTotal?: number;
  exchangesFailed?: number;
}

export interface TopologyEdge {
  fromRouteId: string;
  toRouteId: string;
  endpoint: string;
  connectionType: "internal" | "external";
}

export interface ExternalEndpoint {
  id: string;
  uri: string;
  scheme: string;
  direction: "in" | "out";
  routeId: string;
  exchangesTotal?: number;
  exchangesFailed?: number;
}

export interface TopologyData {
  nodes: TopologyNode[];
  edges: TopologyEdge[];
  externalEndpoints?: ExternalEndpoint[];
}
