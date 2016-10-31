package polyllvm.util;

import polyglot.ast.ClassDecl;
import polyglot.ast.Field;
import polyglot.ast.FieldDecl;
import polyglot.ast.TypeNode;
import polyglot.frontend.goals.Parsed;
import polyglot.types.*;
import polyglot.util.InternalCompilerError;

public class PolyLLVMMangler {
    private static final String JAVA_PREFIX = "_J_";
    private static final String STATIC_STR = "_static_";

    public static String mangleProcedureName(ProcedureInstance pi) {
        if (pi instanceof MethodInstance) {
            return mangleMethodName((MethodInstance) pi);
        }
        else if (pi instanceof ConstructorInstance) {
            return mangleConstructorName((ConstructorInstance) pi);
        }
        else {
            throw new InternalCompilerError("Unknown procedure type: " + pi);
        }
    }

    public static String mangleMethodName(MethodInstance mi) {
        ReferenceType container = mi.container();

        StringBuilder sb = new StringBuilder(JAVA_PREFIX);
        sb.append(container.toString().length());
        sb.append(container.toString());
        sb.append("_");
        sb.append(mi.name().length());
        sb.append(mi.name());
        if (mi.formalTypes().isEmpty()) {
            sb.append("_void");
        }
        for (Type t : mi.formalTypes()) {
            sb.append(mangleFormalType(t));
        }

        return sb.toString();//"_" + container.toString() + "_" + mi.name();
    }

    public static String mangleStaticFieldName(Field f) {
        return mangleStaticFieldName(f.target().type().toReference(), f.name());
    }

    public static String mangleStaticFieldName(ReferenceType classType, FieldDecl f) {
        return mangleStaticFieldName(classType, f.name());
    }

    private static String mangleStaticFieldName(ReferenceType classType, String fieldName) {
        String className = classTypeName(classType);
        return JAVA_PREFIX + STATIC_STR + className + "_" + fieldName;
    }

    private static String mangleConstructorName(ConstructorInstance ci) {
        ReferenceType container = ci.container();
        StringBuilder sb = new StringBuilder(JAVA_PREFIX);
        sb.append(container.toString().length());
        sb.append(container.toString());
        sb.append("__constructor_");

        if (ci.formalTypes().isEmpty()) {
            sb.append("_void");
        }
        for (Type t : ci.formalTypes()) {
            sb.append(mangleFormalType(t));
        }
        return sb.toString();
    }

    private static String mangleFormalType(Type t) {
        StringBuilder sb = new StringBuilder();
        if (t.isArray()) {
            sb.append("_a");
            sb.append(mangleFormalType(t.toArray().base()));
        }
        else if (t.isReference()) {
            sb.append("_");
            sb.append(t.toString().length());
            sb.append(t.toString());
        }
        else if (t.isLongOrLess()) {
            sb.append("_i");
            sb.append(PolyLLVMTypeUtils.numBitsOfIntegralType(t));
        }
        else if (t.isBoolean()) {
            sb.append("_b");
        }
        else if (t.isFloat()) {
            sb.append("_f");

        }
        else if (t.isDouble()) {
            sb.append("_d");

        }
        else {
            throw new InternalCompilerError("Type " + t
                    + " is not properly supported");
        }
        return sb.toString();
    }

    public static String sizeVariable(ClassDecl n) {
        return sizeVariable(n.type());
    }

    public static String sizeVariable(TypeNode superClass) {
        return sizeVariable(superClass.type().toReference());
    }

    public static String sizeVariable(ReferenceType superClass) {
        String className = superClass.isArray() ? "class.support.Array" : superClass.toString();
        return JAVA_PREFIX + "size_" + className.length() + className;
    }

    public static String dispatchVectorVariable(ClassDecl n) {
        return dispatchVectorVariable(n.type());
    }

    public static String dispatchVectorVariable(TypeNode n) {
        return dispatchVectorVariable((ReferenceType) n.type());
    }

    public static String dispatchVectorVariable(ReferenceType rt) {
        String className = rt.isArray() ? "class.support.Array" : rt.toString();
        String prefix = JAVA_PREFIX + "dv_";
        return prefix + className.length() + className;
    }

    public static String InterfaceTableVariable(ReferenceType rt, ReferenceType i ) {
        if(i.isArray() || !(i instanceof ParsedClassType)
                || !((ParsedClassType) i).flags().isInterface()){
            throw new InternalCompilerError("Reference type " + rt + "is not an interface");
        }
        String interfaceName =  i.toString();
        String className =  rt.toString();
        return JAVA_PREFIX + "it_" + interfaceName.length() + interfaceName + "_" + className.length() + className;
    }

    public static String classTypeName(ClassDecl cd) {
        return classTypeName(cd.type());//"class." + cd.name();

    }

    public static String classTypeName(TypeNode superClass) {
        return classTypeName((ReferenceType) superClass.type());//"class." + superClass.name();
    }

    public static String classTypeName(ReferenceType rt) {
        String className = rt.isArray() ? "class.support.Array" : rt.toString();
        if(rt instanceof ParsedClassType){
            return (((ParsedClassType) rt).flags().isInterface() ? "interface." : "class.") + className;
        }
        return "class." + className;
    }

    public static String dispatchVectorTypeName(ClassDecl cd) {
        return dispatchVectorTypeName(cd.type());//"dv." + cd.type().toString();
    }

    public static String dispatchVectorTypeName(TypeNode superClass) {
        return dispatchVectorTypeName((ReferenceType) superClass.type());//"dv." + superClass.name();
    }

    public static String dispatchVectorTypeName(ReferenceType rt) {
        String className = rt.isArray() ? "class.support.Array" : rt.toString();
        if(rt instanceof ParsedClassType){
            return (((ParsedClassType) rt).flags().isInterface() ? "itable." : "dv.") + className;
        }
        return "dv." + className;
    }

    public static String classInitFunction(ClassDecl n) {
        return classInitFunction(n.type());
    }

    public static String classInitFunction(TypeNode n) {
        return classInitFunction(n.type().toReference());
    }

    public static String classInitFunction(ReferenceType n) {
        return JAVA_PREFIX + "init_" + n.toString().length() + n.toString();
    }

    public static String interfacesInitFunction(ReferenceType rt) {
        return JAVA_PREFIX + "it_init_" + rt.toString().length() + rt.toString();
    }

    public static String interfaceStringVariable(ReferenceType it) {
        return JAVA_PREFIX + "itype_" + it.toString().length() + it.toString();
    }
}
