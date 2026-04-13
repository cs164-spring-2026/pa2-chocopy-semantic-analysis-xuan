package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.ListValueType;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.*;


import java.util.ArrayList;
import java.util.List;



/**
 * Analyzes declarations to create a top-level symbol table.
 */
public class DeclarationAnalyzer extends AbstractNodeAnalyzer<Void> {

    /** Current symbol table.  Changes with new declarative region. */
    private SemanticScope sym;
    /** Global symbol table. */
    /*private final SymbolTable<Type> globals = sym;*/
    private final SemanticScope globals;
    /** Receiver for semantic error messages. */
    private final Errors errors;

    /** Tracks which scope we are currently in. */
    private ClassInfo currentClass = null;

    private final java.util.Set<String> classNames = new java.util.HashSet<>();
    private final java.util.Set<String> declaredClasses = new java.util.HashSet<>();

    /** A new declaration analyzer sending errors to ERRORS0. */
    public DeclarationAnalyzer(Errors errors0, SemanticScope globals0) {
        this.errors = errors0;
        this.globals = globals0;
        this.sym = globals0;
    }


    public SemanticScope getGlobals() {
        return globals;
    }

    private void predeclareGlobal(Declaration decl) {
        if (decl instanceof VarDef) {
            VarDef varDef = (VarDef) decl;
            String name = varDef.var.identifier.name;
            Identifier id = varDef.var.identifier;
            ValueType type = ValueType.annotationToValueType(varDef.var.type);

            if (varDef.var.type != null) {
                checkTypeExists(type, varDef.var.type);
            }

            if (sym.declaresHere(name)) {
                errors.semError(id,
                        "Duplicate declaration of identifier in same scope: %s",
                        name);
                return;
            }

            sym.put(name, new VarInfo(name, type, VarInfo.VarKind.GLOBAL));
        } else if (decl instanceof FuncDef) {
            FuncDef nested = (FuncDef) decl;
            String name = nested.name.name;
            Identifier id = nested.name;

            if (sym.declaresHere(name)) {
                errors.semError(id,
                        "Duplicate declaration of identifier in same scope: %s",
                        name);
                return;
            }

            if (classNames.contains(name)) {
                errors.semError(id,
                        "Cannot shadow class name: %s",
                        name);
                return;
            }

            List<ValueType> paramTypes = new ArrayList<>();
            for (TypedVar param : ((FuncDef) decl).params) {
                ValueType t = ValueType.annotationToValueType(param.type);
                if (param.type != null) {
                    checkTypeExists(t, param.type);
                }
                paramTypes.add(t);
            }

            ValueType returnType =
                    nested.returnType == null
                            ? Type.NONE_TYPE
                            : ValueType.annotationToValueType(nested.returnType);

            if (((FuncDef) decl).returnType != null) {
                checkTypeExists(returnType, ((FuncDef) decl).returnType);
            }

            sym.put(name, new FuncInfo(name, paramTypes, returnType, false));
        } else if (decl instanceof ClassDef) {
            ClassDef classDef = (ClassDef) decl;
            String name = classDef.name.name;
            Identifier id = classDef.name;

            if (sym.declaresHere(name)) {
                errors.semError(id,
                        "Duplicate declaration of identifier in same scope: %s",
                        name);
                return;
            }

            sym.put(name, new ClassInfo(name));
        }
    }


    @Override
    public Void analyze(Program program) {
        /** Initial Pass for class names to avoid shadowing. **/
        for (Declaration decl : program.declarations) {
            if (decl instanceof ClassDef) {
                String className = ((ClassDef) decl).name.name;
                classNames.add(className);
            }
        }
        classNames.add("object");
        classNames.add("int");
        classNames.add("str");
        classNames.add("bool");

        for (Declaration decl : program.declarations) {
            predeclareGlobal(decl);
        }

        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }
        return null;
    }

    /** Allows VarDef to check that we are defining to a declared type. **/
    private void checkTypeExists(Type t, Node node) {
        if (t instanceof ClassValueType) {
            String name = t.className();

            if (       t.equals(Type.INT_TYPE)
                    || t.equals(Type.BOOL_TYPE)
                    || t.equals(Type.STR_TYPE)
                    || t.equals(Type.OBJECT_TYPE)
                    || t.equals(Type.NONE_TYPE)
                    || t.equals(Type.EMPTY_TYPE)) {
                return;
            }

            SymbolInfo info = globals.get(name);
            if (!(info instanceof ClassInfo)) {
                errors.semError(node,
                        "Invalid type annotation; there is no class named: %s",
                        name);
            }
        } else if (t instanceof ListValueType) {
            /** We also can't have a list of undeclared class names. **/
            checkTypeExists(((ListValueType) t).elementType(), node);
        }
    }

    /** Analyze Variable - Handle Semantic Errors, Determines Variable Type**/
    @Override
    public Void analyze(VarDef varDef) {
        String name = varDef.var.identifier.name;
        Identifier id = varDef.var.identifier;
        ValueType type = ValueType.annotationToValueType(varDef.var.type);

        checkTypeExists(type, varDef.var.type);


        /** We only prevent shadowing from classNames if the variable is not on the top level.**/
        boolean isGlobal = (sym == globals && currentClass == null);

        if (classNames.contains(name) && !isGlobal) {
            errors.semError(id,
                    "Cannot shadow class name: %s",
                    name);
            return null;
        }

        VarInfo.VarKind kind;
        if (currentClass != null && sym == globals) {
            kind = VarInfo.VarKind.ATTRIBUTE;
        } else if (sym == globals) {
            kind = VarInfo.VarKind.GLOBAL;
        } else {
            kind = VarInfo.VarKind.LOCAL;
        }

        VarInfo varInfo = new VarInfo(name, type, kind);


        /** Handles Attribute cases, cannot redefine an attribute. **/
        if (currentClass != null && sym == globals) {
            if (currentClass.declaresAttribute(name) || currentClass.declaresMethod(name)) {
                errors.semError(id,
                        "Duplicate declaration of identifier in same scope: %s",
                        name);
            } else if (currentClass.lookupAttribute(name) != null
                    || currentClass.lookupMethod(name) != null) {
                errors.semError(id,
                        "Cannot re-define attribute: %s",
                        name);
            } else {
                currentClass.addAttribute(varInfo);
            }
        }
        /** else if (sym == globals){ // Code no longer needed after inserting predeclaring.
            // Function variables are preloaded.
            if (sym.declaresHere(name)) {
                errors.semError(id,
                        "Duplicate declaration of identifier in same scope: %s",
                        name);
                return null;
            } else {
                sym.put(name, varInfo);
            }
        }  **/

        return null;
    }
    /** Used to determine if a function is a valid overwrite of an inhereted method. **/
    private boolean sameMethodSignature(FuncInfo f1, FuncInfo f2) {
        List<ValueType> p1 = f1.getParamTypes();
        List<ValueType> p2 = f2.getParamTypes();

        if (p1.size() != p2.size()) {
            return false;
        }

        /** Ignore Self Param **/
        for (int i = 1; i < p1.size(); i++) {
            if (!p1.get(i).equals(p2.get(i))) {
                return false;
            }
        }

        return f1.getReturnType().equals(f2.getReturnType());
    }

    private void predeclareInScope(Declaration decl) {
        if (decl instanceof VarDef) {
            VarDef varDef = (VarDef) decl;
            String name = varDef.var.identifier.name;
            Identifier id = varDef.var.identifier;
            ValueType type = ValueType.annotationToValueType(varDef.var.type);

            if (sym.declaresHere(name)) {
                errors.semError(id,
                        "Duplicate declaration of identifier in same scope: %s",
                        name);
                return;
            }

            if (classNames.contains(name)) {
                errors.semError(id,
                        "Cannot shadow class name: %s",
                        name);
                return;
            }

            sym.put(name, new VarInfo(name, type, VarInfo.VarKind.LOCAL));
        } else if (decl instanceof FuncDef) {
            FuncDef nested = (FuncDef) decl;
            String name = nested.name.name;
            Identifier id = nested.name;

            if (sym.declaresHere(name)) {
                errors.semError(id,
                        "Duplicate declaration of identifier in same scope: %s",
                        name);
                return;
            }

            if (classNames.contains(name)) {
                errors.semError(id,
                        "Cannot shadow class name: %s",
                        name);
                return;
            }

            List<ValueType> paramTypes = new ArrayList<>();
            for (TypedVar param : ((FuncDef) decl).params) {
                ValueType t = ValueType.annotationToValueType(param.type);
                if (param.type != null) {
                    checkTypeExists(t, param.type);
                }
                paramTypes.add(t);
            }

            ValueType returnType =
                    nested.returnType == null
                            ? Type.NONE_TYPE
                            : ValueType.annotationToValueType(nested.returnType);

            if (((FuncDef) decl).returnType != null) {
                checkTypeExists(returnType, ((FuncDef) decl).returnType);
            }

            sym.put(name, new FuncInfo(name, paramTypes, returnType, false));
        }
    }

    @Override
    public Void analyze(FuncDef funcDef) {
        String name = funcDef.name.name;
        Identifier id = funcDef.name;

        FuncInfo funcInfo = null;
        if (currentClass != null) {
            if (currentClass.declaresAttribute(name) || currentClass.declaresMethod(name)) {
                errors.semError(id,
                        "Duplicate declaration of identifier in same scope: %s",
                        name);
            } else if (currentClass.lookupAttribute(name) != null) {
                errors.semError(id,
                        "Cannot re-define attribute: %s",
                        name);
            }

            List<ValueType> paramTypes = new ArrayList<>();
            for (TypedVar param : funcDef.params) {
                ValueType t = ValueType.annotationToValueType(param.type);
                if (param.type != null) {
                    checkTypeExists(t, param.type);
                }
                paramTypes.add(t);
            }

            ValueType returnType =
                    funcDef.returnType == null
                            ? Type.NONE_TYPE
                            : ValueType.annotationToValueType(funcDef.returnType);

            if (funcDef.returnType != null) {
                checkTypeExists(returnType, funcDef.returnType);
            }

            funcInfo = new FuncInfo(name, paramTypes, returnType, true);

            ClassInfo superCls = currentClass.getSuperClass();
            if (superCls != null) {
                FuncInfo inherited = superCls.lookupMethod(name);
                if (inherited != null && !sameMethodSignature(funcInfo, inherited)) {
                    errors.semError(id,
                            "Method overridden with different type signature: %s",
                            name);
                }
            }

            currentClass.addMethod(funcInfo);
        } else {
            SymbolInfo exists = sym.get(name);
            if (!(exists instanceof FuncInfo)) {
                errors.semError(id,
                        "Duplicate declaration of identifier in same scope: %s",
                        name);
                return null;
            }
            funcInfo = (FuncInfo) exists;
        }

        /** go to a new function scope **/
        SemanticScope oldSym = sym;
        SemanticScope fnScope;

        if (currentClass != null) {
            fnScope = new SemanticScope(sym, funcInfo, currentClass);
        } else {
            fnScope = new SemanticScope(sym, funcInfo);
        }

        funcInfo.setLocalScope(fnScope);
        sym = fnScope;

        for (TypedVar param : funcDef.params) {
            String paramName = param.identifier.name;
            Identifier paramId = param.identifier;
            ValueType paramType = ValueType.annotationToValueType(param.type);

            if (sym.declaresHere(paramName)) {
                errors.semError(paramId,
                        "Duplicate declaration of identifier in same scope: %s",
                        paramName);
                continue;
            }

            if (classNames.contains(paramName)) {
                errors.semError(paramId,
                        "Cannot shadow class name: %s",
                        paramName);
                continue;
            }

            sym.put(paramName,
                    new VarInfo(paramName, paramType, VarInfo.VarKind.PARAM));
        }
        /** Predeclare variables to allow for nonlocal. **/
        for (Declaration decl : funcDef.declarations) {
            predeclareInScope(decl);
        }

        for (Declaration decl : funcDef.declarations) {
            decl.dispatch(this);
        }
        /** Return to Outer Scope after Dispatch **/
        sym = oldSym;
        return null;
    }

    @Override
    public Void analyze(ClassDef classDef) {
        String name = classDef.name.name;
        declaredClasses.add(name);
        Identifier id = classDef.name;

        SymbolInfo exists = sym.get(name);
        if (!(exists instanceof ClassInfo)) {
            errors.semError(id,
                    "Duplicate declaration of identifier in same scope: %s",
                    name);
            return null;
        }

        ClassInfo classInfo = (ClassInfo) exists;

        /** Deal with Superclass Pointers **/

        String superName = classDef.superClass.name;
        SymbolInfo superSym = globals.get(superName);

        if (!"object".equals(superName)) {
            if (!(superSym instanceof ClassInfo)){
                errors.semError(classDef.superClass,
                        "Super-class must be a class: %s", superName);

            }else if ("int".equals(superName) || "bool".equals(superName) || "str".equals(superName)) {
                errors.semError(classDef.superClass,
                        "Cannot extend special class: %s",
                        superName);
            } else if (!declaredClasses.contains(superName)) {
                errors.semError(classDef.superClass,
                        "Super-class not defined: %s",
                        superName);
            } else {
                classInfo.setSuperClass((ClassInfo) superSym);
            }
        } else {
            SymbolInfo objectSym = globals.get("object");
            if (objectSym instanceof ClassInfo) {
                classInfo.setSuperClass((ClassInfo) objectSym);
            }
        }

        ClassInfo oldClass = currentClass;
        currentClass = classInfo;

        for (Declaration decl : classDef.declarations) {
            decl.dispatch(this);
        }

        currentClass = oldClass;
        return null;
    }


    @Override
    public Void analyze(GlobalDecl decl) {
        String name = decl.variable.name;

        if (sym.declaresHere(name)) {
            errors.semError(decl.variable,
                    "Duplicate declaration of identifier in same scope: %s",
                    name);
            return null;
        }

        SymbolInfo info = globals.get(name);

        if (!(info instanceof VarInfo) || !((VarInfo) info).isGlobal()) {
            errors.semError(decl.variable, "Not a global variable: %s", name);
        } else {
            sym.getEnclosingFunctionScope().addGlobalDecl(name);
        }
        return null;
    }

    @Override
    public Void analyze(NonLocalDecl decl) {
        String name = decl.variable.name;

        if (sym.declaresHere(name)) {
            errors.semError(decl.variable,
                    "Duplicate declaration of identifier in same scope: %s",
                    name);
            return null;
        }


        SemanticScope parent = sym.getParent();
        boolean found = false;

        while (parent != null && !parent.isGlobalScope()) {
            if (parent.declaresHere(name)) {
                SymbolInfo info = parent.get(name);
                if (info instanceof VarInfo) {
                    found = true;
                    break;
                }
            }
            parent = parent.getParent();
        }

        if (!found) {
            errors.semError(decl.variable,
                    "Not a nonlocal variable: %s",
                    name);
        } else {
            sym.getEnclosingFunctionScope().addNonLocalDecl(name);
        }
        return null;
    }


}
