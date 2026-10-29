## 1. Backend preparation

- [ ] 1.1 Add `application-aws.yml` (INFO logging for root, `com.ecomassistant` and `org.springframework.ai`; Actuator exposure limited to health) and make the `SimpleLoggerAdvisor` bean conditional on the profile so no prompts are logged under `aws`
- [ ] 1.2 Add `ecom.origin-verify.secret` property (env `ECOM_ORIGIN_VERIFY_SECRET`) and a conditional `OriginVerifyFilter` (constant-time compare, 403 empty body)
- [ ] 1.3 Tests: filter allows the correct header, rejects a missing/wrong header, is not registered without the secret; log test that the `aws` profile emits no message text
- [ ] 1.4 Rewrite `eas-backend/Dockerfile` as multi-stage (Temurin 17 JDK build stage, Temurin 17 JRE runtime stage, non-root user, `-x test` build), build with `docker buildx --platform linux/arm64`, and verify the image starts locally against local Qdrant

## 2. Infrastructure definition

- [ ] 2.1 Create the IaC project under `infra/` (tool per design Open Questions) with a stack for the host: VPC use (default VPC is acceptable), security group (8080 from the CloudFront origin-facing prefix list only), Elastic IP, `t4g.micro` instance, 8 GB gp3 volume, IMDSv2 required
- [ ] 2.2 Instance role: `AmazonSSMManagedInstanceCore`, ECR pull, `ssm:GetParameters` on `/ecom/prod/*` only; create the SecureString parameters (values supplied out of band, never committed)
- [ ] 2.3 ECR repository with lifecycle policy (keep last 3 images)
- [ ] 2.4 Instance bootstrap (user data): install Docker, create 1 GB swap file, configure the Docker log driver with a size cap, install the systemd unit and the `ExecStartPre` script that reads the parameters into the container environment, with JVM flags `-Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m`
- [ ] 2.5 Export the Elastic IP DNS name and the origin-verify secret parameter name as stack outputs for `aws-edge-hosting` and `aws-control-plane`

## 3. Deploy and verify

- [ ] 3.1 Push the first image, start the instance manually, check from Session Manager that `GET /api/v1/health` returns 200 after the catalog import, and that a direct connection to the Elastic IP from the internet is refused
- [ ] 3.2 Load check: ten minutes of searches, record resident memory and swap use; decide `t4g.micro` vs `t4g.small` and record it in the runbook
- [ ] 3.3 Verify no secret and no prompt text in `docker logs` and in the container image layers

## 4. Runbook and cost

- [ ] 4.1 Write `infra/RUNBOOK.md`: first-time setup (parameters, first image push), deploy (push tag, SSM Run Command), rollback (previous tag), reading logs, rotating a secret, replacing the instance
- [ ] 4.2 Create an AWS Budgets alert (for example 5 and 10 USD per month, e-mail) and a billing alarm before the first deploy
- [ ] 4.3 Cost verification: after the first full week, check Cost Explorer for instance, EBS, Elastic IP and data transfer lines against the assumptions in this design and record actual figures in the runbook
