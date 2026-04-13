package chocopy.pa2;

import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.Type;
import chocopy.common.astnodes.Program;

/** Top-level class for performing semantic analysis. */
public class StudentAnalysis {

    /** Perform semantic analysis on PROGRAM, adding error messages and
     *  type annotations. Provide debugging output iff DEBUG. Returns modified
     *  tree. */
    public static Program process(Program program, boolean debug) {
        if (program.hasErrors()) {
            return program;
        }

        SemanticScope globals = new SemanticScope();

        /** Preload Information into Semantic Scope **/
        ClassInfo objectClass = new ClassInfo("object");
        ClassInfo intClass = new ClassInfo("int");
        ClassInfo boolClass = new ClassInfo("bool");
        ClassInfo strClass = new ClassInfo("str");

        globals.put("object", objectClass);
        globals.put("int", intClass);
        globals.put("bool", boolClass);
        globals.put("str", strClass);

        intClass.setSuperClass(objectClass);
        boolClass.setSuperClass(objectClass);
        strClass.setSuperClass(objectClass);

        FuncInfo objectInit = new FuncInfo(
                "__init__",
                java.util.List.of(Type.OBJECT_TYPE),
                Type.NONE_TYPE,
                true
        );
        objectClass.addMethod(objectInit);

        FuncInfo printFunc = new FuncInfo(
                "print",
                java.util.List.of(Type.OBJECT_TYPE),
                Type.NONE_TYPE,
                false
        );

        globals.put("print", printFunc);

        FuncInfo lenFunc = new FuncInfo(
                "len",
                java.util.List.of(Type.OBJECT_TYPE),
                Type.INT_TYPE,
                false
        );

        globals.put("len", lenFunc);
        /** End Preloads **/

        DeclarationAnalyzer declarationAnalyzer =
                new DeclarationAnalyzer(program.errors, globals);
        program.dispatch(declarationAnalyzer);

        if (!program.hasErrors()) {
            TypeChecker typeChecker =
                    new TypeChecker(globals, program.errors);
            program.dispatch(typeChecker);
        }

        return program;
    }
}
