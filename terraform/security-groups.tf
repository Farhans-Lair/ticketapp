# =============================================================
#  vpc.tf
#
#  Layout:
#    public  subnets (AZ-a, AZ-b) → ALB + EC2 (ASG instances)
#    private subnets (AZ-a, AZ-b) → RDS MySQL (no internet route)
# =============================================================

resource "aws_vpc" "ticketapp_vpc" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true   # required so RDS hostname resolves inside VPC

  tags = merge(local.common_tags, { Name = "${var.project_name}-vpc" })
}

# ---------------------------
# Internet Gateway
# ---------------------------
resource "aws_internet_gateway" "ticketapp_igw" {
  vpc_id = aws_vpc.ticketapp_vpc.id
  tags = merge(local.common_tags, { Name = "${var.project_name}-igw" })
}

# ---------------------------
# Public Subnets  (ALB + EC2 ASG instances)
# ---------------------------
resource "aws_subnet" "public_subnet_1" {
  vpc_id                  = aws_vpc.ticketapp_vpc.id
  cidr_block              = var.public_subnet_1_cidr
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = true   # EC2 instances get public IPs to pull from ECR

  tags = merge(local.common_tags, { Name = "${var.project_name}-public-subnet-1" })
}

resource "aws_subnet" "public_subnet_2" {
  vpc_id                  = aws_vpc.ticketapp_vpc.id
  cidr_block              = var.public_subnet_2_cidr
  availability_zone       = "${var.aws_region}b"
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, { Name = "${var.project_name}-public-subnet-2" })
}

# ---------------------------
# Private Subnets  (RDS only)
# ---------------------------
resource "aws_subnet" "private_subnet_1" {
  vpc_id            = aws_vpc.ticketapp_vpc.id
  cidr_block        = var.private_subnet_1_cidr
  availability_zone = "${var.aws_region}a"

  tags = merge(local.common_tags, { Name = "${var.project_name}-private-subnet-1" })
}

resource "aws_subnet" "private_subnet_2" {
  vpc_id            = aws_vpc.ticketapp_vpc.id
  cidr_block        = var.private_subnet_2_cidr
  availability_zone = "${var.aws_region}b"

  tags = merge(local.common_tags, { Name = "${var.project_name}-private-subnet-2" })
}

# ---------------------------
# Public Route Table  → Internet Gateway
# ---------------------------
resource "aws_route_table" "public_rt" {
  vpc_id = aws_vpc.ticketapp_vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.ticketapp_igw.id
  }

  tags = merge(local.common_tags, { Name = "${var.project_name}-public-rt" })
}

resource "aws_route_table_association" "public_rt_assoc_1" {
  subnet_id      = aws_subnet.public_subnet_1.id
  route_table_id = aws_route_table.public_rt.id
}

resource "aws_route_table_association" "public_rt_assoc_2" {
  subnet_id      = aws_subnet.public_subnet_2.id
  route_table_id = aws_route_table.public_rt.id
}

resource "aws_main_route_table_association" "set_public_rt_main" {
  vpc_id         = aws_vpc.ticketapp_vpc.id
  route_table_id = aws_route_table.public_rt.id
}

# ---------------------------
# Private Route Table  (RDS — no internet route at all)
# ---------------------------
resource "aws_route_table" "private_rt" {
  vpc_id = aws_vpc.ticketapp_vpc.id
  tags = merge(local.common_tags, { Name = "${var.project_name}-private-rt" })
}

resource "aws_route_table_association" "private_rt_assoc_1" {
  subnet_id      = aws_subnet.private_subnet_1.id
  route_table_id = aws_route_table.private_rt.id
}

resource "aws_route_table_association" "private_rt_assoc_2" {
  subnet_id      = aws_subnet.private_subnet_2.id
  route_table_id = aws_route_table.private_rt.id
}
