import { useTopology } from "./hooks/use-topology";
import { TopologyGraph } from "./TopologyGraph";
import { ErrorBoundary } from "./ErrorBoundary";

export function TopologyPage() {
  const { data, error, loading } = useTopology();

  if (loading && !data) {
    return (
      <div className="ctv-page">
        <div className="ctv-page__loading">Loading topology...</div>
      </div>
    );
  }

  if (error && !data) {
    return (
      <div className="ctv-page">
        <div className="ctv-page__error">
          <h3>Failed to load topology</h3>
          <p>{error}</p>
        </div>
      </div>
    );
  }

  if (!data || data.nodes.length === 0) {
    return (
      <div className="ctv-page">
        <div className="ctv-page__empty">
          <h3>No routes found</h3>
          <p>The Camel context has no active routes.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="ctv-page">
      {error && (
        <div className="ctv-page__stale-warning">
          Updates failing: {error}
        </div>
      )}
      <ErrorBoundary>
        <TopologyGraph topology={data} />
      </ErrorBoundary>
    </div>
  );
}
