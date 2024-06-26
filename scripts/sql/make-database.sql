/* SPDX-License-Identifier: MulanPSL-2.0 */

/*
 * 自动构建 vesper center 数据库。
 * 注意，该脚本会删掉原有的表。
 *
 * usage:
 *   sudo mariadb < make-database.sql
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
    creator         bigint      not null,
    username        varchar(32) not null comment '用户名。要求唯一。',
    passwd          char(32)    null comment '用户密码。32位小写md5。',
    create_time     datetime    not null comment '用户注册时间。',
    last_login_time datetime    null comment '最后一次登录时间。',
    constraint user_pk
        primary key (id)
);


/*
    user group 表

    用于抽象一个用户组（类似教学班级概念）
*/

create table user_group
(
    id          bigint auto_increment,
    group_name  varchar(32)  not null,
    note        varchar(512) null,
    create_time datetime     not null,
    constraint group_pk
        primary key (id)
);


create table group_member
(
    user_id  bigint not null,
    group_id bigint not null,
    constraint group_permission_grant_pk
        unique (user_id, group_id)
);


/* 
    seat 表 

    用于表示一个桌面环境。
*/

create table seat
(
    id               bigint auto_increment,
    user_id          bigint       not null comment '该桌面环境的使用者。',
    group_id         bigint       null     comment '组号。可以为空。',
    creator          bigint       not null comment '创建者。它拥有删除这个主机的权限。',
    seat_enabled     tinyint(1)   not null,
    nickname         varchar(64)  null,
    note             varchar(512) null,
    linux_uid        int          not null,
    linux_login_name varchar(32)  not null,
    linux_passwd_raw varchar(64)  not null comment 'linux用户明文密码。',
    create_time      datetime     not null,
    last_login_time  datetime     null comment '上次启动时间。',
    constraint seat_pk
        primary key (id)
);


/* 用户权限。 */

create table permission
(
    id       bigint       not null,
    enum_key varchar(64)  not null,
    note     varchar(512) null,
    constraint permission_pk
        primary key (id)
);


create table permission_grant
(
    user_id       bigint not null,
    permission_id bigint not null,
    constraint permission_grant_pk
        unique (user_id, permission_id)
);


/* group 权限。 */

create table group_permission
(
    id       bigint       not null,
    enum_key varchar(64)  not null,
    note     varchar(512) null,
    constraint group_permission_pk
        primary key (id)
);


create table group_permission_grant
(
    user_id       bigint not null,
    group_id      bigint not null,
    permission_id bigint not null,
    constraint group_permission_grant_pk
        unique (user_id, group_id, permission_id)
);

