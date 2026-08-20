import { useMemo, useRef, useCallback } from "react";
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  type NodeTypes,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";

import type { TopologyData } from "./utils/types";
import {
  toReactFlowElements,
  applyDagreLayout,
} from "./utils/transform";
import { RouteNode } from "./nodes/RouteNode";
import { TriggerNode } from "./nodes/TriggerNode";
import { ExternalNode } from "./nodes/ExternalNode";

const nodeTypes: NodeTypes = {
  routeNode: RouteNode,
  triggerNode: TriggerNode,
  externalNode: ExternalNode,
};

interface TopologyGraphProps {
  topology: TopologyData;
}

function topologyFingerprint(t: TopologyData): string {
  const nodeIds = t.nodes.map((n) => n.routeId).sort().join(",");
  const edgeIds = t.edges
    .map((e) => `${e.fromRouteId}->${e.toRouteId}`)
    .sort()
    .join(",");
  const extIds = (t.externalEndpoints ?? []).map((e) => e.id).sort().join(",");
  return `${nodeIds}|${edgeIds}|${extIds}`;
}

export function TopologyGraph({ topology }: TopologyGraphProps) {
  const prevFingerprint = useRef("");

  const { layoutNodes, layoutEdges } = useMemo(() => {
    const fp = topologyFingerprint(topology);
    const structureChanged = fp !== prevFingerprint.current;
    prevFingerprint.current = fp;

    const { nodes, edges } = toReactFlowElements(topology);
    const laid = structureChanged ? applyDagreLayout(nodes, edges) : nodes;
    return { layoutNodes: laid, layoutEdges: edges };
  }, [topology]);

  const [nodes, , onNodesChange] = useNodesState(layoutNodes);
  const [edges, , onEdgesChange] = useEdgesState(layoutEdges);

  const onInit = useCallback(() => {
    // fit view handled by ReactFlow fitView prop
  }, []);

  return (
    <div className="ctv-graph">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onInit={onInit}
        nodeTypes={nodeTypes}
        fitView
        attributionPosition="bottom-left"
      >
        <Background />
        <Controls />
        <MiniMap zoomable pannable />
      </ReactFlow>
    </div>
  );
}
