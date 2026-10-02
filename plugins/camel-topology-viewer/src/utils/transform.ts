import type { Node, Edge } from "@xyflow/react";
import Dagre from "@dagrejs/dagre";
import type { TopologyData } from "./types";

export interface TopologyNodeData {
  label: string;
  from: string;
  scheme: string;
  exchangesTotal?: number;
  exchangesFailed?: number;
  direction?: "in" | "out";
  [key: string]: unknown;
}

export function toReactFlowElements(topology: TopologyData): {
  nodes: Node<TopologyNodeData>[];
  edges: Edge[];
} {
  const nodes: Node<TopologyNodeData>[] = topology.nodes.map((node) => ({
    id: node.routeId,
    type: node.nodeType === "trigger" ? "triggerNode" : "routeNode",
    data: {
      label: node.routeId,
      from: node.from,
      scheme: node.fromScheme,
      exchangesTotal: node.exchangesTotal,
      exchangesFailed: node.exchangesFailed,
    },
    position: { x: 0, y: 0 },
  }));

  const edges: Edge[] = topology.edges.map((edge) => ({
    id: `e-${edge.fromRouteId}-${edge.toRouteId}-${edge.endpoint}`,
    source: edge.fromRouteId,
    target: edge.toRouteId,
    label: edge.endpoint,
    animated: edge.connectionType === "internal",
    style: {
      strokeDasharray:
        edge.connectionType === "external" ? "5,5" : undefined,
    },
  }));

  if (topology.externalEndpoints) {
    for (const ep of topology.externalEndpoints) {
      nodes.push({
        id: ep.id,
        type: "externalNode",
        data: {
          label: ep.uri,
          from: ep.uri,
          scheme: ep.scheme,
          direction: ep.direction,
          exchangesTotal: ep.exchangesTotal,
          exchangesFailed: ep.exchangesFailed,
        },
        position: { x: 0, y: 0 },
      });
      edges.push({
        id: `ext-${ep.id}`,
        source: ep.direction === "in" ? ep.id : ep.routeId,
        target: ep.direction === "in" ? ep.routeId : ep.id,
        style: { strokeDasharray: "5,5" },
      });
    }
  }

  return { nodes, edges };
}

const NODE_WIDTH = 220;
const NODE_HEIGHT = 80;

export function applyDagreLayout(
  nodes: Node<TopologyNodeData>[],
  edges: Edge[]
): Node<TopologyNodeData>[] {
  const g = new Dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
  g.setGraph({ rankdir: "TB", nodesep: 60, ranksep: 80 });

  for (const node of nodes) {
    g.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
  }
  for (const edge of edges) {
    g.setEdge(edge.source, edge.target);
  }

  Dagre.layout(g);

  return nodes.map((node) => {
    const pos = g.node(node.id);
    return {
      ...node,
      position: {
        x: pos.x - NODE_WIDTH / 2,
        y: pos.y - NODE_HEIGHT / 2,
      },
    };
  });
}
