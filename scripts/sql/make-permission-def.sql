/* SPDX-License-Identifier: MulanPSL-2.0 */
/*
    配置权限表。

    usage:
      sudo mariadb < make-permission-def.sql

    这部分代码应该由 ./permission/gen_code.py 自动生成。
*/


use `vesper-center`;


/* ------ 自动生成代码 开头 ------ */


insert ignore into permission (id, enum_key, note)
    values (1, 'GRANT_PERMISSION', '管理所有用户的权限');
insert ignore into permission (id, enum_key, note)
    values (100, 'CREATE_AND_DELETE_USER', '创建和删除自己创建的用户');
insert ignore into permission (id, enum_key, note)
    values (101, 'DELETE_ANY_USER', '删除任何用户');
insert ignore into permission (id, enum_key, note)
    values (200, 'CREATE_SEAT', '创建和删除自己创建的桌面环境');
insert ignore into permission (id, enum_key, note)
    values (201, 'DELETE_ANY_SEAT', '删除任何 seat');
insert ignore into permission (id, enum_key, note)
    values (202, 'NAME_ANY_SEAT', '编辑任意 seat 的名字');
insert ignore into permission (id, enum_key, note)
    values (203, 'LOGIN_TO_ANY_SEAT', '登录到任意用户的环境');
insert ignore into permission (id, enum_key, note)
    values (204, 'LOGIN_TO_DISABLED_SEAT', '登录到已经被关闭的主机');
insert ignore into permission (id, enum_key, note)
    values (205, 'DISABLE_OR_ENABLE_SEAT', '禁用或启用主机');
insert ignore into permission (id, enum_key, note)
    values (300, 'CREATE_GROUP', '创建组。包含删除自己组的权限。创建后，自动获取组内一切权限');
insert ignore into permission (id, enum_key, note)
    values (301, 'MODIFY_ANY_GROUP_MEMBERS_PERMISSION', '编辑任意组的组内成员权限');


insert ignore into group_permission (id, enum_key, note)
    values (1, 'GRANT_PERMISSION', '组内赋权');
insert ignore into group_permission (id, enum_key, note)
    values (2, 'DROP_GROUP', '删除一个组');
insert ignore into group_permission (id, enum_key, note)
    values (100, 'ADD_OR_REMOVE_USER', '将用户移入或移出组');
insert ignore into group_permission (id, enum_key, note)
    values (200, 'CREATE_OR_DELETE_SEAT', '在组内创建主机，以及删除组内任意主机');
insert ignore into group_permission (id, enum_key, note)
    values (201, 'NAME_ANY_SEAT', '编辑组内任意 seat 的名字');
insert ignore into group_permission (id, enum_key, note)
    values (202, 'LOGIN_TO_ANY_SEAT', '登录到组内任意主机');
insert ignore into group_permission (id, enum_key, note)
    values (203, 'LOGIN_TO_DISABLED_SEAT', '登录到已经被关闭的主机');
insert ignore into group_permission (id, enum_key, note)
    values (204, 'DISABLE_OR_ENABLE_SEAT', '禁用或启用主机');
insert ignore into group_permission (id, enum_key, note)
    values (300, 'COLLECT_FILES', '收集指定位置的文件');



/* ------ 自动生成代码 结尾 ------ */
