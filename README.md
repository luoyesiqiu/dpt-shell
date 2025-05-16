# dpt-shell

[![](https://img.shields.io/github/license/luoyesiqiu/dpt-shell)](https://github.com/luoyesiqiu/dpt-shell/blob/main/LICENSE) [![](https://img.shields.io/github/downloads/luoyesiqiu/dpt-shell/total?color=blue)](https://github.com/luoyesiqiu/dpt-shell/releases/latest) [![](https://img.shields.io/github/issues-raw/luoyesiqiu/dpt-shell?color=red)](https://github.com/luoyesiqiu/dpt-shell/issues) ![](https://img.shields.io/badge/Android-5.0%2B-brightgreen)

English | [简体中文](./README.zh-CN.md) 

dpt-shell is an android Dex protect shell that makes Dex's functions code empty and fix on run.

## Usage

### Quick uses

Go to [Releases](https://github.com/luoyesiqiu/dpt-shell/releases/latest) download `executable.zip` and unzip it, run the follow command lines in terminal: 

```shell
java -jar dpt.jar -f /path/to/android-package-file
```

### Manual builds

```shell
git clone --recursive https://github.com/luoyesiqiu/dpt-shell
cd dpt-shell
./gradlew assemble
cd executable
java -jar dpt.jar -f /path/to/android-package-file
```

### Command line options

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
 -l,--noisy-log            Open noisy log.
 -o,--output <arg>         Output directory for protected package.
 -v,--version              Show program's version number.
 -x,--no-sign              Do not sign package.
```

## Notice

This project has not too many tests, be careful use in prod environment. Otherwise, all consequences are at your own risk.

## Dependency or use follows project code

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
