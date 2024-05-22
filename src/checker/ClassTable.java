package checker;

import ast.Ast;
import ast.Ast.Type;
import util.Id;
import util.Todo;
import util.Tuple;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ClassTable {

    private static void error(String msg) {
        System.out.println(msg);
        System.exit(1);
    }

    // a special type for method: argument and return types
    // 包括 返回类型 和 形参类型列表
    public record MethodType(Type.T retType,
                             List<Ast.Type.T> argsType) {
        @Override
        public String toString() {
            // (arg1Type, arg2Type,) -> returnType
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("(");
            for (Type.T type : this.argsType) {
                // stringBuilder.append(STR."\{type.toString()}, ");
                stringBuilder.append(STR."\{Type.convertString(type)}, ");
            }
            stringBuilder.append(STR.") -> \{this.retType.toString()}");
            return stringBuilder.toString();
        }
    }

    // the binding for a class
    public record Binding(
            // null for empty extends
            Id extends_,
            Ast.Class.T self,
            // the field in a class: its type and fresh id
            java.util.HashMap<Id, Tuple.Two<Type.T, Id>> fields,
            // the method in a class: its type and fresh id
            java.util.HashMap<Id, Tuple.Two<MethodType, Id>> methods) {

        // 类新增属性
        public void putField(Id fieldId, Type.T type, Id freshId) {
            if (this.fields.get(fieldId) != null) {
                error(STR."duplicated class field: \{fieldId}");
            }
            this.fields.put(fieldId, new Tuple.Two<>(type, freshId));
        }

        public void putMethod(Id mid, MethodType methodType, Id freshId) {
            if (this.methods.get(mid) != null) {
                error(STR."duplicated class method: \{mid}");
            }
            this.methods.put(mid, new Tuple.Two<>(methodType, freshId));
        }

        @Override
        public String toString() {
            System.out.print("extends: ");
            System.out.println(Objects.requireNonNullElse(this.extends_, "<>"));
            System.out.println("\nfields:\n  ");
            System.out.println(fields.toString());
            System.out.println("\nmethods:\n  ");
            System.out.println(methods.toString());
            return "";
        }
    }

    // map each class, to its corresponding class binding.
    // class Id -> class Binding -> Class.T
    private final java.util.HashMap<Id, Binding> classTable;

    public ClassTable() {
        // 构造函数初始化一个 映射表
        this.classTable = new java.util.HashMap<>();
    }

    // Duplication is not allowed
    public void putClass(Id classId, Id extends_, Ast.Class.T self) {
        if (this.classTable.get(classId) != null) {
            error(STR."duplicated class: \{classId}");
        }
        Binding classBinding = new Binding(extends_,
                self,
                new HashMap<>(),
                new HashMap<>());
        this.classTable.put(classId, classBinding);
    }

    // put a field into class table
    // Duplication is not allowed
    public void putField(Id classId, Ast.AstId fieldId, Type.T type) {
        Binding classBinding = this.classTable.get(classId);
        Id freshId = fieldId.genFreshId();
        classBinding.putField(fieldId.id, type, freshId);
    }

    // put a method into class table
    // Duplication is not allowed.
    // Also note that MiniJava does NOT allow overloading.
    public void putMethod(Id classId, Ast.AstId methodId, MethodType type) {
        Binding classBinding = this.classTable.get(classId);
        Id freshId = methodId.genFreshId();
        classBinding.putMethod(methodId.id, type, freshId);
    }

    // return null for non-existing class
    // "getClass" is a library API, so we use "getClass_"
    public Binding getClass_(Id classId) {
        return this.classTable.get(classId);
    }

    // get type of some field
    // return null for non-existing field.
    public Tuple.Two<Type.T, Id> getField(Id classId, Id fieldId) {
        Binding classBinding = this.classTable.get(classId);
        var result = classBinding.fields.get(fieldId);
        while (result == null) { // search all parent classes until found or fail
            if (classBinding.extends_ == null)
                return null;
            classBinding = this.classTable.get(classBinding.extends_);
            result = classBinding.fields.get(fieldId);
        }
        return result;
    }

    // get type of given method
    // return null for non-existing method
    public Tuple.Two<MethodType, Id> getMethod(Id classId, Id methodId) {
        Binding classBinding = this.classTable.get(classId);
        var result = classBinding.methods.get(methodId);
        while (result == null) { // search all parent classes until found or fail
            if (classBinding.extends_ == null)
                return null;
            classBinding = this.classTable.get(classBinding.extends_);
            result = classBinding.methods.get(methodId);
        }
        return result;
    }

    // lab 2, exercise 7:
    public void dump() {
        // throw new Todo();
        for (Id key : this.classTable.keySet()) {
            Binding binding = this.classTable.get(key);
            System.out.println(STR."========\{key}:");
            binding.toString();
        }
    }

    @Override
    public String toString() {
        return this.classTable.toString();
    }
}








