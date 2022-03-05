# dpt-shell

## 介绍

函数抽取壳是一种将dex文件中的函数代码抽空，然后在程序运行时将函数代码填回的那么一种壳。


dpt目前已适配Android6~11。想要了解实现原理，可以查看[文档](doc/HowItWorks.md)

## 用法

转到[Release页面](https://github.com/luoyesiqiu/dpt-shell/releases)下载executable.zip，解压，执行：

```
java -jar dpt.jar /path/to/apk
```

## 感谢

- [Dobby](https://github.com/jmpews/Dobby)
- [libzip-android](https://github.com/julienr/libzip-android)