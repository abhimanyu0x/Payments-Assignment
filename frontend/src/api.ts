export type Row = Record<string, unknown> & { id: string };
export type Page = { data: Row[]; next_cursor: string | null };
export type Api = <T>(path: string, init?: RequestInit) => Promise<T>;
export function createApi(key: string): Api {
  return async <T>(path: string, init: RequestInit = {}) => {
    const response = await fetch(`/api/v1${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${key}`,
        ...init.headers,
      },
    });
    const payload = await response.json();
    if (!response.ok)
      throw new Error(
        payload.error?.message ?? `Request failed (${response.status})`,
      );
    return payload as T;
  };
}
