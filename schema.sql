create schema if not exists comp4111 collate latin1_swedish_ci;

create table if not exists user
(
	username varchar(32) not null
		primary key,
	password varchar(32) null
);

create table if not exists sessions
(
	username varchar(32) not null
		primary key,
	access_token varchar(32) not null,
	constraint sessions_access_token_uindex
		unique (access_token),
	constraint sessions_user_username_fk
		foreign key (username) references user (username)
);

