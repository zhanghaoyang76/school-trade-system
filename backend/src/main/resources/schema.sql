create table if not exists users (
  id bigint auto_increment primary key,
  username varchar(64) unique not null,
  password_hash varchar(100) not null,
  role varchar(20) not null,
  status varchar(20) not null,
  name varchar(80),
  phone varchar(30),
  email varchar(120),
  city varchar(80),
  gender varchar(20),
  bank_account varchar(16),
  license_file_id bigint,
  identity_file_id bigint,
  review_reason varchar(500),
  points int default 0 not null,
  frozen_points int default 0 not null,
  deleted boolean default false not null,
  created_at timestamp default current_timestamp
);

create table if not exists shops (
  id bigint auto_increment primary key,
  seller_id bigint unique not null,
  name varchar(120),
  status varchar(20) default 'OPEN',
  level int default 1,
  level_mode varchar(20) default 'AUTO',
  created_at timestamp default current_timestamp
);

create table if not exists categories (
  id bigint auto_increment primary key,
  name varchar(100),
  sort_order int default 0,
  created_at timestamp default current_timestamp
);

create table if not exists products (
  id bigint auto_increment primary key,
  shop_id bigint not null,
  category_id bigint,
  name varchar(160),
  original_price decimal(18,2),
  discount decimal(8,4),
  price decimal(18,2),
  description varchar(4000),
  size varchar(100),
  instructions varchar(2000),
  negotiable boolean,
  condition_text varchar(100),
  stock int not null,
  status varchar(20),
  review_reason varchar(500),
  created_at timestamp default current_timestamp
);

create table if not exists product_images (
  id bigint auto_increment primary key,
  product_id bigint,
  file_id bigint,
  sort_order int,
  created_at timestamp default current_timestamp
);

create table if not exists uploaded_files (
  id bigint auto_increment primary key,
  owner_id bigint,
  session_key varchar(100),
  purpose varchar(20),
  storage_name varchar(100),
  original_name varchar(255),
  content_type varchar(100),
  size_bytes bigint,
  created_at timestamp default current_timestamp
);

create table if not exists cart_items (
  id bigint auto_increment primary key,
  buyer_id bigint,
  product_id bigint,
  quantity int,
  unique(buyer_id,product_id),
  created_at timestamp default current_timestamp
);

create table if not exists checkout_batches (
  id bigint auto_increment primary key,
  buyer_id bigint,
  request_key varchar(100),
  total_amount decimal(18,2),
  paid_amount decimal(18,2),
  used_points int,
  unique(buyer_id,request_key),
  created_at timestamp default current_timestamp
);

create table if not exists orders (
  id bigint auto_increment primary key,
  batch_id bigint,
  buyer_id bigint,
  seller_id bigint,
  shop_id bigint,
  order_no varchar(100),
  status varchar(20),
  appointment_time varchar(100),
  appointment_place varchar(300),
  delivered_at timestamp null,
  confirmed_at timestamp null,
  fee_rate decimal(8,5),
  cancel_reason varchar(500),
  buyer_name varchar(80),
  seller_name varchar(80),
  shop_name varchar(120),
  created_at timestamp default current_timestamp
);

create table if not exists order_items (
  id bigint auto_increment primary key,
  order_id bigint,
  product_id bigint,
  product_name varchar(160),
  unit_price decimal(18,2),
  quantity int,
  paid_amount decimal(18,2),
  used_points int,
  reward_points int,
  fee decimal(18,2),
  settlement_status varchar(20),
  created_at timestamp default current_timestamp
);

create table if not exists return_requests (
  id bigint auto_increment primary key,
  item_id bigint,
  buyer_id bigint,
  reason varchar(1000),
  status varchar(20),
  review_reason varchar(500),
  reviewed_at timestamp null,
  refunded_at timestamp null,
  received_at timestamp null,
  created_at timestamp default current_timestamp
);

create table if not exists reviews (
  id bigint auto_increment primary key,
  item_id bigint,
  direction varchar(20),
  author_id bigint,
  target_id bigint,
  product_stars int,
  service_stars int,
  buyer_stars int,
  content varchar(2000),
  unique(item_id,direction),
  created_at timestamp default current_timestamp
);

create table if not exists blacklists (
  id bigint auto_increment primary key,
  scope varchar(20),
  shop_id bigint,
  operator_id bigint,
  buyer_id bigint,
  reason varchar(500),
  active boolean default true,
  released_at timestamp null,
  created_at timestamp default current_timestamp
);

create table if not exists wallets (
  id bigint auto_increment primary key,
  user_id bigint unique,
  account_type varchar(20),
  available decimal(18,2) default 0 not null,
  frozen decimal(18,2) default 0 not null,
  created_at timestamp default current_timestamp
);

create table if not exists wallet_entries (
  id bigint auto_increment primary key,
  business_key varchar(150) unique,
  wallet_id bigint,
  type varchar(40),
  available_delta decimal(18,2),
  frozen_delta decimal(18,2),
  order_id bigint,
  operator_id bigint,
  created_at timestamp default current_timestamp
);

create table if not exists point_entries (
  id bigint auto_increment primary key,
  user_id bigint,
  business_key varchar(150) unique,
  available_delta int,
  frozen_delta int,
  item_id bigint,
  created_at timestamp default current_timestamp
);

create table if not exists audit_logs (
  id bigint auto_increment primary key,
  operator_id bigint,
  action varchar(120),
  object_type varchar(100),
  object_id bigint,
  summary varchar(1000),
  created_at timestamp default current_timestamp
);
