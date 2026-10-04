import { useEffect, useMemo, useState } from 'react';
import {
  ChatMessage,
  PrismApi,
  UsageSummary,
  CacheStats,
  ProviderHealthReport,
  RoutingEvalReport,
  RequestLogEntry,
  HealthReport,
} from './api';
import { demoProfiles, demoTenants, modelOptions, monthFormatter } from './data/demo';

type TabKey = 'overview' | 'request' | 'budget' | 'logs' | 'diagnostics';

type ChatResult = {
  status: number;
  headers: Record<string, string>;
  body: string;
};

const api = new PrismApi(import.meta.env.VITE_API_BASE_URL ?? '');

const currentMonth = () => new Date().toISOString().slice(0, 7);
const formatCurrency = (value: number) => `$${value.toFixed(6)}`;
const formatPercent = (value: number) => `${(value * 100).toFixed(1)}%`;

function parseMessages(text: string): ChatMessage[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [role, ...rest] = line.split(':');
      const content = rest.join(':').trim();
      if (!content) {
        return { role: 'user', content: line };
      }
      return { role: role.trim() || 'user', content };
    });
}

function App() {
  const [tab, setTab] = useState<TabKey>('overview');
  const [selectedKey, setSelectedKey] = useState(demoTenants[0].virtualKey);
  const [selectedModel, setSelectedModel] = useState<string>('fast');
  const [stream, setStream] = useState(false);
  const [bearerToken, setBearerToken] = useState(demoTenants[0].virtualKey);
  const [adminToken, setAdminToken] = useState(api.getAdminToken());
  const [month, setMonth] = useState(currentMonth());
  const [requestTitle, setRequestTitle] = useState(demoProfiles[0].title);
  const [systemPrompt, setSystemPrompt] = useState('');
  const [userPrompt, setUserPrompt] = useState(demoProfiles[0].user);
  const [burstCount, setBurstCount] = useState(5);
  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState<ChatResult | null>(null);
  const [streamChunks, setStreamChunks] = useState<string[]>([]);
  const [streamError, setStreamError] = useState('');
  const [health, setHealth] = useState<HealthReport | null>(null);
  const [cacheStats, setCacheStats] = useState<CacheStats | null>(null);
  const [usageAll, setUsageAll] = useState<UsageSummary[]>([]);
  const [logs, setLogs] = useState<RequestLogEntry[]>([]);
  const [providers, setProviders] = useState<ProviderHealthReport | null>(null);
  const [routingEval, setRoutingEval] = useState<RoutingEvalReport | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [adminStatus, setAdminStatus] = useState('');

  useEffect(() => {
    api.setAdminToken(adminToken);
  }, [adminToken]);

  useEffect(() => {
    void refreshDashboard();
  }, [month, adminToken, selectedKey]);

  useEffect(() => {
    const demo = demoProfiles.find((profile) => profile.title === requestTitle);
    if (demo) {
      setBearerToken(demo.key);
      setSelectedKey(demo.key);
      setSelectedModel(demo.model);
      setStream(Boolean(demo.stream));
      setSystemPrompt(demo.system ?? '');
      setUserPrompt(demo.user);
      setBurstCount(demo.burst ?? 5);
    }
  }, [requestTitle]);

  const selectedTenant = useMemo(
    () => demoTenants.find((tenant) => tenant.virtualKey === selectedKey) ?? demoTenants[0],
    [selectedKey],
  );

  const usageRow = useMemo(
    () => usageAll.find((row) => row.key === selectedKey),
    [usageAll, selectedKey],
  );

  const remainingBudget = useMemo(() => {
    const spent = usageRow?.costUsd ?? 0;
    return Math.max(selectedTenant.monthlyBudgetUsd - spent, 0);
  }, [selectedTenant.monthlyBudgetUsd, usageRow?.costUsd]);

  async function refreshDashboard() {
    setErrorMessage('');
    try {
      const [healthData, cacheData, usageData, logsData, providerData, routingData] = await Promise.all([
        api.health(),
        api.cacheStats(),
        api.usageAll(month),
        api.logs(selectedKey, 25),
        api.providersHealth(),
        api.routingEval(),
      ]);
      setHealth(healthData);
      setCacheStats(cacheData);
      setUsageAll(usageData);
      setLogs(logsData);
      setProviders(providerData);
      setRoutingEval(routingData);
      setAdminStatus('Dashboard refreshed');
    } catch (error) {
      setAdminStatus('');
      setErrorMessage(error instanceof Error ? error.message : 'Failed to refresh dashboard');
    }
  }

  async function sendRequest() {
    setLoading(true);
    setResponse(null);
    setStreamChunks([]);
    setStreamError('');
    setErrorMessage('');

    const messages: ChatMessage[] = [];
    if (systemPrompt.trim()) {
      messages.push({ role: 'system', content: systemPrompt.trim() });
    }
    messages.push({ role: 'user', content: userPrompt.trim() });

    try {
      if (stream) {
        const responseStub = await api.chat({ model: selectedModel, messages, stream: true }, bearerToken);
        const headers = Object.fromEntries(responseStub.headers.entries());
        if (!responseStub.ok || !responseStub.body) {
          const body = await responseStub.text();
          setResponse({ status: responseStub.status, headers, body });
          return;
        }
        const reader = responseStub.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let done = false;
        while (!done) {
          const result = await reader.read();
          done = Boolean(result.done);
          if (result.value) {
            buffer += decoder.decode(result.value, { stream: true });
            const lines = buffer.split(/\r?\n/);
            buffer = lines.pop() ?? '';
            for (const line of lines) {
              if (!line.startsWith('data: ')) {
                continue;
              }
              const payload = line.slice(6);
              setStreamChunks((prev) => [...prev, payload]);
            }
          }
        }
        setResponse({ status: responseStub.status, headers, body: '[stream captured live]' });
      } else {
        const result = await api.postChat({ model: selectedModel, messages }, bearerToken);
        const headers = Object.fromEntries(result.response.headers.entries());
        setResponse({ status: result.response.status, headers, body: result.bodyText });
      }
      await refreshDashboard();
    } catch (error) {
      if (error instanceof Error) {
        setStreamError(error.message);
        setErrorMessage(error.message);
      } else {
        setStreamError('Request failed');
      }
    } finally {
      setLoading(false);
    }
  }

  async function runBurst() {
    const messages: ChatMessage[] = [];
    if (systemPrompt.trim()) {
      messages.push({ role: 'system', content: systemPrompt.trim() });
    }
    messages.push({ role: 'user', content: userPrompt.trim() });

    setLoading(true);
    setErrorMessage('');
    try {
      const tasks = Array.from({ length: burstCount }, () => api.postChat({ model: selectedModel, messages }, bearerToken));
      const results = await Promise.allSettled(tasks);
      const accepted = results.filter((item) => item.status === 'fulfilled' && item.value.response.ok).length;
      const rejected = results.length - accepted;
      setResponse({
        status: 200,
        headers: {},
        body: JSON.stringify({ accepted, rejected, burstCount }, null, 2),
      });
      await refreshDashboard();
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Burst failed');
    } finally {
      setLoading(false);
    }
  }

  const bodyPreview = useMemo(() => {
    const messages: ChatMessage[] = [];
    if (systemPrompt.trim()) {
      messages.push({ role: 'system', content: systemPrompt.trim() });
    }
    messages.push({ role: 'user', content: userPrompt.trim() });
    return JSON.stringify({ model: selectedModel, stream, messages }, null, 2);
  }, [selectedModel, stream, systemPrompt, userPrompt]);

  return (
    <div className="app-shell">
      <header className="hero">
        <div>
          <p className="eyebrow">Prism demo console</p>
          <h1>Gateway lab, budget view, usage, and diagnostics</h1>
          <p className="hero-copy">
            A lightweight root-level UI for showing, testing, and demonstrating the gateway.
            It reads live backend state and can replay the sample prompts and load bursts.
          </p>
        </div>
        {/* <div className="hero-card">
          <label className="field">
            <span>Admin token</span>
            <input value={adminToken} onChange={(e) => setAdminToken(e.target.value)} placeholder="prism-admin-demo-token" />
          </label>
          <label className="field">
            <span>Bearer token</span>
            <input value={bearerToken} onChange={(e) => setBearerToken(e.target.value)} placeholder="prism-sk-search-1a2b3c" />
          </label>
          <button className="secondary" onClick={() => void refreshDashboard()}>
            Refresh dashboard
          </button>
          <div className="small-note">Proxy: frontend at /ui, backend at localhost:8080</div>
        </div> */}
      </header>

      <nav className="tabs">
        {[
          ['overview', 'Overview'],
          ['request', 'Request Lab'],
          ['budget', 'Budget'],
          ['logs', 'Usage & Logs'],
          ['diagnostics', 'Diagnostics'],
        ].map(([key, label]) => (
          <button key={key} className={tab === key ? 'tab active' : 'tab'} onClick={() => setTab(key as TabKey)}>
            {label}
          </button>
        ))}
      </nav>

      {errorMessage ? <div className="banner error">{errorMessage}</div> : null}
      {adminStatus ? <div className="banner success">{adminStatus}</div> : null}

      {tab === 'overview' ? (
        <section className="grid cards">
          <Card title="Gateway health" value={health?.status ?? '—'} detail={`Uptime ${health?.uptime_seconds ?? 0}s`} />
          <Card title="Cache hit rate" value={cacheStats ? formatPercent(cacheStats.hitRate) : '—'} detail={`${cacheStats?.cacheHits ?? 0} hits / ${cacheStats?.totalRequests ?? 0} requests`} />
          <Card title="Selected budget" value={formatCurrency(selectedTenant.monthlyBudgetUsd)} detail={`${selectedTenant.team} · remaining ${formatCurrency(remainingBudget)}`} />
          <Card title="Active key" value={selectedTenant.virtualKey} detail={selectedTenant.notes} />
          <Card title="Providers" value={providers?.providers.length.toString() ?? '0'} detail={providers?.providers.map((p) => `${p.provider}:${p.status}`).join(' · ') ?? '—'} />
          <Card title="Month" value={month} detail="Controls usage / log views" />
        </section>
      ) : null}

      {tab === 'request' ? (
        <section className="panel-grid">
          <div className="panel">
            <div className="panel-header">
              <div>
                <h2>Request Lab</h2>
                <p>Send a request, inspect headers, or run a burst.</p>
              </div>
              <div className="actions-row">
                <button className="secondary" onClick={() => void sendRequest()} disabled={loading}>
                  {loading ? 'Sending…' : 'Send request'}
                </button>
                <button className="secondary" onClick={() => void runBurst()} disabled={loading}>
                  Run burst × {burstCount}
                </button>
              </div>
            </div>

            <div className="form-grid">
              <label className="field">
                <span>Demo request</span>
                <select value={requestTitle} onChange={(e) => setRequestTitle(e.target.value)}>
                  {demoProfiles.map((profile) => (
                    <option key={profile.id} value={profile.title}>
                      {profile.title}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Virtual key</span>
                <select value={selectedKey} onChange={(e) => setSelectedKey(e.target.value)}>
                  {demoTenants.map((tenant) => (
                    <option key={tenant.virtualKey} value={tenant.virtualKey}>
                      {tenant.team} · {tenant.virtualKey}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Model</span>
                <select value={selectedModel} onChange={(e) => setSelectedModel(e.target.value)}>
                  {modelOptions.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field inline">
                <span>Stream</span>
                <input type="checkbox" checked={stream} onChange={(e) => setStream(e.target.checked)} />
              </label>
            </div>

            <label className="field">
              <span>System prompt</span>
              <textarea value={systemPrompt} onChange={(e) => setSystemPrompt(e.target.value)} rows={4} placeholder="Optional system message" />
            </label>
            <label className="field">
              <span>User prompt</span>
              <textarea value={userPrompt} onChange={(e) => setUserPrompt(e.target.value)} rows={7} placeholder="Ask something" />
            </label>
            <div className="chips">
              {demoProfiles.slice(0, 8).map((profile) => (
                <button
                  key={profile.id}
                  className="chip"
                  onClick={() => {
                    setRequestTitle(profile.title);
                    setSelectedKey(profile.key);
                    setSelectedModel(profile.model);
                    setStream(Boolean(profile.stream));
                    setSystemPrompt(profile.system ?? '');
                    setUserPrompt(profile.user);
                    setBurstCount(profile.burst ?? burstCount);
                  }}
                >
                  {profile.title}
                </button>
              ))}
            </div>
            <label className="field">
              <span>Burst count</span>
              <input type="number" min={1} max={30} value={burstCount} onChange={(e) => setBurstCount(Number(e.target.value))} />
            </label>
            <label className="field">
              <span>Body preview</span>
              <textarea value={bodyPreview} readOnly rows={10} />
            </label>
          </div>

          <div className="panel">
            <h3>Response</h3>
            <div className="response-meta">
              <span>Status: {response?.status ?? '—'}</span>
              <span>Budget cap: {formatCurrency(selectedTenant.monthlyBudgetUsd)}</span>
              <span>Remaining: {formatCurrency(remainingBudget)}</span>
            </div>
            <label className="field">
              <span>Stream chunks</span>
              <textarea value={streamChunks.join('')} readOnly rows={10} placeholder="Stream output appears here" />
            </label>
            <label className="field">
              <span>Raw response</span>
              <textarea value={response?.body ?? ''} readOnly rows={12} placeholder="Response body appears here" />
            </label>
            <label className="field">
              <span>Headers</span>
              <pre className="json-box">{response ? JSON.stringify(response.headers, null, 2) : '{}'}</pre>
            </label>
            {streamError ? <div className="banner error">{streamError}</div> : null}
          </div>
        </section>
      ) : null}

      {tab === 'budget' ? (
        <section className="panel-grid two-up">
          <div className="panel">
            <h2>Budget by key</h2>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Team</th>
                    <th>Key</th>
                    <th>Cap</th>
                    <th>Spend</th>
                    <th>Remaining</th>
                  </tr>
                </thead>
                <tbody>
                  {demoTenants.map((tenant) => {
                    const row = usageAll.find((usage) => usage.key === tenant.virtualKey);
                    const spent = row?.costUsd ?? 0;
                    const remaining = Math.max(tenant.monthlyBudgetUsd - spent, 0);
                    return (
                      <tr key={tenant.virtualKey}>
                        <td>{tenant.team}</td>
                        <td>{tenant.virtualKey}</td>
                        <td>{formatCurrency(tenant.monthlyBudgetUsd)}</td>
                        <td>{formatCurrency(spent)}</td>
                        <td>{formatCurrency(remaining)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
          <div className="panel">
            <h2>Current key</h2>
            <div className="stack">
              <Card title="Selected key" value={selectedTenant.team} detail={selectedTenant.virtualKey} />
              <Card title="Configured cap" value={formatCurrency(selectedTenant.monthlyBudgetUsd)} detail={selectedTenant.notes} />
              <Card title="Month spend" value={formatCurrency(usageRow?.costUsd ?? 0)} detail={`${usageRow?.requests ?? 0} requests, ${usageRow?.cacheHits ?? 0} cache hits`} />
              <Card title="Remaining" value={formatCurrency(remainingBudget)} detail="Derived from live usage" />
            </div>
            <p className="small-note">Budget is currently displayed from the seeded limits and live usage; if you want real top-up/reset controls, add a tiny admin mutation endpoint later.</p>
          </div>
        </section>
      ) : null}

      {tab === 'logs' ? (
        <section className="panel-grid two-up">
          <div className="panel">
            <div className="panel-header">
              <div>
                <h2>Usage</h2>
                <p>Month {monthFormatter.format(new Date(`${month}-01`))}</p>
              </div>
              <label className="field compact">
                <span>Month</span>
                <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} />
              </label>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Key</th>
                    <th>Requests</th>
                    <th>Prompt</th>
                    <th>Completion</th>
                    <th>Cost</th>
                    <th>Cache hits</th>
                  </tr>
                </thead>
                <tbody>
                  {usageAll.map((row) => (
                    <tr key={row.key}>
                      <td>{row.key}</td>
                      <td>{row.requests}</td>
                      <td>{row.promptTokens}</td>
                      <td>{row.completionTokens}</td>
                      <td>{formatCurrency(row.costUsd)}</td>
                      <td>{row.cacheHits}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <div className="panel">
            <h2>Recent logs</h2>
            <div className="table-wrap scroll">
              <table>
                <thead>
                  <tr>
                    <th>Time</th>
                    <th>Key</th>
                    <th>Model</th>
                    <th>Status</th>
                    <th>Cache</th>
                    <th>Cost</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((entry) => (
                    <tr key={entry.requestId}>
                      <td>{new Date(entry.createdAt).toLocaleString()}</td>
                      <td>{entry.virtualKey}</td>
                      <td>{entry.requestedModel}</td>
                      <td>{entry.status}</td>
                      <td>{entry.cache}</td>
                      <td>{formatCurrency(entry.costUsd)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </section>
      ) : null}

      {tab === 'diagnostics' ? (
        <section className="panel-grid two-up">
          <div className="panel">
            <h2>Provider health</h2>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Provider</th>
                    <th>Status</th>
                    <th>Requests</th>
                    <th>Errors</th>
                    <th>Error rate</th>
                  </tr>
                </thead>
                <tbody>
                  {providers?.providers.map((provider) => (
                    <tr key={provider.provider}>
                      <td>{provider.provider}</td>
                      <td>{provider.status}</td>
                      <td>{provider.requests}</td>
                      <td>{provider.errors}</td>
                      <td>{formatPercent(provider.errorRate)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <div className="panel">
            <h2>Routing eval</h2>
            <div className="stack">
              <Card title="Accuracy" value={routingEval ? formatPercent(routingEval.accuracy) : '—'} detail={`${routingEval?.correct ?? 0} / ${routingEval?.total ?? 0} correct`} />
              <div className="table-wrap scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Case</th>
                      <th>Expected</th>
                      <th>Actual</th>
                    </tr>
                  </thead>
                  <tbody>
                    {routingEval?.cases.slice(0, 12).map((item) => (
                      <tr key={item.id}>
                        <td>{item.id}</td>
                        <td>{item.expected}</td>
                        <td>{item.actual}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </section>
      ) : null}
    </div>
  );
}

function Card({ title, value, detail }: { title: string; value: string; detail?: string }) {
  return (
    <div className="card">
      <div className="card-title">{title}</div>
      <div className="card-value">{value}</div>
      {detail ? <div className="card-detail">{detail}</div> : null}
    </div>
  );
}

export default App;
