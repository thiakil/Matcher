package matcher;

import matcher.fernflower.AsmContext;
import matcher.fernflower.AsmDecompiler;
import matcher.fernflower.NullLogger;
import matcher.type.ClassFeatureExtractor;
import matcher.type.ClassInstance;
import org.jetbrains.java.decompiler.main.ClassWriter;
import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.collectors.BytecodeSourceMapper;
import org.jetbrains.java.decompiler.main.collectors.ImportCollector;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.main.rels.LambdaProcessor;
import org.jetbrains.java.decompiler.main.rels.NestedClassProcessor;
import org.jetbrains.java.decompiler.main.rels.NestedMemberAccess;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.lazy.LazyLoader;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

/**
 * Created by Thiakil on 7/01/2018.
 */
public class FernFlowerDecompiler {
	//private static ThreadLocal<AsmContext> contextLocal = ThreadLocal.withInitial(AsmContext::new);
	/*private static ThreadLocal<Fernflower> fernflowerLocal = ThreadLocal.withInitial(() -> {
		Map<String,Object> options = new HashMap<>();

		Fernflower ff = new Fernflower(contextLocal.get(), new VoidSaver(), options, new PrintStreamLogger(System.out));
		ff.decompileContext();
		return ff;
	});
	private static ThreadLocal<LazyLoader> lazyLoaderLocal = ThreadLocal.withInitial(()->new LazyLoader(contextLocal.get()));*/

	private static Map<String,Object> options = new HashMap<>(IFernflowerPreferences.getDefaults());
	static {
		options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
		options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "0");
		options.put(IFernflowerPreferences.MAX_PROCESSING_METHOD, "30");
	}
	//options.put(IFernflowerPreferences.USE_JAD_VARNAMING, "1");
	//options.put(IFernflowerPreferences.USE_DEBUG_VAR_NAMES, "0");
	//options.put(IFernflowerPreferences.RENAME_ENTITIES, "1");

	private static MySaver saver = new MySaver();
	private static AsmContext mycontext = new AsmContext();

	public static synchronized String decompile(ClassInstance cls, ClassFeatureExtractor extractor, boolean mapped) {
		AsmDecompiler decompiler = new AsmDecompiler(mycontext, saver, options, Util.DEBUG ? new PrintStreamLogger(System.out) : NullLogger.INSTANCE);
		ClassInstance toAdd = cls;
		while (toAdd.getOuterClass() != null)
			toAdd = toAdd.getOuterClass();
		try {
			addClass(cls, extractor, decompiler, mapped);
			saver.wanted = cls.getName()+".class";
			if (saver.result == null)
				saver.result = new HashMap<>();
			saver.result.clear();
			decompiler.decompileContext();
		} catch (Exception e){
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			return "Decompiler threw an exception: "+e.getMessage()+"\n"+sw.toString();
		}
		if (saver.result.containsKey(saver.wanted))
			return saver.result.get(saver.wanted);
		return "Decompilation did not find a result, num decompiled: "+saver.result.size();
		/*AsmContext context = contextLocal.get();
		context.classEnv = extractor;
		Fernflower fernflower = fernflowerLocal.get();
		//StructClass clazz = getStructClass(context, cls, cls.hasMappedName());
		try {
			fernflower.getStructContext().addData(path, cls.getName(), context.classEnv.serializeClass(cls, mapped), cls.getOuterClass() == null);
		} catch (IOException e){
			//throw new IllegalStateException(e);
			return null;
		}
		StructClass clazz = fernflower.getStructContext().getClass(cls.getName());
		fernflower.getStructContext().getClasses().remove(cls.getName());
		return clazz != null ? fernflower.getClassContent(clazz) : "";*/
	}

	private static void addClass(ClassInstance cls, ClassFeatureExtractor extractor, AsmDecompiler decompiler, boolean mapped) {
		//System.out.println("adding "+cls.getName());
		decompiler.addData(cls.getName(), extractor.serializeClass(cls, mapped), /*cls.getOuterClass() == null*/true);
		for (ClassInstance inner : cls.getInnerClasses()){
			//decompiler.addData(inner.getName(), extractor.serializeClass(inner, mapped), false);
			addClass(inner, extractor, decompiler, mapped);
		}
	}

	/*public static StructClass getStructClass(AsmContext context, ClassInstance instance, boolean mapped){
		try {
			return new StructClass(context.classEnv.serializeClass(instance, mapped), false, lazyLoaderLocal.get());
		} catch (IOException e){
			//throw new IllegalStateException(e);
			return null;
		}
	}*/

	private static class MySaver implements IResultSaver {

		Map<String,String> result = new HashMap<>();
		String wanted;

		@Override
		public void saveFolder(String path) {

		}

		@Override
		public void copyFile(String source, String path, String entryName) {

		}

		@Override
		public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
			//if (path.equals(wanted))
				result.put(path, content);
		}

		@Override
		public void createArchive(String path, String archiveName, Manifest manifest) {

		}

		@Override
		public void saveDirEntry(String path, String archiveName, String entryName) {

		}

		@Override
		public void copyEntry(String source, String path, String archiveName, String entry) {

		}

		@Override
		public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {

		}

		@Override
		public void closeArchive(String path, String archiveName) {

		}
	}
}
