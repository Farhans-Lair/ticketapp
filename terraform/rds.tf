# =============================================================
#  rds.tf
#
#  MySQL 8.0 RDS for Spring Boot ticketapp.
#
#  JDBC URL (application.properties):
#    jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}
#      ?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
#
#  spring.jpa.hibernate.ddl-auto=update → Hibernate manages tables.
#  RDS only needs the empty schema; no manual DDL bootstrap required.
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

  tags = { Name = "${var.project_name}-db-subnet-group" }
}

# ---------------------------
# Parameter Group
# Sets time_zone=UTC to match ?serverTimezone=UTC in the JDBC URL.
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

  tags = { Name = "${var.project_name}-mysql8-params" }
}

# ---------------------------
# RDS MySQL Instance
# ---------------------------
resource "aws_db_instance" "ticketapp_db" {
  identifier = "${var.project_name}-mysql-db"

  engine         = "mysql"
  engine_version = "8.0"
  instance_class = "db.t3.micro"

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp2"

  # Matches DB_NAME / DB_USER / DB_PASS in user_data.sh .env block
  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  parameter_group_name   = aws_db_parameter_group.ticketapp_mysql_params.name
  db_subnet_group_name   = aws_db_subnet_group.ticketapp_db_subnet_group.name
  vpc_security_group_ids = [aws_security_group.rds_sg.id]

  # useSSL=false in JDBC URL — keep SSL off for internal VPC traffic
  publicly_accessible = false

  skip_final_snapshot     = true
  deletion_protection     = false
  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "Mon:04:00-Mon:05:00"

  tags = { Name = "${var.project_name}-mysql-db" }
}
