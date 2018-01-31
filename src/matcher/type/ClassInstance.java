package matcher.type;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import matcher.Util;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ClassInstance implements IMatchable<ClassInstance> {
	/**
	 * Create a shared unknown class.
	 */
	ClassInstance(String id, IClassEnv env) {
		this(id, null, env, null, false, false, null);

		assert id.indexOf('[') == -1 : id;
	}

	/**
	 * Create a known class (class path).
	 */
	public ClassInstance(String id, URI uri, IClassEnv env, ClassNode asmNode) {
		this(id, uri, env, asmNode, false, false, null);

		assert id.indexOf('[') == -1 : id;
	}

	/**
	 * Create an array class.
	 */
	ClassInstance(String id, ClassInstance elementClass) {
		this(id, null, elementClass.env, null, false, false, elementClass);

		assert id.startsWith("[") : id;
		assert id.indexOf('[', getArrayDimensions()) == -1 : id;
		assert !elementClass.isArray();

		elementClass.addArray(this);
	}

	/**
	 * Create a non-array class.
	 */
	ClassInstance(String id, URI uri, IClassEnv env, ClassNode asmNode, boolean nameObfuscated) {
		this(id, uri, env, asmNode, nameObfuscated, true, null);

		assert id.startsWith("L") : id;
		assert id.indexOf('[') == -1 : id;
		assert asmNode != null;
	}

	private ClassInstance(String id, URI uri, IClassEnv env, ClassNode asmNode, boolean nameObfuscated, boolean input, ClassInstance elementClass) {
		if (id.isEmpty()) throw new IllegalArgumentException("empty id");
		if (env == null) throw new NullPointerException("null env");

		this.id = id;
		this.uri = uri;
		this.env = env;
		this.asmNodes = asmNode == null ? null : new ClassNode[] { asmNode };
		this.nameObfuscated = nameObfuscated;
		this.input = input;
		this.elementClass = elementClass;

		if (env.isShared()) matchedClass = this;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getName() {
		return getName(id);
	}

	@Override
	public String getDisplayName(boolean full, boolean mapped) {
		int dims = getArrayDimensions();

		if (dims == 0) {
			if (!mapped) {
				return getName().replace('/', '.');
			} else {
				return getMappedName(true).replace('/', '.');
			}
		} else {
			StringBuilder ret;
			String mappedName;

			if (!mapped || (mappedName = getMappedName(false)) == null) {
				ret = new StringBuilder(id.length() + dims);

				if (id.charAt(dims) == 'L') {
					ret.append(id, dims + 1, id.length() - 1);
				} else {
					ret.append(id, dims, id.length());
				}
			} else {
				ret = new StringBuilder(mappedName.length() + dims * 2);
				ret.append(mappedName);
			}

			for (int i = 0; i < ret.length(); i++) {
				if (ret.charAt(i) == '/') ret.setCharAt(i, '.');
			}

			for (int i = 0; i < dims; i++) {
				ret.append("[]");
			}

			return ret.toString();
		}
	}

	public URI getUri() {
		return uri;
	}

	@Override
	public IClassEnv getEnv() {
		return env;
	}

	public ClassNode[] getAsmNodes() {
		return asmNodes;
	}

	public ClassNode getMergedAsmNode() {
		if (asmNodes == null) return null;
		if (asmNodes.length == 1) return asmNodes[0];

		ClassNode mergedNode = new ClassNode();
		asmNodes[0].accept(mergedNode);

		List<String> baseMethodNames = mergedNode.methods.stream().map(n->n.name+n.desc).collect(Collectors.toList());
		List<String> baseFieldNames = mergedNode.fields.stream().map(f->f.name+f.desc).collect(Collectors.toList());
		List<String> innerClasses = mergedNode.innerClasses.stream().map(ic->ic.name).collect(Collectors.toList());

		for (int i = 0; i < asmNodes.length; i++){
			for (FieldNode f : asmNodes[i].fields){
				if (!baseFieldNames.contains(f.name+f.desc)){
					mergedNode.fields.add(f);
					baseFieldNames.add(f.name+f.desc);
				}
			}

			for (MethodNode m : asmNodes[i].methods){
				if (!baseMethodNames.contains(m.name+m.desc)){
					mergedNode.methods.add(m);
					baseMethodNames.add(m.name+m.desc);
				}
			}

			for (InnerClassNode innerClassNode : asmNodes[i].innerClasses){
				if (!innerClasses.contains(innerClassNode.name)){
					innerClassNode.accept(mergedNode);
					innerClasses.add(innerClassNode.name);
				}
			}
		}

		return mergedNode;
	}

	void addAsmNode(ClassNode node) {
		if (!input) throw new IllegalStateException("not mergeable");

		asmNodes = Arrays.copyOf(asmNodes, asmNodes.length + 1);
		asmNodes[asmNodes.length - 1] = node;
	}

	@Override
	public ClassInstance getMatch() {
		return matchedClass;
	}

	public void setMatch(ClassInstance cls) {
		assert cls == null || cls.getEnv() != env && !cls.getEnv().isShared();

		this.matchedClass = cls;
	}

	@Override
	public boolean isNameObfuscated(boolean recursive) {
		return nameObfuscated;
	}

	public boolean isInput() {
		return input;
	}

	public ClassInstance getElementClass() {
		if (!isArray()) throw new IllegalStateException("not applicable to non-array");

		return elementClass;
	}

	public ClassInstance getElementClassShallow(boolean create) {
		if (!isArray()) throw new IllegalStateException("not applicable to non-array");

		int dims = getArrayDimensions();
		if (dims <= 1) return elementClass;

		String retId = id.substring(1);

		return create ? env.getCreateClassInstance(retId) : env.getClsById(retId);
	}

	public boolean isPrimitive() {
		char start = id.charAt(0);

		return start != 'L' && start != '[';
	}

	public int getSlotSize() {
		char start = id.charAt(0);

		return (start == 'D' || start == 'J') ? 2 : 1;
	}

	public boolean isArray() {
		return elementClass != null;
	}

	public int getArrayDimensions() {
		if (elementClass == null) return 0;

		for (int i = 0; i < id.length(); i++) {
			if (id.charAt(i) != '[') return i;
		}

		throw new IllegalStateException("invalid id: "+id);
	}

	public ClassInstance[] getArrays() {
		return arrays;
	}

	private void addArray(ClassInstance cls) {
		assert !Arrays.asList(arrays).contains(cls);

		arrays = Arrays.copyOf(arrays, arrays.length + 1);
		arrays[arrays.length - 1] = cls;
	}

	public int getAccess() {
		if (asmNodes != null) {
			return asmNodes[0].access;
		} else {
			int ret = Opcodes.ACC_PUBLIC;

			if (!implementers.isEmpty()) {
				ret |= Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
			} else if (superClass != null && superClass.id.equals("Ljava/lang/Enum;")) {
				ret |= Opcodes.ACC_ENUM;
				if (childClasses.isEmpty()) ret |= Opcodes.ACC_FINAL;
			} else if (interfaces.size() == 1 && interfaces.iterator().next().id.equals("Ljava/lang/annotation/Annotation;")) {
				ret |= Opcodes.ACC_ANNOTATION | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
			}

			return ret;
		}
	}

	public boolean isInterface() {
		return (getAccess() & Opcodes.ACC_INTERFACE) != 0;
	}

	public boolean isEnum() {
		return (getAccess() & Opcodes.ACC_ENUM) != 0;
	}

	public boolean isAnnotation() {
		return (getAccess() & Opcodes.ACC_ANNOTATION) != 0;
	}

	public MethodInstance getMethod(String id) {
		return methodIdx.get(id);
	}

	public FieldInstance getField(String id) {
		return fieldIdx.get(id);
	}

	public MethodInstance getMethod(String name, String desc) {
		if (desc != null) {
			return methodIdx.get(MethodInstance.getId(name, desc));
		} else {
			MethodInstance ret = null;

			for (MethodInstance method : methods) {
				if (method.origName.equals(name)) {
					if (ret != null) return null; // non-unique

					ret = method;
				}
			}

			return ret;
		}
	}

	public MethodInstance getMappedMethod(String name, String desc) {
		MethodInstance ret = null;

		methodLoop: for (MethodInstance method : methods) {
			String mappedName = method.getMappedName(false);
			if (mappedName == null || !name.equals(mappedName)) continue;

			if (desc != null) {
				assert desc.startsWith("(");
				int idx = 0;
				int pos = 1;
				boolean last = false;

				do {
					char c = desc.charAt(pos);
					ClassInstance match;

					if (c == ')') {
						last = true;
						pos++;
						c = desc.charAt(pos);
						match = method.retType;
					} else {
						if (idx >= method.args.length) continue methodLoop;
						match = method.args[idx].type;
					}

					int start = pos;
					int dims;

					if (c == '[') { // array cls
						dims = 1;
						while ((c = desc.charAt(++pos)) == '[') dims++;
					} else {
						dims = 0;
					}

					if (match.getArrayDimensions() != dims) continue methodLoop;

					int end;

					if (c != 'L') { // primitive cls
						end = pos + 1;
					} else {
						end = desc.indexOf(';', pos + 1) + 1;
						assert end != 0;
					}

					String clsMappedName = match.getMappedName(false);

					if (clsMappedName == null) { // TODO: only do this in the 2nd pass
						if (match.id.length() != end - start || !desc.startsWith(match.id, start)) continue methodLoop;
					} else if (c != 'L') {
						if (clsMappedName.length() != end - pos || !desc.startsWith(clsMappedName, pos)) continue methodLoop;
					} else {
						if (clsMappedName.length() != end - pos - 2 || !desc.startsWith(clsMappedName, pos + 1)) continue methodLoop;
					}

					pos = end;
					idx++;
				} while (!last);
			}

			if (ret != null) return null; // non-unique

			ret = method;
		}

		if (ret == null) ret = getMethod(name, desc);

		return ret;
	}

	public FieldInstance getField(String name, String desc) {
		if (desc != null) {
			return fieldIdx.get(FieldInstance.getId(name, desc));
		} else {
			FieldInstance ret = null;

			for (FieldInstance field : fields) {
				if (field.origName.equals(name)) {
					if (ret != null) return null; // non-unique

					ret = field;
				}
			}

			return ret;
		}
	}

	public FieldInstance getMappedField(String name, String desc) {
		FieldInstance ret = null;

		for (FieldInstance field : fields) {
			String mappedName = field.getMappedName(false);
			if (mappedName == null || !name.equals(mappedName)) continue;

			if (desc != null) {
				String clsMappedName = field.type.getMappedName(false);

				if (clsMappedName == null) { // TODO: only do this in the 2nd pass
					if (!desc.equals(field.type.id)) continue;
				} else {
					if (desc.length() != clsMappedName.length() + 2 || !desc.startsWith(clsMappedName, 1)) continue;
				}
			}

			if (ret != null) return null; // non-unique

			ret = field;
		}

		if (ret == null) return getField(name, desc);

		return ret;
	}

	public MethodInstance resolveMethod(String name, String desc, boolean toInterface) {
		// toInterface = false: https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.3
		// toInterface = true: https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.4
		// TODO: access check after resolution

		assert asmNodes == null || isInterface() == toInterface;

		if (!toInterface) {
			MethodInstance ret = resolveSignaturePolymorphicMethod(name);
			if (ret != null) return ret;

			ret = getMethod(name, desc);
			if (ret != null) return ret; // <this> is unconditional

			ClassInstance cls = this;

			while ((cls = cls.superClass) != null) {
				ret = cls.resolveSignaturePolymorphicMethod(name);
				if (ret != null) return ret;

				ret = cls.getMethod(name, desc);
				if (ret != null) return ret;
			}

			return resolveInterfaceMethod(name, desc);
		} else {
			MethodInstance ret = getMethod(name, desc);
			if (ret != null) return ret; // <this> is unconditional

			if (superClass != null) {
				assert superClass.id.equals("Ljava/lang/Object;");

				ret = superClass.getMethod(name, desc);
				if (ret != null && (ret.asmNode == null || (ret.asmNode.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC)) return ret;
			}

			return resolveInterfaceMethod(name, desc);
		}
	}

	private MethodInstance resolveSignaturePolymorphicMethod(String name) {
		if (id.equals("Ljava/lang/invoke/MethodHandle;")) { // check for signature polymorphic method - jvms-2.9
			MethodInstance ret = getMethod(name, "([Ljava/lang/Object;)Ljava/lang/Object;");
			final int reqFlags = Opcodes.ACC_VARARGS | Opcodes.ACC_NATIVE;

			if (ret != null && (ret.asmNode == null || (ret.asmNode.access & reqFlags) == reqFlags)) {
				return ret;
			}
		}

		return null;
	}

	private MethodInstance resolveInterfaceMethod(String name, String desc) {
		Queue<ClassInstance> queue = new ArrayDeque<>();
		Set<ClassInstance> queued = Util.newIdentityHashSet();
		ClassInstance cls = this;

		do {
			for (ClassInstance ifCls : cls.interfaces) {
				if (queued.add(ifCls)) queue.add(ifCls);
			}
		} while ((cls = cls.superClass) != null);

		if (queue.isEmpty()) return null;

		Set<MethodInstance> matches = Util.newIdentityHashSet();
		boolean foundNonAbstract = false;

		while ((cls = queue.poll()) != null) {
			MethodInstance ret = cls.getMethod(name, desc);

			if (ret != null
					&& (ret.asmNode == null || (ret.asmNode.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0)) {
				matches.add(ret);

				if (ret.asmNode != null && (ret.asmNode.access & Opcodes.ACC_ABSTRACT) == 0) { // jvms prefers the closest non-abstract method
					foundNonAbstract = true;
				}
			}

			for (ClassInstance ifCls : cls.interfaces) {
				if (queued.add(ifCls)) queue.add(ifCls);
			}
		}

		if (matches.isEmpty()) return null;
		if (matches.size() == 1) return matches.iterator().next();

		// non-abstract methods take precedence over non-abstract methods, remove all abstract ones if there's at least 1 non-abstract

		if (foundNonAbstract) {
			for (Iterator<MethodInstance> it = matches.iterator(); it.hasNext(); ) {
				MethodInstance m = it.next();

				if (m.asmNode == null || (m.asmNode.access & Opcodes.ACC_ABSTRACT) != 0) {
					it.remove();
				}
			}

			assert !matches.isEmpty();
			if (matches.size() == 1) return matches.iterator().next();
		}

		// eliminate not maximally specific method declarations, i.e. those that have a child method in matches

		for (Iterator<MethodInstance> it = matches.iterator(); it.hasNext(); ) {
			MethodInstance m = it.next();

			cmpLoop: for (MethodInstance m2 : matches) {
				if (m2 == m) continue;

				if (m2.cls.interfaces.contains(m.cls)) { // m2 is a direct child of m, so m isn't maximally specific
					it.remove();
					break;
				}

				queue.addAll(m2.cls.interfaces);

				while ((cls = queue.poll()) != null) {
					if (cls.interfaces.contains(m.cls)) { // m2 is an indirect child of m, so m isn't maximally specific
						it.remove();
						queue.clear();
						break cmpLoop;
					}

					queue.addAll(cls.interfaces);
				}
			}
		}

		// return an arbitrary choice

		return matches.iterator().next();
	}

	public FieldInstance resolveField(String name, String desc) {
		FieldInstance ret = getField(name, desc);
		if (ret != null) return ret;

		if (!interfaces.isEmpty()) {
			Deque<ClassInstance> queue = new ArrayDeque<>();
			queue.addAll(interfaces);
			ClassInstance cls;

			while ((cls = queue.pollFirst()) != null) {
				ret = cls.getField(name, desc);
				if (ret != null) return ret;

				for (ClassInstance iface : cls.interfaces) {
					queue.addFirst(iface);
				}
			}
		}

		ClassInstance cls = superClass;

		while (cls != null) {
			ret = cls.getField(name, desc);
			if (ret != null) return ret;

			cls = cls.superClass;
		}

		return null;
	}

	public MethodInstance getMethod(int pos) {
		if (pos < 0 || pos >= methods.length) throw new IndexOutOfBoundsException();
		if (asmNodes == null) throw new UnsupportedOperationException();

		return methods[pos];
	}

	public FieldInstance getField(int pos) {
		if (pos < 0 || pos >= fields.length) throw new IndexOutOfBoundsException();
		if (asmNodes == null) throw new UnsupportedOperationException();

		return fields[pos];
	}

	public MethodInstance[] getMethods() {
		return methods;
	}

	public FieldInstance[] getFields() {
		return fields;
	}

	public ClassInstance getOuterClass() {
		return outerClass;
	}

	public Set<ClassInstance> getInnerClasses() {
		return innerClasses;
	}

	public ClassInstance getSuperClass() {
		return superClass;
	}

	public Set<ClassInstance> getChildClasses() {
		return childClasses;
	}

	public Set<ClassInstance> getInterfaces() {
		return interfaces;
	}

	public Set<ClassInstance> getImplementers() {
		return implementers;
	}

	public Set<MethodInstance> getMethodTypeRefs() {
		return methodTypeRefs;
	}

	public Set<FieldInstance> getFieldTypeRefs() {
		return fieldTypeRefs;
	}

	public Set<String> getStrings() {
		return strings;
	}

	public boolean isShared() {
		return matchedClass == this;
	}

	public boolean hasMappedName() {
		return mappedName != null
				|| matchedClass != null && matchedClass.mappedName != null
				|| elementClass != null && elementClass.hasMappedName();
	}

	@Override
	public String getMappedName(boolean defaultToUnmapped) {
		if (mappedName != null) {
			return mappedName;
		} else if (matchedClass != null && matchedClass.mappedName != null) {
			return matchedClass.mappedName;
		} else if (elementClass != null) {
			return elementClass.getMappedName(defaultToUnmapped);
		} else if (this.outerClass != null && outerClass != this && outerClass.hasMappedName()){
			String myName = getName();
			String outerMapped = this.outerClass.getMappedName(true);
			String outerUnMapped = this.outerClass.getName();
			if (myName.startsWith(outerUnMapped)){
				myName = outerMapped+myName.substring(outerUnMapped.length());
			}
			return myName;
		} else if (defaultToUnmapped) {
			return getName();
		} else {
			return null;
		}
	}

	public void setMappedName(String mappedName) {
		this.mappedName = mappedName;
	}

	public String getMappedComment() {
		return mappedComment;
	}

	public void setMappedComment(String comment) {
		if (comment != null && comment.isEmpty()) comment = null;

		this.mappedComment = comment;
	}

	public boolean isAssignableFrom(ClassInstance c) {
		if (c == this) return true;
		if (isPrimitive()) return false;

		if (!isInterface()) {
			ClassInstance sc = c;

			while ((sc = sc.superClass) != null) {
				if (sc == this) return true;
			}
		} else {
			if (implementers.isEmpty()) return false;

			// check if c directly implements this
			if (implementers.contains(c)) return true;

			// check if a superclass of c directly implements this
			ClassInstance sc = c;

			while ((sc = sc.superClass) != null) {
				if (implementers.contains(sc)) return true; // cls -> this
			}

			// check if c or a superclass of c implements this with one indirection
			sc = c;
			Queue<ClassInstance> toCheck = null;

			do {
				for (ClassInstance iface : sc.getInterfaces()) {
					assert iface != this; // already checked iface directly
					if (iface.interfaces.isEmpty()) continue;
					if (implementers.contains(iface)) return true; // cls -> if -> this

					if (toCheck == null) toCheck = new ArrayDeque<>();

					toCheck.addAll(iface.interfaces);
				}
			} while ((sc = sc.superClass) != null);

			// check if c or a superclass of c implements this with multiple indirections
			if (toCheck != null) {
				while ((sc = toCheck.poll()) != null) {
					for (ClassInstance iface : sc.getInterfaces()) {
						assert iface != this; // already checked

						if (implementers.contains(iface)) return true;

						toCheck.addAll(iface.interfaces);
					}
				}
			}
		}

		return false;
	}

	public ClassInstance getCommonSuperClass(ClassInstance o) {
		if (o == this) return this;
		if (isPrimitive() || o.isPrimitive()) return null;
		if (isAssignableFrom(o)) return this;
		if (o.isAssignableFrom(this)) return o;

		ClassInstance objCls = env.getCreateClassInstance("Ljava/lang/Object;");

		if (!isInterface() && !o.isInterface()) {
			ClassInstance sc = this;

			while ((sc = sc.superClass) != null && sc != objCls) {
				if (sc.isAssignableFrom(o)) return sc;
			}
		}

		if (!interfaces.isEmpty() || !o.interfaces.isEmpty()) {
			List<ClassInstance> ret = new ArrayList<>();
			Queue<ClassInstance> toCheck = new ArrayDeque<>();
			Set<ClassInstance> checked = Util.newIdentityHashSet();
			toCheck.addAll(interfaces);
			toCheck.addAll(o.interfaces);

			ClassInstance cls;

			while ((cls = toCheck.poll()) != null) {
				if (!checked.add(cls)) continue;

				if (cls.isAssignableFrom(o)) {
					ret.add(cls);
				} else {
					toCheck.addAll(cls.interfaces);
				}
			}

			if (!ret.isEmpty()) {
				if (ret.size() >= 1) {
					for (Iterator<ClassInstance> it = ret.iterator(); it.hasNext(); ) {
						cls = it.next();

						for (ClassInstance cls2 : ret) {
							if (cls != cls2 && cls.isAssignableFrom(cls2)) {
								it.remove();
								break;
							}
						}
					}
					// TODO: multiple options..
				}

				return ret.get(0);
			}
		}

		return objCls;
	}

	@Override
	public String toString() {
		return getDisplayName(true, false);
	}

	void addMethod(MethodInstance method) {
		if (method == null) throw new NullPointerException("null method");

		methodIdx.put(method.id, method);
		methods = Arrays.copyOf(methods, methods.length + 1);
		methods[methods.length - 1] = method;
	}

	void addField(FieldInstance field) {
		if (field == null) throw new NullPointerException("null field");

		fieldIdx.put(field.id, field);
		fields = Arrays.copyOf(fields, fields.length + 1);
		fields[fields.length - 1] = field;
	}

	void addAnnotation(String annotation){
		this.annotations.add(annotation);
	}

	public Set<String> getAnnotations(){
		return annotations;
	}

	public static String getId(String name) {
		if (name.isEmpty()) throw new IllegalArgumentException("empty class name");
		assert name.charAt(name.length() - 1) != ';' || name.charAt(0) == '[' : name;

		if (name.charAt(0) == '[') {
			assert name.charAt(name.length() - 1) == ';' || name.lastIndexOf('[') == name.length() - 2;

			return name;
		}

		return "L"+name+";";
	}

	public static String getName(String id) {
		return id.startsWith("L") ? id.substring(1, id.length() - 1) : id;
	}

	public Map<String, String> getEnumValues() {
		return enumValues;
	}

	private static final ClassInstance[] noArrays = new ClassInstance[0];
	private static final MethodInstance[] noMethods = new MethodInstance[0];
	private static final FieldInstance[] noFields = new FieldInstance[0];

	final String id;
	final URI uri;
	final IClassEnv env;
	private ClassNode[] asmNodes;
	final boolean nameObfuscated;
	private final boolean input;
	final ClassInstance elementClass; // TODO: improve handling of array classes (references etc.)

	MethodInstance[] methods = noMethods;
	FieldInstance[] fields = noFields;
	final Map<String, MethodInstance> methodIdx = new HashMap<>();
	final Map<String, FieldInstance> fieldIdx = new HashMap<>();

	private ClassInstance[] arrays = noArrays;

	ClassInstance outerClass;
	final Set<ClassInstance> innerClasses = Util.newIdentityHashSet();

	ClassInstance superClass;
	final Set<ClassInstance> childClasses = Util.newIdentityHashSet();
	final Set<ClassInstance> interfaces = Util.newIdentityHashSet();
	final Set<ClassInstance> implementers = Util.newIdentityHashSet();

	final Set<MethodInstance> methodTypeRefs = Util.newIdentityHashSet();
	final Set<FieldInstance> fieldTypeRefs = Util.newIdentityHashSet();

	final Set<String> strings = new HashSet<>();
	final Map<String,String> enumValues = new HashMap<>();//REAL enum values, pulled from java.lang.Enum's constructor param

	String mappedName;
	String mappedComment;
	ClassInstance matchedClass;
	final Set<String> annotations = new TreeSet<>(Comparator.naturalOrder());
}