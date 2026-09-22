import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { setPluginHost, clearPluginHost, type PluginHost } from "./plugin-host";
import { TopologyPage } from "./TopologyPage";
import "./plugin-styles.scss";

interface Disposable {
  dispose(): void;
}

const disposables: Disposable[] = [];

function mountPage(container: HTMLElement, Page: React.FC): Disposable {
  container.classList.add("camel-topology-viewer-plugin");
  const root = createRoot(container);
  root.render(
    <StrictMode>
      <Page />
    </StrictMode>
  );
  return {
    dispose() {
      root.unmount();
    },
  };
}

export async function activate(host: PluginHost) {
  setPluginHost(host);

  disposables.push(
    host.navigation.add({
      id: "camel-topology",
      label: "Topology",
      route: "/camel/topology",
      order: 200,
    })
  );

  disposables.push(
    host.pages.register({
      route: "/camel/topology",
      mount: (container) => mountPage(container, TopologyPage),
    })
  );
}

export function deactivate() {
  for (const d of disposables) {
    d.dispose();
  }
  disposables.length = 0;
  clearPluginHost();
}
