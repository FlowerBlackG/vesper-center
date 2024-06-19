# Vesper Center

## 姊妹项目

### vesper 核心

Wayland 图形合成器 & 窗口管理；VNC 远程桌面服务器。

[https://github.com/FlowerBlackG/vesper](https://github.com/FlowerBlackG/vesper)

### vesper 启动器

通过 domain socket 向刚登录的用户账户发送程序启动指令。

[https://github.com/FlowerBlackG/vesper-launcher](https://github.com/FlowerBlackG/vesper-launcher)

### vesper 中央服务

为前端操作台提供服务，控制 vesper 启动器与 vesper 核心工作。

[https://github.com/FlowerBlackG/vesper-center](https://github.com/FlowerBlackG/vesper-center)

### vesper 前端操作台

用户的操作界面。

[https://github.com/FlowerBlackG/vesper-front](https://github.com/FlowerBlackG/vesper-front)

## 使用手册

[系统手册](./doc/system-manual.md)

## 必备的环境变量

### VESPER_CENTER_DATA_DIR

Vesper Center 存放数据的地方。

### VESPER_CENTER_TICKET_LOCKER_ENABLE_DUMP

```
true / false
```

### VESPER_CENTER_DB_USERNAME

### VESPER_CENTER_DB_PASSWORD
