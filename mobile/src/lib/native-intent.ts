const BARE_SCHEME_URL = /^[a-z][a-z0-9+.-]*:\/*$/i;
const SCHEME_PREFIX = /^[a-z][a-z0-9+.-]*:\/*/i;

export type QuickAction = "new-entry";

export function resolveSystemPath(path: string): string {
  return BARE_SCHEME_URL.test(path) ? "/" : path;
}

export function parseQuickAction(path: string): QuickAction | null {
  const withoutScheme = path.replace(SCHEME_PREFIX, "");
  const route = withoutScheme.split(/[?#]/)[0].replace(/^\/+|\/+$/g, "");
  return route === "entry/new" ? "new-entry" : null;
}

export function redirectIncomingPath(path: string, signedIn: boolean, deferQuickAction: (action: QuickAction) => void): string {
  const action = parseQuickAction(path);
  if (action === null) {
    return resolveSystemPath(path);
  }
  if (signedIn) {
    return "/entry/new";
  }
  deferQuickAction(action);
  return "/";
}
