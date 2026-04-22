# =============================================================
#  iam_cert.tf
#
#  Uploads the self-signed TLS certificate to the AWS IAM
#  certificate store so the ALB HTTPS listener can terminate TLS.
#
#  Spring Boot runs plain HTTP (USE_HTTPS=false in .env).
#  The JVM never sees raw TLS — the ALB handles it here.
#
#  Your existing keystore.p12 bundles cert + key together in
#  PKCS12 format. AWS IAM needs them as separate PEM files.
#  Run these two OpenSSL commands ONCE before terraform apply:
#
#    # 1. Extract certificate → server.crt
#    openssl pkcs12 ^
#      -in  "A:\AWS_Projects\ticketapp\certs\keystore.p12" ^
#      -clcerts -nokeys ^
#      -out "A:\AWS_Projects\ticketapp\certs\server.crt" ^
#      -passin pass:changeit
#
#    # 2. Extract private key (unencrypted) → server.key
#    openssl pkcs12 ^
#      -in  "A:\AWS_Projects\ticketapp\certs\keystore.p12" ^
#      -nocerts -nodes ^
#      -out "A:\AWS_Projects\ticketapp\certs\server.key" ^
#      -passin pass:changeit
#
#  After extraction your certs\ folder contains:
#    keystore.p12  ← original (keep — used by Spring Boot locally)
#    server.crt    ← certificate body PEM  (used below)
#    server.key    ← private key PEM       (used below)
#
#  cert_chain_path in terraform.tfvars → mkcert CA root PEM path.
#  Find it by running:  mkcert -CAROOT
#  Windows example in terraform.tfvars:
#    cert_chain_path = "C:/Users/YourName/AppData/Local/mkcert/rootCA.pem"
#
#  NOTE: Use forward slashes or double-backslashes in tfvars paths.
#  Terraform on Windows handles both; avoid single backslashes.
# =============================================================

resource "aws_iam_server_certificate" "self_signed" {
  # Hash-suffixed name auto-changes when cert is regenerated →
  # create_before_destroy keeps the ALB live during cert rotation.
  name = "${var.project_name}-selfsigned-${substr(sha256(file("${path.module}/../certs/server.crt")), 0, 8)}"

  # PEM certificate body extracted from keystore.p12
  certificate_body = file("${path.module}/../certs/server.crt")

  # Unencrypted PEM private key extracted from keystore.p12
  private_key = file("${path.module}/../certs/server.key")

  # mkcert CA root — required for the ALB to accept the certificate upload.
  # Value comes from cert_chain_path in terraform.tfvars.
  certificate_chain = file(var.cert_chain_path)

  lifecycle {
    create_before_destroy = true
  }
}
