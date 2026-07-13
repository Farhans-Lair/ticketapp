# =============================================================
#  rds.tf
#
#  MySQL 8.0 primary RDS instance + read replica.
#
#  Changes from original:
#    - task  9: deletion_protection = true, skip_final_snapshot = false
#    - task 10: instance_class = db.t3.small (2 GB RAM)
#    - task 11: read replica for public event listing / search queries
# =============================================================

# ---------------------------
# DB Subnet Group — private subnets only
# ---------------------------
resource "aws_db_subnet_group" "ticketapp_db_subnet_group" {
  name = "${var.project_name}-db-subnet-group"

  subnet_ids = [
    aws_subnet.private_subnet_1.id,
    aws_subnet.private_subnet_2.id
  ]

  tags = merge(local.common_tags, { Name = "${var.project_name}-db-subnet-group" })
}

# ---------------------------
# Parameter Group
# ---------------------------
resource "aws_db_parameter_group" "ticketapp_mysql_params" {
  name   = "${var.project_name}-mysql8-params"
  family = "mysql8.0"

  parameter {
    name  = "time_zone"
    value = "UTC"
  }

  parameter {
    name  = "innodb_lock_wait_timeout"
    value = "50"
  }

  # Allow the read replica to stream binary logs back to the primary.
  # Required when a replica is attached to a Multi-AZ or standalone primary.
  parameter {
    name  = "binlog_format"
    value = "ROW"
  }

  tags = merge(local.common_tags, { Name = "${var.project_name}-mysql8-params" })
}

# ---------------------------
# Primary RDS Instance (writes)
#
# task 9:  deletion_protection = true, skip_final_snapshot = false
# task 10: db.t3.small (2 GB) — handles JVM + Hibernate connection pool
#          HikariCP max-pool-size=5 per EC2 instance × 3 instances = 15
#          connections; db.t3.small max_connections ≈ 180 → safe headroom.
# ---------------------------
resource "aws_db_instance" "ticketapp_db" {
  identifier = "${var.project_name}-mysql-db"

  engine         = "mysql"
  engine_version = "8.0"

  # task 10: upgraded from db.t3.micro (1 GB) to db.t3.small (2 GB)
  instance_class = "db.t3.small"

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp2"

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  parameter_group_name   = aws_db_parameter_group.ticketapp_mysql_params.name
  db_subnet_group_name   = aws_db_subnet_group.ticketapp_db_subnet_group.name
  vpc_security_group_ids = [aws_security_group.rds_sg.id]

  publicly_accessible = false

  # task 9: production safety guards
  # skip_final_snapshot = false → RDS takes a snapshot before deletion.
  # deletion_protection = true  → prevents accidental terraform destroy.
  # To destroy intentionally: set both to false, apply, then destroy.
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-mysql-final-snapshot"
  deletion_protection       = true

  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "Mon:04:00-Mon:05:00"

  # task 11: enable automated backups (required for read replica)
  # backup_retention_period > 0 is already set above.

  tags = merge(local.common_tags, { Name = "${var.project_name}-mysql-primary" })
}

# ---------------------------
# Read Replica (task 11)
#
# WHY: Event listing (/api/events), search (/search), featured/trending
# queries are the highest-traffic read paths. Directing them to a replica
# removes their load from the primary, which keeps write latency stable
# during booking peaks.
#
# HOW TO USE IN CODE:
#   - Add a second DataSource bean (datasource-replica) pointing to
#     read_replica_endpoint below, configured in application-prod.properties.
#   - Annotate read-only service methods with @Transactional(readOnly=true)
#     and use Spring's AbstractRoutingDataSource to route them to the replica.
#     See: https://docs.spring.io/spring-framework/docs/current/javadoc-api/
#          org/springframework/jdbc/datasource/lookup/AbstractRoutingDataSource.html
#
# COST: same as primary (~$0.034/hr for db.t3.small in ap-south-1).
# Promote to primary with aws_db_instance.promote_read_replica if primary fails.
# ---------------------------
resource "aws_db_instance" "ticketapp_db_replica" {
  identifier = "${var.project_name}-mysql-replica"

  # replica inherits engine, version, parameter group from source
  replicate_source_db = aws_db_instance.ticketapp_db.identifier
  instance_class      = "db.t3.small"

  # Replicas do not require a subnet group — they inherit from source,
  # but the security group must be explicit.
  vpc_security_group_ids = [aws_security_group.rds_sg.id]

  publicly_accessible = false
  skip_final_snapshot = true    # replica can be re-created from primary backup
  deletion_protection = false   # replica is expendable; primary is protected above

  tags = merge(local.common_tags, { Name = "${var.project_name}-mysql-replica" })
}

# ---------------------------
# Outputs — endpoint hostnames for .env / SSM
# ---------------------------
output "rds_primary_endpoint" {
  description = "Primary RDS endpoint — use for all writes (DB_HOST in .env)"
  value       = aws_db_instance.ticketapp_db.address
  sensitive   = false
}

output "rds_replica_endpoint" {
  description = "Read replica endpoint — point read-only DataSource here"
  value       = aws_db_instance.ticketapp_db_replica.address
  sensitive   = false
}
