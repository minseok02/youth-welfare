# Frontend

## Environment

Create `frontend/.env` from `frontend/.env.example` and set:

```bash
VITE_API_BASE_URL=
```

Leave it empty when nginx serves the frontend and proxies `/api` on the same origin.
For local development, set it to the backend origin, for example `http://localhost:8082`.
