## Context

Current state of the repo:
- `eas-backend/Dockerfile`: builds with `./gradlew build -x test` inside an `openjdk:17-jdk-slim` image and runs the jar with the same JDK image. The `openjdk` Docker images are deprecated, the image carries a full JDK, and nothing selects an architecture.
- `application.yml` sets `logging.level.org.springframework.ai: DEBUG`, `com.ecomassistant: DEBUG`, `spring.jpa.show-sql: true`; `AiConfig` registers `SimpleLoggerAdvisor`, which logs full request/response objects. On a public host this writes customer prompts to disk.
- `docker-compose.yml` starts MySQL and Qdrant for local development only. After `remove-mysql` and `qdrant-cloud-config`, the host needs only the application container.
- `SecurityConfig` permits everything, so the network path is the only protection of the origin.

All pricing and free-tier statements below are assumptions to verify in the AWS pricing calculator before the first deploy.

## Goals / Non-Goals

**Goals:**
- One reproducible EC2 host that runs the backend container unattended after every start.
- Origin reachable only through the project's own CloudFront distribution.
- No secret in the image, the user data, the repository or the logs.
- Disk and memory behavior that survives a 1 GB instance.

**Non-Goals:**
- High availability, autoscaling, blue/green deploys (single instance, a restart on deploy is acceptable).
- Qdrant on the host (free Qdrant Cloud tier is used).
- Starting/stopping the instance (`aws-control-plane`) and CloudFront/S3 (`aws-edge-hosting`).
- HTTPS to the origin (no domain yet; see `aws-edge-hosting` for the compensating controls).

## Decisions

**1. EC2 `t4g.micro` with Elastic IP.**
Graviton is cheaper than x86 at the same size, the image is built natively on Apple Silicon, and the JVM supports arm64. 1 GB is enough for a heap of about 384 MB plus metaspace and the OS only because Qdrant is remote. The Elastic IP gives CloudFront a stable origin name across stop/start.
- Alternative: `t3.micro` x86 (slightly more expensive, same RAM). Fallback if the app does not fit: `t4g.small` (2 GB).
- Alternative: Fargate (new public IP per task, so an ALB or DNS updates would be needed for a stable origin).

**2. Container from ECR, supervised by systemd.**
A private ECR repository holds the arm64 image (lifecycle policy: keep the last 3 images). A systemd unit runs `docker run` after `docker.service` and `network-online.target`, restarting on failure. Deploy = push a new tag, then an SSM Run Command pulls it and restarts the unit.
- Alternative: copy the jar and run it directly (no image to maintain, but no portable artifact and no reuse of the existing `Dockerfile`).
- Alternative: public registry (zero cost, but images of a private prototype become public).

**3. Multi-stage `Dockerfile` on a maintained base.**
Build stage: a JDK 17 image (Eclipse Temurin); runtime stage: a JRE-only Temurin image, non-root user, `JAVA_TOOL_OPTIONS` supplied by the unit (`-Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m`). The build runs in CI/on the developer machine with `docker buildx --platform linux/arm64`, not on the instance.

**4. Secrets in SSM Parameter Store SecureString.**
Parameters under `/ecom/prod/`: `openai-api-key`, `qdrant-api-key`, `qdrant-host`, `origin-verify-secret`. The instance role may call `ssm:GetParameters` on that path only. A small start script (`ExecStartPre`) reads them into the container environment (`OPEN_AI_TOKEN`, `QDRANT_HOST`, `QDRANT_API_KEY`, `QDRANT_USE_TLS=true`, `ECOM_ORIGIN_VERIFY_SECRET`). Standard-tier parameters are assumed free; verify.
- Alternative: Secrets Manager (a per-secret monthly fee, which is not justified for four values).

**5. `aws` Spring profile and log hygiene.**
`application-aws.yml` sets root and `com.ecomassistant` to INFO, `org.springframework.ai` to INFO, `spring.jpa`/SQL settings are gone with `remove-mysql`, and disables `SimpleLoggerAdvisor` output (the advisor bean is created only when the `aws` profile is not active, or its log level is raised). Docker uses the `local` or `journald` driver with a size cap (for example 10 MB x 3 files) so the 8 GB volume cannot fill up.

**6. `OriginVerifyFilter`.**
A servlet filter registered only when `ecom.origin-verify.secret` is non-empty. It compares the `X-Origin-Verify` header in constant time and answers 403 with an empty body otherwise. CloudFront adds the header as an origin custom header; the control-plane Lambda sends it on its health probe. This closes the gap that the CloudFront prefix list admits traffic from any CloudFront distribution, not only ours.
- Local development: the property is unset, so the filter is not registered.

**7. Operations without SSH.**
SSM Session Manager (instance role `AmazonSSMManagedInstanceCore`) replaces SSH; port 22 is never opened. The instance has a public IPv4 (the Elastic IP) and reaches SSM, ECR, SSM Parameter Store, OpenAI and Qdrant Cloud over the internet, so no NAT gateway is needed.

**8. Readiness before traffic.**
The unit reports healthy only when `GET /api/v1/health` returns 200 (readiness after the catalog import). Nothing on the host depends on that for routing, but the control-plane Lambda and the SPA do.

## Risks / Trade-offs

- [1 GB RAM is tight for a JVM] → Heap capped at about 384 MB, SerialGC, 1 GB swap on EBS, `t4g.small` as the documented fallback; check RSS after a search load test.
- [Public Elastic IP reachable by any CloudFront distribution] → Security group prefix list plus secret header filter (decision 6).
- [Plain HTTP between CloudFront and the origin] → Traffic carries product search text only, no accounts or payments; accepted for the prototype and fixed by the optional domain/HTTPS section in `aws-edge-hosting`.
- [Secrets visible in `docker inspect` or process environment on the host] → Access to the host is through SSM only, limited to the owner's IAM identity; no secrets in logs.
- [Elastic IP is billed while the instance is stopped] → Assumed about $3.65/month (public IPv4 pricing, verify); accepted as the price of a stable origin.
- [Free-tier or credits may not cover t4g.micro] → Cost check task after the first week.

## Migration Plan

1. Land the backend changes (`remove-mysql`, `qdrant-cloud-config`, `api-versioned-search-path`, then the `aws` profile and `OriginVerifyFilter`).
2. Deploy the stack, create the SSM parameters, push the first image, start the instance manually and verify `/api/v1/health` from the instance (Session Manager) and through CloudFront.
3. Hand start/stop over to `aws-control-plane`.
4. Rollback: stop the instance and redeploy the previous image tag; the host holds no state.

## Open Questions

- IaC tool: AWS CDK (TypeScript) is the default in this plan; Terraform is the alternative. Not decided by the owner.
- Region (affects prices and where the CloudFront certificate must live if a domain is added).
- Whether `t4g.micro` is acceptable after a load test, or `t4g.small` is needed.
- Custom domain: decided later; this change must work without it.
