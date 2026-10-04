# Prism Java Gateway

Prism is an OpenAI-compatible gateway that routes requests to two mock providers while enforcing virtual-key authentication, model allowlists, rate limits, budgets, caching, and cost metering.

## Run locally

Prerequisites: Java 21, Maven, and Python 3.9+.

Start each mock provider from `../prism-capstone` in separate terminals:

```text
python scripts/mock_provider.py --port 9001 --name alpha
python scripts/mock_provider.py --port 9002 --name beta
```

Then run the gateway from this directory:

```text
mvn spring-boot:run
```

The seeded data is read from `../prism-capstone/data`. Copy `.env.example` to `.env` and set `PRISM_ADMIN_TOKEN` if you do not want the demo default.

## Optional OpenRouter mode

If `OPENROUTER_API_KEY` is set, Prism switches the `fast` and `smart` aliases to real OpenRouter models, keeps the same OpenAI-compatible contract, and sends the standard OpenRouter request headers. The default model slots are configured through `OPENROUTER_MODEL` and `OPENROUTER_SMART_MODEL`; if they are blank, Prism falls back to `openai/gpt-4o-mini` and `anthropic/claude-sonnet-4`.

## Demo request

```text
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer prism-sk-search-1a2b3c" \
  -H "Content-Type: application/json" \
  -d '{"model":"fast","messages":[{"role":"user","content":"How do I reset my password on the dashboard?"}]}'
```

Responses include `x-prism-provider`, `x-prism-cache`, `x-prism-fallback`, and `x-prism-cost-usd`. Streaming responses use OpenAI-style `data: <payload>` frames and end with `data: [DONE]`.

## Admin and verification

- Console: `http://localhost:8080/admin/ui?token=prism-admin-demo-token`
- Usage by key: `GET /admin/usage?key=<key>&month=YYYY-MM` with `X-Admin-Token` (the month defaults to the current calendar month)
- Usage for every seeded key: `GET /admin/usage/all` with `X-Admin-Token`
- Logs: `GET /admin/logs?limit=25` with `X-Admin-Token`
- Cache stats: `GET /admin/cache/stats` with `X-Admin-Token`
- Routing evaluation: `GET /admin/routing/eval` with `X-Admin-Token`

Run the pack checks from `../prism-capstone` after the gateway is running:

```text
python scripts/smoke_test.py --url http://localhost:8080 --key prism-sk-search-1a2b3c --model fast
python scripts/load_test.py --url http://localhost:8080 --key prism-sk-free-7g8h9i --model fast --requests 30 --concurrency 10 --rpm-limit 10
```

## Design decisions

- Cache entries are scoped to virtual keys and model routes. The local cache normalizer handles the supplied product-support paraphrases; it deliberately excludes time-sensitive wording such as “current status”, “today”, and “latest”.
- `auto` uses transparent keyword-based difficulty signals and records its reason with each request. Its routing evaluation endpoint returns per-case expected and actual tiers.
- Non-streaming calls retry only transient upstream failures and then use the configured fallback model. Failed streaming calls only fail over before any bytes have been sent; Prism never splices a second provider into a partially delivered stream.
- The budget gate keeps one request per key in flight while provider usage is unresolved. This prevents concurrent zero-estimate admissions; successful responses are charged from provider-reported token counts.
- Usage is stored as calendar-month aggregates, so the admin usage API intentionally uses a `month=YYYY-MM` selector rather than arbitrary date ranges.

## Current limitations

State is stored locally in JSON files under `./prism-state` by default, so it survives restarts for a single-instance deployment. The project still needs recorded smoke/load results for final submission; Postgres is a future production-scale replacement.
