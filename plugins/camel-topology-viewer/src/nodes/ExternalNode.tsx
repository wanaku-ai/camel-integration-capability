import { Handle, Position } from "@xyflow/react";
import type { TopologyNodeData } from "../utils/transform";
import { MetricsBadge } from "./MetricsBadge";

export function ExternalNode({ data }: { data: TopologyNodeData }) {
  return (
    <div className="ctv-node ctv-node--external">
      {data.direction === "in" && (
        <Handle type="source" position={Position.Bottom} />
      )}
      {data.direction === "out" && (
        <Handle type="target" position={Position.Top} />
      )}
      {!data.direction && (
        <>
          <Handle type="target" position={Position.Top} />
          <Handle type="source" position={Position.Bottom} />
        </>
      )}
      <div className="ctv-node__label">{data.label}</div>
      <div className="ctv-node__scheme">{data.scheme}</div>
      <MetricsBadge total={data.exchangesTotal} failed={data.exchangesFailed} />
    </div>
  );
}
