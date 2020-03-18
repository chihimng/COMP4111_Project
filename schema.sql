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

DELIMITER $$
CREATE PROCEDURE CreateUser()
BEGIN
    DECLARE counter INT DEFAULT 1;

    REPEAT
        SET @suffix = LPAD(CAST(counter AS CHAR(3)), 3, '0');
        SET @username = CONCAT("user", @suffix);
        SET @password = CONCAT("passwd", @suffix);
        SET @salt = UUID();
        INSERT INTO user VALUES (@username, UNHEX(SHA2(CONCAT(@password, @salt), 256)), @salt);
        SET counter = counter + 1;
    UNTIL counter > 100
    END REPEAT;
END$$
DELIMITER ;

CALL CreateUser();

DROP PROCEDURE CreateUser;