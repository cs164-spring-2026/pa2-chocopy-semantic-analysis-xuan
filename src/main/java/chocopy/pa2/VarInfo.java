package chocopy.pa2;

import chocopy.common.analysis.types.ValueType;

/** Symbol info for variables, parameters, attributes. */
public class VarInfo extends SymbolInfo {

    public enum VarKind {
        GLOBAL,
        LOCAL,
        PARAM,
        ATTRIBUTE
    }

    private final ValueType type;
    private final VarKind varKind;

    public VarInfo(String name, ValueType type, VarKind varKind) {
        super(name, Kind.VARIABLE);
        this.type = type;
        this.varKind = varKind;
    }

    public ValueType getType() {
        return type;
    }

    public VarKind getVarKind() {
        return varKind;
    }
    public boolean isGlobal() {
        return varKind == VarKind.GLOBAL;
    }
    public boolean isLocal() {
        return varKind == VarKind.LOCAL;
    }
    public boolean isParam() {
        return varKind == VarKind.PARAM;
    }
    public boolean isAttribute() {
        return varKind == VarKind.ATTRIBUTE;
    }
    @Override
    public String toString() {
        return "VarInfo(name=" + getName() + ", type=" + type + ", kind=" + varKind + ")";
    }
}
