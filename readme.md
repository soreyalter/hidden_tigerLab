# POPL lab1

**注意：**

- build excludes ./test
- test with command: 

```powershell
java --enable-preview -cp ./out/production/tiger-sorey Tiger [testFilePath] -dump token
```



**实验结果：**

- Parse .\test\BinarySearch.java 并打印 token

```
java --enable-preview -cp production/tiger-sorey Tiger D:\USTC_POPL\tiger-sorey\test\BinarySearch.java -dump token
```

- 可以输出最后的文件终结符 token EOF

![Snipaste_2024-05-18_16-19-09](D:\USTC_POPL\tiger-sorey\assets\Snipaste_2024-05-18_16-19-09.jpg)



IDE：

- IntelliJ IDEA 2023.3.4 (Ultimate Edition)

JDK:

- Oracle OpenJDK version 22.0.1

