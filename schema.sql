create schema comp4111 collate latin1_swedish_ci;

create table user
(
	username varchar(32) not null
		primary key,
	password varchar(32) null
);

create table session
(
	username varchar(32) not null,
	token char(36) not null
		primary key,
	constraint session_username_uindex
		unique (username),
	constraint sessions_user_username_fk
		foreign key (username) references user (username)
);

