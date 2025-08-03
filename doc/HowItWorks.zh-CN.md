## 0x0 前言

函数抽取壳这个词不知道从哪起源的，但我理解的函数抽取壳是那种将dex文件中的函数代码给nop，然后在运行时再把字节码给填回dex的这么一种壳。

函数抽取前：

![](unnop.png)

函数抽取后：

![](nop.png)


## 0x1 项目的结构

dpt代码分为两个部分，一个是processor，另一个是shell。

processor是可以将普通apk处理成加壳apk的模块。它的主要功能有：

- 解压apk

- 提取apk中的dex的CodeItem保存起来

- 修改AndroidManifest.xml中的Application类名

- 生成新的apk

流程如下：

![](processor.png)

shell模块最终生成的dex文件和so文件将被集成到需要加壳的apk中。它的要功能有：

- 处理App的启动

- 替换dexElements

- hook相关函数

- 调用目标Application

- CodeItem文件读取

- CodeItem填回


流程如下：


![](shell.png)

## 0x2 processor

processor比较重要的逻辑两点，AndroidManifest.xml的处理和CodeItem的提取

### （1）处理AndroidManifest.xml

我们处理AndroidManifest.xml的操作主要是备份原Application的类名和写入壳的代理Application的类名。备份原Application类名目的是在壳的流程执行完成后，调用我们原APK的Application。写入壳的代理Application类名的目的是在app启动时尽早的启动我们的代理Application，这样我们就可以做一些准备工作，比如自定义加载dex,Hook一些函数等。我们知道，AndroidManifest.xml在生成apk后它不是以普通xml文件的格式来存放的，而是以axml格式来存放的。不过幸运的是，已经有许多大佬写了对axml解析和编辑的库，我们直接拿来用就行。这里用到的axml处理的库是[ManifestEditor](https://github.com/WindySha/ManifestEditor)。

提取原AndroidManifest.xml Application完整类名代码如下，直接调用getApplicationName函数即可

```java

    public static String getValue(String file,String tag,String ns,String attrName){
        byte[] axmlData = IoUtils.readFile(file);
        AxmlParser axmlParser = new AxmlParser(axmlData);
        try {
            while (axmlParser.next() != AxmlParser.END_FILE) {
                if (axmlParser.getAttrCount() != 0 && !axmlParser.getName().equals(tag)) {
                    continue;
                }
                for (int i = 0; i < axmlParser.getAttrCount(); i++) {
                    if (axmlParser.getNamespacePrefix().equals(ns) && axmlParser.getAttrName(i).equals(attrName)) {
                        return (String) axmlParser.getAttrValue(i);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getApplicationName(String file) {
        return getValue(file,"application","android","name");
    }
```

写入Application类名的代码如下：

```java
    public static void writeApplicationName(String inManifestFile, String outManifestFile, String newApplicationName){
        ModificationProperty property = new ModificationProperty();
        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.NAME,newApplicationName));

        FileProcesser.processManifestFile(inManifestFile, outManifestFile, property);

    }
```

### (2) 提取CodeItem

CodeItem是dex文件中存放函数字节码相关数据的结构。下图显示的就是CodeItem大概的样子。

![](codeitem.png)

说是提取CodeItem，其实我们提取的是CodeItem中的insns，它里面存放的是函数真正的字节码。提取insns，我们使用的是Android源码中的[dx](https://android.googlesource.com/platform/dalvik/+/refs/heads/master/dx/)工具，使用dx工具可以很方便的读取dex文件的各个部分。

下面的代码遍历所有ClassDef，并遍历其中的所有函数，再调用extractMethod对单个函数进行处理。

```java
    public static List<Instruction> extractAllMethods(File dexFile, File outDexFile) {
        List<Instruction> instructionList = new ArrayList<>();
        Dex dex = null;
        RandomAccessFile randomAccessFile = null;
        byte[] dexData = IoUtils.readFile(dexFile.getAbsolutePath());
        IoUtils.writeFile(outDexFile.getAbsolutePath(),dexData);

        try {
            dex = new Dex(dexFile);
            randomAccessFile = new RandomAccessFile(outDexFile, "rw");
            Iterable<ClassDef> classDefs = dex.classDefs();
            for (ClassDef classDef : classDefs) {
                
                ......
                
                if(classDef.getClassDataOffset() == 0){
                    String log = String.format("class '%s' data offset is zero",classDef.toString());
                    logger.warn(log);
                    continue;
                }

                ClassData classData = dex.readClassData(classDef);
                ClassData.Method[] directMethods = classData.getDirectMethods();
                ClassData.Method[] virtualMethods = classData.getVirtualMethods();
                for (ClassData.Method method : directMethods) {
                    Instruction instruction = extractMethod(dex,randomAccessFile,classDef,method);
                    if(instruction != null) {
                        instructionList.add(instruction);
                    }
                }

                for (ClassData.Method method : virtualMethods) {
                    Instruction instruction = extractMethod(dex, randomAccessFile,classDef, method);
                    if(instruction != null) {
                        instructionList.add(instruction);
                    }
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        finally {
            IoUtils.close(randomAccessFile);
        }

        return instructionList;
    }
```

处理函数的过程中发现没有代码（通常为native函数）或者insns的容量不足以填充return语句则跳过处理。这里就是对应函数抽取壳的抽取操作

```java
    private static Instruction extractMethod(Dex dex ,RandomAccessFile outRandomAccessFile,ClassDef classDef,ClassData.Method method)
            throws Exception{
        String returnTypeName = dex.typeNames().get(dex.protoIds().get(dex.methodIds().get(method.getMethodIndex()).getProtoIndex()).getReturnTypeIndex());
        String methodName = dex.strings().get(dex.methodIds().get(method.getMethodIndex()).getNameIndex());
        String className = dex.typeNames().get(classDef.getTypeIndex());
        //native函数或者abstract函数
        if(method.getCodeOffset() == 0){
            String log = String.format("method code offset is zero,name =  %s.%s , returnType = %s",
                    TypeUtils.getHumanizeTypeName(className),
                    methodName,
                    TypeUtils.getHumanizeTypeName(returnTypeName));
            logger.warn(log);
            return null;
        }
        Instruction instruction = new Instruction();
        //16 = registers_size + ins_size + outs_size + tries_size + debug_info_off + insns_size
        int insnsOffset = method.getCodeOffset() + 16;
        Code code = dex.readCode(method);
        //容错处理
        if(code.getInstructions().length == 0){
            String log = String.format("method has no code,name =  %s.%s , returnType = %s",
                    TypeUtils.getHumanizeTypeName(className),
                    methodName,
                    TypeUtils.getHumanizeTypeName(returnTypeName));
            logger.warn(log);
            return null;
        }
        int insnsCapacity = code.getInstructions().length;
        //insns容量不足以存放return语句，跳过
        byte[] returnByteCodes = getReturnByteCodes(returnTypeName);
        if(insnsCapacity * 2 < returnByteCodes.length){
            logger.warn("The capacity of insns is not enough to store the return statement. {}.{}() -> {} insnsCapacity = {}byte(s),returnByteCodes = {}byte(s)",
                    TypeUtils.getHumanizeTypeName(className),
                    methodName,
                    TypeUtils.getHumanizeTypeName(returnTypeName),
                    insnsCapacity * 2,
                    returnByteCodes.length);

            return null;
        }
        instruction.setOffsetOfDex(insnsOffset);
        //这里的MethodIndex对应method_ids区的索引
        instruction.setMethodIndex(method.getMethodIndex());
        //注意：这里是数组的大小
        instruction.setInstructionDataSize(insnsCapacity * 2);
        byte[] byteCode = new byte[insnsCapacity * 2];
        //写入nop指令
        for (int i = 0; i < insnsCapacity; i++) {
            outRandomAccessFile.seek(insnsOffset + (i * 2));
            byteCode[i * 2] = outRandomAccessFile.readByte();
            byteCode[i * 2 + 1] = outRandomAccessFile.readByte();
            outRandomAccessFile.seek(insnsOffset + (i * 2));
            outRandomAccessFile.writeShort(0);
        }
        instruction.setInstructionsData(byteCode);
        outRandomAccessFile.seek(insnsOffset);
        //写出return语句
        outRandomAccessFile.write(returnByteCodes);

        return instruction;
    }
```

## 0x3 shell模块

shell模块是函数抽取壳的主要逻辑，它的功能我们上面已经讲过。

### (1) Hook函数

Hook函数时机最好要早点，dpt在`.init_array`节被加载时开始进行一系列Hook

```cpp
__attribute__ ((constructor)) void init_dpt() {
    dpt_hook();
}
```

Hook框架使用的[Dobby](https://github.com/jmpews/Dobby)和[bhook](https://github.com/bytedance/bhook)，主要Hook两个函数：mmap和DefineClass。

#### **mmap**

Hook mmap函数的目的是在我们加载dex能够修改dex的属性，让加载的dex可写，这样我们才能把字节码填回dex，有大佬详细的分析过，具体参考[这篇文章](https://bbs.pediy.com/thread-266527.htm)。

```cpp
bytehook_stub_t stub = bytehook_hook_single(
        getArtLibName(),
        "libc.so",
        "mmap",
        (void*)fake_mmap,
        nullptr,
        nullptr);
if(stub != nullptr){
    DLOGD("mmap hook success!");
}
```
Hook到了之后，给__prot参数追加PROT_WRITE属性

```cpp
void* fake_mmap(void* __addr, size_t __size, int __prot, int __flags, int __fd, off_t __offset){
    BYTEHOOK_STACK_SCOPE();
    int hasRead = (__prot & PROT_READ) == PROT_READ;
    int hasWrite = (__prot & PROT_WRITE) == PROT_WRITE;
    int prot = __prot;

    if(hasRead && !hasWrite) {
        prot = prot | PROT_WRITE;
        DLOGD("fake_mmap call fd = %p,size = %d, prot = %d,flag = %d",__fd,__size, prot,__flags);
    }

    void *addr = BYTEHOOK_CALL_PREV(fake_mmap,__addr,  __size, prot,  __flags,  __fd,  __offset);
    return addr;
}
```

#### **DefineClass**

在Hook DefineClass函数之前，我们需要了解DefineClass函数流程。为什么是DefineClass函数，其他函数是否可行？

当一个类被加载的时候，它的调用顺序是这样的(部分流程已省略)：

1. ClassLoader.java::loadClass 
2. DexFile.java::defineClass 
3. class_linker.cc::DefineClass 
4. class_linker.cc::LoadClass 
5. class_linker.cc::LoadClassMembers 
6. class_linker.cc::LoadMethod

也就是说，当一个类被加载，它是会去调用DefineClass函数的，我们看一下它的函数原型：

```cpp
mirror::Class* ClassLinker::DefineClass(Thread* self,
                                        const char* descriptor,
                                        size_t hash,
                                        Handle<mirror::ClassLoader> class_loader,
                                        const DexFile& dex_file,
                                        const DexFile::ClassDef& dex_class_def);
```

DefineClass函数的参数很巧，有DexFile结构，还有ClassDef结构，我们通过Hook这个函数就知道以下信息：
- 加载的类来自哪个dex文件
- 加载类的数据的偏移

第一条可以帮助我们大致定位到存储的CodeItem的位置；第二条可以帮助我们找到CodeItem具体存储的位置以及填充到的位置。

来看一下ClassDef的定义：

```cpp
struct ClassDef {
    uint32_t class_idx_;  // index into type_ids_ array for this class
    uint32_t access_flags_;
    uint32_t superclass_idx_;  // index into type_ids_ array for superclass
    uint32_t interfaces_off_;  // file offset to TypeList
    uint32_t source_file_idx_;  // index into string_ids_ for source file name
    uint32_t annotations_off_;  // file offset to annotations_directory_item
    uint32_t class_data_off_;  // file offset to class_data_item
    uint32_t static_values_off_;  // file offset to EncodedArray
};
```

其中最重要的字段就是`class_data_off_`它的值是当前加载的类的具体数据在dex文件中的偏移，通过这个字段就可以顺藤摸瓜定位到当前加载类的所有函数的在内存中CodeItem的具体位置。

代码如下：

```cpp
void* DefineClass(void* thiz,void* self,
                 const char* descriptor,
                 size_t hash,
                 void* class_loader,
                 const void* dex_file,
                 const void* dex_class_def) {

    ......

    auto* class_def = (dex::ClassDef *)dex_class_def;

    size_t read = 0;
    auto *class_data = (uint8_t *)((uint8_t *)begin + class_def->class_data_off_);

    uint64_t static_fields_size = 0;
    read += DexFileUtils::readUleb128(class_data, &static_fields_size);

    uint64_t instance_fields_size = 0;
    read += DexFileUtils::readUleb128(class_data + read, &instance_fields_size);

    uint64_t direct_methods_size = 0;
    read += DexFileUtils::readUleb128(class_data + read, &direct_methods_size);

    uint64_t virtual_methods_size = 0;
    read += DexFileUtils::readUleb128(class_data + read, &virtual_methods_size);

    dex::ClassDataField staticFields[static_fields_size];
    read += DexFileUtils::readFields(class_data + read,staticFields,static_fields_size);

    dex::ClassDataField instanceFields[instance_fields_size];
    read += DexFileUtils::readFields(class_data + read,instanceFields,instance_fields_size);

    dex::ClassDataMethod directMethods[direct_methods_size];
    read += DexFileUtils::readMethods(class_data + read,directMethods,direct_methods_size);

    dex::ClassDataMethod virtualMethods[virtual_methods_size];
    read += DexFileUtils::readMethods(class_data + read,virtualMethods,virtual_methods_size);

    for(int i = 0;i < direct_methods_size;i++){
        auto method = directMethods[i];
        patchMethod(begin, location.c_str(), dexSize, dexIndex, method.method_idx_delta_,method.code_off_);
    }

    for(int i = 0;i < virtual_methods_size;i++){
        auto method = virtualMethods[i];
        patchMethod(begin, location.c_str(), dexSize, dexIndex, method.method_idx_delta_,method.code_off_);
    }

    ......
}
```

ClassDef这个结构还有一个特点，它是dex文件的结构，也就是说dex文件格式不变，它一般就不会变。

还有，DefineClass函数的参数会改变吗？目前来看从Android M到现在没有变过。

所以使用它不用太担心随着Android版本的升级而导致字段偏移的变化，也就是兼容性较强。

这就是为什么用DefineClass作为Hook点。

**Hook其他函数是否可行？**

答案是肯定的，dpt之前就是使用的LoadMethod函数作为Hook点，在LoadMethod函数里面做CodeItem填充操作。

但是后来发现，LoadMethod函数参数不太固定，随着Android版本的升级可能要不断适配，而且每个函数都要填充，会影响一定的性能。

### (2) 加载dex

所有apk中的dex在处理阶段dpt都把它们放到了单独的zip文件中，不存在apk中了，所以App启动时要手动加载。

系统加载的dex是以只读方式加载的，我们没办法去修改dex那一部分的内存，所以我们要手动加载apk中的dex文件。

```java
    private ClassLoader loadDex(Context context){
        String sourcePath = context.getApplicationInfo().sourceDir;
        String nativePath = context.getApplicationInfo().nativeLibraryDir;

        ShellClassLoader shellClassLoader = new ShellClassLoader(sourcePath,nativePath,ClassLoader.getSystemClassLoader());
        return shellClassLoader;
    }
```

自定义的ClassLoader

```java
public class ShellClassLoader extends PathClassLoader {

    private final String TAG = ShellClassLoader.class.getSimpleName();

    public ShellClassLoader(String dexPath,ClassLoader classLoader) {
        super(dexPath,classLoader);
    }

    public ShellClassLoader(String dexPath, String librarySearchPath,ClassLoader classLoader) {
        super(dexPath, librarySearchPath, classLoader);
    }
}
```

### (3) 合并dexElements

这一步也非常重要。我们加载apk，dex或者jar，它是以Element方式存放在内存中的，合并dexElements目的是把我们新加载的dex放到dexElements数组开头，这样ClassLoader加载类时就会优先从我们的dex中查找。代码如下：

```cpp
void combineDexElements(JNIEnv* env,jclass klass,jobject oldClassLoader,jobject newClassLoader){
    jclass BaseDexClassLoaderClass = env->FindClass("dalvik/system/BaseDexClassLoader");
    jfieldID  pathList = env->GetFieldID(BaseDexClassLoaderClass,"pathList","Ldalvik/system/DexPathList;");
    jobject oldDexPathListObj = env->GetObjectField(oldClassLoader,pathList);
    if(env->ExceptionCheck() || nullptr == oldDexPathListObj ){
        env->ExceptionClear();
        return;
    }
    jobject newDexPathListObj = env->GetObjectField(newClassLoader,pathList);
    if(env->ExceptionCheck() || nullptr == newDexPathListObj){
        env->ExceptionClear();
        return;
    }

    jclass DexPathListClass = env->FindClass("dalvik/system/DexPathList");
    jfieldID  dexElementField = env->GetFieldID(DexPathListClass,"dexElements","[Ldalvik/system/DexPathList$Element;");


    jobjectArray newClassLoaderDexElements = static_cast<jobjectArray>(env->GetObjectField(
            newDexPathListObj, dexElementField));
    if(env->ExceptionCheck() || nullptr == newClassLoaderDexElements){
        env->ExceptionClear();
        return;
    }

    jobjectArray oldClassLoaderDexElements = static_cast<jobjectArray>(env->GetObjectField(
            oldDexPathListObj, dexElementField));
    if(env->ExceptionCheck() || nullptr == oldClassLoaderDexElements){
        env->ExceptionClear();
        return;
    }
    jint oldLen = env->GetArrayLength(oldClassLoaderDexElements);
    jint newLen = env->GetArrayLength(newClassLoaderDexElements);

    jclass ElementClass = env->FindClass("dalvik/system/DexPathList$Element");

    jobjectArray  newElementArray = env->NewObjectArray(oldLen + newLen,ElementClass, nullptr);

    for(int i = 0;i < newLen;i++) {
        jobject elementObj = env->GetObjectArrayElement(newClassLoaderDexElements, i);
        env->SetObjectArrayElement(newElementArray,i,elementObj);
    }

    for(int i = newLen;i < oldLen + newLen;i++) {
        jobject elementObj = env->GetObjectArrayElement(oldClassLoaderDexElements, i - newLen);
        env->SetObjectArrayElement(newElementArray,i,elementObj);
    }
    env->SetObjectField(oldDexPathListObj, dexElementField,newElementArray);
}
```

### (4) AppComponentFactory

从Android P开始，Android添加了`android.app.AppComponentFactory`类，它允许开发者覆盖Android的常用组件。

AppComponentFactory支持开发者对Application,Activity,Service,Receiver,Provider,ClassLoader(AndroidQ支持)等组件的替换。

这意味着开发者想替换Application等组件时不用写一堆反射代码了，对加固或者插件开发者带来极大的便利。

dpt在AppComponentFactory类的instantiateClassLoader和instantiateApplication函数中做了替换ClassLoader和Application的操作。

具体可以看[ProxyComponentFactory](https://github.com/luoyesiqiu/dpt-shell/blob/main/shell/src/main/java/com/luoyesiqiu/shell/ProxyComponentFactory.java)类，这里不再贴出。

### (5) 性能优化

dpt中有几个性能优化的细节：

- 使用`mmap`函数映射apk到内存，然后再从内存中读取apk中的信息，这样做比从本地直接读apk性能要好上不少，尤其体现在大型apk上。
- 对于需要填充的CodeItem来讲，插入和查找非常频繁，但是在内存中存储的顺序并不重要。基于这个需求，使用足够的`std::vector`来存储从本地加载到的CodeItem地址，插入和查找对应函数的CodeItem的时间复杂度都是O(1)。
- 加载对齐（zipalign）且未压缩的zip文件来存储dex文件，可以避免art额外的解压耗时。