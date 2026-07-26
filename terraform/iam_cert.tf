
resource "aws_iam_server_certificate" "self_signed" {
  # Hash-suffixed name auto-changes when cert is regenerated → create_before_destroy keeps the ALB live during cert rotation.
  name = "${var.project_name}-selfsigned-${substr(sha256(file("${path.module}/../certs/server.crt")), 0, 8)}"

  # PEM certificate body extracted from keystore.p12
  certificate_body = file("${path.module}/../certs/server.crt")

  # Unencrypted PEM private key extracted from keystore.p12
  private_key = file("${path.module}/../certs/server.key")

  # mkcert CA root — required for the ALB to accept the certificate upload.
  certificate_chain = file(var.cert_chain_path)

  lifecycle {
    create_before_destroy = true
  }
}
