
resource "aws_vpc" "ticketapp_vpc" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true     # required so RDS hostname resolves inside VPC

  tags = merge(local.common_tags, { Name = "${var.project_name}-vpc" })
}

# Internet Gateway (ALB ingress + NAT egress)
resource "aws_internet_gateway" "ticketapp_igw" {
  vpc_id = aws_vpc.ticketapp_vpc.id
  tags   = merge(local.common_tags, { Name = "${var.project_name}-igw" })
}

# Public Subnets (ALB only — no EC2 ASG instances here)
resource "aws_subnet" "public_subnet_1" {
  vpc_id                  = aws_vpc.ticketapp_vpc.id
  cidr_block              = var.public_subnet_1_cidr
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = false     # ALB managed by AWS — no EIP needed

  tags = merge(local.common_tags, { Name = "${var.project_name}-public-subnet-1" })
}

resource "aws_subnet" "public_subnet_2" {
  vpc_id                  = aws_vpc.ticketapp_vpc.id
  cidr_block              = var.public_subnet_2_cidr
  availability_zone       = "${var.aws_region}b"
  map_public_ip_on_launch = false

  tags = merge(local.common_tags, { Name = "${var.project_name}-public-subnet-2" })
}

# Private Subnets (EC2 ASG instances + RDS)
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

resource "aws_eip" "nat_eip_a" {
  domain = "vpc"

  tags = merge(local.common_tags, { Name = "${var.project_name}-nat-eip-a" })

  # EIP must be created after the IGW is attached to the VPC
  depends_on = [aws_internet_gateway.ticketapp_igw]
}

resource "aws_eip" "nat_eip_b" {
  domain = "vpc"

  tags = merge(local.common_tags, { Name = "${var.project_name}-nat-eip-b" })

  depends_on = [aws_internet_gateway.ticketapp_igw]
}

resource "aws_nat_gateway" "ticketapp_nat_a" {
  allocation_id = aws_eip.nat_eip_a.id
  subnet_id     = aws_subnet.public_subnet_1.id     # NAT GW lives in a PUBLIC subnet, AZ-a

  tags = merge(local.common_tags, { Name = "${var.project_name}-nat-gw-a" })

  depends_on = [aws_internet_gateway.ticketapp_igw]
}

resource "aws_nat_gateway" "ticketapp_nat_b" {
  allocation_id = aws_eip.nat_eip_b.id
  subnet_id     = aws_subnet.public_subnet_2.id     # NAT GW lives in a PUBLIC subnet, AZ-b

  tags = merge(local.common_tags, { Name = "${var.project_name}-nat-gw-b" })

  depends_on = [aws_internet_gateway.ticketapp_igw]
}

# Public Route Table → Internet Gateway (ALB traffic)
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

# Private Route Tables → NAT Gateway (EC2 + RDS outbound) One route table per AZ, each
resource "aws_route_table" "private_rt_a" {
  vpc_id = aws_vpc.ticketapp_vpc.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.ticketapp_nat_a.id
  }

  tags = merge(local.common_tags, { Name = "${var.project_name}-private-rt-a" })
}

resource "aws_route_table" "private_rt_b" {
  vpc_id = aws_vpc.ticketapp_vpc.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.ticketapp_nat_b.id
  }

  tags = merge(local.common_tags, { Name = "${var.project_name}-private-rt-b" })
}

resource "aws_route_table_association" "private_rt_assoc_1" {
  subnet_id      = aws_subnet.private_subnet_1.id
  route_table_id = aws_route_table.private_rt_a.id
}

resource "aws_route_table_association" "private_rt_assoc_2" {
  subnet_id      = aws_subnet.private_subnet_2.id
  route_table_id = aws_route_table.private_rt_b.id
}
