package matcher.fernflower;

import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.modules.renamer.ConverterHelper;
import org.jetbrains.java.decompiler.modules.renamer.IdentifierConverter;
import org.jetbrains.java.decompiler.modules.renamer.PoolInterceptor;
import org.jetbrains.java.decompiler.struct.IDecompiledData;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructContext;
import org.jetbrains.java.decompiler.struct.lazy.LazyLoader;
import org.jetbrains.java.decompiler.util.ClasspathScanner;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Created by Thiakil on 7/01/2018.
 */
public class AsmDecompiler implements IDecompiledData {
    private StructContext structContext;
    private final ClassesProcessor classProcessor;
    private IIdentifierRenamer helper;
    private IdentifierConverter converter;
    private PoolInterceptor interceptor = null;
    private Map<String, Object> options;
    private IFernflowerLogger logger;

    public AsmDecompiler(IBytecodeProvider provider, IResultSaver saver, Map<String, Object> options, IFernflowerLogger logger) {
        structContext = new StructContext(saver, this, new LazyLoader(provider));
        classProcessor = new ClassesProcessor(structContext);
        this.options = options;
        this.logger = logger;

        Object rename = options.get(IFernflowerPreferences.RENAME_ENTITIES);
        if ("1".equals(rename) || rename == null && "1".equals(IFernflowerPreferences.DEFAULTS.get(IFernflowerPreferences.RENAME_ENTITIES))) {
            helper = loadHelper((String)options.get(IFernflowerPreferences.USER_RENAMER_CLASS));
            interceptor = new PoolInterceptor();
            converter = new IdentifierConverter(structContext, helper, interceptor);
        }

        DecompilerContext.initContext(options, logger, structContext, classProcessor, interceptor);

        /*if (DecompilerContext.getOption(IFernflowerPreferences.INCLUDE_ENTIRE_CLASSPATH)) {
            ClasspathScanner.addAllClasspath(structContext);
        }*/
    }

    private void decompileContextInner() {
        if (converter != null) {
            converter.rename();
        }

        classProcessor.loadClasses(helper);

        structContext.saveContext();
    }

    private static IIdentifierRenamer loadHelper(String className) {
        if (className != null) {
            try {
                Class<?> renamerClass = Fernflower.class.getClassLoader().loadClass(className);
                return (IIdentifierRenamer) renamerClass.getDeclaredConstructor().newInstance();
            }
            catch (Exception ignored) { }
        }

        return new ConverterHelper();
    }

    public void clearContext() {
        DecompilerContext.clearContext();
    }

    public StructContext getStructContext() {
        return structContext;
    }

    @Override
    public String getClassEntryName(StructClass cl, String entryName) {
        ClassesProcessor.ClassNode node = classProcessor.getMapRootClasses().get(cl.qualifiedName);
        if (node.type != ClassesProcessor.ClassNode.CLASS_ROOT) {
            return null;
        }
        else if (converter != null) {
            String simpleClassName = cl.qualifiedName.substring(cl.qualifiedName.lastIndexOf('/') + 1);
            return entryName.substring(0, entryName.lastIndexOf('/') + 1) + simpleClassName + ".java";
        }
        else {
            return entryName.substring(0, entryName.lastIndexOf(".class")) + ".java";
        }
    }

    @Override
    public String getClassContent(StructClass cl) {
        try {
            TextBuffer buffer = new TextBuffer(ClassesProcessor.AVERAGE_CLASS_SIZE);
            buffer.append(DecompilerContext.getProperty(IFernflowerPreferences.BANNER).toString());
            classProcessor.writeClass(cl, buffer);
            return buffer.toString();
        }
        catch (Throwable ex) {
            DecompilerContext.getLogger().writeMessage("Class " + cl.qualifiedName + " couldn't be fully decompiled.", ex);
            return null;
        }
    }

    public void addData(String clazz, byte[] data, boolean isOwn) {
        try {
            getStructContext().addData("/" + clazz+".class", clazz+".class", data, isOwn);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void decompileContext() {
        try {
            decompileContextInner();
        }
        finally {
            clearContext();
            /*structContext.getClasses().clear();
            DecompilerContext.initContext(options, logger, structContext, classProcessor, interceptor);*/
        }
    }
}
