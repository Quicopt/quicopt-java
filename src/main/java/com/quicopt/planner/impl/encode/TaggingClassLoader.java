// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.encode;

import java.io.IOException;
import java.io.InputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Loads one class, the constraint provider, with every lambda creation site rewritten to register the new lambda
 * in {@link LambdaRegistry}. Every other class comes from the parent loader unchanged, so the domain's objects
 * are shared with the rest of the application.
 */
public final class TaggingClassLoader extends ClassLoader {

    private static final String METAFACTORY = "java/lang/invoke/LambdaMetafactory";
    private static final String REGISTRY = Type.getInternalName(LambdaRegistry.class);
    private final String target;

    /**
     * @param parent the loader of everything else, including the domain classes
     * @param target the binary name of the one class to rewrite
     */
    public TaggingClassLoader(ClassLoader parent, String target) {
        super(parent);
        this.target = target;
    }

    /**
     * Defines the target class from rewritten bytes and delegates every other class to the parent.
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.equals(target)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            var loaded = findLoadedClass(name);
            if (loaded == null) {
                var rewritten = rewrite(readBytes(getParent(), name));
                loaded = defineClass(name, rewritten, 0, rewritten.length);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    /**
     * @return the class file of a class, as the given loader sees it
     * @throws IllegalStateException when the loader has no class file for it
     */
    static byte[] readBytes(ClassLoader loader, String className) {
        try (InputStream in = loader.getResourceAsStream(className.replace('.', '/') + ".class")) {
            if (in == null) {
                throw new IllegalStateException("No class file for " + className);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the class file of " + className, e);
        }
    }

    /**
     * @return the class file with a {@link LambdaRegistry#tag} call after every lambda creation site
     */
    private static byte[] rewrite(byte[] original) {
        var reader = new ClassReader(original);
        // The inserted code is straight-line with zero net stack effect, so the
        // existing stack map frames stay valid; only max stack grows.
        var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new TaggingVisitor(writer, reader.getClassName()), 0);
        return writer.toByteArray();
    }

    /**
     * Passes a class through, rewriting each method with a {@link TaggingMethodVisitor}. The rewritten class lives
     * in its own loader, apart from any class it is nested in, so the links to that outer class are dropped: the
     * JVM would otherwise follow them, and may not be allowed to access the outer class from here.
     */
    private static final class TaggingVisitor extends ClassVisitor {

        private final String className;

        /**
         * @param next the writer of the rewritten class
         * @param className the internal name of the class being rewritten
         */
        TaggingVisitor(ClassVisitor next, String className) {
            super(Opcodes.ASM9, next);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
            return new TaggingMethodVisitor(super.visitMethod(access, name, desc, signature, exceptions));
        }

        /**
         * Drops the nest host, which the JVM consults to check nest membership.
         */
        @Override
        public void visitNestHost(String nestHost) {
        }

        /**
         * Drops the class's own entry among its inner classes, which names the class it is nested in; entries for
         * other classes pass through.
         */
        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (!name.equals(className)) {
                super.visitInnerClass(name, outerName, innerName, access);
            }
        }
    }

    /**
     * Inserts {@code dup; ldc siteId; invokestatic LambdaRegistry.tag} after each lambda creation.
     */
    private static final class TaggingMethodVisitor extends MethodVisitor {

        /**
         * @param next the writer of the rewritten method
         */
        TaggingMethodVisitor(MethodVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrap, Object... args) {
            super.visitInvokeDynamicInsn(name, desc, bootstrap, args);
            if (METAFACTORY.equals(bootstrap.getOwner()) && args.length >= 3 && args[1] instanceof Handle impl) {
                int site = LambdaRegistry.register(new LambdaRegistry.Site(impl, ((Type) args[2]).getDescriptor()));
                super.visitInsn(Opcodes.DUP);
                super.visitLdcInsn(site);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, REGISTRY, "tag", "(Ljava/lang/Object;I)V", false);
            }
        }
    }
}
