## Why

The backend only runs on a developer machine (`docker-compose.yml` starts MySQL and Qdrant; the backend service is commented out). To show the prototype to hiring managers it must run on AWS at near-zero cost, reachable only through CloudFront, with secrets outside the image and logs that do not fill a small disk. The chosen host is a single small EC2 instance with a stable Elastic IP, because an ALB (about $16/month, assumption to verify) or a per-start IP change on Fargate would cost more or need extra moving parts. The instance is stopped most of the time (see `aws-control-plane`), so everything it needs must survive stop/start.

## What Changes

- Provision one EC2 `t4g.micro` (Graviton, 1 GB RAM, assumption: cheapest viable size, verify free-tier/credit eligibility) with an 8 GB gp3 volume, a 1 GB swap file, IMDSv2 only, and an Elastic IP.
- Security group: inbound TCP 8080 only from the CloudFront origin-facing managed prefix list; no SSH. Administration through SSM Session Manager.
- Run the backend as a container pulled from a private ECR repository (arm64 image), started by systemd at boot, with JVM flags sized for 1 GB (`-Xmx` about 384 MB).
- Secrets (OpenAI key, Qdrant Cloud key and URL, origin-verification secret) live in SSM Parameter Store SecureString parameters and are read at container start; none are baked into the image or the user data.
- Add an `aws` Spring profile: INFO logging (the default profile logs `org.springframework.ai` and the prompt advisor at DEBUG, which writes customer prompts to disk), no SQL logging, Docker log rotation.
- Add a backend `OriginVerifyFilter`, enabled only when `ECOM_ORIGIN_VERIFY_SECRET` is set, rejecting requests without the matching `X-Origin-Verify` header.
- Fix the image build for the target: `Dockerfile` uses `openjdk:17` (deprecated image, build inside the image, x86 default), so build a multi-stage arm64 image on a supported JRE base.
- Document a deployment runbook and a cost check.

## Capabilities

### New Capabilities
- `backend-host`: the EC2 host, its network exposure, secrets handling, process supervision, logging policy and origin verification.

### Modified Capabilities
<!-- none: no existing infra spec exists; backend request behavior is unchanged -->

## Impact

- New infrastructure code (CDK app under `infra/`, see design Open Questions), new `Dockerfile` stage layout, `application-aws.yml`, `OriginVerifyFilter` in `eas-backend`.
- Depends on: `remove-mysql` (no database on the host), `qdrant-cloud-config` (remote Qdrant with TLS and key), `api-versioned-search-path` (`/api/v1/health` readiness), `aws-edge-hosting` (CloudFront prefix-list and secret header), `aws-control-plane` (start/stop of this instance).
- Cost: instance, volume and Elastic IP are the only always-on lines (assumption about $4-6/month while stopped most of the time; verify).
