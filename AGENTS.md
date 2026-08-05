## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues for `cimere/spellixir`. See `docs/agents/issue-tracker.md`.

### Domain docs

This is a single-context repository using root `CONTEXT.md` and `docs/adr/`. See `docs/agents/domain.md`.

## Feature workflow

Before starting work on a feature or ticket:

1. Switch to `main` and pull the latest `main` from `origin`.
2. Create and switch to a new branch from the updated `main`.
3. Name the branch with both the ticket ID and a short feature description, using the form `issue-<ticket-id>-<feature-name>` (for example, `issue-16-core-single-line-highlighting`).

When the work is finished and verified, push the feature branch and create a pull request from that branch into `main`. Include the ticket reference in the pull request so GitHub links the work to its issue.
