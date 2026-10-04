export type ChatMessage = { role: string; content: string };

export type ChatCompletionRequest = {
  model: string;
  messages: ChatMessage[];
  stream?: boolean;
};

export type UsageSummary = {
  key: string;
  month: string;
  requests: number;
  promptTokens: number;
  completionTokens: number;
  costUsd: number;
  cacheHits: number;
};

export type CacheStats = {
  totalRequests: number;
  cacheHits: number;
  cacheMisses: number;
  hitRate: number;
  byKey: Record<string, unknown>;
};

export type RequestLogEntry = {
  requestId: string;
  virtualKey: string;
  requestedModel: string;
  resolvedProvider: string;
  resolvedModel: string;
  status: string;
  promptTokens: number;
  completionTokens: number;
  costUsd: number;
  cache: string;
  fallback: boolean;
  routeReason: string;
  retries: number;
  latencyMs: number;
  createdAt: string;
};

export type ProviderHealth = {
  provider: string;  // backend field name
  name?: string;     // alias for compatibility
  status: string;
  requests: number;
  errors: number;
  errorRate: number;
};

export type ProviderHealthReport = {
  providers: ProviderHealth[];
};

export type RoutingEvalCase = {
  id: string;
  expected: string;
  actual: string;
  reason: string;
};

export type RoutingEvalReport = {
  total: number;
  correct: number;
  accuracy: number;
  cases: RoutingEvalCase[];
};

export type HealthReport = {
  status: string;
  uptime_seconds: number;
  providers: ProviderHealth[];
};

export class PrismApi {
  private adminToken = 'prism-admin-demo-token';
  constructor(private readonly baseUrl = '') {}

  setAdminToken(token: string) {
    this.adminToken = token.trim();
  }

  getAdminToken() {
    return this.adminToken;
  }

  async health(): Promise<HealthReport> {
    return this.getJson('/health');
  }

  async cacheStats(): Promise<CacheStats> {
    return this.getAdminJson('/admin/cache/stats');
  }

  async usageAll(month: string): Promise<UsageSummary[]> {
    return this.getAdminJson(`/admin/usage/all?month=${encodeURIComponent(month)}`);
  }

  async usage(key: string, month: string): Promise<UsageSummary> {
    return this.getAdminJson(`/admin/usage?key=${encodeURIComponent(key)}&month=${encodeURIComponent(month)}`);
  }

  async logs(key: string, limit = 25): Promise<RequestLogEntry[]> {
    const query = key ? `?key=${encodeURIComponent(key)}&limit=${limit}` : `?limit=${limit}`;
    return this.getAdminJson(`/admin/logs${query}`);
  }

  async providersHealth(): Promise<ProviderHealthReport> {
    return this.getAdminJson('/admin/providers/health');
  }

  async routingEval(): Promise<RoutingEvalReport> {
    return this.getAdminJson('/admin/routing/eval');
  }

  async chat(request: ChatCompletionRequest, bearerToken?: string, signal?: AbortSignal): Promise<Response> {
    return fetch(this.url('/v1/chat/completions'), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(bearerToken ? { Authorization: `Bearer ${bearerToken}` } : {}),
      },
      body: JSON.stringify(request),
      signal,
    });
  }

  async streamChat(request: ChatCompletionRequest, onChunk: (chunk: string) => void, bearerToken?: string, signal?: AbortSignal) {
    const response = await this.chat({ ...request, stream: true }, bearerToken, signal);
    if (!response.ok || !response.body) {
      throw new Error(`Request failed with ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? '';
      for (const line of lines) {
        if (!line.startsWith('data: ')) {
          continue;
        }
        const payload = line.slice(6);
        onChunk(payload);
      }
    }
  }

  async postChat(request: ChatCompletionRequest, bearerToken?: string, signal?: AbortSignal): Promise<{ response: Response; bodyText: string }> {
    const response = await this.chat(request, bearerToken, signal);
    const bodyText = await response.text();
    return { response, bodyText };
  }

  private async getJson<T>(path: string): Promise<T> {
    const response = await fetch(this.url(path));
    if (!response.ok) {
      throw new Error(`GET ${path} failed with ${response.status}`);
    }
    return response.json() as Promise<T>;
  }

  private async getAdminJson<T>(path: string): Promise<T> {
    const response = await fetch(this.url(path), {
      headers: {
        'X-Admin-Token': this.adminToken,
      },
    });
    if (!response.ok) {
      throw new Error(`GET ${path} failed with ${response.status}`);
    }
    return response.json() as Promise<T>;
  }

  private url(path: string) {
    const base = this.baseUrl.endsWith('/') ? this.baseUrl.slice(0, -1) : this.baseUrl;
    return `${base}${path}`;
  }
}
