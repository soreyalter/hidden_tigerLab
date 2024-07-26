package regalloc;

import cfg.Cfg;
import codegen.Munch;
import codegen.X64;
import control.Control;
import util.Id;
import util.Todo;
import util.Trace;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

// A register allocator to allocate each variable to a physical register,
// using a stack-based allocation approach.
public class StackAllocator {

    private X64.Program.T allocProgram0(X64.Program.T x64) {
        // throw new Todo();
        TempMap tempMap = new TempMap();
        switch (x64) {
            case X64.Program.Singleton(
                    Id entryClassName,
                    Id entryFuncName,
                    List<X64.Vtable.T> vtables,
                    List<X64.Struct.T> structs,
                    List<X64.Function.T> functions
            ) -> {
                // step #1: allocating stack slots for variables
                // (i.e., arguments and locals) in a given function;
                for (X64.Function.T function : functions) {
                    X64.Function.Singleton funcSingle = (X64.Function.Singleton) function;
                    Frame funcFrame = new Frame(STR."\{funcSingle.classId()}_\{funcSingle.methodId()}");
                    // function args
                    for (X64.Dec.T formal : funcSingle.formals()) {
                        X64.Dec.Singleton formalSingle = (X64.Dec.Singleton) formal;
                        tempMap.put(formalSingle.id(),
                                new TempMap.Position.InStack(funcFrame.alloc()));
                    }
                    // function locals
                    for (X64.Dec.T local : funcSingle.locals()) {
                        X64.Dec.Singleton localSingle = (X64.Dec.Singleton) local;
                        tempMap.put(localSingle.id(),
                                new TempMap.Position.InStack(funcFrame.alloc()));
                    }

                    // step #2: rewriting each assembly instruction
                    // by inserting necessary load/stores of the variables from/to the stack frame;
                    for (X64.Block.T block : funcSingle.blocks()) {
                        X64.Block.Singleton blockSingle = (X64.Block.Singleton) block;

                        // rewrite instructions
                        List<X64.Instr.T> reInstr = new LinkedList<>();

                        // current instr
                        X64.Instr.T curr;

                        for (X64.Instr.T instr : blockSingle.instrs()) {
                            X64.Instr.Singleton ins = (X64.Instr.Singleton) instr;
                            List<X64.VirtualReg.T> uses = ins.uses();
                            List<X64.VirtualReg.T> defs = ins.defs();
                            List<X64.VirtualReg.T> newUses;
                            List<X64.VirtualReg.T> newDefs;

                            switch (ins.kind()) {
                                case Move -> {
                                    if (Objects.requireNonNull(uses.getFirst()) instanceof X64.VirtualReg.Vid(
                                            Id id,
                                            X64.Type.T ty
                                    )) {
                                        curr = new X64.Instr.Singleton(
                                                X64.Instr.Kind.Move,
                                                (uarg, darg) -> STR."movq\t\{tempMap.getOffset(id)}(%rbp),%r10",
                                                List.of(uses.getFirst()),
                                                List.of(new X64.VirtualReg.Reg(Id.newName("%r10"), new X64.Type.Int()))
                                        );
                                        reInstr.add(curr);
                                    } else {
                                        throw new IllegalStateException("Unexpected value: " + uses.getFirst());
                                    }
                                    curr = new X64.Instr.Singleton(
                                            X64.Instr.Kind.Move,
                                            (uarg, darg) -> STR."movq\t%r10,%r11",
                                            List.of(new X64.VirtualReg.Reg(Id.newName("%r10"), new X64.Type.Int())),
                                            List.of(new X64.VirtualReg.Reg(Id.newName("%r11"), new X64.Type.Int()))
                                    );
                                    reInstr.add(curr);
                                }
                            }
                        }
                    }
                }

            }
        }
        return x64;
    }


    public X64.Program.T allocProgram(X64.Program.T x64) {
        Trace<X64.Program.T, X64.Program.T> trace =
                new Trace<>("regalloc.StackAllocator.allocProgram",
                        this::allocProgram0,
                        x64,
                        X64.Program::pp,
                        X64.Program::pp);
        X64.Program.T result = trace.doit();
        // this should not be controlled by trace
        if (Control.X64.assemFile != null) {
            new PpAssem().ppProgram(result);
        }
        return result;
    }
}