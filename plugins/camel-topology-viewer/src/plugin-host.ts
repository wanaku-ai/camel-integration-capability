export const SERVICE_ID = "camel-topology-api";

interface PluginHostHttp {
  get<T>(service: string, path: string): Promise<T>;
  post<T>(service: string, path: string, body?: unknown): Promise<T>;
  put<T>(service: string, path: string, body?: unknown): Promise<T>;
  delete<T>(service: string, path: string): Promise<T>;
}

interface PluginHostNavigation {
  add(entry: {
    id: string;
    label: string;
    route: string;
    icon?: string;
    section?: string;
    order?: number;
  }): Disposable;
}

interface PluginHostPages {
  register(page: {
    route: string;
    mount: (container: HTMLElement) => void | Disposable;
  }): Disposable;
}

interface PluginHostNotifications {
  show(message: { title?: string; text: string; kind?: string }): void;
}

interface Disposable {
  dispose(): void;
}

export interface PluginHost {
  version: string;
  navigation: PluginHostNavigation;
  pages: PluginHostPages;
  http: PluginHostHttp;
  notifications: PluginHostNotifications;
}

let pluginHost: PluginHost | null = null;

export function setPluginHost(host: PluginHost): void {
  pluginHost = host;
}

export function getPluginHost(): PluginHost | null {
  return pluginHost;
}

export function clearPluginHost(): void {
  pluginHost = null;
}
