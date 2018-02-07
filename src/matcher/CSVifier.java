package matcher;

import matcher.classifier.ClassClassifier;
import matcher.classifier.ClassifierUtil;
import matcher.mapping.MappingFormat;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MemberInstance;
import matcher.type.MethodInstance;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.BufferedWriter;
import java.io.File;
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

public class CSVifier {
	public static void main(String[] args) throws IOException {
		ClassEnvironment env;
		
		Matcher.init();
		
		env = new ClassEnvironment();
		Matcher matcher = new Matcher(env);
		ProjectConfig config = new ProjectConfig();
		
		config.getPathsA().add(Paths.get("C:\\Users\\xander.v\\Downloads\\1.12.2_merged_classes.jar"));
		DoubleConsumer simpleProgressListener = v->{
			System.out.printf("%.2f", v*100.0);
			System.out.print("%          \r");
		};
		
		System.out.println("Loading inputs");
		matcher.init(config, simpleProgressListener);
		
		matcher.readMappings(Paths.get("C:\\Users\\xander.v\\Downloads\\mcp\\1.12.2"), MappingFormat.MCP, true, true);
		
		BufferedWriter writer = Files.newBufferedWriter(Paths.get("C:\\Users\\xander.v\\Downloads\\mcp\\1.12.2.classifiers.csv"));
		
		CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
				"obfName",
				"obfIndex",
				"mappedName",
				"hierarchyDepth",
				"classType",
				"hierarchySiblings",
				"parentClass",
				"childClasses",
				"interfaces",
				"implementers",
				"outerClass",
				"innerClasses",
				"methodCount",
				"fieldCount",
				"outReferences",
				"inReferences",
				"methodOutReferences",
				"methodInReferences",
				"fieldReadReferences",
				"fieldWriteReferences",
				"stringConstants",
				"numericConstants",
				"classAnnotations"
		).withEscape('\\').withQuote(null).withDelimiter('\t'));
		for (ClassInstance cls : env.getClassesA()){
			csvPrinter.printRecord(
					cls.getName(),
					ClassClassifier.getObfIndex(cls.getName()),
					cls.getMappedName(false),
					ClassClassifier.getHierarchDepth(cls),
					getClassType(cls.getAccess()),
					cls.getSuperClass().getChildClasses().size(),
					cls.getSuperClass(),
					toNameList(cls.getChildClasses()),
					toNameList(cls.getInterfaces()),
					toNameList(cls.getImplementers()),
					cls.getOuterClass() != null ? cls.getOuterClass().getName() : "",
					toNameList(cls.getInnerClasses()),
					cls.getMethods().length,
					cls.getFields().length,
					toNameList(ClassClassifier.getOutRefs(cls)),
					toNameList(ClassClassifier.getInRefs(cls)),
					toNameListMember(ClassClassifier.getMethodOutRefs(cls)),
					toNameListMember(ClassClassifier.getMethodInRefs(cls)),
					toNameListMember(ClassClassifier.getFieldReadRefs(cls)),
					toNameListMember(ClassClassifier.getFieldWriteRefs(cls)),
					String.join(",", cls.getStrings()),
					extractNumbers(cls),
					cls.getAnnotations().stream().collect(Collectors.joining("#"))
			);
		}
		csvPrinter.flush();
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
}
