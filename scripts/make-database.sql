/* SPDX-License-Identifier: MulanPSL-2.0 */

/*
 * 自动构建 vesper center 数据库。
 * 注意，该脚本会删掉原有的表。
 */


/* 删除原来的 vesper-center 库。 */

drop database if exists `vesper-center`;
create database `vesper-center`;
use `vesper-center`;


/* 
    user 表 
*/

create table user
(
    id              bigint auto_increment comment '用户身份唯一识别编号。',
    username        varchar(32) not null comment '用户名。要求唯一。',
    passwd          char(32)    not null comment '用户密码。32位小写md5。',
    create_time     datetime    not null comment '用户注册时间。',
    last_login_time datetime    null comment '最后一次登录时间。',
    constraint user_pk
        primary key (id)
);


/* 
    seat 表 

    用于表示一个桌面环境。
*/

create table seat
(
    id               bigint auto_increment,
    user_id          bigint      not null comment '该桌面环境的使用者。',
    enabled          tinyint(1)  not null,
    linux_uid        int         not null,
    linux_login_name varchar(32) not null,
    linux_passwd_raw varchar(64) not null comment 'linux用户明文密码。',
    create_time      datetime    not null,
    last_login_time  datetime    null comment '上次启动时间。',
    constraint seat_pk
        primary key (id)
);


/* 用户权限。 */

create table permission_group
(
    id       bigint       not null,
    fullname varchar(64)  not null,
    note     varchar(512) null,
    constraint permission_group_pk
        primary key (id)
);


create table permission_grant
(
    user_id       bigint not null,
    permission_id bigint not null,
    constraint permission_grant_pk
        unique (user_id, permission_id)
);


/*
    配置权限表。

    权限定义模板：

    insert ignore into permission_group (id, fullname, note)
        values (, '', '');
*/

insert ignore into permission_group (id, fullname, note)
    values (1, '赋权者', '管理所有用户的权限。');

insert ignore into permission_group (id, fullname, note)
    values (20, '创建和删除用户', '');

insert ignore into permission_group (id, fullname, note)
    values (50, '创建和删除桌面环境', '');
    
insert ignore into permission_group (id, fullname, note)
    values (100, '登录到任意用户的环境', '');


/*
    创建超级用户。
*/

set @target_id := 1;

/* 备注：管理员密码是 mf98hhB8Dzo3AtZ7C */

insert ignore into user (
    id, username, passwd, create_time, last_login_time
) values (
    @target_id, '卡皮吧啦', 'e4d0ef219636a103733c591ea0f708ca', now(), null
);

/* 赋权。 */

insert ignore into permission_grant (user_id, permission_id) values (@target_id, 1);
insert ignore into permission_grant (user_id, permission_id) values (@target_id, 20);
insert ignore into permission_grant (user_id, permission_id) values (@target_id, 50);
insert ignore into permission_grant (user_id, permission_id) values (@target_id, 100);

set @target_id := null;
