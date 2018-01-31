package matcher.bcremap;

import org.objectweb.asm.commons.Remapper;

import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.IClassEnv;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class AsmRemapper extends Remapper {
	public AsmRemapper(IClassEnv env) {
		this.env = env;
	}

	public static String fixName(String name){
		switch (name) {
			case "do":
				return "do_";
			case "if":
				return "if_";
		}
		return name;
	}

	@Override
	public String map(String typeName) {
		ClassInstance cls = env.getClsByName(typeName);
		if (cls == null) return typeName;

		return cls.getMappedName(true);
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		ClassInstance cls = env.getClsByName(owner);
		if (cls == null) return name;

		FieldInstance field = cls.resolveField(name, desc);
		if (field == null) return name;

		return fixName(field.getMappedName(true));
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		ClassInstance cls = env.getClsByName(owner);
		if (cls == null) return name;

		MethodInstance method = cls.getMethod(name, desc);

		if (method == null) {
			assert false : String.format("can't find method %s%s in %s", name, desc, cls);;
			return name;
		}

		return fixName(method.getMappedName(true));
	}

	public String mapMethodName(String owner, String name, String desc, boolean itf) {
		ClassInstance cls = env.getClsByName(owner);
		if (cls == null) return name;

		MethodInstance method = cls.resolveMethod(name, desc, itf);
		if (method == null) return name;

		return fixName(method.getMappedName(true));
	}

	public String mapArbitraryInvokeDynamicMethodName(String owner, String name) {
		ClassInstance cls = env.getClsByName(owner);
		if (cls == null) return name;

		MethodInstance method = cls.getMethod(name, null);
		if (method == null) return name;

		return method.getMappedName(true);
	}

	public String mapLocalVariableName(String className, String methodName, String methodDesc, String name, String desc, int lvtIndex, int startInsn, int endInsn) {
		ClassInstance cls = env.getClsByName(className);
		if (cls == null) return name;

		MethodInstance method = cls.getMethod(methodName, methodDesc);
		if (method == null) return name;

		for (MethodVarInstance var : method.getArgs()) { // TODO: iterate all method vars once available
			if (var.getLvtIndex() == lvtIndex && var.getEndInsn() > startInsn && var.getStartInsn() < endInsn) {
				assert var.getType().getId().equals(desc);

				return var.getMappedName(true);
			}
		}

		return name;
	}

	private final IClassEnv env;
}
