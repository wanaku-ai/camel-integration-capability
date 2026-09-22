import { Handle, Position } from "@xyflow/react";
import type { TopologyNodeData } from "../utils/transform";
import { MetricsBadge } from "./MetricsBadge";

export function TriggerNode({ data }: { data: TopologyNodeData }) {
  return (
    <div className="ctv-node ctv-node--trigger">
      <Handle type="target" position={Position.Top} />
      <div className="ctv-node__label">{data.label}</div>
      <div className="ctv-node__scheme">{data.from}</div>
      <MetricsBadge total={data.exchangesTotal} failed={data.exchangesFailed} />
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}
