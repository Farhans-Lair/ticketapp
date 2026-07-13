# =============================================================
#  vpc.tf
#
#  UPDATED LAYOUT (task 8 — EC2 moved to private subnets):
#    public  subnets (AZ-a, AZ-b) → ALB only
#    private subnets (AZ-a, AZ-b) → EC2 ASG instances + RDS MySQL
#
#  EC2 instances no longer have public IPs.
#  Outbound internet traffic (ECR pull, S3, SES, Razorpay, Twilio)
#  flows through the NAT Gateway in public_subnet_1 via the
#  private route table.
# =============================================================

resource "aws_vpc" "ticketapp_vpc" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true   # required so RDS hostname resolves inside VPC

  tags = merge(local.common_tags, { Name = "${var.project_name}-vpc" })
}

# ---------------------------
# Internet Gateway  (ALB ingress + NAT egress)
# ---------------------------
resource "aws_internet_gateway" "ticketapp_igw" {
  vpc_id = aws_vpc.ticketapp_vpc.id
  tags   = merge(local.common_tags, { Name = "${var.project_name}-igw" })
}

# ---------------------------
# Public Subnets  (ALB only — no EC2 ASG instances here)
# ---------------------------
resource "aws_subnet" "public_subnet_1" {
  vpc_id                  = aws_vpc.ticketapp_vpc.id
  cidr_block              = var.public_subnet_1_cidr
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = false   # ALB managed by AWS — no EIP needed

  tags = merge(local.common_tags, { Name = "${var.project_name}-public-subnet-1" })
}

resource "aws_subnet" "public_subnet_2" {
  vpc_id                  = aws_vpc.ticketapp_vpc.id
  cidr_block              = var.public_subnet_2_cidr
  availability_zone       = "${var.aws_region}b"
  map_public_ip_on_launch = false

  tags = merge(local.common_tags, { Name = "${var.project_name}-public-subnet-2" })
}

# ---------------------------
# Private Subnets  (EC2 ASG instances + RDS)
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
# NAT Gateway  (outbound internet for EC2 in private subnets)
#
# WHY: EC2 instances in private subnets have no public IP so they
# can't reach ECR (docker pull), S3, SES, Razorpay or Twilio
# directly. The NAT Gateway translates their outbound traffic to
# a single public EIP, while the subnets remain unreachable from
# the internet.
#
# COST: ~$0.045/hr + data processing. One NAT GW in AZ-a is
# sufficient for this scale; add a second in AZ-b (+ separate
# private route tables per AZ) for full HA at double the cost.
# ---------------------------
resource "aws_eip" "nat_eip" {
  domain = "vpc"

  tags = merge(local.common_tags, { Name = "${var.project_name}-nat-eip" })

  # EIP must be created after the IGW is attached to the VPC
  depends_on = [aws_internet_gateway.ticketapp_igw]
}

resource "aws_nat_gateway" "ticketapp_nat" {
  allocation_id = aws_eip.nat_eip.id
  subnet_id     = aws_subnet.public_subnet_1.id   # NAT GW lives in a PUBLIC subnet

  tags = merge(local.common_tags, { Name = "${var.project_name}-nat-gw" })

  depends_on = [aws_internet_gateway.ticketapp_igw]
}

# ---------------------------
# Public Route Table  → Internet Gateway  (ALB traffic)
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
# Private Route Table  → NAT Gateway  (EC2 + RDS outbound)
#
# EC2 instances use this route for all outbound internet traffic.
# RDS has no outbound routes (it never initiates internet connections).
# ---------------------------
resource "aws_route_table" "private_rt" {
  vpc_id = aws_vpc.ticketapp_vpc.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.ticketapp_nat.id
  }

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
