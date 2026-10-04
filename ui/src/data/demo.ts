export type DemoRequestProfile = {
  id: string;
  title: string;
  description: string;
  key: string;
  model: 'fast' | 'smart' | 'auto' | string;
  stream?: boolean;
  system?: string;
  user: string;
  burst?: number;
};

export type DemoTenant = {
  team: string;
  virtualKey: string;
  monthlyBudgetUsd: number;
  notes: string;
};

export const demoTenants: DemoTenant[] = [
  {
    team: 'search',
    virtualKey: 'prism-sk-search-1a2b3c',
    monthlyBudgetUsd: 50,
    notes: 'fast-only, cache enabled',
  },
  {
    team: 'research',
    virtualKey: 'prism-sk-research-4d5e6f',
    monthlyBudgetUsd: 500,
    notes: 'fast + smart + auto',
  },
  {
    team: 'free-tier',
    virtualKey: 'prism-sk-free-7g8h9i',
    monthlyBudgetUsd: 5,
    notes: 'tiny budget, good for burst / rate-limit demo',
  },
  {
    team: 'budget-demo',
    virtualKey: 'prism-sk-budget-demo-0j1k2l',
    monthlyBudgetUsd: 0.00001,
    notes: 'used to trigger budget_exceeded',
  },
];

export const demoProfiles: DemoRequestProfile[] = [
  {
    id: 'basic',
    title: 'Basic request',
    description: 'Non-streaming fast request for a normal answer.',
    key: 'prism-sk-search-1a2b3c',
    model: 'fast',
    user: 'What is a message queue and when should I use one?',
  },
  {
    id: 'stream',
    title: 'Streaming',
    description: 'Streaming response with SSE transcript.',
    key: 'prism-sk-search-1a2b3c',
    model: 'fast',
    stream: true,
    user: 'Explain how server-sent events work.',
  },
  {
    id: 'smart',
    title: 'Smart-tier',
    description: 'Longer reasoning prompt for the smart alias.',
    key: 'prism-sk-research-4d5e6f',
    model: 'smart',
    system: 'You are a senior backend engineer. Be concise.',
    user: 'Design a rate limiter for a multi-tenant API. What algorithm would you pick and why?',
  },
  {
    id: 'cache-a1',
    title: 'Cache pair A1',
    description: 'First prompt in the paraphrase pair.',
    key: 'prism-sk-search-1a2b3c',
    model: 'fast',
    user: 'How do I reset my password on the dashboard?',
  },
  {
    id: 'cache-a2',
    title: 'Cache pair A2',
    description: 'Paraphrase of A1; should hit cache.',
    key: 'prism-sk-search-1a2b3c',
    model: 'fast',
    user: 'What are the steps to reset my dashboard password?',
  },
  {
    id: 'cache-a3',
    title: 'Cache near miss',
    description: 'Similar words, different intent; should miss.',
    key: 'prism-sk-search-1a2b3c',
    model: 'fast',
    user: 'How do I reset my two-factor authentication on the dashboard?',
  },
  {
    id: 'cache-b1',
    title: 'Cache pair B1',
    description: 'Refund policy base prompt.',
    key: 'prism-sk-search-1a2b3c',
    model: 'fast',
    user: 'What is the refund policy for annual plans?',
  },
  {
    id: 'cache-b2',
    title: 'Cache pair B2',
    description: 'Refund paraphrase; should hit cache.',
    key: 'prism-sk-search-1a2b3c',
    model: 'fast',
    user: 'If I bought an annual plan, can I get my money back?',
  },
  {
    id: 'load-test',
    title: 'Load-test sample',
    description: 'Long prompt used for burst tests and token metering.',
    key: 'prism-sk-free-7g8h9i',
    model: 'fast',
    user: 'Summarize the following incident report. At 09:12 UTC the payments service began returning elevated 500s. On-call was paged at 09:15. Initial investigation pointed to a connection pool exhaustion in the primary database caused by a slow migration query deployed at 08:55. The migration was rolled back at 09:31 and error rates recovered by 09:40. Total customer impact was 28 minutes of degraded checkout, affecting an estimated 4200 sessions. Action items: add a query timeout to the migration runner, alert on connection pool saturation at 80%, and require migrations to run against a replica first. Please produce a 3-sentence executive summary and a bulleted list of action items.',
    burst: 10,
  },
  {
    id: 'no-cache',
    title: 'Time-sensitive',
    description: 'Should bypass semantic cache.',
    key: 'prism-sk-search-1a2b3c',
    model: 'fast',
    user: 'What is the current status of the payments service?',
  },
];

export const modelOptions = ['fast', 'smart', 'auto', 'openai/gpt-4o-mini', 'anthropic/claude-sonnet-4'] as const;

export const monthFormatter = new Intl.DateTimeFormat('en-US', {
  year: 'numeric',
  month: '2-digit',
});
