interface MetricsBadgeProps {
  total?: number;
  failed?: number;
}

export function MetricsBadge({ total, failed }: MetricsBadgeProps) {
  if (total === undefined) return null;

  return (
    <div className="ctv-metrics">
      <span className="ctv-metrics__total">{total.toLocaleString()}</span>
      {failed !== undefined && failed > 0 && (
        <span className="ctv-metrics__failed">
          {failed.toLocaleString()} failed
        </span>
      )}
    </div>
  );
}
