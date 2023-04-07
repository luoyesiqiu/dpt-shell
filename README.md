# dpt-shell

[![](https://img.shields.io/github/license/luoyesiqiu/dpt-shell)](https://github.com/luoyesiqiu/dpt-shell/blob/main/LICENSE) [![](https://img.shields.io/github/downloads/luoyesiqiu/dpt-shell/total?color=blue)](https://github.com/luoyesiqiu/dpt-shell/releases/latest) [![](https://img.shields.io/github/issues-raw/luoyesiqiu/dpt-shell?color=red)](https://github.com/luoyesiqiu/dpt-shell/issues) ![](https://img.shields.io/badge/Android-6.0%2B-brightgreen)

## 介绍

dpt-shell是函数抽取壳的实现。一种将dex文件中的函数代码抽空，然后在程序运行时将函数代码填回的那么一种壳。

## 声明

本项目未经大量测试，仅用于学习交流，不要线上使用，否则自行承担后果。

## 用法

### 快速使用

转到[Releases](https://github.com/luoyesiqiu/dpt-shell/releases/latest)页面下载executable.zip，解压，执行：

```shell
java -jar dpt.jar /path/to/apk
```

### 手动编译

```shell
git clone --recursive https://github.com/luoyesiqiu/dpt-shell
cd dpt-shell
gradlew dpt:assemble
gradlew shell:assemble
cd executable
java -jar dpt.jar /path/to/apk
```

## 原理解析

[How it works](doc/HowItWorks.md)

## 感谢

- [dx](https://android.googlesource.com/platform/dalvik/+/refs/heads/master/dx/)
- [Dobby](https://github.com/jmpews/Dobby)
- ~~[libzip-android](https://github.com/julienr/libzip-android)~~
- [ManifestEditor](https://github.com/WindySha/ManifestEditor)
- [Xpatch](https://github.com/WindySha/Xpatch)
- [bhook](https://github.com/bytedance/bhook)
- [zipalign-java](https://github.com/Iyxan23/zipalign-java)
- [minizip-ng](https://github.com/zlib-ng/minizip-ng)
- [JSON-java](https://github.com/stleary/JSON-java)