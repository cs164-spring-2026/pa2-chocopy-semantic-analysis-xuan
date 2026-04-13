package chocopy.pa2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import chocopy.common.analysis.types.ValueType;

/** Symbol info for functions and methods. */
public class FuncInfo extends SymbolInfo {

    private final List<ValueType> paramTypes;
    private final ValueType returnType;
    private final boolean isMethod;

    /** Optional lexical scope associated with this function body. */
    private SemanticScope localScope;

    public FuncInfo(String name, List<ValueType> paramTypes, ValueType returnType, boolean isMethod) {
        super(name, Kind.FUNCTION);
        this.paramTypes = new ArrayList<>(paramTypes);
        this.returnType = returnType;
        this.isMethod = isMethod;
    }

    public List<ValueType> getParamTypes() {
        return Collections.unmodifiableList(paramTypes);
    }

    public ValueType getReturnType() {
        return returnType;
    }

    public boolean isMethod() {
        return isMethod;
    }

    public SemanticScope getLocalScope() {
        return localScope;
    }

    public void setLocalScope(SemanticScope localScope) {
        this.localScope = localScope;
    }

    /** Signature match for override checking, ignoring the first implicit self type if desired externally. */
    public boolean hasSameSignature(FuncInfo other) {
        if (other == null) {
            return false;
        }
        return paramTypes.equals(other.paramTypes) && returnType.equals(other.returnType);
    }

    @Override
    public String toString() {
        return "FuncInfo(name=" + getName()
                + ", params=" + paramTypes
                + ", return=" + returnType
                + ", isMethod=" + isMethod + ")";
    }
}
