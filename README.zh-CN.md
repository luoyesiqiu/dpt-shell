# dpt-shell

[![](https://img.shields.io/github/license/luoyesiqiu/dpt-shell)](https://github.com/luoyesiqiu/dpt-shell/blob/main/LICENSE) [![](https://img.shields.io/github/downloads/luoyesiqiu/dpt-shell/total?color=blue)](https://github.com/luoyesiqiu/dpt-shell/releases/latest) [![](https://img.shields.io/github/issues-raw/luoyesiqiu/dpt-shell?color=red)](https://github.com/luoyesiqiu/dpt-shell/issues) ![](https://img.shields.io/badge/Android-5.0%2B-brightgreen)

[English](./README.md) | 简体中文 

dpt-shell 是一种将 dex 文件中的函数代码抽空，然后在程序运行时将函数代码填回的那么一种壳。

## 用法

### 快速使用

转到 [Releases](https://github.com/luoyesiqiu/dpt-shell/releases/latest) 页面下载 `executable.zip`，解压，执行以下命令：

```shell
java -jar dpt.jar -f /path/to/android-package-file
```

### 手动编译

```shell
git clone --recursive https://github.com/luoyesiqiu/dpt-shell
cd dpt-shell
./gradlew assemble
cd executable
java -jar dpt.jar -f /path/to/android-package-file
```

### 命令行参数

```text
usage: java -jar dpt.jar [option] -f <package_file>
 -c,--disable-acf          Disable app component factory(just use for
                           debug).
 -d,--dump-code            Dump the code item of DEX and save it to .json
                           files.
 -D,--debug                Make package debuggable.
 -e,--exclude-abi <arg>    Exclude specific ABIs (comma separated, e.g.
                           x86,x86_64).
                           Supported ABIs:
                           - arm       (armeabi-v7a)
                           - arm64     (arm64-v8a)
                           - x86
                           - x86_64
 -f,--package-file <arg>   Need to protect android package(*.apk, *.aab)
                           file.
 -K,--keep-classes         Keeping some classes in the package can improve
                           the app's startup speed to a certain extent,
                           but it is not supported by some application
                           packages.
 -l,--noisy-log            Open noisy log.
 -o,--output <arg>         Output directory for protected package.
 -r,--rules-file <arg>     Rules file for class names that will not be
                           protected.
 -v,--version              Show program's version number.
 -x,--no-sign              Do not sign package.
```

## 原理解析

[How it works](doc/HowItWorks.zh-CN.md)

## 声明

本项目未经大量测试，仅用于学习交流，不要线上使用，否则自行承担后果。

## 使用或依赖以下项目

- [dx](https://android.googlesource.com/platform/dalvik/+/refs/heads/master/dx/)
- [Dobby](https://github.com/jmpews/Dobby)
- ~~[libzip-android](https://github.com/julienr/libzip-android)~~
- [ManifestEditor](https://github.com/WindySha/ManifestEditor)
- ~~[Xpatch](https://github.com/WindySha/Xpatch)~~
- [bhook](https://github.com/bytedance/bhook)
- [zipalign-java](https://github.com/Iyxan23/zipalign-java)
- [minizip-ng](https://github.com/zlib-ng/minizip-ng)
- [JSON-java](https://github.com/stleary/JSON-java)
- [zip4j](https://github.com/srikanth-lingala/zip4j)
- [commons-cli](https://github.com/apache/commons-cli)
- [dexmaker](https://android.googlesource.com/platform/external/dexmaker)
- [Obfuscate](https://github.com/adamyaxley/Obfuscate)