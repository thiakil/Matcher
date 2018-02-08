package matcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import matcher.classifier.ClassClassifier;
import matcher.classifier.ClassifierUtil;
import matcher.mapping.MappingFormat;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;

public class JSONifier {
	public static void main(String[] args) throws IOException {
		ClassEnvironment env;
		
		Matcher.init();
		
		env = new ClassEnvironment();
		Matcher matcher = new Matcher(env);
		ProjectConfig config = new ProjectConfig();
		
		config.getPathsA().add(Paths.get("E:\\MCCache\\1.12.2_merged_classes.jar"));
		DoubleConsumer simpleProgressListener = v->{
			System.out.printf("%.2f", v*100.0);
			System.out.print("%          \r");
		};
		
		System.out.println("Loading inputs");
		matcher.init(config, simpleProgressListener);
		
		matcher.readMappings(Paths.get("C:\\Users\\lex\\Downloads\\1.13mappings\\mcp\\20171112\\1.12.2"), MappingFormat.MCP, true, true);
		
		BufferedWriter writer = Files.newBufferedWriter(Paths.get("C:\\Users\\lex\\Downloads\\1.13mappings\\1.12.2.classifiers.json"));

		JsonObject root = new JsonObject();

		JsonObject header = new JsonObject();
		root.add("header", header);

		header.addProperty("relation", "mcClasses");

		JsonArray attributes = new JsonArray();
		header.add("attributes", attributes);
		
		addAttribute(attributes, "obfName", "string");
		addAttribute(attributes, "obfIndex", "numeric");
		addAttribute(attributes, "mappedName", "nominal", env.getClassesA().stream().filter(ClassInstance::hasMappedName).map(c->c.getMappedName(true)).toArray(String[]::new));
		addAttribute(attributes, "hierarchyDepth", "numeric");
		addAttribute(attributes, "classType", "nominal", "ENUM","INTERFACE","ANNOTATION","ABSTRACT","SYNTHETIC","CLASS");
		addAttribute(attributes, "hierarchySiblings", "numeric");
		addAttribute(attributes, "parentClass", "string");
		addAttribute(attributes, "childClasses", "string");
		addAttribute(attributes, "interfaces", "string");
		addAttribute(attributes, "implementers", "string");
		addAttribute(attributes, "outerClass", "string");
		addAttribute(attributes, "innerClasses", "string");
		addAttribute(attributes, "methodCount", "numeric");
		addAttribute(attributes, "fieldCount", "numeric");
		addAttribute(attributes, "outReferences", "string");
		addAttribute(attributes, "inReferences", "string");
		addAttribute(attributes, "methodOutReferences", "string");
		addAttribute(attributes, "methodInReferences", "string");
		addAttribute(attributes, "fieldReadReferences", "string");
		addAttribute(attributes, "fieldWriteReferences", "string");
		//addAttribute(attributes, "stringConstants", "string");
		addAttribute(attributes, "numericConstants", "string");
		addAttribute(attributes, "classAnnotations", "string");

		JsonArray data = new JsonArray(env.getClassesA().size());
		root.add("data", data);
		
		for (ClassInstance cls : env.getClassesA()){
			JsonObject dataEl = new JsonObject();
			data.add(dataEl);

			dataEl.addProperty("sparse", false);
			dataEl.addProperty("weight", 1.0);

			JsonArray valuesArray = new JsonArray();
			dataEl.add("values", valuesArray);

			valuesArray.add(cls.getName());
			valuesArray.add(ClassClassifier.getObfIndex(cls.getName()));
			valuesArray.add(cls.getMappedName(false));
			valuesArray.add(ClassClassifier.getHierarchDepth(cls));
			valuesArray.add(getClassType(cls.getAccess()));
			valuesArray.add(cls.getSuperClass().getChildClasses().size());
			valuesArray.add(cls.getSuperClass() != null ? cls.getSuperClass().getName() : "");
			valuesArray.add(toNameList(cls.getChildClasses()));
			valuesArray.add(toNameList(cls.getInterfaces()));
			valuesArray.add(toNameList(cls.getImplementers()));
			valuesArray.add(cls.getOuterClass() != null ? cls.getOuterClass().getName() : "");
			valuesArray.add(toNameList(cls.getInnerClasses()));
			valuesArray.add(cls.getMethods().length);
			valuesArray.add(cls.getFields().length);
			valuesArray.add(toNameList(ClassClassifier.getOutRefs(cls)));
			valuesArray.add(toNameList(ClassClassifier.getInRefs(cls)));
			valuesArray.add(toNameListMember(ClassClassifier.getMethodOutRefs(cls)));
			valuesArray.add(toNameListMember(ClassClassifier.getMethodInRefs(cls)));
			valuesArray.add(toNameListMember(ClassClassifier.getFieldReadRefs(cls)));
			valuesArray.add(toNameListMember(ClassClassifier.getFieldWriteRefs(cls)));
			//valuesArray.add(String.join(",", cls.getStrings()));
			valuesArray.add(extractNumbers(cls));
			valuesArray.add(cls.getAnnotations().stream().collect(Collectors.joining("#")));
			
		}
		writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(root));
		writer.flush();
	}
	
	private static String getClassType(int access){
		if ((access & Opcodes.ACC_ENUM) != 0){
			return "ENUM";
		}
 		if ((access & Opcodes.ACC_INTERFACE) != 0){
			return "INTERFACE";
		}
 		if ((access & Opcodes.ACC_ANNOTATION) != 0){
			return "ANNOTATION";
		}
 		if ((access & Opcodes.ACC_ABSTRACT) != 0){
			return "ABSTRACT";
		}
 		if ((access & Opcodes.ACC_SYNTHETIC) != 0){
			return "SYNTHETIC";
		}
 		return "CLASS";
	}
	
	private static String toNameList(Collection<ClassInstance> classes){
		return classes.stream().map(ClassInstance::getName).collect(Collectors.joining(","));
	}
	
	private static String toNameListMember(Set<? extends MemberInstance> member){
		return member.stream().map(memberInstance -> memberInstance.getDisplayName(true, false)).collect(Collectors.joining(","));
	}
	
	private static String extractNumbers(ClassInstance cls) {
		Set<Integer> ints = new HashSet<>();
		Set<Long> longs = new HashSet<>();
		Set<Float> floats = new HashSet<>();
		Set<Double> doubles = new HashSet<>();
		
		for (MethodInstance method : cls.getMethods()) {
			MethodNode asmNode = method.getAsmNode();
			if (asmNode == null) continue;
			
			ClassifierUtil.extractNumbers(asmNode, ints, longs, floats, doubles);
		}
		
		for (FieldInstance field : cls.getFields()) {
			FieldNode asmNode = field.getAsmNode();
			if (asmNode == null) continue;
			
			ClassifierUtil.handleNumberValue(asmNode.value, ints, longs, floats, doubles);
		}
		
		List<Number> numbers = new ArrayList<>(ints.size()+longs.size()+floats.size()+doubles.size());
		numbers.addAll(ints);
		numbers.addAll(longs);
		numbers.addAll(floats);
		numbers.addAll(doubles);
		return numbers.stream().map(Object::toString).collect(Collectors.joining(","));
	}

	private static void addAttribute(JsonArray list, String name, String type, String... values){
		JsonObject obj = new JsonObject();
		list.add(obj);
		obj.addProperty("name", name);
		obj.addProperty("type", type);
		obj.addProperty("weight", 1.0);
		if (values.length > 0){
			JsonArray vals = new JsonArray(values.length);
			obj.add("labels", vals);
			for (String v : values){
				vals.add(v);
			}
		}
	}

	private static void addArrayElements(JsonObject node, String key, String... values){
		JsonArray array = new JsonArray(values.length);
		node.add(key, array);
		for (String v : values){
			array.add(v);
		}
	}
}
