export type Row = Record<string, unknown> & { id: string };
export type Page = { data: Row[]; next_cursor: string | null };
export type Api = <T>(path: string, init?: RequestInit) => Promise<T>;
type ApiErrorBody = {
    error?: {
        code?: string;
        message?: string;
        details?: { field: string; message: string }[];
    };
};

export class ApiError extends Error {
    constructor(
        message: string,
        readonly status: number,
    ) {
        super(message);
    }
}

const FALLBACK = "Something went wrong. Please try again.";
const OFFLINE = "We could not reach the service. Please try again.";

function describe(body: ApiErrorBody | null): string {
    const error = body?.error;
    const details = [...new Set((error?.details ?? []).map((d) => d.message))];
    if (details.length) return details.join(" ");
    return error?.message ?? FALLBACK;
}

export function createApi(key: string): Api {
    return async <T>(path: string, init: RequestInit = {}) => {
        let response: Response;
        try {
            response = await fetch(`/api/v1${path}`, {
                ...init,
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${key}`,
                    ...init.headers,
                },
            });
        } catch {
            throw new ApiError(OFFLINE, 0);
        }
        const payload = await response.json().catch(() => null);
        if (!response.ok)
            throw new ApiError(describe(payload), response.status);
        return payload as T;
    };
}

export function uuidv7(): string {
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    const now = Date.now();
    for (let i = 0; i < 6; i++) bytes[i] = Math.floor(now / 2 ** (8 * (5 - i))) & 0xff;
    bytes[6] = (bytes[6] & 0x0f) | 0x70;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
