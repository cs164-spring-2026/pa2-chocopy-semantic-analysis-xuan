package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.*;
import chocopy.common.astnodes.*;

import java.util.ArrayList;
import java.util.List;

import static chocopy.common.analysis.types.Type.*;

/** Analyzer that performs ChocoPy type checks on all nodes.  Applied after
 *  collecting declarations. */
public class TypeChecker extends AbstractNodeAnalyzer<Type> {

    /** The current symbol table (changes depending on the function
     *  being analyzed). */
    private SemanticScope sym;
    private final SemanticScope globals;
    /** Collector for errors. */
    private Errors errors;

    private ClassInfo currentClass = null;
    private FuncInfo currentFunction = null;



    /** Creates a type checker using GLOBALSYMBOLS for the initial global
     *  symbol table and ERRORS0 to receive semantic errors. */
    public TypeChecker(SemanticScope globalSymbols, Errors errors0) {
        sym = globalSymbols;
        globals = globalSymbols;
        errors = errors0;
    }

    /** Inserts an error message in NODE if there isn't one already.
     *  The message is constructed with MESSAGE and ARGS as for
     *  String.format. */
    private void err(Node node, String message, Object... args) {
        errors.semError(node, message, args);
    }

    private boolean isInt(Type t) {
        return INT_TYPE.equals(t);
    }

    private boolean isBool(Type t) {
        return BOOL_TYPE.equals(t);
    }

    private boolean isStr(Type t) {
        return STR_TYPE.equals(t);
    }

    private boolean isObject(Type t) {
        return OBJECT_TYPE.equals(t);
    }

    private boolean isList(Type t) {
        return t instanceof ListValueType;
    }

    private boolean isClassType(Type t) {
        return t instanceof ClassValueType;
    }

    private FuncInfo lookupFunction(String name) {
        SymbolInfo info = sym.get(name);
        if (info instanceof FuncInfo) {
            return (FuncInfo) info;
        }
        return null;
    }

    private ClassInfo lookupClass(String name) {
        SymbolInfo info = globals.get(name);
        if (info instanceof ClassInfo) {
            return (ClassInfo) info;
        }
        return null;
    }
    /** Var Info checks if a variable is declared nonlocal or global, or uses the regular variable info.**/
    private VarInfo lookupVariable(String name) {
        SemanticScope fn = sym.getEnclosingFunctionScope();

        if (fn != null && fn.isDeclaredGlobal(name)) {
            SymbolInfo info = globals.get(name);
            return (info instanceof VarInfo) ? (VarInfo) info : null;
        }

        // NONLOCAL case
        if (fn != null && fn.isDeclaredNonlocal(name)) {
            SemanticScope parent = fn.getParent();

            while (parent != null && !parent.isGlobalScope()) {
                if (parent.declaresHere(name)) {
                    SymbolInfo info = parent.get(name);
                    return (info instanceof VarInfo) ? (VarInfo) info : null;
                }
                parent = parent.getParent();
            }
            return null;
        }

        SymbolInfo info = sym.get(name);
        return (info instanceof VarInfo) ? (VarInfo) info : null;
    }

    @Override
    public Type analyze(Program program) {
        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }
        for (Stmt stmt : program.statements) {
            stmt.dispatch(this);
        }
        return null;
    }

    @Override
    public Type analyze(ClassDef classDef) {
        SymbolInfo info = globals.get(classDef.name.name);
        ClassInfo oldClass = currentClass;

        if (info instanceof ClassInfo) {
            currentClass = (ClassInfo) info;
        }

        for (Declaration decl : classDef.declarations) {
            decl.dispatch(this);
        }

        currentClass = oldClass;
        return null;
    }
    /** Search through if statements that each path leads to a return statement.**/
    private boolean stmtListReturns(List<Stmt> stmts) {
        for (Stmt s : stmts) {
            if (returns(s)) {
                return true;
            }
        }
        return false;
    }

    private boolean returns(Stmt stmt) {
        if (stmt instanceof ReturnStmt) {
            return true;
        }

        if (stmt instanceof IfStmt) {
            IfStmt ifs = (IfStmt) stmt;
            return stmtListReturns(ifs.thenBody)
                    && stmtListReturns(ifs.elseBody);
        }

        return false;
    }

    @Override
    public Type analyze(FuncDef funcDef) {
        SymbolInfo info;
        if (currentClass != null) {
            info = currentClass.lookupMethod(funcDef.name.name);
        } else {
            info = sym.get(funcDef.name.name);
        }

        if (!(info instanceof FuncInfo)) {
            return null;
        }

        FuncInfo fn = (FuncInfo) info;
        FuncInfo oldFunction = currentFunction;
        SemanticScope oldSym = sym;

        currentFunction = fn;
        if (fn.getLocalScope() != null) {
            sym = fn.getLocalScope();
        }
        /** If we are in a method, ensure that the first parameter is self. **/
        if (currentClass != null && sym.getParent() == globals) {
            List<TypedVar> params = funcDef.params;

            if (params.isEmpty()) {
                err(funcDef.name,
                        "First parameter of the following method must be of the enclosing class: %s", funcDef.name.name);
            } else {
                TypedVar first = params.get(0);
                Type firstType = ValueType.annotationToValueType(first.type);
                Type expectedSelfType = currentClass.getType();

                if (!expectedSelfType.equals(firstType)) {
                    err(funcDef.name,
                            "First parameter of the following method must be of the enclosing class: %s", funcDef.name.name);
                }
            }
        }

        for (Declaration decl : funcDef.declarations) {
            decl.dispatch(this);
        }
        for (Stmt stmt : funcDef.statements) {
            stmt.dispatch(this);
        }

        if (!isSubtype(Type.NONE_TYPE, fn.getReturnType())
                && !stmtListReturns(funcDef.statements)) {
            err(funcDef.name,
                    "All paths in this function/method must have a return statement: %s", funcDef.name.name);
        }


        sym = oldSym;
        currentFunction = oldFunction;
        return null;
    }

    /**Deal with subtypes, needed to clear variable definitions. **/
    private boolean isSubtype(Type sub, Type sup) {
        if (sub == null || sup == null) {
            return false;
        }
        if (sub.equals(sup)) {
            return true;
        }

        if (OBJECT_TYPE.equals(sup)) {
            return true;
        }
        /** NoneType and EmptyType need to be able to be assigned to list values, and none to class values.**/
        if (sub.equals(Type.NONE_TYPE)) {
            if (sup instanceof ListValueType) {
                return true;
            }
            if (sup instanceof ClassValueType) {
                return !sup.isSpecialType() && !sup.equals(Type.NONE_TYPE) && !sup.equals(Type.EMPTY_TYPE);
            }
            return false;
        }

        if (sub.equals(Type.EMPTY_TYPE)) {
            return sup instanceof ListValueType;
        }

        if (sub instanceof ClassValueType && sup instanceof ClassValueType) {
            ClassInfo subCls = getClassInfo(sub);
            ClassInfo supCls = getClassInfo(sup);
            return subCls != null && supCls != null && subCls.isSubclassOf(supCls);
        }

        if (sub instanceof ListValueType && sup instanceof ListValueType) {
            Type subElt = ((ListValueType) sub).elementType();
            Type supElt = ((ListValueType) sup).elementType();
            return subElt != null && supElt != null && subElt.equals(supElt);
        }

        return false;
    }

    private boolean isAssignable(Type rhs, Type lhs) {
        if (rhs == null || lhs == null) {
            return false;
        }

        if (rhs.equals(lhs)) {
            return true;
        }

        if (isSubtype(rhs, lhs)) {
            return true;
        }

        if (rhs.equals(Type.NONE_TYPE)) {
            return lhs instanceof ClassValueType || lhs instanceof ListValueType;
        }

        if (rhs.equals(Type.EMPTY_TYPE)) {
            return lhs instanceof ListValueType;
        }

        if (rhs instanceof ListValueType && lhs instanceof ListValueType) {
            Type rhsElt = ((ListValueType) rhs).elementType();
            Type lhsElt = ((ListValueType) lhs).elementType();

            if (rhsElt != null && lhsElt != null && rhsElt.equals(Type.NONE_TYPE)) {
                return true;
            }
        }

        return false;
    }

    private ClassInfo getClassInfo(Type t) {
        if (!(t instanceof ClassValueType)) {
            return null;
        }
        String className = ((ClassValueType) t).className();
        SymbolInfo info = globals.get(className);
        if (info instanceof ClassInfo) {
            return (ClassInfo) info;
        }
        return null;
    }



    @Override
    public Type analyze(VarDef varDef) {
        if (varDef.value != null) {
            Type r = varDef.value.dispatch(this);
            ValueType l = ValueType.annotationToValueType(varDef.var.type);
            if (!isAssignable(r, l)) {
                err(varDef, "Expected type `%s`; got type `%s`", l, r);
            }
        }
        return null;
    }

    @Override
    public Type analyze(GlobalDecl decl) {
        return null;
    }

    @Override
    public Type analyze(NonLocalDecl decl) {
        return null;
    }

    @Override
    public Type analyze(ExprStmt stmt) {
        stmt.expr.dispatch(this);
        return null;
    }

    @Override
    public Type analyze(ReturnStmt stmt) {
        if (currentFunction == null) {
            err(stmt, "Return statement cannot appear at the top level");
            return null;
        }

        Type actual = stmt.value == null ? Type.NONE_TYPE : stmt.value.dispatch(this);
        Type expected = currentFunction.getReturnType();

        if (!isSubtype(actual, expected)) {
            if (stmt.value == null) {
                err(stmt, "Expected type `%s`; got `None`", expected);
            } else {
                err(stmt, "Expected type `%s`; got type `%s`", expected, actual);
            }
        }
        return null;
    }
    /** Ensures our assign statments that look outside of scope have variables already declared global or nonlocal.**/
    private VarInfo lookupAssignableVariable(String name) {
        SemanticScope fn = sym.getEnclosingFunctionScope();

        if (fn == null) {
            SymbolInfo info = sym.get(name);
            return (info instanceof VarInfo) ? (VarInfo) info : null;
        }

        if (fn.isDeclaredGlobal(name)) {
            SymbolInfo info = globals.get(name);
            return (info instanceof VarInfo) ? (VarInfo) info : null;
        }

        if (fn.isDeclaredNonlocal(name)) {
            SemanticScope parent = fn.getParent();
            while (parent != null && !parent.isGlobalScope()) {
                if (parent.declaresHere(name)) {
                    SymbolInfo info = parent.get(name);
                    return (info instanceof VarInfo) ? (VarInfo) info : null;
                }
                parent = parent.getParent();
            }
            return null;
        }
        if (sym.declaresHere(name)) {
            SymbolInfo info = sym.get(name);
            return (info instanceof VarInfo) ? (VarInfo) info : null;
        }

        return null;
    }

    @Override
    public Type analyze(AssignStmt stmt) {
        Type rhs = stmt.value.dispatch(this);

        boolean sawBadTarget = false;
        for (Expr target : stmt.targets) {
            if (!(target instanceof Identifier
                    || target instanceof MemberExpr
                    || target instanceof IndexExpr)) {
                err(target, "Not a variable");
                sawBadTarget = true;
            } else if (target instanceof Identifier && sym.getEnclosingFunctionScope() != null) {
                String name = ((Identifier) target).name;
                VarInfo v = lookupAssignableVariable(name);
                if (v == null) {
                    err(target, "Cannot assign to variable that is not explicitly declared in this scope: %s", name);
                    sawBadTarget = true;
                } else {
                    target.dispatch(this);
                }
            }  else if (target instanceof IndexExpr) {
                IndexExpr idx = (IndexExpr) target;
                Type listType = idx.list.dispatch(this);
                Type indexType = idx.index.dispatch(this);

                if (!isInt(indexType)) {
                    err(idx.index, "Index is of non-integer type `%s`", indexType);
                }

                if (!(listType instanceof ListValueType)) {
                    err(target, "`%s` is not a list type", listType);
                    sawBadTarget = true;
                } else {
                    target.dispatch(this);
                }

            } else {
                target.dispatch(this);
            }
        }

        if (sawBadTarget) {
            return null;
        }
        /** Make sure each target matches with the value. **/
        for (Expr target : stmt.targets) {
            Type targetType;

            if (target instanceof Identifier) {
                VarInfo v = lookupAssignableVariable(((Identifier) target).name);
                if (v == null) {
                    err(target, "Not a variable: %s", ((Identifier) target).name);
                    continue;
                }
                targetType = v.getType();
            } else if (target instanceof MemberExpr) {
                targetType = target.getInferredType();
            } else if (target instanceof IndexExpr) {
                targetType = target.getInferredType();
            } else {
                targetType = OBJECT_TYPE;
            }

            if (Type.NONE_TYPE.equals(rhs) && targetType.isSpecialType()) {
                err(stmt, "Expected type `%s`; got type `%s`", targetType, rhs);
                continue;
            }

            if (!isAssignable(rhs, targetType)) {
                err(stmt, "Expected type `%s`; got type `%s`", targetType, rhs);
                return null;
            }
        }

        return null;
    }

    @Override
    public Type analyze(IfStmt stmt) {
        Type cond = stmt.condition.dispatch(this);
        if (!isBool(cond)) {
            err(stmt.condition, "Condition expression must be Boolean, not type `%s`", cond);
        }
        for (Stmt s : stmt.thenBody) {
            s.dispatch(this);
        }
        for (Stmt s : stmt.elseBody) {
            s.dispatch(this);
        }
        return null;
    }

    @Override
    public Type analyze(WhileStmt stmt) {
        Type cond = stmt.condition.dispatch(this);
        if (!isBool(cond)) {
            err(stmt.condition, "Condition expression must be Boolean, not type `%s`", cond);
        }
        for (Stmt s : stmt.body) {
            s.dispatch(this);
        }
        return null;
    }


    @Override
    public Type analyze(ForStmt stmt) {
        Type idType = stmt.identifier.dispatch(this);
        Type iterableType = stmt.iterable.dispatch(this);

        Type elementType = OBJECT_TYPE;
        if (iterableType instanceof ListValueType) {
            elementType = iterableType.elementType();
        } else if (isStr(iterableType)) {
            elementType = STR_TYPE;
        } else {
            err(stmt.iterable, "Cannot iterate over type `%s`", iterableType);
        }

        VarInfo target = lookupVariable(stmt.identifier.name);
        if (target == null) {
            err(stmt.identifier, "Not a variable: %s", stmt.identifier.name);
        } else if (!isSubtype(elementType, target.getType())) {
            err(stmt.identifier, "Expected type `%s`; got type `%s`", target.getType(), elementType);
        }

        for (Stmt s : stmt.body) {
            s.dispatch(this);
        }
        return null;
    }

    @Override
    public Type analyze(Identifier id) {
        SymbolInfo info = sym.get(id.name);
        if (info instanceof VarInfo) {
            return id.setInferredType(((VarInfo) info).getType());
        }
        if (info instanceof FuncInfo) {
            FuncInfo f = (FuncInfo) info;
            Type t = new FuncType(f.getParamTypes(), f.getReturnType());
            return id.setInferredType(t);
        }

        err(id, "Not a variable: %s", id.name);
        return id.setInferredType(OBJECT_TYPE);
    }


    @Override
    public Type analyze(IntegerLiteral i) {
        return i.setInferredType(INT_TYPE);
    }

    @Override
    public Type analyze(BooleanLiteral b) {
        return b.setInferredType(BOOL_TYPE);
    }

    @Override
    public Type analyze(StringLiteral s) {
        return s.setInferredType(STR_TYPE);
    }

    @Override
    public Type analyze(NoneLiteral n) {
        return n.setInferredType(NONE_TYPE);
    }

    @Override
    public Type analyze(UnaryExpr e) {
        Type t = e.operand.dispatch(this);

        switch (e.operator) {
            case "-":
                if (!isInt(t)) {
                    err(e, "Cannot apply operator `%s` on type `%s`", e.operator, t);
                }
                return e.setInferredType(INT_TYPE);
            case "not":
                if (!isBool(t)) {
                    err(e, "Cannot apply operator `%s` on type `%s`", e.operator, t);
                }
                return e.setInferredType(BOOL_TYPE);
            default:
                return e.setInferredType(OBJECT_TYPE);
        }
    }


    @Override
    public Type analyze(BinaryExpr e) {
        Type t1 = e.left.dispatch(this);
        Type t2 = e.right.dispatch(this);

        switch (e.operator) {
            case "-":
            case "*":
            case "//":
            case "%":
                if (!isInt(t1) || !isInt(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(INT_TYPE);

            case "+":
                if (isInt(t1) && isInt(t2)) {
                    return e.setInferredType(INT_TYPE);
                }
                if (isStr(t1) && isStr(t2)) {
                    return e.setInferredType(STR_TYPE);
                }
                if (t1 instanceof ListValueType && t2 instanceof ListValueType) {
                    Type elt1 = ((ListValueType) t1).elementType();
                    Type elt2 = ((ListValueType) t2).elementType();
                    Type joined = join(elt1, elt2);

                    if (!(joined instanceof ValueType)) {
                        joined = OBJECT_TYPE;
                    }

                    return e.setInferredType(new ListValueType((ValueType) joined));
                }
                err(e, "Cannot apply operator `+` on types `%s` and `%s`", t1, t2);

                return e.setInferredType(INT_TYPE);

            case "<":
            case "<=":
            case ">":
            case ">=":
                if (!(isInt(t1) && isInt(t2))) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);

            case "==":
            case "!=":

                if (t1.equals(Type.NONE_TYPE) || t2.equals(Type.NONE_TYPE)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                            e.operator, t1, t2);
                    return e.setInferredType(Type.BOOL_TYPE);
                }

                if (!t1.equals(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);

            case "and":
            case "or":
                if (!(isBool(t1) && isBool(t2))) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);

            case "is":
                if (t1.isSpecialType() || t2.isSpecialType()) {
                    err(e, "Cannot apply operator `is` on types `%s` and `%s`",
                            t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);

            default:
                return e.setInferredType(OBJECT_TYPE);
        }
    }
    /** Determine type of result after joining. **/
    private Type join(Type a, Type b) {
        if (a == null || b == null) {
            return OBJECT_TYPE;
        }
        if (a.equals(b)) {
            return a;
        }

        if (a.equals(Type.EMPTY_TYPE) && b instanceof ListValueType) {
            return b;
        }

        if (b.equals(Type.EMPTY_TYPE) && a instanceof ListValueType) {
            return a;
        }

        if (isSubtype(a, b)) {
            return b;
        }
        if (isSubtype(b, a)) {
            return a;
        }
        return OBJECT_TYPE;
    }

    @Override
    public Type analyze(IfExpr e) {
        Type cond = e.condition.dispatch(this);
        Type thenT = e.thenExpr.dispatch(this);
        Type elseT = e.elseExpr.dispatch(this);

        if (!isBool(cond)) {
            err(e.condition, "Condition expression cannot be of type `%s`", cond);
        }
        return e.setInferredType(join(thenT, elseT));
    }

    @Override
    public Type analyze(ListExpr e) {
        if (e.elements.isEmpty()) {
            return e.setInferredType(EMPTY_TYPE);
        }

        List<Type> elemTypes = new ArrayList<>();
        for (Expr elem : e.elements) {
            elemTypes.add(elem.dispatch(this));
        }

        /** Get the most general type for the list, object if necessary **/
        Type joined = elemTypes.get(0);
        for (int i = 1; i < elemTypes.size(); i++) {
            joined = join(joined, elemTypes.get(i));
        }

        return e.setInferredType(new ListValueType((ValueType) joined));
    }

    @Override
    public Type analyze(IndexExpr e) {
        Type listType = e.list.dispatch(this);
        Type indexType = e.index.dispatch(this);

        if (!isInt(indexType)) {
            err(e, "Index is of non-integer type `%s`", indexType);
        }

        if (listType instanceof ListValueType) {
            return e.setInferredType (((ListValueType) listType).elementType());
        } else if (isStr(listType)) {
            return e.setInferredType(STR_TYPE);
        } else {
            err(e, "Cannot index into type `%s`", listType);
            return e.setInferredType(OBJECT_TYPE);
        }
    }

    @Override
    public Type analyze(MemberExpr e) {
        Type objType = e.object.dispatch(this);

        if (!(objType instanceof ClassValueType)) {
            err(e.object, "Cannot access member of non-class type `%s`", objType);
            return e.setInferredType(OBJECT_TYPE);
        }

        ClassInfo classI = getClassInfo(objType);
        if (classI == null) {
            err(e, "There is no attribute named `%s` in class `%s`", e.member.name, objType);
            return e.setInferredType(OBJECT_TYPE);
        }

        VarInfo attr = classI.lookupAttribute(e.member.name);
        if (attr != null) {
            return e.setInferredType(attr.getType());
        }

        FuncInfo method = classI.lookupMethod(e.member.name);
        if (method != null) {
            return e.setInferredType(
                    new FuncType(method.getParamTypes(), method.getReturnType()));
        }

        err(e, "There is no attribute named `%s` in class `%s`",
                e.member.name, classI.getClassName());
        return e.setInferredType(OBJECT_TYPE);
    }

    @Override
    public Type analyze(CallExpr e) {
        String fnName = e.function.name;


        FuncInfo fn = lookupFunction(fnName);
        if (fn != null) {
            Type calleeType = e.function.dispatch(this);
            List<ValueType> params = fn.getParamTypes();
            if (params.size() != e.args.size()) {
                err(e, "Expected %d arguments; got %d", params.size(), e.args.size());
                for (Expr arg : e.args) {
                    arg.dispatch(this);
                }
                return e.setInferredType(fn.getReturnType());
            }

            for (int i = 0; i < e.args.size(); i++) {
                Type actual = e.args.get(i).dispatch(this);
                Type expected = params.get(i);
                if (!isSubtype(actual, expected)) {
                    err(e,
                            "Expected type `%s`; got type `%s` in parameter %d",
                            expected, actual, i);
                    return e.setInferredType(fn.getReturnType());
                }
            }
            return e.setInferredType(fn.getReturnType());
        }

        ClassInfo classI = lookupClass(fnName);
        if (classI != null) {
            if (!e.args.isEmpty()) {
                err(e, "Expected 0 arguments; got %d", e.args.size());
            }

            for (Expr arg : e.args) {
                arg.dispatch(this);
            }
            return e.setInferredType(classI.getType());
        }

        err(e, "Not a function or class: %s", e.function.name);
        for (Expr arg : e.args) {
            arg.dispatch(this);
        }
        return e.setInferredType(OBJECT_TYPE);
    }

    @Override
    public Type analyze(MethodCallExpr e) {
        Type memberType = e.method.object.dispatch(this);
        Type calleeType = e.method.dispatch(this);

        if (!(memberType instanceof ClassValueType)) {
            err(e.method.object, "Cannot call method on non-class type `%s`", memberType);
            for (Expr arg : e.args) {
                arg.dispatch(this);
            }
            return e.setInferredType(OBJECT_TYPE);
        }

        ClassInfo classI = getClassInfo(memberType);
        if (classI == null) {
            err(e, "There is no method named `%s` in class `%s`", e.method.member.name, memberType);
            for (Expr arg : e.args) {
                arg.dispatch(this);
            }
            return e.setInferredType(OBJECT_TYPE);
        }

        FuncInfo method = classI.lookupMethod(e.method.member.name);
        if (method == null && sym == globals) {
            err(e, "There is no method named `%s` in class `%s`", e.method.member.name, classI.getClassName());
            for (Expr arg : e.args) {
                arg.dispatch(this);
            }
            return e.setInferredType(OBJECT_TYPE);
        }

        List<ValueType> params = method.getParamTypes();
        /** Size - 1 because first parameter is self **/
        int expectedArgs = Math.max(0, params.size() - 1);
        if (expectedArgs != e.args.size()) {
            err(e, "Expected %d arguments; got %d", expectedArgs, e.args.size());
            for (Expr arg : e.args) {
                arg.dispatch(this);
            }
            return e.setInferredType(method.getReturnType());
        }
        /** We use params.get(i+1) here because the first parameter is self. **/
        for (int i = 0; i < e.args.size(); i++) {
            Type actual = e.args.get(i).dispatch(this);
            Type expected = params.get(i + 1);
            if (!isSubtype(actual, expected)) {
                err(e,
                        "Expected type `%s`; got type `%s` in parameter %d",
                        expected, actual, i + 1);
                return e.setInferredType(method.getReturnType());
            }
        }

        return e.setInferredType(method.getReturnType());
    }

    @Override
    public Type analyze(ClassType t) {
        return null;
    }

    @Override
    public Type analyze(ListType t) {
        return null;
    }

    @Override
    public Type analyze(TypedVar t) {
        return null;
    }

    @Override
    public Type analyze(CompilerError node) {
        return null;
    }

    @Override
    public Type analyze(Errors node) {
        return null;
    }

}
