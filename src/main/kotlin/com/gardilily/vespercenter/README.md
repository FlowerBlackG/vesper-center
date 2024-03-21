# Vesper Center 代码

## 安全责任说明

controller 负责接收网络请求。如果请求比较简单，应该在 controller 内直接完成。

service 负责完成相对复杂的逻辑。

controller 负责保证系统安全，包含权限校验和参数校验。service 的方法默认认为当自己被执行时，执行者拥有充分的权限执行这些指令（例如，如果某个 vesper 用户希望删除某个文件，controller 需要确保该用户有资格（资格指的是 vesper 系统内的资格，而不是 Linux 用户组权限）执行删除操作。service 不需要检查这个 vesper 用户是否拥有该权限）。

特别注意，service 内的部分逻辑会使用 sudo 执行。controller 不能过度依赖 Linux 自带的权限校验机制。
