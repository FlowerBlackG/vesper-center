
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
    with open(f'./{titles["sql"]}.sql.gen', 'w') as f:
        for it in permissions:
            f.write(f'insert ignore into {titles["sql"]} (id, enum_key, note)\n')
            f.write(f'    values ({it.id}, \'{it.enum_key}\', \'{it.note}\');\n')


def gen_kt_permission_def_code(permissions: list[Permission], titles):
    with open(f'./{titles["kt"]}.kt.gen', 'w') as f:
        f.write('        UNDEFINED(0),\n')
        for it in permissions:
            f.write('\n')
            f.write('        /**\n')
            f.write('         * ' + it.note + '\n')
            f.write('         */\n')
            f.write(f'        {it.enum_key}({it.id}),\n')



def gen_kt_superuser_grant_code(permissions: list[Permission], titles):
    with open(f'./kt-superuser-grant-{titles["kt"]}.kt.gen', 'w') as f:
        for it in permissions:
            f.write(f'{" " * 12}{titles["kt"]}.{it.enum_key},\n')


def gen_ts_permission_def_code(permissions: list[Permission], titles):
    with open(f'./{titles["kt"]}.ts.gen', 'w') as f:
        f.write('    UNDEFINED = 0,\n')
        for it in permissions:
            f.write('\n')
            f.write('    /**\n')
            f.write('     * ' + it.note + '\n')
            f.write('     */\n')
            f.write(f'    {it.enum_key} = {it.id},\n')



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

    id_set = set()

    idx = 0
    while idx < len(lines):
        line = lines[idx]
        idx += 1

        if line.startswith(key):

            id = int(lines[idx])
            print(f'{key} found: {id} {lines[idx+1]} {lines[idx+2]}')
            
            if id in id_set:
                print(f'[error] duplicated id: {id}')
                exit(-1)
            id_set.add(id)

            permissions.append(Permission(
                id=id,
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
        method(group_permissions, titles['group-permission'])
        print(f'{method.__name__} done.')
    
    print('all done.')


if __name__ == '__main__':
    main()
