create table yelp_data_set(
id int not null auto_increment primary key,
business_name varchar(64),

sunday_open_hour varchar(5),
sunday_close_hour varchar(5),

monday_open_hour varchar(5),
monday_close_hour varchar(5),

tuesday_open_hour varchar(5),
tuesday_close_hour varchar(5),

wednesday_open_hour varchar(5),
wednesday_close_hour varchar(5),

thursday_open_hour varchar(5),
thursday_close_hour varchar(5),

friday_open_hour varchar(5),
friday_close_hour varchar(5),

saturday_open_hour varchar(5),
saturday_close_hour varchar(5),

address varchar(128),
categories varchar(256),
stars decimal);

