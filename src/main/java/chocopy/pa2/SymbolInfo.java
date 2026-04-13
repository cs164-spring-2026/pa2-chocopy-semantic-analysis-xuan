package chocopy.pa2;

public abstract class SymbolInfo {

    public enum Kind {
        VARIABLE,
        FUNCTION,
        CLASS
    }
    private final String name;
    private final Kind kind;
    protected SymbolInfo(String name, Kind kind) {
        this.name = name;
        this.kind = kind;
    }
    public String getName() {
        return name;
    }
    public Kind getKind() {
        return kind;
    }
    public boolean isVariable() {
        return kind == Kind.VARIABLE;
    }

    public boolean isFunction() {
        return kind == Kind.FUNCTION;
    }

    public boolean isClass() {
        return kind == Kind.CLASS;
    }

    @Override
    public String toString() {
        return kind + "(" + name + ")";
    }
}
