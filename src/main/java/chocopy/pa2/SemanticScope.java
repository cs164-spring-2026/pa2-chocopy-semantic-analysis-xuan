package chocopy.pa2;

import chocopy.common.analysis.SymbolTable;

import java.util.HashSet;
import java.util.Set;

/**
 * Lexical scope for semantic analysis.
 * Uses SymbolTable parent links for nested scopes.
 */
public class SemanticScope extends SymbolTable<SymbolInfo> {


    public enum ScopeKind {
        GLOBAL,
        FUNCTION,
        METHOD
    }

    private final ScopeKind scopeKind;
    private final FuncInfo ownerFunction;
    private final ClassInfo ownerClass;

    private final Set<String> declaredGlobals = new HashSet<>();
    private final Set<String> declaredNonLocals = new HashSet<>();

    /** Create global scope. */
    public SemanticScope() {
        super();
        this.scopeKind = ScopeKind.GLOBAL;
        this.ownerFunction = null;
        this.ownerClass = null;
    }

    /** Create nested function scope. */
    public SemanticScope(SemanticScope parent, FuncInfo ownerFunction) {
        super(parent);
        this.scopeKind = ScopeKind.FUNCTION;
        this.ownerFunction = ownerFunction;
        this.ownerClass = parent == null ? null : parent.getOwnerClass();
    }

    /** Create nested method scope. */
    public SemanticScope(SemanticScope parent, FuncInfo ownerFunction, ClassInfo ownerClass) {
        super(parent);
        this.scopeKind = ScopeKind.METHOD;
        this.ownerFunction = ownerFunction;
        this.ownerClass = ownerClass;
    }

    public ScopeKind getScopeKind() {
        return scopeKind;
    }

    public boolean isGlobalScope() {
        return scopeKind == ScopeKind.GLOBAL;
    }

    public boolean isFunctionScope() {
        return scopeKind == ScopeKind.FUNCTION;
    }

    public boolean isMethodScope() {
        return scopeKind == ScopeKind.METHOD;
    }

    public FuncInfo getOwnerFunction() {
        return ownerFunction;
    }

    public ClassInfo getOwnerClass() {
        return ownerClass;
    }

    public Set<String> getGlobals() {
        return declaredGlobals;
    }

    public Set<String> getNonLocals() {
        return declaredNonLocals;
    }

    public void addGlobalDecl(String name) {
        declaredGlobals.add(name);
    }

    public void addNonLocalDecl(String name) {
        declaredNonLocals.add(name);
    }

    public boolean isDeclaredGlobal(String name) {
        return declaredGlobals.contains(name);
    }

    public boolean isDeclaredNonlocal(String name) {
        return declaredNonLocals.contains(name);
    }
    @Override
    public SemanticScope getParent() {
        return (SemanticScope) super.getParent();
    }

    /** Returns the nearest enclosing non-global function/method scope, or null. */
    public SemanticScope getEnclosingFunctionScope() {
        SemanticScope current = this;
        while (current != null) {
            if (current.isFunctionScope() || current.isMethodScope()) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /** Returns the most global scope. */
    public SemanticScope getGlobalScope() {
        SemanticScope current = this;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current;
    }

    public boolean declaresHere(String name) {
        return declares(name);
    }
}
