
# DB Subnet Group — private subnets only
resource "aws_db_subnet_group" "ticketapp_db_subnet_group" {
  name = "${var.project_name}-db-subnet-group"

  subnet_ids = [
    aws_subnet.private_subnet_1.id,
    aws_subnet.private_subnet_2.id
  ]

  tags = merge(local.common_tags, { Name = "${var.project_name}-db-subnet-group" })
}

# Parameter Group
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
  parameter {
    name  = "binlog_format"
    value = "ROW"
  }

  tags = merge(local.common_tags, { Name = "${var.project_name}-mysql8-params" })
}

resource "aws_db_instance" "ticketapp_db" {
  identifier = "${var.project_name}-mysql-db"

  engine         = "mysql"
  engine_version = "8.0"

  instance_class = "db.t3.small"

  allocated_storage     = 20
  max_allocated_storage = 100
  # gp3 is the current AWS-recommended baseline: cheaper than gp2 at the same size and includes 3,000
  storage_type          = "gp3"

  # Encrypts the underlying EBS storage, automated backups, snapshots, and read replica with the default AWS-managed RDS
  storage_encrypted = true

  # Synchronous standby in a second AZ with automatic failover (typically 60-120s) if the primary AZ or
  multi_az = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  parameter_group_name   = aws_db_parameter_group.ticketapp_mysql_params.name
  db_subnet_group_name   = aws_db_subnet_group.ticketapp_db_subnet_group.name
  vpc_security_group_ids = [aws_security_group.rds_sg.id]

  publicly_accessible = false

  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-mysql-final-snapshot"
  deletion_protection       = true

  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "Mon:04:00-Mon:05:00"

  tags = merge(local.common_tags, { Name = "${var.project_name}-mysql-primary" })
}

resource "aws_db_instance" "ticketapp_db_replica" {
  identifier = "${var.project_name}-mysql-replica"

  # replica inherits engine, version, parameter group from source
  replicate_source_db = aws_db_instance.ticketapp_db.identifier
  instance_class      = "db.t3.small"

  # Replicas do not require a subnet group — they inherit from source, but the security group
  vpc_security_group_ids = [aws_security_group.rds_sg.id]

  publicly_accessible = false
  skip_final_snapshot = true      # replica can be re-created from primary backup
  deletion_protection = false     # replica is expendable; primary is protected above

  tags = merge(local.common_tags, { Name = "${var.project_name}-mysql-replica" })
}

# Outputs — endpoint hostnames for .env / SSM
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
