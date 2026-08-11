# env-report (CXM Atelier app — pending provisioning)

A single-page app that reports live information about the environment it runs
in: runtime (Node version, uptime, memory), host/container facts (hostname, OS,
CPUs, memory, load), allowlisted platform env vars, and the *names* (never
values) of all other environment variables.

## Status

**Blocked on provisioning.** The app repo `app-env-report` in `eapps-ws-george`
does not exist yet: `provision_app` (the only sanctioned way to originate an
Atelier app) fails because the ADO identity `george.g@cxm.com` is denied
queueing the platform `new-app` pipeline with parameters:

> TF215106: Access denied. George Georgiev needs **Edit queue build
> configuration** permissions for build pipeline 2:new-app in team project
> employee-apps.

Ask `employee-apps-platform-admins` to grant that permission (or run the
provisioning for workspace `george`, app `env-report`).

## Once provisioned

Copy the author-owned files into the scaffolded `app-env-report` repo via
`ado_put_file` on `main` (this auto-deploys to the workspace sandbox preview):

- `src/server.js` — the page (matches the template's `node src/server.js`,
  port 8080, non-root container)
- `app.config.yaml` — appName/owners/class/subdomain request

Do not touch `azure-pipelines.yml` or `infra/` beyond what the platform
template allows. Going public at `env-report.atelier.cxm.com` requires the
one human go-live approval (cut a `release/*` branch).
