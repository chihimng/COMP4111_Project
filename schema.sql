use comp4111;

create table book
(
	id smallint unsigned auto_increment
		primary key,
	title varchar(128) not null,
	author varchar(128) not null,
	publisher varchar(128) not null,
	year smallint unsigned not null,
	isAvailable boolean default true not null,
	constraint book_pk_2
		unique (title)
);

create table user
(
	username varchar(32) not null
		primary key,
	password binary(32) not null,
	salt char(36) null
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

DELIMITER $$
CREATE PROCEDURE CreateUser()
BEGIN
    DECLARE counter INT DEFAULT 1;

    REPEAT
        SET @suffix = LPAD(CAST(counter AS CHAR(5)), 5, '0');
        SET @username = CONCAT("user", @suffix);
        SET @password = CONCAT("pass", @suffix);
        SET @salt = UUID();
        INSERT INTO user VALUES (@username, UNHEX(SHA2(CONCAT(@password, @salt), 256)), @salt);
        SET counter = counter + 1;
    UNTIL counter > 10000
    END REPEAT;
END$$
DELIMITER ;

CALL CreateUser();

DROP PROCEDURE CreateUser;