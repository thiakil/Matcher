package matcher.type;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldNode;

import matcher.Util;

public class FieldInstance extends MemberInstance<FieldInstance> {
	/**
	 * Create a shared unknown field.
	 */
	FieldInstance(ClassInstance cls, String origName, String desc, boolean isStatic) {
		this(cls, origName, desc, null, false, -1, isStatic);
	}

	/**
	 * Create a known field.
	 */
	FieldInstance(ClassInstance cls, String origName, String desc, FieldNode asmNode, boolean nameObfuscated, int position) {
		this(cls, origName, desc, asmNode, nameObfuscated, position, (asmNode.access & Opcodes.ACC_STATIC) != 0);
	}

	private FieldInstance(ClassInstance cls, String origName, String desc, FieldNode asmNode, boolean nameObfuscated, int position, boolean isStatic) {
		super(cls, getId(origName, desc), origName, nameObfuscated, position, isStatic);

		this.type = cls.getEnv().getCreateClassInstance(desc);
		this.asmNode = asmNode;

		type.fieldTypeRefs.add(this);
	}

	@Override
	public String getDesc() {
		return type.id;
	}

	@Override
	public boolean isReal() {
		return asmNode != null;
	}

	public FieldNode getAsmNode() {
		return asmNode;
	}

	public ClassInstance getType() {
		return type;
	}

	@Override
	public int getAccess() {
		if (asmNode == null) {
			int ret = Opcodes.ACC_PUBLIC;
			if (isStatic) ret |= Opcodes.ACC_STATIC;
			if (isStatic && type == cls && cls.isEnum()) ret |= Opcodes.ACC_ENUM;
			if (isStatic && cls.isInterface()) ret |= Opcodes.ACC_FINAL;

			return ret;
		} else {
			return asmNode.access;
		}
	}

	public List<AbstractInsnNode> getInitializer() {
		return initializer;
	}

	public Set<MethodInstance> getReadRefs() {
		return readRefs;
	}

	public Set<MethodInstance> getWriteRefs() {
		return writeRefs;
	}

	static String getId(String name, String desc) {
		return name+";;"+desc;
	}

	final FieldNode asmNode;
	final ClassInstance type;
	ClassInstance exactType;
	List<AbstractInsnNode> initializer;

	final Set<MethodInstance> readRefs = Util.newIdentityHashSet();
	final Set<MethodInstance> writeRefs = Util.newIdentityHashSet();
}