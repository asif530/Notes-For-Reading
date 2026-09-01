KV Secrets Engine

It's one of Vault's secrets engines — pluggable backends that determine how and where Vault stores/generates secrets. The KV (Key-Value) engine is the simplest one: it    
just stores arbitrary key-value pairs as static secrets, similar to a secured, versioned dictionary/config store. Contrast with dynamic engines (e.g. the database engine,
which generates short-lived DB credentials on demand) — KV doesn't generate anything, it just stores what you put in it.

Two versions:
- KV v1 — simple, unversioned. Writing overwrites the previous value permanently.
- KV v2 — adds versioning (each write creates a new version, old ones retained and recoverable), soft-delete/undelete, and metadata (like TTLs on versions). This is the   
  default/recommended one today.                                                                                                                                             

How it fits the command you asked about:                                                                                                                                   
vault kv get -mount="x" "y"
1. You first enable the engine at a mount path: vault secrets enable -path=secret kv-v2 → this is the x.
2. You write a secret into it: vault kv put -mount=secret myapp/config username=admin password=hunter2 → myapp/config is the y, username/password are the actual key-value
   pairs stored.
3. vault kv get -mount=secret myapp/config then reads that whole key-value blob back out.

Under the hood (KV v2 specifically): the CLI path myapp/config is a friendly alias — Vault actually stores it at the API path secret/data/myapp/config, with a parallel    
secret/metadata/myapp/config holding version history. The CLI hides this translation from you; raw curl/API calls against Vault do need the data/ segment explicit.

Why it matters practically: anyone can enable multiple KV mounts (e.g. secret/ for app configs, ci-secrets/ for pipeline tokens) with different access policies attached to
each mount path — that's the main reason -mount is a separate flag from the secret path itself: it's the access-control/organizational boundary, not just a namespace.  

#  vault kv get -mount="x" "y"
In vault kv get -mount="x" "y":

- x — the mount path of the KV secrets engine, i.e. where it was enabled with vault secrets enable -path=<x> kv-v2. Default is often secret, but any org-chosen name works
  (e.g. kv, myapp-kv).
- y — the secret's path within that mount, i.e. where the actual key/value data lives (e.g. myapp/config, database/creds).

Example:                                                                                                                                                                   
vault kv get -mount="secret" "myapp/config"                                                                                                                                
reads the secret stored at myapp/config inside the secret/ KV mount (internally, for KV v2, this maps to secret/data/myapp/config).

You can list what's available with vault secrets list (for mount names) and vault kv list -mount="x" "" (for secret paths under that mount).    