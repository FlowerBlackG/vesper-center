
from dataclasses import dataclass

@dataclass
class Permission:
    id: int
    enum_key: str
    note: str


titles = {
    'permission': {
        'sql': 'permission',
        'kt': 'Permission',
    },
    'group-permission': {
        'sql': 'group_permission',
        'kt': 'GroupPermission',
    }
}


def gen_sql_mkdatabase_code(permissions: list[Permission], titles):
    for it in permissions:
        print(f'insert ignore into {titles["sql"]} (id, enum_key, note)')
        print(f'    values ({it.id}, \'{it.enum_key}\', \'{it.note}\');')
        print('')
    print('')


def gen_kt_permission_def_code(permissions: list[Permission], titles):
    print('--- kt permission def:  ---')
    print('        UNDEFINED(0),')
    for it in permissions:
        print('')
        print('        /**')
        print('         * ' + it.note)
        print('         */')
        print(f'        {it.enum_key}({it.id}),')
    print('')


def gen_kt_superuser_grant_code(permissions: list[Permission], titles):
    print('--- kt superuser grant: UserController::createSuperUser ---')
    for it in permissions:
        print(f'{" " * 12}{titles["kt"]}.{it.enum_key},')
    print('')


def gen_ts_permission_def_code(permissions: list[Permission], titles):
    print('--- ts permission def: api/Permissions.ts ---')
    print('    UNDEFINED = 0,')
    for it in permissions:
        print('')
        print('    /**')
        print('     * ' + it.note)
        print('     */')
        print(f'    {it.enum_key} = {it.id},')
    print('')


def gen_code(permissions: list[Permission], titles):
    gen_sql_mkdatabase_code(permissions, titles)
    gen_kt_permission_def_code(permissions, titles)
    gen_kt_superuser_grant_code(permissions, titles)
    gen_ts_permission_def_code(permissions, titles)


def process(lines: list[str], key: str, titles):
    permissions: list[Permission] = []
    idx = 0
    while idx < len(lines):
        line = lines[idx]
        idx += 1

        if line.startswith(key):
            permissions.append(Permission(
                id=int(lines[idx]),
                enum_key=lines[idx+1],
                note=lines[idx+2]
            ))
            idx += 3

    gen_code(permissions, titles)


def get_permissions(lines: list[str], key: str) -> list[Permission]:
    permissions: list[Permission] = []

    idx = 0
    while idx < len(lines):
        line = lines[idx]
        idx += 1

        if line.startswith(key):
            permissions.append(Permission(
                id=int(lines[idx]),
                enum_key=lines[idx+1],
                note=lines[idx+2]
            ))
            idx += 3

    return permissions


def main():
    file = open('permission-def.txt')
    lines = file.readlines()
    file.close()

    for idx in range(0, len(lines)):
        it = lines[idx]
        it = it.replace('\r', '')
        it = it.replace('\n', '')
        lines[idx] = it

    global_permissions = get_permissions(lines, 'permission:')
    group_permissions = get_permissions(lines, 'group-permission:')

    methods = [
        gen_sql_mkdatabase_code,
        gen_kt_permission_def_code,
        # gen_kt_superuser_grant_code,
        gen_ts_permission_def_code
    ]

    for method in methods:
        method(global_permissions, titles['permission'])
        print('\n' * 2)
        method(group_permissions, titles['group-permission'])
        print('\n' * 4)
        print('* ==== +++ () +++ ==== ****** ==== +++ () +++ ==== *')
        print('\n' * 4)


if __name__ == '__main__':
    main()
