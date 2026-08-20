import { useState, useEffect, useRef, useCallback } from "react";
import { getPluginHost, SERVICE_ID } from "../plugin-host";
import type { TopologyData } from "../utils/types";

const POLL_INTERVAL_MS = 5000;

export function useTopology() {
  const [data, setData] = useState<TopologyData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const mountedRef = useRef(true);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const fetchTopology = useCallback(async () => {
    const host = getPluginHost();
    if (!host) {
      if (mountedRef.current) {
        setError("Plugin host not available");
        setLoading(false);
      }
      return;
    }

    try {
      let raw: unknown = await host.http.get<unknown>(
        SERVICE_ID,
        "/q/dev/route-topology?metric=true&external=true"
      );
      if (typeof raw === "string") {
        raw = JSON.parse(raw);
      }
      const obj = raw as Record<string, unknown>;
      const result = (obj["route-topology"] ?? obj) as TopologyData;
      if (mountedRef.current) {
        setData(result);
        setError(null);
      }
    } catch (e) {
      if (mountedRef.current) {
        setError(e instanceof Error ? e.message : "Failed to fetch topology");
      }
    } finally {
      if (mountedRef.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;

    const poll = async () => {
      await fetchTopology();
      if (mountedRef.current) {
        timerRef.current = setTimeout(poll, POLL_INTERVAL_MS);
      }
    };
    poll();

    return () => {
      mountedRef.current = false;
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [fetchTopology]);

  return { data, error, loading, refresh: fetchTopology };
}
