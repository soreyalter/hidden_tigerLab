# 辅助测试命令
## Lab 3
- exercise 1: compile the test case
```shell
java --enable-preview -cp ./out/production/tiger Tiger -builtin SumRec.java -trace cfg.Translate.doitProgram
java --enable-preview -cp ./out/production/tiger Tiger ./test/SumRec.java -trace cfg.Translate.doitProgram

```
exercise 4: draw a sample CFG
```shell
java --enable-preview -cp ./out/production/tiger Tiger -builtin SumRec.java -dot cfg
java --enable-preview -cp ./out/production/tiger Tiger -builtin SumRec.java -trace cfg.Translate.doitProgram -dot cfg

```
exercise 5: build inherit tree
```shell
# 测试

# 画图
dot -Tpng example.dot -o example.png

```

## Lab 4
```shell
java --enable-preview -cp ./out/production/tiger Tiger -builtin SumRec.java -trace codegen.Layout.layoutProgram
```