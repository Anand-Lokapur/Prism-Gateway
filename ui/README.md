# Prism Demo UI

Root-level lightweight demo console for the Prism gateway.

## Stack
- Vite
- React
- TypeScript

## Run
1. Start the Prism gateway on `http://localhost:8080`
2. In this folder:
   - `npm install`
   - `npm run dev`

## Build
- `npm run build`

## Notes
- The UI proxies `/v1`, `/health`, and `/admin` to the backend.
- Budget is displayed from seeded demo limits plus live usage.
- If you want actual budget reset/top-up controls, add small admin mutation endpoints in the Java backend.
