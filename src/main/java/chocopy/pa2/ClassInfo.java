package chocopy.pa2;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import chocopy.common.analysis.types.ClassValueType;

/** Symbol info for classes. */
public class ClassInfo extends SymbolInfo {

    private final ClassValueType type;
    private ClassInfo superClass;
    private final Map<String, VarInfo> attributes = new LinkedHashMap<>();
    private final Map<String, FuncInfo> methods = new LinkedHashMap<>();
    public ClassInfo(String name) {
        super(name, Kind.CLASS);
        this.type = new ClassValueType(name);
    }
    public String getClassName() {
        return getName();
    }
    public ClassValueType getType() {
        return type;
    }
    public ClassInfo getSuperClass() {
        return superClass;
    }
    public void setSuperClass(ClassInfo superClass) {
        this.superClass = superClass;
    }
    public boolean declaresAttribute(String name) {
        return attributes.containsKey(name);
    }
    public boolean declaresMethod(String name) {
        return methods.containsKey(name);
    }
    public VarInfo getDeclaredAttribute(String name) {
        return attributes.get(name);
    }
    public FuncInfo getDeclaredMethod(String name) {
        return methods.get(name);
    }
    public void addAttribute(VarInfo attr) {
        attributes.put(attr.getName(), attr);
    }

    public void addMethod(FuncInfo method) {
        methods.put(method.getName(), method);
    }
    public Collection<VarInfo> getDeclaredAttributes() {
        return attributes.values();
    }
    public Collection<FuncInfo> getDeclaredMethods() {
        return methods.values();
    }

    /** Lookup attribute through inheritance chain. */
    public VarInfo lookupAttribute(String name) {
        ClassInfo current = this;
        while (current != null) {
            VarInfo attr = current.attributes.get(name);
            if (attr != null) {
                return attr;
            }
            current = current.superClass;
        }
        return null;
    }

    /** Lookup method through inheritance chain. */
    public FuncInfo lookupMethod(String name) {
        ClassInfo current = this;
        while (current != null) {
            FuncInfo method = current.methods.get(name);
            if (method != null) {
                return method;
            }
            current = current.superClass;
        }
        return null;
    }

    /** True iff this class is the same as or a subclass of other. */
    public boolean isSubclassOf(ClassInfo other) {
        if (other == null) {
            return false;
        }
        ClassInfo current = this;
        while (current != null) {
            if (current == other) {
                return true;
            }
            current = current.superClass;
        }
        return false;
    }

    @Override
    public String toString() {
        return "ClassInfo(name=" + getName()
                + ", super=" + (superClass == null ? "null" : superClass.getName()) + ")";
    }
}
