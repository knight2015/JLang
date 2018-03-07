package polyllvm.util;

import polyglot.ast.*;
import polyglot.types.*;
import polyglot.util.InternalCompilerError;
import polyglot.util.Position;
import polyllvm.ast.*;
import polyllvm.extension.PolyLLVMArrayAccessExt;
import polyllvm.extension.PolyLLVMCallExt;
import polyllvm.types.PolyLLVMTypeSystem;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper methods for creating typed nodes. Handles generics.
 *
 * This is used in PolyLLVM, so we don't care about (for example)
 * exception types on method instances.
 */
public class TypedNodeFactory {
    protected final PolyLLVMTypeSystem ts;
    protected final PolyLLVMNodeFactory nf;

    public TypedNodeFactory(PolyLLVMTypeSystem ts, PolyLLVMNodeFactory nf) {
        this.ts = ts;
        this.nf = nf;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Formals and variables.
    ////////////////////////////////////////////////////////////////////////////

    public FieldDecl FieldDecl(
            Position pos, String name, Type type, ParsedClassType container,
            Expr init, Flags flags) {
        FieldInstance fi = ts.fieldInstance(pos, container, flags, type, name);
        container.addField(fi);
        return nf.FieldDecl(pos, flags, nf.CanonicalTypeNode(pos, type), nf.Id(pos, name), init)
                .fieldInstance(fi);
    }

    public Field StaticField(Position pos, String name, ReferenceType container) {
        return Field(pos, nf.CanonicalTypeNode(pos, container), name);
    }

    public Field Field(Position pos, Receiver receiver, String name) {
        // We lie and tell the type system that fromClass == container since we want to bypass
        // visibility checks. If container is not a class type, we instead use Object.
        ReferenceType container = receiver.type().toReference();
        ClassType fromClass = container.isClass() ? container.toClass() : ts.Object();
        try {
            FieldInstance fi = ts.findField(container, name, fromClass, /*fromClient*/ true);
            return (Field) nf.Field(pos, receiver, nf.Id(pos, name))
                    .fieldInstance(fi)
                    .type(fi.type());
        } catch (SemanticException e) {
            throw new InternalCompilerError(e);
        }
    }

    public Formal Formal(Position pos, String name, Type type, Flags flags) {
        return nf.Formal(pos, flags, nf.CanonicalTypeNode(pos, type), nf.Id(pos, name))
                .localInstance(ts.localInstance(pos, flags, type, name));
    }

    public LocalDecl TempVar(Position pos, String name, Type type, Expr init) {
        return Temp(pos, name, type, init, Flags.NONE, /*isSSA*/ false);
    }

    public LocalDecl TempSSA(String name, Expr init) {
        if (init == null)
            throw new InternalCompilerError("SSA temporaries must have an init expression");
        return Temp(init.position(), name, init.type(), init, Flags.FINAL, /*isSSA*/ true);
    }

    private LocalDecl Temp(
            Position pos, String name, Type type, Expr init, Flags flags, boolean isSSA) {
        return nf.LocalDecl(pos, flags, nf.CanonicalTypeNode(pos, type), nf.Id(pos, name), init)
                .localInstance(ts.localInstance(pos, flags, type, name, /*isTemp*/ true, isSSA));
    }

    public Local Local(Position pos, VarDecl vd) {
        return Local(pos, vd.localInstance());
    }

    public Local Local(Position pos, LocalInstance li) {
        return (Local) nf.Local(pos, nf.Id(pos, li.name())).localInstance(li).type(li.type());
    }

    ////////////////////////////////////////////////////////////////////////////
    // Methods and constructors.
    ////////////////////////////////////////////////////////////////////////////

    public MethodDecl MethodDecl(
            Position pos, String name, ParsedClassType container, Type returnType,
            List<Formal> formals, Block body, Flags flags) {
        List<Type> argTypes = formals.stream().map(Formal::declType).collect(Collectors.toList());
        MethodInstance mi = ts.methodInstance(
                pos, container, flags, returnType, name, argTypes,
                Collections.emptyList());  // PolyLLVM does not care about exn types.
        container.addMethod(mi);
        return nf.MethodDecl(
                pos, flags, nf.CanonicalTypeNode(pos, returnType), nf.Id(pos, name), formals,
                Collections.emptyList(), // PolyLLVM does not care about exn types.
                body, /*javaDoc*/ null)
                .methodInstance(mi);
    }

    public ConstructorDecl ConstructorDecl(
            Position pos, ParsedClassType container, List<Formal> formals, Block body) {
        List<Type> argTypes = formals.stream().map(Formal::declType).collect(Collectors.toList());
        Flags flags = Flags.NONE; // Constructor flags not important for PolyLLVM.
        ConstructorInstance ci = ts.constructorInstance(
                pos, container, flags, argTypes,
                Collections.emptyList()); // PolyLLVM does not care about exn types.
        container.addConstructor(ci);
        return nf.ConstructorDecl(
                pos, flags, nf.Id(pos, container.name()),
                formals, Collections.emptyList(), body, /*javaDoc*/ null)
                .constructorInstance(ci);
    }

    public Call StaticCall(
            Position pos, String name, ClassType container, Type returnType, Expr... args) {
        return Call(pos, nf.CanonicalTypeNode(pos, container), name, container, returnType, args);
    }

    public Call Call(
            Position pos, Receiver receiver, String name, ClassType container,
            Type returnType, Expr... args) {
        List<Type> argTypes = Arrays.stream(args).map(Expr::type).collect(Collectors.toList());
        try {
            MethodInstance mi = ts.findMethod(
                    container, name, argTypes, /*actualTypeArgs*/ null, container,
                    returnType, /*fromClient*/ true);
            Call c = (Call) nf.Call(pos, receiver, nf.Id(pos, name), args)
                    .methodInstance(mi)
                    .type(returnType);
            PolyLLVMCallExt ext = (PolyLLVMCallExt) PolyLLVMExt.ext(c);
            return ext.determineIfDirect(c);
        } catch (SemanticException e) {
            throw new InternalCompilerError(e);
        }
    }

    public ConstructorCall ConstructorCall(
            Position pos, ConstructorCall.Kind kind, ClassType container, List<Expr> args) {
        List<Type> argTypes = args.stream().map(Expr::type).collect(Collectors.toList());
        try {
            ConstructorInstance ci = ts.findConstructor(
                    container, argTypes, /*actualTypeArgs*/ null, container, /*fromClient*/ true);
            return nf.ConstructorCall(pos, kind, args).constructorInstance(ci);
        } catch (SemanticException e) {
            throw new InternalCompilerError(e);
        }
    }

    /**
     * Assumes that {@link polyllvm.visit.DeclareExplicitAnonCtors} has already declared
     * explicit constructors for anonymous classes.
     */
    public New New(Position pos, ClassType type, Expr outer, List<Expr> args, ClassBody body) {
        List<Type> argTypes = args.stream().map(Expr::type).collect(Collectors.toList());
        try {
            ConstructorInstance ci = ts.findConstructor(
                    type, argTypes, /*actualTypeArgs*/ null, type, /*fromClient*/ true);
            New res = (New) nf.New(pos, outer, nf.CanonicalTypeNode(pos, type), args, body)
                    .constructorInstance(ci)
                    .type(type);
            if (body != null) {
                if (!(type instanceof ParsedClassType))
                    throw new InternalCompilerError(
                            "Trying to create new anonymous instance without parsed class type");
                res = res.anonType((ParsedClassType) type);
            }
            return res;

        } catch (SemanticException e) {
            throw new InternalCompilerError(e);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Misc
    ////////////////////////////////////////////////////////////////////////////

    public AddressOf AddressOf(Expr expr) {
        return (AddressOf) nf.AddressOf(expr.position(), expr).type(expr.type());
    }

    public Load Load(Expr expr) {
        return (Load) nf.Load(expr.position(), expr).type(expr.type());
    }

    public Cast Cast(Expr expr, Type type) {
        Position pos = expr.position();
        return (Cast) nf.Cast(pos, nf.CanonicalTypeNode(pos, type), expr).type(type);
    }

    public ClassLit ClassLit(Position pos, ReferenceType type) {
        Type classType = ts.Class(pos, type);
        return (ClassLit) nf.ClassLit(pos, nf.CanonicalTypeNode(pos, type)).type(classType);
    }

    public Eval EvalAssign(Position pos, Expr target, Expr val) {
        Assign assign = (Assign) nf.Assign(pos, target, Assign.ASSIGN, val).type(target.type());
        return nf.Eval(pos, assign);
    }

    public Special This(Position pos, ReferenceType container) {
        return (Special) nf.This(pos, nf.CanonicalTypeNode(pos, container)).type(container);
    }

    public Special UnqualifiedThis(Position pos, ReferenceType container) {
        return (Special) nf.This(pos).type(container);
    }

    public Instanceof InstanceOf(Expr expr, ReferenceType type) {
        Position pos = expr.position();
        CanonicalTypeNode typeNode = nf.CanonicalTypeNode(pos, type);
        return (Instanceof) nf.Instanceof(pos, expr, typeNode).type(ts.Boolean());
    }

    public If If(Expr cond, Stmt consequent) {
        assert cond.type().typeEquals(ts.Boolean());
        return nf.If(cond.position(), cond, consequent);
    }

    public ESeq ESeq(List<Stmt> statements, Expr expr) {
        return (ESeq) nf.ESeq(expr.position(), statements, expr).type(expr.type());
    }

    public Throw Throw(Position pos, ClassType t, List<Expr> args) {
        assert t.isSubtype(ts.Throwable());
        New exn = New(pos, t, /*outer*/ null, args, /*body*/ null);
        return nf.Throw(pos, exn);
    }

    public Unary Not(Expr expr) {
        assert expr.type().typeEquals(ts.Boolean());
        return (Unary) nf.Unary(expr.position(), Unary.NOT, expr).type(ts.Boolean());
    }

    public Binary CondOr(Expr l, Expr r) {
        assert l.type().typeEquals(ts.Boolean());
        assert r.type().typeEquals(ts.Boolean());
        return (Binary) nf.Binary(l.position(), l, Binary.COND_OR, r).type(ts.Boolean());
    }

    public ArrayAccess ArrayAccess(Expr base, Expr index, boolean alreadyGuarded) {
        assert base.type().isArray();
        ArrayAccess n = (ArrayAccess) nf.ArrayAccess(base.position(), base, index)
                .type(base.type().toArray().base());
        if (alreadyGuarded)
            n = ((PolyLLVMArrayAccessExt) PolyLLVMExt.ext(n)).setGuarded();
        return n;
    }

    public Type typeForName(String name) {
        try {
            return ts.typeForName(name);
        } catch (SemanticException e) {
            throw new InternalCompilerError(e);
        }
    }

    public StringLit StringLit(Position pos, String value) {
        return (StringLit) nf.StringLit(pos, value).type(ts.String());
    }
}