# dpt-shell

## 介绍

函数抽取壳是一种将dex文件中的函数代码抽空，然后在程序运行时将函数代码填回的那么一种壳。


dpt目前已适配Android6~11。想要了解实现原理，可以查看[文档](doc/HowItWorks.md)


已实现自动签名(cv自Xpatch)

## 用法

转到[Release页面](https://github.com/luoyesiqiu/dpt-shell/releases)下载executable.zip，解压，执行：


## 编译使用
```
对整个项目assemble
cd executable
java -jar dpt.jar /path/to/apk
```

## 感谢

- [dx](https://android.googlesource.com/platform/dalvik/+/refs/heads/master/dx/)
- [Dobby](https://github.com/jmpews/Dobby)
- [libzip-android](https://github.com/julienr/libzip-android)
- [ManifestEditor](https://github.com/WindySha/ManifestEditor)
- [Xpatch](https://github.com/WindySha/Xpatch)